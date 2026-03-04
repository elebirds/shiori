FROM golang:1.26-alpine AS builder

WORKDIR /src
COPY shiori-notify/go.mod shiori-notify/go.sum /src/
RUN go mod download

COPY shiori-notify/ /src/
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/shiori-notify .

FROM golang:1.26-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=builder /out/shiori-notify /usr/local/bin/shiori-notify

ENV NACOS_ADDR=nacos:8848
ENV NACOS_CONFIG_GROUP=SHIORI_DEV_DOCKER

EXPOSE 8090
USER app
ENTRYPOINT ["shiori-notify"]
