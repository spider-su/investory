FROM python:3.12-slim
WORKDIR /app
COPY services/rag/pyproject.toml /app/pyproject.toml
COPY services/rag/src /app/src
RUN pip install --no-cache-dir .
# Run with repository root as Docker build context.
EXPOSE 8090
CMD ["uvicorn", "investory_rag.main:app", "--host", "0.0.0.0", "--port", "8090"]
