# Build stage
FROM golang:1.25-alpine AS builder

RUN apk add --no-cache git

WORKDIR /app

# Cache dependencies
COPY go.mod go.sum ./
RUN go mod download

# Build
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /nebula-sidecar . && \
    CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /healthcheck ./cmd/healthcheck

# Runtime stage
FROM gcr.io/distroless/static:nonroot

COPY --from=builder /nebula-sidecar /nebula-sidecar
COPY --from=builder /healthcheck /healthcheck

USER nonroot:nonroot

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD ["/healthcheck"]

ENTRYPOINT ["/nebula-sidecar"]
