"""Offline seed lifecycle regression: empty replacement and rollback behavior."""
from pathlib import Path
from unittest.mock import patch

import pytest

from investory_rag.config import Settings
from investory_rag.seed import restore_seed, write_manifest


def make_seed(tmp_path: Path) -> Settings:
    seed = tmp_path / "seed"
    seed.mkdir()
    (seed / "chroma.sqlite3").write_bytes(b"version-1-public-index")
    config = Settings(
        embedding_provider="local",
        index_seed=seed,
        db_path=tmp_path / "pod-a" / "chroma",
        _env_file=None,
    )
    write_manifest(seed, config)
    return config


def test_cold_start_pod_replacement_and_existing_index_are_safe(tmp_path):
    config = make_seed(tmp_path)
    assert restore_seed(config)
    assert (config.db_path / "chroma.sqlite3").read_bytes() == b"version-1-public-index"

    # A restart with an already populated volume MUST NOT overwrite its index.
    (config.db_path / "chroma.sqlite3").write_bytes(b"current-index")
    assert not restore_seed(config)
    assert (config.db_path / "chroma.sqlite3").read_bytes() == b"current-index"

    # New pod with empty writable volume can start from immutable image seed.
    config.db_path = tmp_path / "replacement-pod" / "chroma"
    assert restore_seed(config)
    assert (config.db_path / "chroma.sqlite3").read_bytes() == b"version-1-public-index"


def test_embedding_identity_mismatch_preserves_existing_state(tmp_path):
    config = make_seed(tmp_path)
    assert restore_seed(config)
    original = (config.db_path / "chroma.sqlite3").read_bytes()
    config.local_embedding_model = "different-model"
    assert not restore_seed(config)
    assert (config.db_path / "chroma.sqlite3").read_bytes() == original


def test_interrupted_cold_copy_can_retry_without_leaving_partial_index(tmp_path):
    config = make_seed(tmp_path)
    with patch("investory_rag.seed.shutil.copytree", side_effect=OSError("copy interrupted")):
        with pytest.raises(OSError):
            restore_seed(config)
    assert not config.db_path.exists()
    assert restore_seed(config)
    assert (config.db_path / "chroma.sqlite3").read_bytes() == b"version-1-public-index"
