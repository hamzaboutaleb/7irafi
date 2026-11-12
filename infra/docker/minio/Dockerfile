FROM golang:1.25-bookworm AS minio-build

ARG MINIO_RELEASE

RUN git clone --depth 1 --branch "${MINIO_RELEASE}" \
    https://github.com/minio/minio.git /src

WORKDIR /src
RUN CGO_ENABLED=0 go build -trimpath -o /out/minio .


FROM golang:1.25-bookworm AS mc-build

ARG MC_RELEASE

RUN git clone --depth 1 --branch "${MC_RELEASE}" \
    https://github.com/minio/mc.git /src

WORKDIR /src
RUN CGO_ENABLED=0 go build -trimpath -o /out/mc .

FROM alpine:3.22

RUN apk add --no-cache ca-certificates curl

COPY --from=minio-build /out/minio /usr/local/bin/minio
COPY --from=mc-build /out/mc /usr/local/bin/mc
COPY --from=minio-build /src/LICENSE /usr/share/licenses/minio/LICENSE

COPY init.sh /init.sh
RUN chmod +x /init.sh

ENTRYPOINT ["/bin/sh", "/init.sh"]