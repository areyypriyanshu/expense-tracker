# syntax=docker/dockerfile:1
#
# Standalone image for the site.
#
# The Dockerfile exists for self-hosting. The site has no backend, no
# database and no file storage, so the same image also runs fine on any
# container platform, and the simpler path remains Vercel or any static host.

FROM node:20-alpine AS base
RUN corepack enable
WORKDIR /app

# Dependencies are installed from the lockfile alone, so a dependency change
# is the only thing that invalidates this layer.
FROM base AS deps
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

FROM base AS builder
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# A token is optional. Without one the build falls back to the committed
# release snapshot and still succeeds.
ARG GITHUB_TOKEN
ENV GITHUB_TOKEN=$GITHUB_TOKEN
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm build

FROM base AS runner
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000

RUN addgroup -g 1001 -S nodejs && adduser -S nextjs -u 1001

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
  CMD wget -qO- http://localhost:3000/ >/dev/null || exit 1

CMD ["node", "server.js"]