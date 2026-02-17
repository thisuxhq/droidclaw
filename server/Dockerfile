FROM oven/bun:1

WORKDIR /app

COPY packages/shared ./packages/shared
COPY server ./server

WORKDIR /app/server
RUN bun install

EXPOSE 8080
CMD ["bun", "src/index.ts"]
