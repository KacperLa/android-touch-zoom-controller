#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v javac >/dev/null || { echo 'JDK 17 or newer is required (javac not found).' >&2; exit 1; }
mkdir -p build/unit
sources=()
for source in app/src/main/java/io/github/kacperla/touchzoom/*.java; do
    case "${source##*/}" in MainActivity.java|ZoomService.java) continue ;; esac
    sources+=("$source")
done
javac --release 17 -d build/unit "${sources[@]}" tests/*.java
for test in tests/*Test.java; do
    java -cp build/unit "$(basename "$test" .java)"
done
