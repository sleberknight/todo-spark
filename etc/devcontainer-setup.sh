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
#
# Respect SDKMAN_DIR when it's already set rather than assuming $HOME/.sdkman - the
# default Codespaces image installs SDKMAN system-wide at /usr/local/sdkman (with
# SDKMAN_DIR set accordingly), not per-user, so the $HOME-only assumption silently
# skipped this entire block there.
SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
if [[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
  set +euo pipefail
  # shellcheck disable=SC1091
  source "$SDKMAN_DIR/bin/sdkman-init.sh"
  # "install" (not just "env") since a plain "sdk env" only switches to an
  # already-installed version - it silently leaves things as they are (and, as
  # discovered live, JAVA_HOME pointing nowhere valid) if the .sdkmanrc-pinned
  # version was never installed on this machine, which a fresh container never has.
  sdk env install > /dev/null

  # "sdk env" only sets JAVA_HOME/PATH for this script's own process - it doesn't
  # change what a brand-new terminal defaults to. "sdk default" persists that choice
  # as the container-wide default, so every future terminal picks it up too, not just
  # scripts (like etc/run.sh) that explicitly call "sdk env" themselves.
  #
  # Read the pinned version from .sdkmanrc directly rather than deriving it from
  # $JAVA_HOME - discovered live in a Codespace that "sdk env" there sets JAVA_HOME
  # to the generic ".../java/current" symlink path, not the concrete version
  # directory (unlike a local macOS SDKMAN install, where it resolves to the real
  # versioned path), so basename-ing it gave the literal string "current" instead
  # of an actual version, and "sdk default java current" failed outright.
  JAVA_PIN="$(sed -n 's/^java=//p' "$PROJECT_DIR/.sdkmanrc")"
  sdk default java "$JAVA_PIN" > /dev/null

  if [[ ! -d "$SDKMAN_DIR/candidates/mvnd/current" ]]; then
    echo "Installing mvnd..."
    sdk install mvnd
  fi

  set -euo pipefail
  export PATH="$JAVA_HOME/bin:$PATH"
else
  # Unlike etc/run.sh, SDKMAN isn't optional here - this script's whole job is
  # getting the container's JDK/tooling right, so silently building with whatever
  # happens to be on PATH would report postCreateCommand success while quietly
  # failing at that one job. Fail loudly instead of leaving that to be discovered
  # later in some unrelated terminal.
  echo "Error: no sdkman-init.sh found at '$SDKMAN_DIR/bin/sdkman-init.sh' - cannot pin the JDK/install mvnd." >&2
  exit 1
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
