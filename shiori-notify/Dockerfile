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

ENV NOTIFY_HTTP_ADDR=:8090
ENV RABBITMQ_ADDR=amqp://rabbitmq:5672/

EXPOSE 8090
USER app
ENTRYPOINT ["shiori-notify"]
