from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="RAG_", env_file=".env", extra="ignore")

    docs_root: Path = Path("../../docs")
    db_path: Path = Path("./data/chroma")
    collection: str = "investory_docs"
    embedding_model: str = "text-embedding-3-small"
    chat_model: str = "gpt-4.1-mini"
    chunk_size: int = Field(default=900, gt=0)
    chunk_overlap: int = Field(default=150, ge=0)
    index_batch_size: int = Field(default=64, ge=1, le=1000)
    top_k: int = Field(default=4, ge=1, le=20)
    min_relevance_score: float = Field(default=0.3, ge=0, le=1)
    max_question_chars: int = Field(default=2000, ge=1, le=2000)
    api_token: SecretStr | None = None
    max_concurrent_requests: int = Field(default=1, ge=1, le=16)
    openai_timeout_seconds: float = Field(default=20, gt=0, le=120)


@lru_cache
def get_settings() -> Settings:
    return Settings()
