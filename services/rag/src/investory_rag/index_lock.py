"""Single-writer advisory file lock for local Chroma collection updates.

Requires a shared POSIX filesystem for all indexers using the same Chroma path.
The lock file is deliberately outside the collection data files.
"""
import fcntl
from contextlib import contextmanager
from pathlib import Path


@contextmanager
def exclusive_index_lock(db_path: Path):
    db_path.mkdir(parents=True, exist_ok=True)
    path = db_path / ".index.lock"
    with path.open("a+b") as handle:
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise RuntimeError("Another Investory RAG indexing process is active") from exc
        try:
            yield
        finally:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
