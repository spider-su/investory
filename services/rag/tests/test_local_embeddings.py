from types import SimpleNamespace

import numpy as np
import pytest

from investory_rag.config import Settings
from investory_rag.embeddings import build_embeddings, collection_name
from investory_rag.service import RagService


def test_local_embeddings_use_bounded_cpu_without_provider_key(monkeypatch, tmp_path):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    calls = []

    class LocalModel:
        def __init__(self, **options):
            calls.append(options)

        def passage_embed(self, texts, batch_size):
            assert batch_size == 16
            return iter([np.array([1.0, 0.0])] * len(texts))

        def query_embed(self, text):
            return iter([np.array([0.0, 1.0])])

    monkeypatch.setattr("fastembed.TextEmbedding", LocalModel)
    def reject_remote(**options):
        raise AssertionError("Local vectors must not use a remote embedding API")
    monkeypatch.setattr("investory_rag.embeddings.OpenAIEmbeddings", reject_remote)
    embeddings = build_embeddings(Settings(embedding_provider="local", local_model_cache=tmp_path,
                                           local_files_only=True, _env_file=None))
    assert embeddings.embed_documents(["first", "second"]) == [[1.0, 0.0], [1.0, 0.0]]
    assert embeddings.embed_query("query") == [0.0, 1.0]
    assert calls[0]["threads"] == 1
    assert calls[0]["local_files_only"] is True
    assert calls[0]["providers"] == ["CPUExecutionProvider"]


def test_embedding_provider_and_model_have_separate_collections():
    local = Settings(embedding_provider="local", _env_file=None)
    remote = Settings(embedding_provider="openai", _env_file=None)
    alternate = local.model_copy(update={"local_embedding_model": "another-model"})
    assert len({collection_name(local), collection_name(remote), collection_name(alternate)}) == 3


def test_responses_blocks_preserve_answer_and_ignore_reasoning():
    message = SimpleNamespace(content=[{"type": "reasoning", "summary": "private reasoning"},
                                      {"type": "text", "text": "123 PLN [live:profile]"}])
    assert RagService.answer_text(message) == "123 PLN [live:profile]"
    with pytest.raises(ValueError):
        RagService.answer_text(SimpleNamespace(content=[]))


def test_chat_is_lazy_stateless_and_omits_temperature(monkeypatch):
    captured = []
    monkeypatch.setattr("investory_rag.service.ChatOpenAI",
                        lambda **options: captured.append(options) or object())
    service = RagService(Settings(chat_model="gpt-5-mini", chat_reasoning_effort="minimal",
                                  _env_file=None), object())
    assert captured == []
    assert service.chat() is service.chat()
    assert len(captured) == 1
    assert captured[0]["store"] is False
    assert captured[0]["use_responses_api"] is True
    assert captured[0]["temperature"] is None
    assert captured[0]["reasoning"] == {"effort": "minimal"}
