#!/usr/bin/env bash
# Entscheidet, ob ein Release neu gebaut werden muss.
# Eingabe (env): TAG, VER (neue Version), HASH (Fingerabdruck der Quelle), FORCE, GITHUB_REPOSITORY
# Ausgabe: build=true|false in $GITHUB_OUTPUT
set -euo pipefail
BODY=$(gh release view "$TAG" --repo "$GITHUB_REPOSITORY" --json body -q .body 2>/dev/null || true)
OLD_HASH=$(printf '%s' "$BODY" | sed -n 's/.*source: \([0-9a-f]*\).*/\1/p' | head -1)
OLD_VER=$(printf '%s' "$BODY" | sed -n 's/.*version: \([0-9][0-9A-Za-z.+-]*\).*/\1/p' | head -1)
echo "Quelle: $VER ($HASH) · veröffentlicht: ${OLD_VER:-keine} (${OLD_HASH:-keine})"

if [ "$HASH" = "$OLD_HASH" ] && [ "${FORCE:-false}" != "true" ]; then
  echo "Keine Änderungen, nichts zu tun."
  echo "build=false" >> "$GITHUB_OUTPUT"; exit 0
fi
# Nie eine ältere Version über eine neuere legen.
if [ -n "$OLD_VER" ] && node -e '
  const p = (v) => v.split(/[.+-]/).slice(0, 3).map(Number);
  const [a, b] = [p(process.argv[1]), p(process.argv[2])];
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) process.exit(a[i] < b[i] ? 0 : 1);
  process.exit(1);
' "$VER" "$OLD_VER"; then
  echo "::warning::$TAG: Quelle hat Version $VER, veröffentlicht ist schon $OLD_VER. Version erhöhen, dann wird gebaut."
  echo "build=false" >> "$GITHUB_OUTPUT"; exit 0
fi
echo "build=true" >> "$GITHUB_OUTPUT"
