FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*
COPY services/rag/pyproject.toml /app/pyproject.toml
COPY services/rag/src /app/src
COPY docs /app/docs
COPY services/rag/eval /app/eval
ENV RAG_DOCS_ROOT=/app/docs
RUN pip install --no-cache-dir . \
    && useradd --system --uid 10001 --create-home rag \
    && mkdir -p /app/data/chroma \
    && chown -R rag:rag /app/data
USER rag
EXPOSE 8090
# Versioned docs and evaluation inputs are bundled; index at deployment startup.
# Inject OPENAI_API_KEY and RAG_API_TOKEN at runtime.
CMD ["uvicorn", "investory_rag.main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1"]
