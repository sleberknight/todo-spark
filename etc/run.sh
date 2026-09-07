#!/usr/bin/env bash
#
# run.sh — build and/or run todo-spark in one command
#
# By default this just runs the already-built jar. Pass --build to build
# (mvn package) first, e.g. after pulling changes or on a fresh checkout.
#
# Usage:
#   etc/run.sh                 # run the existing jar (must already be built)
#   etc/run.sh --build         # build first, then run
#   etc/run.sh --build 8080    # build first, then run on port 8080
#   etc/run.sh 8080            # run the existing jar on port 8080
#
# If SDKMAN is installed, this picks up the JDK pinned in .sdkmanrc
# automatically. Otherwise it just uses whatever "java"/"mvn" are on PATH.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_DIR/target/todo-spark-1.0-SNAPSHOT.jar"

cd "$PROJECT_DIR"

usage() {
  grep '^#' "$0" | sed -e 's/^#!\?//' -e 's/^ //'
  exit 1
}

BUILD=""
PORT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -b|--build)
      BUILD="1"; shift ;;
    -h|--help)
      usage ;;
    *)
      PORT="$1"; shift ;;
  esac
done

if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # SDKMAN's own init script isn't "set -u"-safe (references unset vars like
  # ZSH_VERSION when running under bash), so relax strict mode just for this
  set +euo pipefail
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env > /dev/null
  set -euo pipefail
  # "sdk env" sets JAVA_HOME correctly but doesn't reliably reorder an
  # already-inherited PATH ahead of it, so make sure it actually wins
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ -n "$BUILD" ]]; then
  echo "Building $JAR ..."
  mvn -q package
fi

if [[ ! -f "$JAR" ]]; then
  echo "Error: $JAR not found. Run with --build (or -b) to build it first." >&2
  exit 1
fi

echo "Running $JAR ..."
exec java -jar "$JAR" ${PORT:+"$PORT"}
