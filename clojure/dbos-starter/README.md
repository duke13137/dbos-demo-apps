# DBOS Starter app for Clojure

> **Note:** This is a community prototype that demonstrates DBOS running on Clojure via the Java interop layer.
> It is a placeholder until official first-class Clojure support is added to DBOS.

## Prerequisites

- Java 17 or later
- Clojure version 1.12
- PostgreSQL running on localhost:5432
  - can be overridden with `DBOS_SYSTEM_JDBC_URL` env var
- PostgreSQL user and password in `PGUSER` and `PGPASSWORD` env vars
  - defaults to `postgres` user and `dbos` password

## Setup

```bash
# Start application
clj -X:run
```

The application runs on `http://localhost:7070` by default.

## Development

### REPL workflow

```bash
# Start nREPL with development reload enabled
clj -M:dev:repl
```

```clojure
(load-file "dev/user.clj")

;; Load Clojure workflow functions for isolated testing
(require 'dbos-starter.core)

;; Boot the full app from the REPL
(org.example.App/main (into-array String []))
```

### Useful commands

```bash
# Check for outdated dependencies
clj -M:antq

# Lint source files
clj -M:lint

# Check formatting
clj -M:fmt

# Apply formatting fixes
clj -M:fix
```

### E2E test

```bash
# Compile + run JUnit E2E test (inlines DBOS, PG only)
clj -T:build compile-e2e-java && java -cp "$(clj -A:e2e-test -Spath)" org.junit.platform.console.ConsoleLauncher --class-path classes --select-class org.example.AppE2ETest

```
