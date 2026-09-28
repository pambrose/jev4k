---
icon: lucide/download
---

# Installation

## Requirements

- **Kotlin 2.4** or later, on any of the [platforms](#platforms) below.
- **JDK 17 or newer** to run on the JVM. jev4k is compiled to Java 17 bytecode, so it embeds in applications on 17,
  21, 25, or anything later. Building jev4k itself uses JDK 25; Gradle's toolchain support downloads it if it's
  missing.
- **A TypeSafe API key.**

jev4k is built on the Ktor client, kotlinx.serialization, and kotlinx.coroutines. Ktor core,
kotlinx.serialization-json and kotlinx.coroutines are exposed as `api` dependencies, because their types (`JsonElement`,
`HttpClientEngine`, suspend functions) appear in jev4k's public API.

## Platforms

jev4k is a Kotlin Multiplatform library. The whole API is common code, so the same queries, handles and answers work
everywhere; only the HTTP engine underneath changes.

| Platform | Targets                                          | Default engine          |
|----------|--------------------------------------------------|-------------------------|
| JVM      | `jvm` (Java 17 bytecode)                         | CIO                     |
| Apple    | macOS, iOS, tvOS and watchOS, with the simulators | Darwin (`NSURLSession`) |
| Linux    | `linuxX64`, `linuxArm64`                         | Curl                    |
| Windows  | `mingwX64`                                       | WinHttp                 |
| Node.js  | `js`, `wasmJs`                                   | Js (`fetch`)            |

The Apple targets are `macosArm64`, `iosArm64`, `iosX64`, `iosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64`,
`watchosArm32`, `watchosArm64`, `watchosSimulatorArm64` and `watchosDeviceArm64`.

A few things differ by platform:

- **Blocking calls are JVM-only.** `jev.blocking` has its methods on the JVM alone; everywhere else, call the
  suspend API.
- **Node.js, not the browser.** The `js` and `wasmJs` artifacts are built and tested for Node.js. Nothing stops them
  loading in a browser, but a page would hand the API key to every visitor, so a browser app should call a backend
  of your own instead.
- **Linux needs CA certificates.** The Curl engine checks the server's certificate against the system's CA bundle
  (the `ca-certificates` package on Debian and Ubuntu). Without one, every request fails with a
  `JevConnectionException`.
- **iOS refuses plain `http://`.** App Transport Security blocks an `http://` `baseUrl`, such as a local
  [Ollaya](../client/configuration.md#running-with-ollaya) server, unless the app's `Info.plist` allows it
  (`NSAllowsLocalNetworking` covers local addresses).
- **Environment variables** (`TYPESAFE_API_KEY` and the rest) are read on the JVM, on native platforms, and on
  Node.js.

## Adding the jev4k dependency

!!! warning "Upgrading from 0.1.0? The Maven coordinates have changed"

    From 0.2.0 the group is `com.pambrose.jev4k` (0.1.0 was `com.pambrose:jev4k`), so update the dependency as
    shown below. Most code written for 0.1.0 then works unchanged. The
    [release notes](https://github.com/pambrose/jev4k/blob/master/RELEASE_NOTES.md) list the few API changes that
    came with the move, and how to exclude 0.1.0 if another library still brings it in.

jev4k is published to Maven Central. `com.pambrose.jev4k:jev4k` is the multiplatform module, and Gradle resolves it to
the right artifact for each target, so a JVM project and a Kotlin Multiplatform project use the same coordinates.
Maven doesn't read Gradle's module metadata, so a Maven build names the JVM artifact, `com.pambrose.jev4k:jev4k-jvm`.

=== "Gradle (Kotlin DSL)"

    ```kotlin
    --8<-- "GettingStarted.txt:dependency-gradle"
    ```

=== "Kotlin Multiplatform"

    ```kotlin
    --8<-- "GettingStarted.txt:dependency-kmp"
    ```

=== "Maven"

    ```xml
    --8<-- "GettingStarted.txt:dependency-maven"
    ```

That single dependency brings the Ktor client, kotlinx.serialization and kotlinx.coroutines with it; see
[what it puts on your classpath](#what-it-puts-on-your-classpath) for the full set, and
[using a different Ktor engine](#using-a-different-ktor-engine) if you'd rather not ship CIO on the JVM.

## TypeSafe API key

The client reads the key from the `TYPESAFE_API_KEY` environment variable:

```bash
--8<-- "GettingStarted.txt:api-key"
```

You can also set it in code; see [Configuration](../client/configuration.md). Keep API keys server-side: don't
ship them in a browser or mobile app. An app on a phone or watch should send its requests through a backend of your
own: point `baseUrl` at it and have it replace the `Authorization` header with the real key. The client still
requires an `apiKey`, so give it a placeholder.

## Checking your setup

The repository includes a runnable example that uses both DSL styles against the live API:

```bash
--8<-- "GettingStarted.txt:run-example"
```

## Embedding jev4k in an application

jev4k is a library meant to be embedded in an application, so it keeps out of the host's way.

### What it puts on your classpath.

On the JVM, four `compile` dependencies (`ktor-client-core`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`,
`kotlin-stdlib`) and one `runtime` one, the CIO engine (`ktor-client-cio`). Nothing else: no test framework, no logging
backend, and no content-negotiation plugin, since jev4k encodes its request body itself.

### Logging

jev4k never logs: it writes nothing to stdout or stderr itself, installs no Ktor `Logging` plugin, and ships no
SLF4J binding, so it can't interfere with your logging setup. Ktor does use SLF4J on the JVM, though, and `slf4j-api`
reaches the classpath through it. With no binding, SLF4J prints a three-line "No SLF4J providers were found" warning
to stderr when the first `JevClient` is built. Add your application's binding, or `slf4j-nop` to silence it.

### Using a different Ktor engine

Pass one through [`engine`](../client/configuration.md#a-custom-http-engine) and jev4k uses
it instead of the platform's default engine. Closing a `JevClient` never closes an engine you supplied, so several
clients can share one. If you do supply an engine on the JVM, CIO can be dropped:

```kotlin
--8<-- "GettingStarted.txt:exclude-cio"
```

### Calling jev4k from Java

jev4k is a Kotlin library, but the inline builder DSL works from Java through `jev.getBlocking()`:

```java title="Calling jev4k from Java"
--8<-- "JavaInterop.java:basics"
```

Kotlin's `Duration` doesn't cross to Java either, so each setting of that type has a counterpart in milliseconds:
`setTimeoutMillis` on the builder, `JevDefaults.TIMEOUT_MILLIS`, and `with…` methods on `RetryPolicy`, whose
constructor Java can't call:

```java title="Settings in milliseconds"
--8<-- "JavaInterop.java:settings"
```

[Per-call options](../client/calls.md#per-call-options) work the same way, and `BlockingJevKt.blocking(api)` gives
any `JevApi` the blocking calls. Every blocking call declares `InterruptedException`, and a rate-limit error's hint
is `getRetryAfterMillis()`:

```java title="Per-call options from Java"
--8<-- "JavaInterop.java:per-call"
```

Two Kotlin features don't cross to Java: property delegates, which a typed `JevQuery` is built from, and
`inline reified` functions, which the Kotlin compiler emits as synthetic members that javac can't resolve. So
four things are out of reach from Java:

- **Typed `JevQuery` objects** can't be declared. One declared in Kotlin can still be passed to `ask`.
- **`@Serializable` states.** A state must be a `String` or a `JsonElement`; the reified `query`, `ask` and
  `jsonEntry` overloads are hidden rather than compiling into a runtime failure.
- **Enum Choices through the DSL.** `QueryBuilder.choice<E>()` is reified. Java can see the functions it calls
  (`enumChoiceRef`, `QueryBuilder.add`, `JevResult.enumChoiceOf`), because inline code needs them public in the
  bytecode, but they're internal to jev4k and can change without notice. Build a `ChoiceQuestion` with the option
  keys you want and add it with `QueryBuilder.question(id, question)` instead.
- **`JevResult.enumChoice<E>(id)`** is reified too. Read that answer with `result.choice(id)`, which is keyed by
  option string.

Everything else is callable: `evaluate`, `models`, the inline `noul`, `choice` and `score` builders, the other
result accessors, and enums implementing `JevOption`. Only the getters that return a `Duration`, such as
`JevConfig.timeout` and `RetryPolicy.initialBackoff`, stay Kotlin-only.

### Module name

The jar declares `Automatic-Module-Name: com.pambrose.jev4k` for JPMS builds.

### Java version

The class files are Java 17 (`org.gradle.jvm.version = 17` in the published metadata), and the
compiler is held to the Java 17 API, so nothing newer can slip in.

### Threads

A `JevClient` is immutable once built and safe to share across coroutines. On the JVM, `jev.blocking` wraps the
suspend calls in `runBlocking`, so call it from ordinary threads, never from inside a coroutine.

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

Next: the [Quick Start](quick-start.md).
