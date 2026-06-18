#!/usr/bin/env bash
# Extract a version section from CHANGELOG.md (content under ## [VERSION], excluding the header).
set -euo pipefail

VERSION="${1:?usage: extract-changelog.sh VERSION [CHANGELOG.md]}"
VERSION="${VERSION#v}"
CHANGELOG="${2:-CHANGELOG.md}"

if [[ ! -f "$CHANGELOG" ]]; then
  echo "Error: $CHANGELOG not found" >&2
  exit 1
fi

awk -v ver="$VERSION" '
  $0 ~ "^## \\[" ver "\\]" { capture = 1; next }
  capture && /^## \[/ { exit }
  capture { print }
' "$CHANGELOG"
