"""Explicit indexing command: never runs implicitly on app boot."""
import argparse

from .config import get_settings
from .service import RagService


def main() -> None:
    parser = argparse.ArgumentParser(description="Index local Investory Markdown documentation")
    parser.parse_args()
    settings = get_settings()
    result = RagService.build(settings).index()
    print(f"Indexed {result['documents']} documents into {result['chunks']} chunks; added {result['added']}, reused {result['reused']}")


if __name__ == "__main__":
    main()
