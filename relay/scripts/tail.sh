#!/bin/bash
# tail.sh — Live-tail relay logs via wrangler
#
# Usage: bash relay/scripts/tail.sh [staging|production]
# Default: production

ENV="${1:-production}"

case "$ENV" in
    staging|production)
        echo "Tailing relay logs ($ENV)... (Ctrl+C to stop)"
        npx wrangler tail --env "$ENV" --format pretty
        ;;
    *)
        echo "Usage: bash relay/scripts/tail.sh [staging|production]"
        exit 1
        ;;
esac
