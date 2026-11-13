"""Metrics are exposed on a separate authenticated port, never the inference API."""
from http.client import HTTPConnection

import pytest
from pydantic import SecretStr

from investory_rag.config import Settings
from investory_rag.metrics_listener import private_metrics_listener


def test_metrics_only_port_and_token_isolation():
    settings = Settings(metrics_listener_enabled=True, metrics_listener_port=0,
                        api_token=SecretStr("inference-only"),
                        metrics_token=SecretStr("scrape-only"), _env_file=None)
    with private_metrics_listener(settings) as server:
        def request(path, bearer=None, method="GET"):
            client = HTTPConnection("127.0.0.1", server.server_port, timeout=2)
            headers = {"Authorization": "Bearer " + bearer} if bearer else {}
            client.request(method, path, headers=headers)
            response = client.getresponse()
            result = (response.status, response.read().decode("utf-8"))
            client.close()
            return result
        assert request("/metrics")[0] == 401
        assert request("/metrics", "inference-only")[0] == 401
        assert request("/metrics", "invalid-\u00e9")[0] == 401
        status, body = request("/metrics", "scrape-only")
        assert status == 200
        assert "investory_rag_requests_total" in body
        assert "scrape-only" not in body
        assert "inference-only" not in body
        assert request("/ask", "scrape-only")[0] == 404
        assert request("/ask-with-facts", "scrape-only", method="POST")[0] == 405


def test_listener_fails_closed_without_scrape_token():
    settings = Settings(metrics_listener_enabled=True, metrics_listener_port=0,
                        metrics_token=None, _env_file=None)
    with pytest.raises(RuntimeError, match="dedicated metrics token"):
        with private_metrics_listener(settings):
            pytest.fail("Must not run without a dedicated token")


def test_listener_is_off_by_default():
    with private_metrics_listener(Settings(_env_file=None)) as server:
        assert server is None


def test_listener_rejects_reused_inference_token():
    settings = Settings(metrics_listener_enabled=True, metrics_listener_port=0,
                        api_token=SecretStr("same-secret"),
                        metrics_token=SecretStr("same-secret"), _env_file=None)
    with pytest.raises(RuntimeError, match="distinct"):
        with private_metrics_listener(settings):
            pytest.fail("A scrape credential must not grant inference access")
