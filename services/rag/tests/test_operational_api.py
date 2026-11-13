import pytest
from concurrent.futures import ThreadPoolExecutor
from threading import Event

from fastapi.testclient import TestClient
from pydantic import SecretStr

from investory_rag.config import Settings
from investory_rag.main import app, get_access_gate, get_service_provider
from investory_rag.security import AccessGate


class Stub:
    def ask(self, question):
        return {"answer": "Documented [a.md]", "sources": ["a.md"]}


class SlowStub:
    def __init__(self, started, release):
        self.started = started
        self.release = release

    def ask(self, question):
        self.started.set()
        self.release.wait(timeout=3)
        return {"answer": "Documented [a.md]", "sources": ["a.md"]}


def test_authenticated_metrics_expose_only_fixed_labels():
    app.dependency_overrides[get_service_provider] = lambda: lambda: Stub()
    app.dependency_overrides[get_access_gate] = lambda: AccessGate(
        Settings(api_token=SecretStr("inference-secret"),
                 metrics_token=SecretStr("metrics-secret"), _env_file=None)
    )
    try:
        client = TestClient(app)
        assert client.get("/metrics").status_code == 401
        assert client.get("/metrics", headers={"Authorization": "Bearer inference-secret"}).status_code == 401
        headers = {"Authorization": "Bearer metrics-secret"}
        assert client.post("/ask", json={"question": "SECRET_BALANCE_999"},
                           headers={"Authorization": "Bearer inference-secret"}).status_code == 200
        response = client.get("/metrics", headers=headers)
        assert response.status_code == 200
        text = response.text
        assert "investory_rag_requests_total" in text
        assert "investory_rag_request_seconds" in text
        assert "SECRET_BALANCE_999" not in text
        assert "metrics-secret" not in text
        assert "inference-secret" not in text
    finally:
        app.dependency_overrides.clear()


def test_timeout_keeps_concurrency_slot_until_background_worker_completes():
    started, release = Event(), Event()
    app.dependency_overrides[get_service_provider] = lambda: lambda: SlowStub(started, release)
    shared_gate = AccessGate(
        Settings(api_token=SecretStr("timeout-secret"),
                 request_deadline_seconds=0.05, max_concurrent_requests=1,
                 _env_file=None)
    )
    app.dependency_overrides[get_access_gate] = lambda: shared_gate
    try:
        with TestClient(app) as client, ThreadPoolExecutor(max_workers=2) as pool:
            headers = {"Authorization": "Bearer timeout-secret"}
            def post():
                return client.post("/ask", json={"question": "reserve withdrawal"},
                                   headers=headers)
            first = pool.submit(post)
            assert started.wait(timeout=2)
            try:
                # Parallel request must not enter while first provider worker is active.
                assert post().status_code == 429
                assert first.result(timeout=2).status_code == 504
            finally:
                release.set()
    finally:
        app.dependency_overrides.clear()


def test_slow_initialization_is_inside_deadline_and_admission_gate():
    started, release = Event(), Event()
    calls = []
    def initialize():
        calls.append(1)
        started.set()
        release.wait(timeout=3)
        return Stub()
    app.dependency_overrides[get_service_provider] = lambda: initialize
    gate = AccessGate(Settings(api_token=SecretStr("init-secret"),
                               request_deadline_seconds=0.05, _env_file=None))
    app.dependency_overrides[get_access_gate] = lambda: gate
    try:
        with TestClient(app) as client:
            headers = {"Authorization": "Bearer init-secret"}
            try:
                first = client.post("/ask", json={"question": "reserve policy"}, headers=headers)
                assert started.is_set() and first.status_code == 504
                assert client.post("/ask", json={"question": "reserve policy"}, headers=headers).status_code == 429
                assert len(calls) == 1
            finally:
                release.set()
    finally:
        app.dependency_overrides.clear()


@pytest.mark.parametrize("cancelled", [False, True])
def test_interrupted_queued_worker_eventually_releases_capacity(cancelled):
    import asyncio
    import pytest
    from fastapi import HTTPException
    from investory_rag.main import run_bounded
    release, completed = Event(), Event()
    gate = AccessGate(Settings(request_deadline_seconds=5 if cancelled else 0.05, _env_file=None))
    def operation():
        completed.set()
        return {"answer": "done", "sources": []}
    async def scenario():
        loop = asyncio.get_running_loop()
        loop.set_default_executor(ThreadPoolExecutor(max_workers=1))
        blocker = loop.run_in_executor(None, release.wait)
        try:
            request = asyncio.create_task(run_bounded("documents", gate, operation))
            if cancelled:
                await asyncio.sleep(0.02)
                request.cancel()
            with pytest.raises(asyncio.CancelledError if cancelled else HTTPException) as error:
                await request
            if not cancelled:
                assert error.value.status_code == 504
            with pytest.raises(HTTPException) as busy:
                gate.acquire()
            assert busy.value.status_code == 429
            release.set()
            await blocker
            for _ in range(100):
                if completed.is_set():
                    break
                await asyncio.sleep(0.01)
            assert completed.is_set()
            # Let the worker's finally run after setting its completion marker.
            await asyncio.sleep(0.02)
            gate.acquire()
            gate.release()
        finally:
            release.set()
    asyncio.run(scenario())


@pytest.mark.parametrize("kind", ["model", "gate"])
def test_concurrent_initialization_builds_only_one_singleton(monkeypatch, kind):
    from investory_rag import main
    started, release = Event(), Event()
    calls = []
    instance = Stub()
    def build(_):
        calls.append(1)
        started.set()
        release.wait(timeout=3)
        return instance
    cached, get = ((main._build_service, main.get_service) if kind == "model"
                   else (main._build_access_gate, main.get_access_gate))
    cached.cache_clear()
    if kind == "model":
        monkeypatch.setattr(main.RagService, "build", build)
    else:
        monkeypatch.setattr(main, "AccessGate", build)
    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            first = pool.submit(get)
            assert started.wait(timeout=2)
            second = pool.submit(get)
            release.set()
            assert first.result(timeout=2) is second.result(timeout=2) is instance
            assert calls == [1]
    finally:
        release.set()
        cached.cache_clear()
