FROM golang:1.25-bookworm AS build

ARG PROJECT
ARG RELEASE

RUN git clone --depth 1 --branch "${RELEASE}" \
    "https://github.com/minio/${PROJECT}.git" /src

WORKDIR /src
RUN CGO_ENABLED=0 go build -trimpath -o /out/app .

FROM alpine:3.22

RUN apk add --no-cache ca-certificates curl
ARG PROJECT
COPY --from=build /out/app /usr/local/bin/${PROJECT}
COPY --from=build /src/LICENSE /usr/share/licenses/minio/LICENSE
