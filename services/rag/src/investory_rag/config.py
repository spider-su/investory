from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="RAG_", env_file=".env", extra="ignore")

    docs_root: Path = Path("../../docs")
    db_path: Path = Path("./data/chroma")
    collection: str = "investory_docs"
    embedding_model: str = "text-embedding-3-small"
    chat_model: str = "gpt-4.1-mini"
    chunk_size: int = 900
    chunk_overlap: int = 150
    top_k: int = 4
    max_question_chars: int = 2000


@lru_cache
def get_settings() -> Settings:
    return Settings()
