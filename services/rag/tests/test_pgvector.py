"""Integration checks against a disposable PostgreSQL 16 database with pgvector."""

import os
from pathlib import Path

import psycopg
import pytest
from langchain_core.embeddings import Embeddings
from psycopg.conninfo import make_conninfo

from investory_rag.config import Settings
from investory_rag.service import RagService

MIGRATION = (
    Path(__file__).resolve().parents[3]
    / "app/src/main/resources/sql/migration/rag-pgvector/V1__documentation_chunks.pgsql"
)


class TestEmbeddings(Embeddings):
    __test__ = False

    def embed_documents(self, texts):
        return [self.embed_query(text) for text in texts]

    def embed_query(self, text):
        return [1., 0.] if "reserve" in text else [0., 1.]


@pytest.fixture(scope="module")
def database():
    dsn = os.environ.get("TEST_RAG_PGVECTOR_DSN")
    if not dsn:
        pytest.skip("A disposable pgvector database is required")
    with psycopg.connect(dsn) as connection:
        if connection.execute("SELECT current_database()").fetchone()[0] != "rag_pgvector_test":
            pytest.fail("TEST_RAG_PGVECTOR_DSN must target the disposable rag_pgvector_test database")
        connection.execute("CREATE SCHEMA investory_rag")
        connection.execute(MIGRATION.read_text())
        connection.execute("""
            CREATE SCHEMA investory;
            CREATE TABLE investory.private_fixture (amount integer);
            CREATE ROLE rag_test_reader LOGIN PASSWORD 'reader-fixture';
            CREATE ROLE rag_test_writer LOGIN PASSWORD 'writer-fixture';
            GRANT USAGE ON SCHEMA investory_rag TO rag_test_reader, rag_test_writer;
            GRANT SELECT ON investory_rag.chunks TO rag_test_reader, rag_test_writer;
            GRANT INSERT, DELETE ON investory_rag.chunks TO rag_test_writer;
        """)
    return dsn


def test_pgvector_dsn_required():
    with pytest.raises(ValueError, match="RAG_PGVECTOR_DSN is required"):
        RagService.build(Settings(vector_backend="pgvector", _env_file=None))


def test_indexing_is_atomic_and_reader_is_scoped(database, tmp_path, monkeypatch):
    docs = tmp_path / "docs"
    docs.mkdir()
    source = docs / "retirement.md"
    source.write_text("A retirement reserve funds living expenses.")
    embeddings = TestEmbeddings()
    monkeypatch.setattr("investory_rag.service.build_embeddings", lambda _: embeddings)

    settings = Settings(
        _env_file=None, vector_backend="pgvector", docs_root=docs,
        index_batch_size=1,
        pgvector_dsn=make_conninfo(database, user="rag_test_writer", password="writer-fixture"),
    )
    writer = RagService.build(settings)
    assert writer.index()["added"] == 1
    assert writer.index()["reused"] == 1

    reader_dsn = make_conninfo(database, user="rag_test_reader", password="reader-fixture")
    reader = RagService.build(Settings(
        _env_file=None, vector_backend="pgvector", pgvector_dsn=reader_dsn,
    ))
    assert reader.search("How does the retirement reserve work?")[0]["source"] == "retirement.md"
    assert reader.search("Should I buy SPY instead of my investments?") == []

    with writer.store.index_transaction():
        with pytest.raises(RuntimeError, match="already being indexed"):
            writer.index()

    with psycopg.connect(reader_dsn) as connection:
        assert not connection.execute(
            "SELECT has_table_privilege(current_user,'investory_rag.chunks','INSERT')"
        ).fetchone()[0]
    with pytest.raises(psycopg.errors.InsufficientPrivilege), psycopg.connect(reader_dsn) as connection:
        connection.execute("SELECT amount FROM investory.private_fixture")

    original_ids = reader.store.get(include=[])["ids"]
    source.write_text("The retirement reserve covers several years of expenses.")
    (docs / "portfolio.md").write_text("The portfolio accounting policy handles transfers.")
    original_embed = embeddings.embed_documents
    calls = []

    def fail_second_batch(texts):
        calls.append(texts)
        if len(calls) == 2:
            raise ValueError("synthetic embedding failure")
        return original_embed(texts)

    with monkeypatch.context() as patch:
        patch.setattr(embeddings, "embed_documents", fail_second_batch)
        with pytest.raises(ValueError, match="synthetic embedding failure"):
            writer.index()
    assert len(calls) == 2
    assert reader.store.get(include=[])["ids"] == original_ids

    assert writer.index()["removed"] == 1
    result = reader.search("How does the retirement reserve work?")
    assert result[0]["source"] == "retirement.md"
    assert "several years" in result[0]["text"]
