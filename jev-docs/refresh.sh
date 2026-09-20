#!/usr/bin/env bash
# Re-download the TypeSafe docs into jev-docs/pages/ as Markdown.
# Pages come from https://docs.typesafe.ai/llms.txt plus a few pages the index omits.
# Each page is cleaned of the Mintlify index banner and the inline JS component
# that only builds "Try it in the Playground" links.
set -euo pipefail

BASE=https://docs.typesafe.ai
DIR="$(cd "$(dirname "$0")" && pwd)"
EXTRA_PAGES=(migrating-to-v1.md)

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -fsSL "$BASE/llms.txt" -o "$tmp/llms.txt"
{
  grep -oE "\($BASE/[^)]+\.md\)" "$tmp/llms.txt" | tr -d '()' | sed "s#^$BASE/##"
  printf '%s\n' "${EXTRA_PAGES[@]}"
} | sort -u > "$tmp/pages.txt"

fail=0
while read -r page; do
  mkdir -p "$tmp/pages/$(dirname "$page")"
  if curl -fsSL "$BASE/$page" -o "$tmp/raw.md"; then
    awk '
      NR <= 4 && (/^> ## Documentation Index/ || /^> Fetch the complete documentation index/ || /^> Use this file to discover/ || /^$/) { next }
      /^export function [A-Za-z]+\(/ { skip = 1 }
      skip { if ($0 == "}") skip = 0; next }
      { print }
    ' "$tmp/raw.md" > "$tmp/pages/$page"
  else
    echo "FAILED: $page" >&2
    fail=$((fail + 1))
  fi
done < "$tmp/pages.txt"

if [ "$fail" -ne 0 ]; then
  echo "$fail page(s) failed; leaving $DIR/pages untouched" >&2
  exit 1
fi

cp "$tmp/llms.txt" "$tmp/pages/llms.txt"
rm -rf "$DIR/pages"
mv "$tmp/pages" "$DIR/pages"
echo "Fetched $(wc -l < "$tmp/pages.txt" | tr -d ' ') pages into $DIR/pages on $(date +%F)"
