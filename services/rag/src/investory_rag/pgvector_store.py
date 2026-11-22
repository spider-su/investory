"""PostgreSQL documentation store with exact cosine search and atomic indexing."""

import json
from contextlib import contextmanager
from dataclasses import dataclass, replace

import psycopg
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from pydantic import SecretStr


@dataclass(frozen=True)
class PgVectorStore:
    dsn: SecretStr
    collection: str
    embeddings: Embeddings
    transaction: psycopg.Connection | None = None

    @contextmanager
    def _connection(self):
        if self.transaction is not None:
            yield self.transaction
        else:
            with psycopg.connect(
                self.dsn.get_secret_value(), connect_timeout=3,
                options="-c statement_timeout=10000",
            ) as connection:
                yield connection

    @contextmanager
    def index_transaction(self):
        with self._connection() as connection:
            locked = connection.execute(
                "SELECT pg_try_advisory_xact_lock(hashtextextended(%s, 0))",
                ("investory_rag:" + self.collection,),
            ).fetchone()[0]
            if not locked:
                raise RuntimeError("This PostgreSQL documentation collection is already being indexed")
            yield replace(self, transaction=connection)

    def get(self, *, include, limit=None):
        if include:
            raise ValueError("The document store only supports listing chunk IDs")
        with self._connection() as connection:
            rows = connection.execute(
                "SELECT id FROM investory_rag.chunks WHERE collection = %s LIMIT %s",
                (self.collection, limit),
            ).fetchall()
        return {"ids": [row[0] for row in rows]}

    def add_documents(self, *, documents: list[Document], ids: list[str]):
        vectors = self.embeddings.embed_documents([document.page_content for document in documents])
        rows = [
            (self.collection, chunk_id, document.metadata["source"],
             document.metadata["chunk_index"], document.page_content, json.dumps(vector, allow_nan=False))
            for chunk_id, document, vector in zip(ids, documents, vectors, strict=True)
        ]
        with self._connection() as connection, connection.cursor() as cursor:
            cursor.executemany(
                """
                INSERT INTO investory_rag.chunks
                    (collection, id, source, chunk_index, content, embedding)
                VALUES (%s, %s, %s, %s, %s, %s::public.vector)
                ON CONFLICT (collection, id) DO NOTHING
                """,
                rows,
            )

    def delete(self, *, ids: list[str]):
        with self._connection() as connection:
            connection.execute(
                "DELETE FROM investory_rag.chunks WHERE collection = %s AND id = ANY(%s)",
                (self.collection, ids),
            )

    def similarity_search_with_relevance_scores(self, question: str, k: int):
        query = json.dumps(self.embeddings.embed_query(question), allow_nan=False)
        with self._connection() as connection:
            rows = connection.execute(
                """
                SELECT source, chunk_index, content,
                       embedding OPERATOR(public.<=>) %s::public.vector AS distance
                FROM investory_rag.chunks
                WHERE collection = %s
                ORDER BY distance, id
                LIMIT %s
                """,
                (query, self.collection, k),
            ).fetchall()
        return [
            (Document(page_content=content, metadata={"source": source, "chunk_index": index}),
             1 - distance)
            for source, index, content, distance in rows
        ]
