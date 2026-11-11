import json

import pytest

from investory_rag.evaluation import EvaluationCase, evaluate, load_cases


def test_source_hit_and_abstention():
    cases = [
        EvaluationCase("reserve?", ("domain/retirement-simulation.md",)),
        EvaluationCase("live value?", (), False),
        EvaluationCase("accounting?", ("domain/portfolio-accounting.md",)),
    ]
    def retrieve(question):
        if question == "reserve?":
            return [{"source": "domain/retirement-simulation.md"}]
        if question == "accounting?":
            return [{"source": "unrelated.md"}]
        return []

    result = evaluate(cases, retrieve)
    assert result["cases"] == 3
    assert result["source_hit_rate"] == 0.5
    assert result["abstention_accuracy"] == 1.0
    assert result["passed"] == 2


def test_requires_cases():
    with pytest.raises(ValueError):
        evaluate([], lambda _: [])


def test_loads_json_and_rejects_invalid(tmp_path):
    path = tmp_path / "cases.json"
    path.write_text(json.dumps([{
        "question": "Why?",
        "expected_sources": ["docs/one.md"],
    }]))
    assert load_cases(path)[0].question == "Why?"
    path.write_text(json.dumps([{"question": "", "expected_sources": []}]))
    with pytest.raises(ValueError):
        load_cases(path)


def test_negative_case_cannot_require_sources():
    with pytest.raises(ValueError):
        EvaluationCase.from_dict({
            "question": "Current balance",
            "expected_sources": ["portfolio.md"],
            "should_retrieve": False,
        })
