# todo-spark

A sample TODO application built on [Spark](https://github.com/dsingley/spark) (dsingley's fork),
exercising a typical set of Spark features via both a server-rendered HTML UI and a JSON REST API.

## Prerequisites

- Java 25
- The fork's `spark-core:3.0.0-SNAPSHOT` installed locally:

  ```
  cd /path/to/dsingley/spark
  mvn install -DskipTests
  ```

## Running

Easiest - one command, builds if needed:

```
etc/run.sh --build      # first run, or after pulling changes
etc/run.sh               # subsequent runs, reuses the already-built jar
etc/run.sh --build 8080  # optional port argument works with either form
```

If SDKMAN is installed, this picks up the JDK pinned in `.sdkmanrc` automatically.

Or manually, as a packaged jar (builds a self-contained uber jar via maven-shade-plugin):

```
mvn package
java -jar target/todo-spark-1.0-SNAPSHOT.jar
```

or via Maven directly, without packaging:

```
mvn compile exec:java
```

or run `todo.spark.TodoApp#main` directly from an IDE.

The app listens on port 4567 by default (override with a single port-number argument,
e.g. `java -jar target/todo-spark-1.0-SNAPSHOT.jar 8080`).
