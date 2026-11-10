"""Deterministic retrieval evaluation, without chat generation or portfolio data."""
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Any


@dataclass(frozen=True)
class EvaluationCase:
    question: str
    expected_sources: tuple[str, ...]
    should_retrieve: bool = True

    @classmethod
    def from_dict(cls, obj: dict[str, Any]) -> "EvaluationCase":
        question = obj.get("question")
        expected = obj.get("expected_sources")
        should_retrieve = obj.get("should_retrieve", True)
        if (not isinstance(question, str) or not question.strip()
                or not isinstance(expected, list)
                or any(not isinstance(x, str) or not x.strip() for x in expected)
                or not isinstance(should_retrieve, bool)
                or (should_retrieve and not expected)
                or (not should_retrieve and expected)):
            raise ValueError("Invalid retrieval evaluation case")
        return cls(question, tuple(expected), should_retrieve)


def load_cases(path: Path) -> list[EvaluationCase]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list) or not raw:
        raise ValueError("Evaluation dataset must be a non-empty JSON array")
    return [EvaluationCase.from_dict(item) for item in raw]


def evaluate(
    cases: list[EvaluationCase],
    retrieve: Callable[[str], list[dict]],
) -> dict[str, Any]:
    if not cases:
        raise ValueError("At least one evaluation case required")
    rows = []
    hit_count = 0
    eligible = 0
    correct_abstentions = 0
    abstention_cases = 0
    for case in cases:
        results = retrieve(case.question)
        actual = list(dict.fromkeys(row["source"] for row in results))
        hit = any(source in actual for source in case.expected_sources)
        abstained = len(actual) == 0
        if case.should_retrieve:
            eligible += 1
            hit_count += int(hit)
            passed = hit
        else:
            abstention_cases += 1
            correct_abstentions += int(abstained)
            passed = abstained
        rows.append({
            "question": case.question,
            "expected_sources": list(case.expected_sources),
            "retrieved_sources": actual,
            "passed": passed,
        })
    return {
        "cases": len(cases),
        "source_hit_rate": hit_count / eligible if eligible else None,
        "abstention_accuracy": (
            correct_abstentions / abstention_cases if abstention_cases else None
        ),
        "passed": sum(bool(row["passed"]) for row in rows),
        "results": rows,
    }
