FROM gcc:14

RUN apt-get update \
    && apt-get install -y --no-install-recommends coreutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
