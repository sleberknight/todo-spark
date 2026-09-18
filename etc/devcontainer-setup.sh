#!/usr/bin/env bash
#
# devcontainer-setup.sh — postCreateCommand for .devcontainer/devcontainer.json.
# Clones dsingley/spark (ossrh) alongside this checkout and installs its spark-core
# SNAPSHOT, so etc/run.sh --build works with no other setup, and installs mvnd for
# faster repeat builds inside the container.
#
# Safe to re-run: reuses/updates the existing spark clone and skips mvnd if it's
# already installed, rather than failing on either.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SPARK_DIR="$(dirname "$PROJECT_DIR")/spark"

cd "$PROJECT_DIR"

# Match whatever JDK etc/run.sh itself will build/run todo-spark with, rather than
# whatever happens to be SDKMAN's default in this container - same guarded sourcing
# run.sh uses; cwd is already PROJECT_DIR so "sdk env" picks up its .sdkmanrc.
if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  set +euo pipefail
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env > /dev/null

  if [[ ! -d "$HOME/.sdkman/candidates/mvnd/current" ]]; then
    echo "Installing mvnd..."
    sdk install mvnd
  fi

  set -euo pipefail
  export PATH="$JAVA_HOME/bin:$PATH"
fi

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
