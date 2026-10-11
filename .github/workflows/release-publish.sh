#!/usr/bin/env bash
# Lädt Dateien in das Release $TAG hoch (legt es bei Bedarf an) und merkt sich Version + Quelle.
# Eingabe (env): TAG, TITLE, VER, HASH, GITHUB_REPOSITORY, optional PRUNE (Muster alter Dateien, z. B. 'MythosCode-Setup-*')
# Argumente: Dateien – in dieser Reihenfolge hochgeladen (Update-Feed latest.yml also zuletzt angeben).
set -euo pipefail
NOTES="Automatisch gebaut. Download über https://mythos-core-ai-ai-ai.lovable.app/downloads

<!-- version: $VER -->
<!-- source: $HASH -->"
gh release view "$TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1 || \
  gh release create "$TAG" --repo "$GITHUB_REPOSITORY" --title "$TITLE" --notes "$NOTES" --latest=false
for FILE in "$@"; do
  gh release upload "$TAG" "$FILE" --repo "$GITHUB_REPOSITORY" --clobber
done
# Alte versionierte Dateien entfernen, damit im Release nur die aktuelle Version liegt.
if [ -n "${PRUNE:-}" ]; then
  KEEP=$(for f in "$@"; do basename "$f"; done)
  gh release view "$TAG" --repo "$GITHUB_REPOSITORY" --json assets -q '.assets[].name' | while read -r NAME; do
    case "$NAME" in
      $PRUNE) printf '%s\n' "$KEEP" | grep -qxF "$NAME" || gh release delete-asset "$TAG" "$NAME" --repo "$GITHUB_REPOSITORY" --yes ;;
    esac
  done
fi
gh release edit "$TAG" --repo "$GITHUB_REPOSITORY" --title "$TITLE" --notes "$NOTES"
