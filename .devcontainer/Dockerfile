FROM mcr.microsoft.com/devcontainers/java:25-bookworm

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        dos2unix \
        postgresql-client \
    && rm -rf /var/lib/apt/lists/*

USER vscode
