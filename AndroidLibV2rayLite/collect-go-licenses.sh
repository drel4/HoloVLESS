#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset

OUTPUT_DIR="${1:?usage: collect-go-licenses.sh OUTPUT_DIR}"
MODULE_LIST="$OUTPUT_DIR/GO-MODULES.txt"
MODULE_DETAILS=$(mktemp)

cleanup () {
  rm -f "$MODULE_DETAILS"
}

trap cleanup EXIT

mkdir -p "$OUTPUT_DIR"

GOOS=android GOARCH=arm CGO_ENABLED=1 go list -deps \
  -f '{{with .Module}}{{.Path}}|{{.Version}}|{{.Dir}}{{end}}' ./... \
  | sed '/^$/d' \
  | sort -u > "$MODULE_DETAILS"

awk -F'|' '$2 != "" { print $1 "|" $2 }' "$MODULE_DETAILS" > "$MODULE_LIST"

while IFS='|' read -r module version module_dir; do
  if [[ -z "$version" || -z "$module_dir" ]]; then
    continue
  fi

  destination="$OUTPUT_DIR/$module@$version"
  found=0

  while IFS= read -r -d '' license_file; do
    relative_path="${license_file#"$module_dir"/}"
    mkdir -p "$destination/$(dirname "$relative_path")"
    cp -p "$license_file" "$destination/$relative_path"
    found=1
  done < <(find "$module_dir" -maxdepth 2 -type f \
    \( -iname 'LICENSE*' -o -iname 'COPYING*' -o -iname 'NOTICE*' -o -iname 'PATENTS*' \) \
    -print0)

  if [[ "$found" -eq 0 ]]; then
    echo "No license file found for $module@$version" >&2
  fi
done < "$MODULE_DETAILS"
