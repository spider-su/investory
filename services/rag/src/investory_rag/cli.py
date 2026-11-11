"""Explicit indexing command: never runs implicitly on app boot."""
import argparse

from .config import get_settings
from .service import RagService


def main() -> None:
    parser = argparse.ArgumentParser(description="Index local Investory Markdown documentation")
    parser.parse_args()
    result = RagService.build(get_settings()).index()
    print(f"Indexed {result['documents']} documents into {result['chunks']} chunks")


if __name__ == "__main__":
    main()
