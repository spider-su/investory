"""Local documentation ingestion; no broker data or arbitrary web URLs."""
import hashlib
from pathlib import Path

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter


def load_markdown(root: Path) -> list[Document]:
    root = root.resolve(strict=True)
    if not root.is_dir():
        raise ValueError("docs_root must be a directory")
    result = []
    for path in sorted(root.rglob("*.md")):
        if path.is_symlink() or not path.is_file():
            continue
        resolved = path.resolve()
        if not resolved.is_relative_to(root):
            continue
        content = resolved.read_text(encoding="utf-8")
        if not content.strip():
            continue
        result.append(Document(
            page_content=content,
            metadata={"source": resolved.relative_to(root).as_posix()},
        ))
    return result


def chunk_documents(documents: list[Document], size: int, overlap: int) -> list[Document]:
    if size <= 0 or overlap < 0 or overlap >= size:
        raise ValueError("chunk size must be positive and overlap smaller than size")
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=size,
        chunk_overlap=overlap,
        separators=["\n## ", "\n### ", "\n\n", "\n", " ", ""],
    )
    return splitter.split_documents(documents)


def stable_id(document: Document) -> str:
    raw = (document.metadata["source"] + "\0" + document.page_content).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()
