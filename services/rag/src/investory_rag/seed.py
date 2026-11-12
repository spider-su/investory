"""Restore an image-built documentation index atomically before opening Chroma."""
import fcntl
import json
import os
from pathlib import Path
import shutil
import tempfile

from .config import Settings
from .embeddings import collection_name

MANIFEST = "seed-manifest.json"


def write_manifest(path: Path, settings: Settings) -> None:
    (path / MANIFEST).write_text(json.dumps({"collection": collection_name(settings)}))


def restore_seed(settings: Settings) -> bool:
    seed = settings.index_seed
    if seed is None:
        return False
    if json.loads((seed / MANIFEST).read_text())["collection"] != collection_name(settings):
        return False
    destination = settings.db_path
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Separate lock survives the atomic directory rename; never overwrite an active index.
    with (destination.parent / ".rag-seed.lock").open("a+b") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        if destination.exists() and any(destination.iterdir()):
            return False
        staging = Path(tempfile.mkdtemp(prefix=".rag-seed-", dir=destination.parent))
        try:
            shutil.copytree(seed, staging, dirs_exist_ok=True)
            if destination.exists():
                destination.rmdir()
            os.replace(staging, destination)
        finally:
            if staging.exists():
                shutil.rmtree(staging)
    return True
