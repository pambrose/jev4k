# Changelog

All notable changes to jev4k are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Narrative notes for each release are in
[RELEASE_NOTES.md](RELEASE_NOTES.md).

## [0.2.0] — unreleased

jev4k is now a Kotlin Multiplatform library, published under the new group `com.pambrose.jev4k`. The JVM API is
unchanged; the other platforms are new.

### Added

- Targets beyond the JVM: Apple platforms (`macosArm64`, `iosArm64`, `iosX64`, `iosSimulatorArm64`, `tvosArm64`,
  `tvosSimulatorArm64`, `watchosArm32`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`), Linux
  (`linuxX64`, `linuxArm64`), Windows (`mingwX64`), and Node.js (`js`, `wasmJs`). The whole API is common code.
- A default Ktor engine per platform: CIO on the JVM, as before, Darwin on Apple platforms, Curl on Linux, WinHttp
  on Windows, and the `fetch`-based Js engine on Node.js. CIO can't make HTTPS requests on Kotlin/Native, hence the
  others.
- Each engine's own way of reporting a failed connection (a bare `IllegalStateException` from Curl and WinHttp, a
  failed fetch from the Js engine) becomes a `JevConnectionException` and is retried like an `IOException`. On
  Linux and Windows that covers any bare `IllegalStateException` raised during a call, including one from a
  caller-supplied engine or a `MockEngine` handler; the JVM and Apple platforms rethrow those unchanged.
- ABI validation. `api/jev4k.api` (the JVM surface) and `api/jev4k.klib.api` (the other targets) record the public
  API; `make tests` and CI fail when it changes, and `make abi-update` rewrites them after an intended change.
- `LiveProbeTest`, two opt-in calls to the real API that spend no tokens, run on every platform: an invalid key must
  come back as `JevAuthenticationException` and a 1 ms timeout as `JevTimeoutException`.
- `make docker-linux-tests` runs the linuxX64 and linuxArm64 tests in Docker containers, so any host with Docker can
  run them, and linuxArm64, which has no Gradle test task, is tested at all. `make all-tests` includes it when Docker
  is running.
- The tvOS and watchOS simulator tests run on a Mac that has a simulator device for them, and are skipped on one
  that doesn't; `make tests` and `make native-tests` include them.

### Changed

- **Breaking: Maven coordinates.** The group is now `com.pambrose.jev4k`, so every artifact sits under one group, as
  common-utils' do. `com.pambrose.jev4k:jev4k` is the multiplatform root module: Gradle builds depend on it and get
  the right artifact for each target, and Maven builds depend on `com.pambrose.jev4k:jev4k-jvm`. 0.1.0 stays at
  `com.pambrose:jev4k`, and nothing newer is published there.
- The JVM API is unchanged: its ABI dump matches 0.1.0's, `JavaInterop.java` compiles as before, and the
  `jev4k-jvm` POM lists the same seven dependencies. The published metadata still carries
  `org.gradle.jvm.version = 17`.
- `JevClient.blocking` has its methods on the JVM only. On the other platforms `BlockingJev` has no members:
  Kotlin/JS and Kotlin/Wasm can't block a thread, and Kotlin/Native callers can wrap the suspend calls in
  `runBlocking` themselves.
- The `User-Agent` version is compiled in rather than read from the jar manifest, so it is correct on every
  platform and in tests. The JVM jar still carries `Implementation-Version` and `Automatic-Module-Name`.
- Sources moved to `src/commonMain`, `src/jvmMain` and the per-platform source sets. Tests moved to
  `src/commonTest`, which runs on every platform, and `src/jvmTest`, which keeps the MockK, blocking, live-smoke and
  real-CIO tests, the runnable example, and the documentation examples.
- `make build` compiles every target without running tests; `make tests` re-runs every test task the host supports;
  `jvm-tests`, `js-tests`, `native-tests`, `platform-tests` (every platform's tests, Docker Linux included),
  `abi-check` and `abi-update` are new. The Maven Central publishing targets require macOS, the only host that
  builds the Apple targets.
- CI runs the jvm, js, wasmJs and linuxX64 tests on Linux, and adds a macOS job (macOS and the iOS, tvOS and
  watchOS simulators) and a Windows job (`mingwX64`). A `ci-ok` job, required by branch protection, passes only when
  every other job did, so the JDK matrix and the native jobs gate a merge.
- The documentation site is built, in strict mode, on every pull request and `master` push, and deployed only when a
  release is published, so it never shows a version that isn't on Maven Central yet.

### Fixed

- Failures that escaped as raw Ktor or JDK exceptions are now `JevException`s:
  - a malformed `Content-Type` header, which the body is no longer decoded by;
  - a response body that isn't valid UTF-8, which is now decoded leniently on every platform;
  - on the JVM, a server certificate the JDK doesn't trust, and a response CIO can't parse (both
    `JevConnectionException`);
  - a response body cut short of its `Content-Length` (`JevConnectionException`, retried).
- A call on a closed `JevClient` fails with `IllegalStateException("JevClient is closed")` instead of a bare
  `CancellationException` that looked like the caller's own cancellation.
- A call cancelled because a sibling coroutine failed ends with its `CancellationException`, not a
  `JevConnectionException` made from the sibling's exception, and is no longer retried.
- JSON nested more than 128 levels deep, in a state, a question entry or a response, is rejected with a
  `JevValidationException` or `JevResponseValidationException` instead of overflowing the stack.
- With a caller-supplied engine, a `JevTimeoutException` from that engine's own connect or socket timeout names it
  instead of quoting `timeout`, which jev4k sets only as the request timeout for such an engine.

## [0.1.0] - 2026-09-20

First release: a Kotlin DSL and client for [TypeSafe](https://docs.typesafe.ai)'s **Jev** *System One* model.

### Added

#### Questions and answers

- Three question types — **Noul** (`NoulQuestion`), **Choice** (`ChoiceQuestion`) and **Score** (`ScoreQuestion`) —
  with the typed answers `NoulAnswer`, `ChoiceAnswer<K>` and `ScoreAnswer`.
- `QuestionRef<A>`: a handle pairing a question id with its definition and a decoder for the typed answer.
- `QuestionSet`, validated before anything is sent. `JevValidationException` collects every problem into one
  message: at least one question, unique non-blank ids, non-empty instructions, 1..255 Choice options,
  2..10 Score levels.

#### Inline DSL

- `jev.query(state) { ... }` builds a request from string ids: `noul`, `choice`, `score` and `question`.
- Option and level builders: `options(...)`, `option(key, description)`, the `means` infix, `level(...)` and
  `levels(...)`.
- Builder functions return `QuestionRef` handles, so answers can be read by handle instead of by id.
- `include(query)` merges a typed `JevQuery` into the same request.

#### Typed queries

- `object X : JevQuery()` declares questions as properties. A `PropertyDelegateProvider` takes each question id
  from the property name, or from an explicit `id =`, and registers questions in declaration order.
- `JevQuery.questions` is built lazily, so an invalid definition fails on first use rather than at class load.
- `choice<E>()` builds options from an enum. `JevOption.entry` supplies the description and
  `JevOption.optionKey` overrides the option key, which defaults to the constant's name.
- `jev.ask(query, state)` sends a typed query.

#### State

- A state may be a `String`, a `JsonElement`, or any `@Serializable` value. Caller-supplied values are encoded
  with `encodeDefaults = true`, so default-valued fields reach the model.
- `jsonEntry()` and the entry builders in `Entries.kt` assemble structured states.

#### Reading results

- `result[handle]` decodes through the handle's decoder and verifies the handle belongs to the request.
- By-id accessors `noul(id)`, `choice(id)`, `score(id)` and `enumChoice<E>(id)` check that the question type
  matches what was asked.
- `nouls`, `choices`, `scores` and `answers` expose every answer; `model` and `usage` report what the call cost.
- Choice probabilities are returned in the order the options were declared. Score keys `"0".."n"` become `Int`.
- An unknown enum option becomes `JevResponseValidationException`; an answer with no `type` is read as the type
  of question that was asked, and an unrecognized type becomes `UnknownAnswer`.

#### Client

- `JevClient`, a `JevApi` implementation over the Ktor client (CIO engine), closeable with `use { }`.
- `JevApi.evaluate(state, questionSet, model)` is the only call that sends questions;
  `JevApi.models()` lists available models.
- `BlockingJev` (`jev.blocking`) wraps the suspend API for non-coroutine callers.
- Retry rules matching TypeSafe's official SDKs: 408, 429 and 5xx, connection errors and timeouts, backoff from
  0.5 s doubling to 5 s with 25% jitter, honoring `retry-after-ms` and `Retry-After` hints up to 60 s.
- Requests carry a User-Agent built from the jar's `Implementation-Version`.

#### Configuration

- `JevConfig` resolves each setting as explicit value, then environment variable, then default. Blank
  environment values are ignored.
- `TYPESAFE_API_KEY` (required), `TYPESAFE_BASE_URL` (default `https://api.typesafe.ai`) and
  `TYPESAFE_DEFAULT_MODEL` (default `jev-latest`).

#### Errors

- A sealed `JevException` hierarchy: `JevConfigException`, `JevValidationException`,
  `JevResponseValidationException`, and `JevApiException` with the per-status subclasses
  `JevBadRequestException`, `JevAuthenticationException`, `JevPermissionDeniedException`,
  `JevNotFoundException`, `JevUnprocessableEntityException`, `JevRateLimitException`,
  `JevInternalServerException` and `JevOverloadedException`, plus `JevConnectionException` and
  `JevTimeoutException`.
- API exceptions keep the response status, the raw body and the `x-typesafe-request-id` header.
- Response parsing errors name the field path that failed, such as `answers.<id>.noul`.

#### Testing support

- `jevResult(...)` and `jevApiException(...)` build results and failures without HTTP, so application code can
  be tested against a mocked `JevApi`.

#### Java interop

- `@JvmOverloads` on `JevClient`'s builder constructor, `BlockingJev`'s calls and `QueryBuilder.noul` gives Java
  the overloads Kotlin's trailing-default rule doesn't generate.
- `@JvmSynthetic` marks every public `inline reified` member, which javac cannot resolve in any case.
- The jar declares `Automatic-Module-Name: com.pambrose.jev4k` for JPMS builds.
- `src/test/java/website/JavaInterop.java` compiles with the test sources, so the Java-visible surface can't
  drift unnoticed.

#### Build and distribution

- Published to Maven Central as `com.pambrose:jev4k`, with sources and Dokka HTML javadoc jars, under
  Apache 2.0.
- Java 17 bytecode floor (`-Xjdk-release=17`), built with a JDK 25 toolchain.
- `ktor-client-core`, `kotlinx-serialization-json` and `kotlinx-coroutines-core` are `api` dependencies, since
  their types appear in the public API. The CIO engine can be excluded and another Ktor engine supplied.
- No logging binding is pulled in.

#### Documentation

- A documentation site at <https://jev4k.com/>, published from `website/jev4k` on every push to `master`. Every
  example on it is compiled with the test sources.
- Dokka KDocs for the public API at <https://jev4k.com/kdocs/>.
- `llms.txt` at <https://jev4k.com/llms.txt>, indexing the site for coding agents.

[0.2.0]: https://github.com/pambrose/jev4k/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/pambrose/jev4k/releases/tag/0.1.0
