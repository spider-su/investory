from pathlib import Path

import pytest
from langchain_core.documents import Document

from investory_rag.ingestion import chunk_documents, load_markdown, stable_id


def test_loads_only_markdown_under_root(tmp_path: Path):
    (tmp_path / "nested").mkdir()
    (tmp_path / "nested" / "strategy.md").write_text("Five-year reserve", encoding="utf-8")
    (tmp_path / "secret.txt").write_text("not indexed", encoding="utf-8")
    loaded = load_markdown(tmp_path)
    assert len(loaded) == 1
    assert loaded[0].metadata["source"] == "nested/strategy.md"
    assert loaded[0].page_content == "Five-year reserve"


def test_chunks_and_ids_are_deterministic():
    doc = Document(page_content="Investory retirement strategy " * 30, metadata={"source": "a.md"})
    first = chunk_documents([doc], size=100, overlap=10)
    second = chunk_documents([doc], size=100, overlap=10)
    assert len(first) > 1
    assert [stable_id(x) for x in first] == [stable_id(x) for x in second]


def test_invalid_split_parameters():
    with pytest.raises(ValueError):
        chunk_documents([], size=10, overlap=10)
