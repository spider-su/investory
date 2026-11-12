from unittest.mock import patch

import pytest

from investory_rag.config import Settings
from investory_rag.seed import restore_seed, write_manifest


def settings(tmp_path):
    seed = tmp_path / "seed"
    seed.mkdir()
    (seed / "chroma.sqlite3").write_bytes(b"public documentation fixture")
    config = Settings(embedding_provider="local", index_seed=seed, db_path=tmp_path / "data" / "chroma")
    write_manifest(seed, config)
    return config


def test_restores_seed_and_preserves_existing_index(tmp_path):
    config = settings(tmp_path)
    assert restore_seed(config)
    assert (config.db_path / "chroma.sqlite3").read_bytes() == b"public documentation fixture"
    (config.db_path / "chroma.sqlite3").write_bytes(b"updated index")
    assert not restore_seed(config)
    assert (config.db_path / "chroma.sqlite3").read_bytes() == b"updated index"


def test_different_embedding_collection_does_not_restore(tmp_path):
    config = settings(tmp_path)
    config.local_embedding_model = "different-model"
    assert not restore_seed(config)
    assert not config.db_path.exists()


def test_failed_copy_leaves_destination_absent_and_removes_staging(tmp_path):
    config = settings(tmp_path)
    with patch("investory_rag.seed.shutil.copytree", side_effect=OSError("interrupted")):
        with pytest.raises(OSError):
            restore_seed(config)
    assert not config.db_path.exists()
    assert not list(config.db_path.parent.glob(".rag-seed-*"))


def test_empty_volume_directory_can_receive_seed(tmp_path):
    config = settings(tmp_path)
    config.db_path.mkdir(parents=True)
    assert restore_seed(config)
