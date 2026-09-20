# Changelog

All notable changes to jev4k are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Narrative notes for each release are in
[RELEASE_NOTES.md](RELEASE_NOTES.md).

## [0.1.0] — unreleased

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

- A documentation site at <https://pambrose.github.io/jev4k/>, published from `website/jev4k` on every push to
  `master`. Every example on it is compiled with the test sources.
- Dokka KDocs for the public API at <https://pambrose.github.io/jev4k/kdocs/>.
- `llms.txt` at <https://pambrose.github.io/jev4k/llms.txt>, indexing the site for coding agents.

[0.1.0]: https://github.com/pambrose/jev4k/releases/tag/0.1.0
