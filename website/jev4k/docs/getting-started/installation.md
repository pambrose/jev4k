---
icon: lucide/download
---

# Installation

## Requirements

- **JDK 17 or newer** to run. jev4k is compiled to Java 17 bytecode, so it embeds in applications on 17, 21, 25,
  or anything later. Building jev4k itself uses JDK 25; Gradle's toolchain support downloads it if it's missing.
- **Kotlin 2.4** or later.
- **A TypeSafe API key.**

jev4k is built on the Ktor client (CIO engine), kotlinx.serialization, and kotlinx.coroutines. Ktor core,
kotlinx.serialization-json and kotlinx.coroutines are exposed as `api` dependencies, because their types (`JsonElement`,
`HttpClientEngine`, suspend functions) appear in jev4k's public API.

## Embedding in an application

jev4k is a library meant to be embedded in an application, so it keeps out of the host's way.

### What it puts on your classpath.

Four `compile` dependencies (`ktor-client-core`,
`kotlinx-serialization-json`, `kotlinx-coroutines-core`, `kotlin-stdlib`) and three `runtime` ones (`ktor-client-cio`,
`ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`). Nothing else: no test
framework, no logging backend.

### Logging

jev4k never logs. It writes nothing to stdout or stderr, installs no Ktor `Logging` plugin, and
ships no SLF4J binding, so it can't interfere with your logging setup. `slf4j-api` reaches the classpath through
Ktor, not jev4k; supply your own binding if you want Ktor's own output.

### Your own engine

Pass one through [`engine`](../client/configuration.md#a-custom-http-engine) and jev4k uses
it instead of CIO. Closing a `JevClient` never closes an engine you supplied, so several clients can share one.
If you do supply an engine, CIO can be dropped:

```kotlin
--8<-- "GettingStarted.txt:exclude-cio"
```

### From Java

jev4k is a Kotlin library, but the inline builder DSL works from Java through `jev.getBlocking()`:

```java title="Calling jev4k from Java"
--8<-- "JavaInterop.java:basics"
```

Two Kotlin features don't cross to Java: property delegates, which a typed `JevQuery` is built from, and
`inline reified` functions, which the Kotlin compiler emits as synthetic members that javac can't resolve. So
four things are out of reach from Java:

- **Typed `JevQuery` objects** can't be declared. One declared in Kotlin can still be passed to `ask`.
- **`@Serializable` states.** A state must be a `String` or a `JsonElement`; the reified `query`, `ask` and
  `jsonEntry` overloads are hidden rather than compiling into a runtime failure.
- **Enum Choices through the DSL.** `QueryBuilder.choice<E>()` is reified and `enumChoiceRef` is `internal`, so
  there's no route to one. Build a `ChoiceQuestion` with the option keys you want and add it with
  `QueryBuilder.question(id, question)` instead.
- **`JevResult.enumChoice<E>(id)`** is reified too. Read that answer with `result.choice(id)`, which is keyed by
  option string.

Everything else is callable: `evaluate`, `models`, the inline `noul`, `choice` and `score` builders, the other
result accessors, and enums implementing `JevOption`.

### Module name

The jar declares `Automatic-Module-Name: com.pambrose.jev4k` for JPMS builds.

### Java version

The class files are Java 17 (`org.gradle.jvm.version = 17` in the published metadata), and the
compiler is held to the Java 17 API, so nothing newer can slip in.

### Threads

A `JevClient` is immutable once built and safe to share across coroutines. `jev.blocking` wraps the
suspend calls in `runBlocking`, so call it from ordinary threads, never from inside a coroutine.

## Adding the dependency

!!! warning "0.1.0 hasn't been published yet"

    Nothing has reached Maven Central so far, so these coordinates don't resolve. Until the first release
    lands, [build from source](#building-from-source) and depend on the checkout. This note goes away when the
    artifact is on Central.

=== "Gradle (Kotlin DSL)"

    ```kotlin
    --8<-- "GettingStarted.txt:dependency-gradle"
    ```

=== "Maven"

    ```xml
    --8<-- "GettingStarted.txt:dependency-maven"
    ```

That single dependency brings the Ktor client, kotlinx.serialization and kotlinx.coroutines with it; see
[what it puts on your classpath](#what-it-puts-on-your-classpath) for the full set, and
[your own engine](#your-own-engine) if you'd rather not ship CIO.

## Building from source

Contributors, and anyone who wants a build before the next release reaches Central, can build jev4k from a
checkout:

```bash
--8<-- "GettingStarted.txt:build"
```

## Using an unreleased build from another project

The simplest way to depend on a checkout rather than a published artifact is a Gradle
[composite build](https://docs.gradle.org/current/userguide/composite_builds.html). Include the jev4k checkout
in your project's settings:

```kotlin
--8<-- "GettingStarted.txt:composite-settings"
```

Then depend on it by its coordinates, which Gradle substitutes with the included build:

```kotlin
--8<-- "GettingStarted.txt:composite-dependency"
```

The `plugin.serialization` line is needed only if you pass your own `@Serializable` classes as
[state](../queries/state.md).

## API key

The client reads the key from the `TYPESAFE_API_KEY` environment variable:

```bash
--8<-- "GettingStarted.txt:api-key"
```

You can also set it in code; see [Configuration](../client/configuration.md). Keep API keys server-side: don't
ship them in a browser or mobile app.

## Checking your setup

The repository includes a runnable example that uses both DSL styles against the live API:

```bash
--8<-- "GettingStarted.txt:run-example"
```

Next: the [Quick Start](quick-start.md).
