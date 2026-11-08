# Multi-stage build: Node builds the static files, nginx serves them. The final image has no Node.js.

# ---- Stage 1: build ----
FROM node:24-alpine AS build
WORKDIR /app

# Dependencies first (cached layer): reinstalled only when package*.json change.
# npm ci installs exactly what package-lock.json pins.
COPY package.json package-lock.json ./
RUN npm ci

# Vite inlines VITE_* variables into the JavaScript at BUILD time, so the API URL is a build argument,
# not a runtime env var. It is the URL the user's browser calls, e.g. http://localhost:8098.
ARG VITE_API_BASE_URL=http://localhost:8098
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY . .
RUN npm run build

# ---- Stage 2: serve ----
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
