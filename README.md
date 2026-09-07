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

As a packaged jar (recommended - builds a self-contained uber jar via maven-shade-plugin):

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
