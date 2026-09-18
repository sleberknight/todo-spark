#!/usr/bin/env bash
#
# install-spark-core.sh — clone dsingley/spark (ossrh) alongside this checkout and
# install its spark-core SNAPSHOT, so etc/run.sh --build works with no other setup.
#
# Run from a fresh container/Codespace via .devcontainer/devcontainer.json's
# postCreateCommand. Safe to re-run: reuses and updates an existing clone rather
# than failing on one.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SPARK_DIR="$(dirname "$PROJECT_DIR")/spark"

# HTTPS, not SSH - a fresh container has no SSH key configured for GitHub, but this
# is a public repo, so an anonymous HTTPS clone works with no auth needed.
SPARK_REPO_URL="https://github.com/dsingley/spark.git"

if [[ -d "$SPARK_DIR/.git" ]]; then
  echo "dsingley/spark already cloned at $SPARK_DIR - updating ossrh..."
  git -C "$SPARK_DIR" fetch origin ossrh
  git -C "$SPARK_DIR" checkout ossrh
  git -C "$SPARK_DIR" pull --ff-only origin ossrh
else
  echo "Cloning dsingley/spark (ossrh) into $SPARK_DIR..."
  # ossrh is a transitional name - see README's "Note on ossrh branch" if this fails
  git clone --branch ossrh "$SPARK_REPO_URL" "$SPARK_DIR"
fi

echo "Installing spark-core SNAPSHOT..."
mvn -f "$SPARK_DIR/pom.xml" install -DskipTests
