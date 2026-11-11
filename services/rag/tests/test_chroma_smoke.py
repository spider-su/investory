"""End-to-end local Chroma smoke using normalized embeddings (no API key)."""
from math import sqrt
from langchain_chroma import Chroma
from langchain_core.embeddings import Embeddings

from investory_rag.config import Settings
from investory_rag.service import RagService


class KeywordEmbeddings(Embeddings):
    words = ("reserve", "bond", "cash", "contribution", "withdrawal", "portfolio", "retirement")

    def embed_documents(self, texts):
        return [self.embed_query(t) for t in texts]

    def embed_query(self, text):
        text = text.lower()
        values = [float(text.count(word)) for word in self.words] + [0.01]
        norm = sqrt(sum(value * value for value in values))
        return [value / norm for value in values]


def test_chroma_index_retrieval_and_reindex(tmp_path):
    root = tmp_path / "docs"
    root.mkdir()
    (root / "retirement.md").write_text(
        "Retirement reserve consists of cash and bonds. Reserve cash bond retirement."
    )
    (root / "accounting.md").write_text(
        "Portfolio contribution is not an internal transfer. Portfolio contribution."
    )
    settings = Settings(docs_root=root, db_path=tmp_path / "vectors",
                        min_relevance_score=0, _env_file=None)
    store = Chroma(collection_name="rag_test", embedding_function=KeywordEmbeddings(),
                   persist_directory=str(settings.db_path))
    rag = RagService(settings, store, None)
    assert rag.index()["added"] == 2
    assert rag.index()["reused"] == 2
    assert rag.retrieve("retirement reserve cash")[0]["source"] == "retirement.md"
    (root / "accounting.md").unlink()
    assert rag.index()["removed"] == 1
    assert store._collection.count() == 1
