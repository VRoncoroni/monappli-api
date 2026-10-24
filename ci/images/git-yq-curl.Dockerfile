FROM docker.io/library/alpine:3.20
ARG YQ_VERSION=v4.44.3
RUN apk add --no-cache git curl coreutils \
 && curl -fsSL -o /usr/local/bin/yq "https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64" \
 && chmod +x /usr/local/bin/yq
