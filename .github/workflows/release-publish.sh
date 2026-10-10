#!/usr/bin/env bash
# Lädt eine Datei in das Release $TAG hoch (legt es bei Bedarf an) und merkt sich Version + Quelle.
# Eingabe (env): TAG, TITLE, VER, HASH, GITHUB_REPOSITORY · Argument: Datei
set -euo pipefail
FILE="$1"
NOTES="Automatisch gebaut. Download über https://mythos-core-ai.lovable.app/downloads

<!-- version: $VER -->
<!-- source: $HASH -->"
gh release view "$TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1 || \
  gh release create "$TAG" --repo "$GITHUB_REPOSITORY" --title "$TITLE" --notes "$NOTES" --latest=false
gh release upload "$TAG" "$FILE" --repo "$GITHUB_REPOSITORY" --clobber
gh release edit "$TAG" --repo "$GITHUB_REPOSITORY" --title "$TITLE" --notes "$NOTES"
