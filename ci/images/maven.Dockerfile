FROM docker.io/library/maven:3.9.9-eclipse-temurin-21
# git : scripts/check-version-free.sh interroge les tags du repo
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
