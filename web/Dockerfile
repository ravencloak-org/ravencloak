# Stage 1: Build
FROM oven/bun:alpine AS build

WORKDIR /app

ARG VITE_API_BASE_URL
ARG VITE_KEYCLOAK_URL
ARG VITE_KEYCLOAK_REALM
ARG VITE_KEYCLOAK_CLIENT_ID
ARG VITE_OTEL_COLLECTOR_URL
ARG VITE_OTEL_ENABLED

# Write build-time env vars for Vite
RUN printf "VITE_API_BASE_URL=%s\nVITE_KEYCLOAK_URL=%s\nVITE_KEYCLOAK_REALM=%s\nVITE_KEYCLOAK_CLIENT_ID=%s\nVITE_OTEL_COLLECTOR_URL=%s\nVITE_OTEL_ENABLED=%s\n" \
    "$VITE_API_BASE_URL" "$VITE_KEYCLOAK_URL" "$VITE_KEYCLOAK_REALM" "$VITE_KEYCLOAK_CLIENT_ID" "$VITE_OTEL_COLLECTOR_URL" "$VITE_OTEL_ENABLED" \
    > .env.production

COPY package.json bun.lockb ./
RUN bun install --frozen-lockfile

COPY . .
RUN bun run build

# Stage 2: Serve
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
