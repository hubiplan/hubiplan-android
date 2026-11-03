#!/usr/bin/env bash
# App-Releases: Changelogs aus den PRs und Tag (siehe scripts/release.py und
# docs/veroeffentlichen.md). Braucht git und gh (in WSL: GH=gh.exe).
set -euo pipefail
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
exec python3 scripts/release.py "$@"
