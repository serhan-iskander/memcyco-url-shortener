# syntax=docker/dockerfile:1.7
# Multi-stage build: compile the Vite SPA with Node, serve with nginx.

# --- Stage 1: build ----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first so npm cache layers can be reused.
COPY package.json package-lock.json* ./
RUN npm ci

# Copy source and produce the production bundle in /app/dist.
COPY tsconfig.json vite.config.ts index.html ./
COPY src ./src
RUN npm run build

# --- Stage 2: serve ----------------------------------------------------------
FROM nginx:alpine AS runtime

# Static assets.
COPY --from=build /app/dist /usr/share/nginx/html

# nginx config: SPA fallback + /api proxy to the backend service.
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# nginx's default entrypoint already runs nginx in the foreground.
CMD ["nginx", "-g", "daemon off;"]
