import pytest

from investory_rag.acceptance_cli import validate_answer


def test_acceptance_requires_live_provenance_and_nonempty_answer():
    validate_answer({"answer": "Facts explanation", "sources": ["live:profile"]})
    for payload in [
        {"answer": "", "sources": ["live:profile"]},
        {"answer": "Ungrounded answer", "sources": ["accounting.md"]},
        {"answer": "Answer", "sources": ["live:profile", None]},
    ]:
        with pytest.raises(ValueError):
            validate_answer(payload)
