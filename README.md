# todo-spark

[![build](https://github.com/sleberknight/todo-spark/actions/workflows/build.yml/badge.svg)](https://github.com/sleberknight/todo-spark/actions/workflows/build.yml)

A sample TODO application built on [Spark](https://github.com/dsingley/spark) (dsingley's fork),
exercising a typical set of Spark features via both a server-rendered HTML UI and a JSON REST API.

## Prerequisites

- Java 25

`spark-core` is resolved from Maven Central like any other dependency, so there is nothing
else to install.

## Running

Easiest – one command, builds if needed:

```
etc/run.sh --build      # first run, or after pulling changes
etc/run.sh               # subsequent runs, reuses the already-built jar
etc/run.sh --build 8080  # optional port argument works with either form
```

If SDKMAN is installed, this picks up the JDK pinned in `.sdkmanrc` automatically.

Or manually, as a packaged jar (builds a self-contained uber jar via maven-shade-plugin):

```
mvn package -DskipTests
java -jar target/todo-spark-1.0-SNAPSHOT.jar
```

`-DskipTests` avoids requiring the Playwright browser binaries (see Testing below) just
to run the app - drop it if you want the full verification a plain `mvn package` gives you.

Or, run `todo.spark.TodoApp#main` directly from an IDE - in which case add
`--enable-native-access=ALL-UNNAMED` as a VM option on that run configuration, since the
packaged jar's manifest (which silences this automatically for `java -jar`) doesn't apply
to a classpath-based IDE launch. Without it, `sqlite-jdbc` loading its native library
prints a JDK warning at startup - harmless today, but the JDK states this will eventually
be blocked without it.

The app listens on port 4567 by default (override with a single port-number argument,
e.g., `java -jar target/todo-spark-1.0-SNAPSHOT.jar 8080`).

## Data

Todos are persisted to an SQLite database at `~/.todo-spark/todo.db` - fixed under the user's
home directory rather than the process's working directory, so the same data is found no
matter where the jar is launched from. The resolved path is logged at startup. Delete the
file (or the whole `~/.todo-spark` directory) to reset to an empty list.

To point the app at a different directory (e.g., for manual testing/verification, so a real
data directory is never touched or deleted out from under a running instance), set the
`todo.spark.dbDir` system property:

```
java -Dtodo.spark.dbDir=/tmp/todo-spark-scratch -jar target/todo-spark-1.0-SNAPSHOT.jar
```

## Testing

`TodoBrowserTest` drives a real (headless) browser via [Playwright](https://playwright.dev/java/)
to cover things HTTP-only tests can't: actual JS execution, the WebSocket connection, and
whether a page did or didn't navigate. It needs the Playwright browser binaries downloaded
once - `mvn test` will fail with a clear error if they're missing:

```
mvn dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/cp.txt
java -cp "$(cat /tmp/cp.txt):target/test-classes:target/classes" com.microsoft.playwright.CLI install chromium
```
