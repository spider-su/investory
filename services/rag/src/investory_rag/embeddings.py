"""Bounded CPU embeddings; local vectors never require a provider API key."""
from hashlib import sha256

from langchain_core.embeddings import Embeddings
from langchain_openai import OpenAIEmbeddings

from .config import Settings


class LocalEmbeddings(Embeddings):
    def __init__(self, settings: Settings):
        from fastembed import TextEmbedding

        self.model = TextEmbedding(
            model_name=settings.local_embedding_model,
            cache_dir=str(settings.local_model_cache),
            threads=1,
            providers=["CPUExecutionProvider"],
            local_files_only=settings.local_files_only,
        )

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [vector.tolist() for vector in self.model.passage_embed(texts, batch_size=16)]

    def embed_query(self, text: str) -> list[float]:
        return next(iter(self.model.query_embed(text))).tolist()


def build_embeddings(settings: Settings) -> Embeddings:
    if settings.embedding_provider == "local":
        return LocalEmbeddings(settings)
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        request_timeout=settings.openai_timeout_seconds,
        max_retries=1,
    )


def collection_name(settings: Settings) -> str:
    model = (settings.local_embedding_model if settings.embedding_provider == "local"
             else settings.embedding_model)
    identity = f"cosine-v1:{settings.embedding_provider}:{model}"
    return f"{settings.collection}_{sha256(identity.encode()).hexdigest()[:12]}"
