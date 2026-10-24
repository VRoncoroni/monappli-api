FROM docker.io/goreleaser/nfpm:v2.41.1 AS nfpm

FROM docker.io/library/alpine:3.20
RUN apk add --no-cache coreutils
COPY --from=nfpm /usr/bin/nfpm /usr/local/bin/nfpm
