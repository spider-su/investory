from pathlib import Path
from types import SimpleNamespace

from langchain_core.documents import Document

from investory_rag.config import Settings
from investory_rag.service import NO_EVIDENCE, RagService


class FakeStore:
    def __init__(self):
        self.documents = {}
        self.search_results = []

    def get(self, include=None, limit=None):
        ids = list(self.documents)
        return {"ids": ids[:limit] if limit is not None else ids}

    def add_documents(self, documents, ids):
        self.documents.update(zip(ids, documents))

    def delete(self, ids):
        for key in ids:
            self.documents.pop(key)

    def similarity_search_with_relevance_scores(self, question, k):
        return self.search_results[:k]


class FakeLLM:
    def __init__(self):
        self.calls = []

    def __ror__(self, other):
        raise AssertionError("Model should never be invoked for unsupported answers")


def create_service(tmp_path: Path):
    docs = tmp_path / "docs"
    docs.mkdir()
    settings = Settings(
        docs_root=docs,
        db_path=tmp_path / "db",
        chunk_size=100,
        chunk_overlap=10,
        min_relevance_score=0.5,
        _env_file=None,
    )
    return RagService(settings, FakeStore(), FakeLLM()), docs


def test_index_reuses_existing_chunks_and_removes_deleted(tmp_path):
    service, docs = create_service(tmp_path)
    file = docs / "retirement.md"
    file.write_text("Investory has a five-year reserve target.")
    first = service.index()
    assert first["added"] == 1
    assert first["reused"] == 0
    assert first["removed"] == 0

    second = service.index()
    assert second["added"] == 0
    assert second["reused"] == 1

    file.write_text("Investory has a six-year reserve target.")
    third = service.index()
    assert third["added"] == 1
    assert third["removed"] == 1
    assert third["reused"] == 0

    file.unlink()
    last = service.index()
    assert last["chunks"] == 0
    assert last["removed"] == 1
    assert not service.store.documents


def test_unsupported_question_does_not_invoke_llm(tmp_path):
    service, docs = create_service(tmp_path)
    (docs / "retirement.md").write_text("Reserve strategy and funding.")
    service.index()
    service.store.search_results = [
        (Document(page_content="Unrelated", metadata={"source": "x.md"}), 0.2)
    ]
    result = service.ask("What is my live brokerage balance?")
    assert result == {"answer": NO_EVIDENCE, "sources": []}


def test_retrieve_preserves_source_and_score(tmp_path):
    service, docs = create_service(tmp_path)
    (docs / "retirement.md").write_text("Reserve strategy.")
    service.index()
    service.store.search_results = [
        (Document(page_content="Five years.", metadata={"source": "retirement.md"}), 0.9),
        (Document(page_content="Wrong topic", metadata={"source": "other.md"}), 0.1),
    ]
    assert service.retrieve("reserve") == [
        {"source": "retirement.md", "text": "Five years.", "score": 0.9}
    ]
