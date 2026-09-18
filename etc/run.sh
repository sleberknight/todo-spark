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

# Respect SDKMAN_DIR when it's already set rather than assuming $HOME/.sdkman - some
# environments (e.g. GitHub Codespaces' default image) install SDKMAN system-wide at
# /usr/local/sdkman, with SDKMAN_DIR set accordingly, not per-user.
SDKMAN_DIR_WAS_SET="${SDKMAN_DIR:+1}"
SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
if [[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
  # SDKMAN's own init script isn't "set -u"-safe (references unset vars like
  # ZSH_VERSION when running under bash), so relax strict mode just for this
  set +euo pipefail
  # shellcheck disable=SC1091
  source "$SDKMAN_DIR/bin/sdkman-init.sh"
  # "install" (not just "env") since a plain "sdk env" only switches to an
  # already-installed version - it silently leaves things as they are if the
  # .sdkmanrc-pinned version was never installed on this machine.
  sdk env install > /dev/null
  set -euo pipefail
  # "sdk env" sets JAVA_HOME correctly but doesn't reliably reorder an
  # already-inherited PATH ahead of it, so make sure it actually wins
  export PATH="$JAVA_HOME/bin:$PATH"
elif [[ -n "$SDKMAN_DIR_WAS_SET" ]]; then
  # SDKMAN_DIR was explicitly set in the environment but nothing was found there -
  # unlike SDKMAN simply not being installed (a supported, silent fallback per the
  # header comment above), this smells like a wrong path assumption, so say so
  # instead of quietly building with whatever's on PATH.
  echo "Warning: SDKMAN_DIR is set to '$SDKMAN_DIR' but no sdkman-init.sh was found there - building with whatever JDK/Maven are already on PATH, not the .sdkmanrc-pinned version." >&2
fi

if [[ -n "$BUILD" ]]; then
  echo "Building $JAR ..."
  # Skip tests here - this script is for running the app, not verifying it (that's
  # what CI's "mvn verify" is for). Running tests would also require the Playwright
  # browser binaries (see README's Testing section), which is an extra manual step
  # nobody should need just to start the app.
  mvn -q package -DskipTests
fi

if [[ ! -f "$JAR" ]]; then
  echo "Error: $JAR not found. Run with --build (or -b) to build it first." >&2
  exit 1
fi

echo "Running $JAR ..."
exec java -jar "$JAR" ${PORT:+"$PORT"}
