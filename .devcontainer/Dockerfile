FROM mcr.microsoft.com/devcontainers/java:25-bookworm

ARG DOCKER_GID=999

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        dos2unix \
        maven \
        postgresql-client \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg \
        -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian \
        $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
        > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        docker-ce-cli \
        docker-compose-plugin \
    && if getent group "${DOCKER_GID}" >/dev/null; then \
         existing_group="$(getent group "${DOCKER_GID}" | cut -d: -f1)"; \
         usermod -aG "${existing_group}" vscode; \
       else \
         groupadd --gid "${DOCKER_GID}" docker-host; \
         usermod -aG docker-host vscode; \
       fi \
    && rm -rf /var/lib/apt/lists/*

USER vscode
