"""Run evaluation against an indexed collection. Uses the configured local or provider embeddings."""
import argparse
import json
from pathlib import Path

from .config import get_settings
from .evaluation import evaluate, load_cases
from .service import RagService


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate document source retrieval")
    parser.add_argument(
        "--cases",
        type=Path,
        default=Path("eval/cases.json"),
        help="Path to evaluation JSON",
    )
    parser.add_argument(
        "--min-hit-rate",
        type=float,
        default=0,
        help="Fail with exit code 1 when source hit rate is lower",
    )
    parser.add_argument("--min-abstention-accuracy", type=float, default=0,
                        help="Fail if negative/ambiguous question abstention accuracy is lower")
    args = parser.parse_args()
    if not 0 <= args.min_abstention_accuracy <= 1:
        parser.error("--min-abstention-accuracy must be between 0 and 1")
    if not 0 <= args.min_hit_rate <= 1:
        parser.error("--min-hit-rate must be between 0 and 1")
    service = RagService.build(get_settings())
    if not service.store.get(include=[], limit=1)["ids"]:
        parser.error("Index is empty: run investory-rag-index first")
    report = evaluate(load_cases(args.cases), service.retrieve_evidence)
    print(json.dumps(report, indent=2))
    hit_rate = report["source_hit_rate"]
    if hit_rate is not None and hit_rate < args.min_hit_rate:
        raise SystemExit(1)
    abstention = report["abstention_accuracy"]
    if abstention is not None and abstention < args.min_abstention_accuracy:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
