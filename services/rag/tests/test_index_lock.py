import pytest

from investory_rag.index_lock import exclusive_index_lock


def test_index_lock_prevents_second_writer_and_releases(tmp_path):
    with exclusive_index_lock(tmp_path):
        with pytest.raises(RuntimeError, match="Another"):
            with exclusive_index_lock(tmp_path):
                pass
    with exclusive_index_lock(tmp_path):
        pass
