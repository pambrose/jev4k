# jev4k code review

Reviewed 2026-09-27, at commit `8c89c87` on the `kmp` branch. That commit includes the Kotlin Multiplatform move and
the simplification pass after it. The review covers the whole repository: library, tests, build, Makefile, CI,
README, documentation site and release documents. The method is described at the end.

Every issue has a number. Tick its box when it's fixed, and update the count below in the same change.

**Status: 85 of 85 fixed.** 1 high, 15 medium, 65 low, 4 nit.

## Summary

### High

- [x] [#1](#issue-1) **Client (JVM).** `defaultEngine = CIO` loads CIO in a static initializer, so the documented
  "exclude CIO" setup throws `NoClassDefFoundError` (a regression from `8c89c87`)

### Medium

- [x] [#2](#issue-2) **Client.** Reading the response body can throw raw Ktor exceptions: a bad `Content-Type`, or
  non-UTF-8 bytes off the JVM
- [x] [#3](#issue-3) **Client (JVM).** A TLS trust failure escapes as a raw `CertificateException` instead of
  `JevConnectionException`
- [x] [#4](#issue-4) **Client (JVM).** A truncated body or an unparseable status line escapes as a raw exception and
  is never retried
- [x] [#5](#issue-5) **Client.** A call on a closed `JevClient` fails with a bare `CancellationException`
- [x] [#6](#issue-6) **Client.** A call cancelled because a sibling failed is reported as a Jev error instead of
  `CancellationException`
- [x] [#7](#issue-7) **Client.** `JevApiException.headers` is case-sensitive, and key casing differs by engine
- [x] [#8](#issue-8) **Client.** Deeply nested JSON (a state, or a response) overflows the stack
- [x] [#9](#issue-9) **Config.** An API key or header with a control character leaks the full key in a raw Ktor
  exception on every call
- [x] [#10](#issue-10) **Config.** A `baseUrl` with trailing whitespace or a bad port passes validation, then every
  call throws `URLParserException`
- [x] [#11](#issue-11) **Results.** `enumChoice<E>(id)` doesn't check E against the declared options, so a caller
  mistake is blamed on the server
- [x] [#12](#issue-12) **Java interop.** Java can't reach any `Duration`-typed API (timeout, `RetryPolicy`,
  `retryAfter`), yet the docs say "everything else is callable"
- [x] [#13](#issue-13) **CI.** The docs site and KDocs are never built on PRs, and Zensical runs without `--strict`
- [x] [#14](#issue-14) **Release.** Install snippets name the unpublished 0.2.0 coordinates, and the docs deploy
  before Central has them
- [x] [#15](#issue-15) **Docs.** The README's MockK testing example fails: `"technical"` versus the enum key
  `TECHNICAL`
- [x] [#16](#issue-16) **Examples.** Examples that build Choice options from caller data throw on ordinary inputs
  (over 255 lines, empty lists)

### Low

Client and HTTP

- [x] [#17](#issue-17) A `null` answer fails the whole response instead of failing lazily on read
- [x] [#18](#issue-18) The response mapper accepts NaN/Infinity and out-of-range Score level keys
- [x] [#19](#issue-19) `JevResponseValidationException` from mapping drops the headers and holds re-serialized JSON,
  not the raw body
- [x] [#20](#issue-20) `models()` discards the `x-typesafe-request-id` of a successful call
- [x] [#21](#issue-21) A blank per-call model is sent as `"model": ""`
- [x] [#22](#issue-22) A `Retry-After` HTTP-date is ignored, unlike the official Python SDK
- [x] [#23](#issue-23) With a supplied engine, `JevTimeoutException` quotes `config.timeout` even when the engine's
  own connect timeout fired
- [x] [#24](#issue-24) A configured `Accept` header doesn't replace jev4k's; `ContentNegotiation` appends
  `application/json`
- [x] [#25](#issue-25) Redirects are followed by default: custom headers go to another host, and Node re-sends
  POST bodies
- [x] [#26](#issue-26) Response bodies are read whole into memory, with no size cap
- [x] [#27](#issue-27) No per-call timeout, retry or header overrides, unlike both official SDKs

Configuration and validation

- [x] [#28](#issue-28) A `baseUrl` with userinfo or a query string is accepted, then credentials leak into messages
  and queries are corrupted
- [x] [#29](#issue-29) Plain `http://` is accepted for any host, so a mistyped URL sends the key in cleartext
- [x] [#30](#issue-30) `RetryPolicy.retryStatuses` aliases the caller's set, so a built client can change later
- [x] [#31](#issue-31) A number or boolean state, and empty `{}`/`[]` instructions, pass local validation
- [x] [#32](#issue-32) `QuestionSet.toJson()` throws a raw kotlinx exception for a non-finite number
- [x] [#33](#issue-33) `jsonOf` rejects primitive arrays (`IntArray`, `DoubleArray`), though its KDoc promises arrays
- [x] [#34](#issue-34) `entry()`/`jsonOf()` errors escape a `JevQuery` object's initializer as
  `ExceptionInInitializerError`

DSL and results

- [x] [#35](#issue-35) A `JevQuery` silently drops questions declared after `questions` was first read
- [x] [#36](#issue-36) `QuestionSet.ids` reads the caller's live list instead of the defensive copy
- [x] [#37](#issue-37) `QueryBuilder.question(id, q)` uses an identity decoder, so reads never check the answer type
- [x] [#38](#issue-38) `QueryBuilder.question()` keeps the caller's `Map`/`List` by reference after validation
- [x] [#39](#issue-39) A string-keyed Choice returns an undeclared option as is, while the enum path rejects it
- [x] [#40](#issue-40) Inline and typed builders take id and instructions in opposite order, so a swapped call
  compiles
- [x] [#41](#issue-41) No stated policy for adding subtypes to the sealed `Answer` and `Question` types

Java interop and public API

- [x] [#42](#issue-42) `BlockingJev` throws an undeclared checked `InterruptedException`
- [x] [#43](#issue-43) `JevApiException` isn't Java-serializable with a JSON body, nor is `JevRateLimitException`
  with a retry hint
- [x] [#44](#issue-44) `BlockingJev` can only be built by `JevClient`, so blocking code can't wrap a fake `JevApi`
- [x] [#45](#issue-45) The docs say Java has "no route" to enum Choices, but the `@PublishedApi` members are callable
- [x] [#46](#issue-46) `@PublishedApi` on `ValueJson` does nothing, and puts an internal `Json` into both ABI dumps
- [x] [#47](#issue-47) The testing docs show only a fixed-QuestionSet mock, which fails for per-call handles
- [x] [#48](#issue-48) `jevResult(String)` skips the client's body-level validation, contrary to its docs
- [x] [#49](#issue-49) `RetryPolicy`'s docs say the defaults match both SDKs; Python also has a 30 s total budget

Tests

- [x] [#50](#issue-50) The "no tests discovered" guard is off for every test task
- [x] [#51](#issue-51) No test checks that `platformGetenv` actually returns a value
- [x] [#52](#issue-52) `mapModels`'s rejection branches and optional fields are untested
- [x] [#53](#issue-53) The default `retryDelay` (the real wait) never runs in any test
- [x] [#54](#issue-54) No test checks that a supplied engine keeps its own connect and socket timeouts
- [x] [#55](#issue-55) No test pins the false side of the exact-class `IllegalStateException` rule
- [x] [#56](#issue-56) The real-CIO timeout test's 1 s budget includes CIO's cold start
- [x] [#57](#issue-57) `make live-tests` passes without any real API call when `TYPESAFE_API_KEY` is missing

Build and CI

- [x] [#58](#issue-58) Branch protection requires only `build`, so the JDK matrix and native jobs never block a merge
- [x] [#59](#issue-59) CI's Apple row omits the tvOS and watchOS simulator tests
- [x] [#60](#issue-60) linuxArm64 is published, but no CI job runs its tests
- [x] [#61](#issue-61) `make build` never compiles `jvmTest`, so the website examples and the Java guard go unchecked
- [x] [#62](#issue-62) `check` links test binaries the host can never run (iosX64, mingwX64)
- [x] [#63](#issue-63) javac compiles the Java example against JDK 25's library instead of `--release 17`
- [x] [#64](#issue-64) The JDK 21 gate on `-XX:+EnableDynamicAgentLoading` rests on a false premise
- [x] [#65](#issue-65) A CI comment says coverage is uploaded when tests fail; a `jvmTest` failure skips it
- [x] [#66](#issue-66) A CI comment says every master commit is built; queued runs are replaced
- [x] [#67](#issue-67) The Kover comment says `build -x allTests` skips the tests; it doesn't
- [x] [#68](#issue-68) `dependabot.yml` leaves out `website/uv.lock` without saying why

Release and documentation

- [x] [#69](#issue-69) The release checklist's version step names only README.md, not `GettingStarted.txt`
- [x] [#70](#issue-70) The release checklist's intro and its Dokka line reference are stale
- [x] [#71](#issue-71) The release notes say JVM error handling "works unchanged elsewhere"; Linux and Windows
  retry every bare `IllegalStateException`
- [x] [#72](#issue-72) The docs say jev4k writes nothing to stderr; without an SLF4J provider a warning appears
- [x] [#73](#issue-73) The development page says `.env` reaches every test task; it reaches only the JVM ones
- [x] [#74](#issue-74) "Node.js only" is true of the tests, but the js/wasmJs artifacts also run in a browser

Website examples

- [x] [#75](#issue-75) The model-router example caps uncertain prompts at LARGE even when they need REASONING
- [x] [#76](#issue-76) Two concurrency examples fan out without a bound, against the docs' own advice
- [x] [#77](#issue-77) The "Rank without new requests" example re-queries Jev for every ranking
- [x] [#78](#issue-78) The extraction examples leave out the `none` escape the page requires
- [x] [#79](#issue-79) The guardrail example blocks an uncertain self-harm signal that the page says goes to support
- [x] [#80](#issue-80) The guardrail example can't switch policies without re-asking, as its page claims
- [x] [#81](#issue-81) The verification example has no explicit criteria and checks content on empty fields

### Nit

- [x] [#82](#issue-82) `JavaInterop.java`'s comment overstates what it pins
- [x] [#83](#issue-83) `BlockingJevTest` matches the `QuestionSet` with `any()`
- [x] [#84](#issue-84) CLAUDE.md says every type name follows the JS SDK; only the question types do
- [x] [#85](#issue-85) README testing section: a missing space and a doubled lead-in colon

## Plan: order of fixing

Four rules set the order:

1. The regression comes first.
2. The checks that catch later mistakes come next.
3. Changes to the same file are batched, so each file is opened once.
4. Every change to the public API is batched into one `make abi-update`, landing before 0.2.0 ships. 0.2.0 already
   changes the Maven coordinates, so it's the cheapest time for any other break.

Steps 1 and 2 belong before `kmp` is merged, and steps 3 to 5 before 0.2.0 is published. Steps 6 to 11 can follow
in 0.2.x. Each step ends with `make tests`. Steps that touch the site also end with
`cd website/jev4k && uv run zensical build --clean`.

1. **Fix the regression now: #1.** It's a one-line getter. Add the classloader or bytecode guard in the same
   change.
2. **Safety net before merging `kmp`: #13, #58, #59, #61, #50, #14, #69, #70, #15.** CI then builds the docs on
   PRs, gates merges on every job, runs the tvOS/watchOS tests and compiles the examples, so later steps are
   checked. Settle the release and deploy order (#14, #69, #70) before merging. Merging deploys the site, so this
   decision can't wait. #15 is a one-word fix.
3. **Error paths in `JevClient.execute`/`send`: #2, #3, #4, #5, #6, #8, #23, then tests #55 and #53, then release
   notes #71.** These all go through the same catch chain, `Retry.kt` and the platform `isPlatformConnectionError`
   functions. #5 and #6 share the `ensureActive()` check. For #8, passing the raw response text into the mapper
   also sets up #19. Decide the bare-ISE policy before writing #71.
4. **Configuration validation in `JevConfigBuilder.build()`: #9, #10, #28, #29, #30, #21.** One pass through
   `build()`: trim, parse the URL once, check characters, and snapshot the retry set. #29 adds public API, so pair
   it with step 5's ABI update.
5. **Public API before 0.2.0 (one `make abi-update` on a Mac): #12, #42, #43, #44, #20, #46, #40, #41, #7, #27.**
   Java-reachable members for the `Duration` settings (#12), `@Throws` on `BlockingJev` (#42, source-breaking for
   Java), serializable exceptions (#43), the `BlockingJev` factory (#44), `models()` returning a request id (#20),
   dropping `@PublishedApi` from `ValueJson` (#46), and the header map contract (#7). For #40, #41 and #27, decide:
   change the API or document the choice. Update `JavaInterop.java` once for all of them.
6. **DSL and result correctness: #11, #35, #36, #37, #38, #39, #31, #32, #33, #34, #17, #18, #19.** These are
   mostly local changes in `Builders.kt`, `JevQuery.kt`, `Questions.kt`, `JevResult.kt`, `Entries.kt` and
   `ResponseMapper.kt`, each with a common test. Do #35 before #36, since #35 is the root cause behind #36.
7. **HTTP hardening: #25, #24, #22, #26.** `followRedirects = false` and the `Accept` merge strategy are one line
   each. The HTTP-date parsing and the body cap are optional hardening.
8. **Test gaps and helpers: #47, #48, #51, #52, #54, #56, #57, #83.** #48 moves `parseObject` into a shared helper.
   #51 needs an environment variable set on every test task.
9. **Build and CI hygiene: #60, #62, #63, #64, #65, #66, #67, #68.** #60 is the only one with real work: QEMU in CI,
   or a separate arm64 run. The rest are small.
10. **Documentation: #72, #73, #74, #45, #49, #82, #84, #85.** Text-only changes to the README, the site, KDoc and
    CLAUDE.md.
11. **Website examples: #16, #75, #76, #77, #78, #79, #80, #81.** Example code under `src/jvmTest/kotlin/website`
    and the matching pages. After this batch, run `make tests` and the Zensical build with `--strict` (from #13).

## Issues

### High

<a id="issue-1"></a>

#### 1. `defaultEngine = CIO` loads CIO in a static initializer, so excluding CIO breaks the JVM client

**High** · Client (JVM) · small · `src/jvmMain/kotlin/com/pambrose/jev4k/internal/Platform.jvm.kt:9`

**Fixed.** `defaultEngine` is now a getter in its own `Engine.jvm.kt`, so `Platform_jvmKt` has no static initializer
and never references CIO. `CioExclusionTest` builds a config in a class loader that hides CIO. It failed with
`NoClassDefFoundError: io/ktor/client/engine/cio/CIO` before the change and passes after it.

**What's wrong.**
- `internal actual val defaultEngine … = CIO` is a top-level property with a backing field. That means the
  `Platform_jvmKt` class's static initializer reads `CIO.INSTANCE` (confirmed with `javap`).
- The same class holds `platformGetenv`, `isPlatformConnectionError` and `enumTypeName`, so the first call to any
  of them loads CIO.
- `JevConfigBuilder` reads the environment through `platformGetenv` whenever `apiKey`, `baseUrl` or
  `defaultModel` isn't set explicitly.

**Impact.** A user who follows the README and Installation page advice gets
`NoClassDefFoundError: io/ktor/client/engine/cio/CIO` from `JevClient { engine = OkHttp.create() }`. That advice is
to exclude `ktor-client-cio-jvm` when supplying their own engine. With every setting explicit, the error still
surfaces later, from the retry predicate.

This is a regression from the simplification commit `8c89c87`. At `ca779f7`, CIO appeared only inside a function
body. It hasn't been released.

**Fix.** Two options:
- Declare it with a getter, `internal actual val defaultEngine: HttpClientEngineFactory<HttpClientEngineConfig>
  get() = CIO`, so there is no backing field.
- Or move it into its own `Engine.jvm.kt`, like the native `Engine.*.kt` files.

Add a guard against it coming back. Either a jvmTest that loads jev4k through a classloader hiding
`io/ktor/client/engine/cio/`, or at least a bytecode assertion that `Platform_jvmKt`'s static initializer doesn't
reference CIO.

### Medium

<a id="issue-2"></a>

#### 2. Reading the response body can throw raw Ktor exceptions

**Medium** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:86`

**Fixed.** `send` reads the body with `readRawBytes().decodeToString()` and drops a leading byte-order mark, so
neither a malformed `Content-Type` nor bytes that aren't UTF-8 escape as a Ktor exception. ClientTest covers both,
on a success and an error status, on every platform.

**What's wrong.** `send()` calls `response.bodyAsText()` outside `execute()`'s try/catch, before the status check.
Two things can fail there:
- `bodyAsText()` first parses `Content-Type` to pick a charset. A malformed value such as `Content-Type: json`
  throws `BadContentTypeFormatException` on every platform, even for an error status.
- On every non-JVM target the charset decoder is fatal. A body that isn't valid UTF-8 throws
  `MalformedInputException`, for example a Latin-1 error page from a proxy or from the backend the Apple docs
  recommend.

**Impact.** Either way the caller gets a Ktor exception rather than a `JevException`. That breaks the documented
promise that every failure of a request is a `JevException` (`website/jev4k/docs/client/errors.md:31`).

**Fix.**
- Use `val text = response.readRawBytes().decodeToString()`. It never parses the `Content-Type`, JSON is UTF-8
  by RFC 8259, and the stdlib decoder replaces bad bytes rather than throwing.
- Add MockEngine tests for a bad `Content-Type` and for invalid UTF-8, each on a 2xx and on an error status.

<a id="issue-3"></a>

#### 3. On the JVM, a TLS trust failure escapes as a raw `CertificateException`

**Medium** · Client (JVM) · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:118`,
`src/jvmMain/kotlin/com/pambrose/jev4k/internal/Platform.jvm.kt:12`

**Fixed.** The JVM `isPlatformConnectionError` counts any `GeneralSecurityException`, so an untrusted certificate is
retried and then reported as `JevConnectionException`. ClientJvmTest checks it against a local TLS server with a
self-signed certificate.

**What's wrong.** With CIO, an untrusted certificate chain throws `java.security.cert.CertificateException`. Typical
causes:
- a TLS-inspecting corporate proxy whose root isn't in the JDK's `cacerts`;
- an expired or self-signed `baseUrl`.

It isn't an `IOException`, and the JVM's `isPlatformConnectionError` is always false. So `execute` rethrows it
unchanged (`else -> e`), and it isn't retried. README.md:525 and the errors page promise `JevConnectionException`.

**Fix.**
- Make the JVM `isPlatformConnectionError` true for `java.security.GeneralSecurityException`. That also retries
  it, as the official SDKs retry their connection error.
- If retrying a trust failure isn't wanted, map it in `execute` only.
- Correct the `Platform.jvm.kt` comment, and add a ClientJvmTest against a local self-signed TLS server.

<a id="issue-4"></a>

#### 4. On the JVM, a truncated body or an unparseable status line escapes raw and is never retried

**Medium** · Client (JVM) · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:118`

**Fixed.** The truncated-body error turned out to be Ktor's own `Content-Length` check in `SavedCall`, on the JVM
and native targets, so `isConnectionError` matches it on every platform. The JVM also counts CIO's
`ParserException`. ClientJvmTest drives a real CIO engine against a truncated body (three attempts) and a garbled
response, which also pins Ktor's wording.

**What's wrong.** CIO reports two ordinary network failures with exceptions the JVM doesn't treat as connection
errors:
- a body cut short of its `Content-Length` becomes `IllegalStateException("Content-Length mismatch…")`;
- a garbled status line becomes `io.ktor.http.cio.ParserException`.

**Impact.** A load balancer or proxy that drops the connection mid-response makes `evaluate`, `ask` and `models`
throw a raw `IllegalStateException` on the first attempt. None of the default retries run.

**Fix.**
- In `Platform.jvm.kt`, treat `ParserException`, and an `IllegalStateException` of exactly that class whose
  message starts with `"Content-Length mismatch"`, as connection errors.
- Pin the message with a test, so a Ktor upgrade that rewords it fails loudly.
- Keep ClientJvmTest's "a bare ISE is an ordinary failure" rule for every other ISE.

<a id="issue-5"></a>

#### 5. A call on a closed `JevClient` fails with a bare `CancellationException`

**Medium** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:107`, `:71`

**Fixed.** `send` fails fast with `IllegalStateException("JevClient is closed")`. A call that loses the client just
as it starts gets an `IllegalStateException` too, instead of a bare `CancellationException`.

**What's wrong.** `close()` only closes the Ktor client, and jev4k never checks for it. A later call runs into Ktor's
completed client job and fails with a `JobCancellationException`. `execute` then rethrows that as if the caller had
been cancelled.

**Impact.**
- A coroutine that calls a closed client ends as cancelled. No exception handler runs,
  `catch (e: JevException)` doesn't match, and the question is never asked, with no sign of why.
- Java's blocking callers get a `CancellationException` with no cause.

**Fix.**
- Add `check(http.isActive) { "JevClient is closed" }` at the top of `send`.
- In the `CancellationException` branch, call `currentCoroutineContext().ensureActive()` first. If the caller is
  still active, report the cancellation as a closed-client error rather than rethrowing it.
- Add a call-after-close test. Do this together with #6.

<a id="issue-6"></a>

#### 6. A call cancelled because a sibling failed is reported as a Jev error

**Medium** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:111`

**Fixed.** `execute` calls `ensureActive()` before classifying a failure, so a caller cancelled when a sibling
failed gets its own `CancellationException`. `retriesOn` now follows Ktor's rule and retries a cancellation only
when it wraps a timeout, and the `unwrapCancellation` KDoc is corrected.

**What's wrong.** When a sibling coroutine fails, structured concurrency cancels an in-flight Jev call with a
`JobCancellationException` whose cause is the sibling's exception. Ktor's `unwrapRequestTimeoutException` unwraps
it, so `execute` classifies the sibling's exception:
- an `IOException("disk full")` becomes `JevConnectionException("Could not reach …: disk full")`;
- anything else is rethrown as the sibling's exception.

**Impact.** The cancelled coroutine gets a misleading Jev error instead of `CancellationException`. Metrics or
alerts for Jev outages then fire for an unrelated failure.

**Fix.**
- Call `currentCoroutineContext().ensureActive()` at the start of `execute`'s catch, so a cancelled caller always
  gets its own `CancellationException`. Real timeouts still map to `JevTimeoutException`, because `HttpTimeout`
  cancels the request's context, not the caller's.
- Correct the `unwrapCancellation` KDoc in `Retry.kt`.

<a id="issue-7"></a>

#### 7. `JevApiException.headers` is case-sensitive, and key casing differs by engine

**Medium** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:89`,
`src/commonMain/kotlin/com/pambrose/jev4k/Errors.kt:36`, `src/commonTest/kotlin/com/pambrose/jev4k/ClientTest.kt:403`

**Fixed.** `JevApiException` lowercases header names in its constructor, merging the values of names that differ only in
case, so every engine and `jevApiException` agree. The KDoc, the README and the errors page state the contract, and the
client test sends `X-Trace` and reads `x-trace`.

**What's wrong.** `response.headers.toMap()` copies Ktor's case-insensitive `Headers` into a plain `LinkedHashMap`.
The engines then differ:
- CIO keeps the server's spelling.
- The Js engine (js and wasmJs) lowercases every name, as fetch does, and merges repeated headers.

**Impact.**
- `e.headers["Retry-After"]` works on the JVM and returns null on Node.
- On the JVM it also depends on how the server spelled the name.
- The only test sends and reads `X-Trace` in the same case, which MockEngine preserves.
- The JS SDK exposes case-insensitive headers.

**Fix.**
- Normalize once, in the internal `apiException` factory, so the `jevApiException` test helper matches. Either
  copy into Ktor's `CaseInsensitiveMap<List<String>>`, which keeps the type and the ABI, or lowercase every key.
- Document the contract on `JevApiException.headers`.
- Change the test to send `X-Trace` and read `x-trace`.

<a id="issue-8"></a>

#### 8. Deeply nested JSON overflows the stack

**Medium** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:60`, `:129`

**Fixed.** A state or question entry nested more than 128 levels fails with `JevValidationException` before anything
is sent. A response body that deep is refused before parsing with `JevResponseValidationException`, and an error
body's `bodyJson` is null. The limit started at 512, but encoding 512 levels crashed the mingwX64 test binary, whose
main thread has a 1 MB stack, so it is 128, serde_json's default. Tests at and just over the limit run on every
platform.

**What's wrong.** kotlinx.serialization encodes and parses JSON recursively. A state nested a couple of thousand
levels deep (about 10 KB, such as forwarded user JSON) makes `evaluate()` fail with:
- `StackOverflowError` on the JVM, which even `catch (e: Exception)` misses;
- a `RangeError` on Node;
- possibly a crash on Native.

On the response side, a deeply nested body overflows the parser. Error reporting then re-serializes the parsed tree
recursively (`BodyReader.fail`, `JevResult.invalid`), which is a second, shallower trigger.

**Fix.**
- Before sending, walk the state iteratively and reject depth over about 512 with `JevValidationException`. Do
  the same for criteria and option entries in `validate()`.
- For responses, pre-scan the `[`/`{` depth, skipping string literals, before `parseToJsonElement`.
- Pass the original response text into the mapper instead of `body.toString()`, which also covers #19.

<a id="issue-9"></a>

#### 9. An API key or header with a control character leaks the full key on every call

**Medium** · Config / security · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:103`,
`src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:63`

**Fixed.** `build()` trims the key and rejects any control character left in it with a `JevConfigException` that
gives the index, never the key. It also checks header names and values with Ktor's own rules, without quoting a
value. A key read with a trailing newline now just works.

**What's wrong.** `build()` rejects only a blank key. It never trims the key or checks its characters, and it
doesn't validate `headers`. A key with a trailing newline builds fine. Common sources:
- a secret created with `echo`;
- `File(...).readText()`;
- a CRLF env file.

**Impact.** Every call then fails in Ktor's header validation with `IllegalHeaderValueException`:
- The message is `Header value 'Bearer <full key>\n' contains illegal character …`, so the whole API key lands in
  logs and error reports.
- It isn't a `JevException`, so `catch (e: JevException)` misses it.
- No test covers this.

**Fix.**
- In `build()`, trim the resolved key and reject any remaining control character (below `0x20` other than tab,
  or `0x7F`). Report it as a `JevConfigException` that names the setting and the index, never the value.
- Validate header names and values with `HttpHeaders.checkHeaderName`/`checkHeaderValue`, again reporting
  without values.
- Add ConfigTest cases for a trailing newline, an embedded CR and a bad header name.

<a id="issue-10"></a>

#### 10. A `baseUrl` with trailing whitespace or a bad port passes validation, then every call fails

**Medium** · Config · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:108`

**Fixed.** `apiKey`, `baseUrl` and `defaultModel` are trimmed, and one that is blank after trimming counts as unset.
The base URL is parsed once in `build()`, so a bad port, a missing host or embedded whitespace is a
`JevConfigException` at build time, with no quoted URL when it could hold a secret.

**What's wrong.** `build()` strips trailing `/` and checks the scheme prefix, but never parses the URL. Ktor parses
it on every request. Because jev4k appends `/`, a trailing newline or space ends up inside the authority, and a
non-numeric or out-of-range port fails as well. `JevClient()` builds without complaint, then every call throws
`URLParserException`, which isn't a `JevException`.

**Fix.**
- Trim the resolved `apiKey`, `baseUrl` and `defaultModel`, treating a value that's blank after trimming as unset.
- Parse the base URL once in `build()` (`URLBuilder().takeFrom("$url/")`). Require http or https and a non-empty
  host, and add any failure to `problems`.
- Do this together with #9, #28 and #29.

<a id="issue-11"></a>

#### 11. `enumChoice<E>(id)` doesn't check E against the declared options

**Medium** · Results · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevResult.kt:49`

**Fixed.** `enumChoiceOf` gets the declared `ChoiceQuestion` from `requireQuestion` and requires E to cover every
declared option, naming the question, the missing options and E's keys. An option the server invents is still a
`JevResponseValidationException`. `enums.md`, the README and the errors page say so, and `DslTest` covers a wrong enum,
a subset enum and a superset one.

**What's wrong.** `enumChoiceOf` checks only that the id is a Choice. It never compares E's option keys with the
options the question declared. Two failure modes follow:
- **The wrong enum.** For example, options declared as `"technical"` read with an enum whose key is `TECHNICAL`.
  Every read throws `JevResponseValidationException`, a `JevApiException` carrying the request id, which blames
  the server for a caller mistake.
- **Overlapping keys.** For example, two enums that both define `OTHER`. A read can silently return the other
  enum's constant with a truncated probability map.

README.md:544 says misuse is an `IllegalArgumentException`.

**Fix.**
- Have `requireQuestion` return the declared `ChoiceQuestion`, then
  `require(question.options.keys.all { it in byKey })`, naming the question, the declared keys and E's keys.
- Keep `JevResponseValidationException` for options the server invents.
- Reword `website/jev4k/docs/queries/enums.md:69`, and add tests for a mismatched enum and a subset enum.

<a id="issue-12"></a>

#### 12. Java can't reach any `Duration`-typed API, yet the docs say everything else is callable

**Medium** · Java interop · medium · `README.md:506`, `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:83`

**Fixed.** Each `Duration` setting has a millisecond twin: `JevConfigBuilder.timeoutMillis`,
`JevCallOptionsBuilder.timeoutMillis`, `JevDefaults.TIMEOUT_MILLIS`, a `with…` method per `RetryPolicy` setting and
`JevRateLimitException.retryAfterMillis`. `jevApiException` takes `headers` before `retryAfter` and reads the hint from
them, and `jevResult` has `@JvmOverloads`. `JavaInterop.java` calls each, and the README and the Installation page name
the `Duration` getters as the only part left Kotlin-only.

**What's wrong.** `kotlin.time.Duration` is an inline class. Members that take or return one get mangled JVM names
that javac can't call, and constructors with `Duration` defaults become synthetic. From Java you can't:
- set `JevConfigBuilder.timeout`, or read `JevDefaults.TIMEOUT`;
- build any `RetryPolicy` other than the default or `NONE`, not even to change `maxRetries`, because the full
  constructor is synthetic;
- read `JevRateLimitException.retryAfter`;
- call the `jevApiException` overloads that take `retryAfter` or `headers`.

README.md:493-507 and the Installation page (lines 141-155) list four Java limitations and then say "Everything else
is callable". CLAUDE.md doesn't record this as deliberate.

**Fix.** Either option works:
- Add Java-reachable members without inline-class types: `JevConfigBuilder.timeoutMillis`,
  `JevRateLimitException.retryAfterMillis`, `JevDefaults.TIMEOUT_MILLIS`, a `RetryPolicy` builder or
  `@JvmStatic` factories taking millis, and `@JvmOverloads` on `jevResult`.
- Or document `Duration`-typed settings as a fifth Java limitation.

Either way, extend `JavaInterop.java` and run `make abi-update` on a Mac.

<a id="issue-13"></a>

#### 13. The docs site and KDocs are never built on PRs, and Zensical runs without `--strict`

**Medium** · CI · small · `.github/workflows/docs.yml:2`

**Fixed.** `docs.yml` builds the site and KDocs on every PR and `master` push with `--strict`, in its own
concurrency group per ref, and deploys only for a published release or a manual run. Its `docs` check is required,
and `make site-build` and CLAUDE.md use `--strict` too.

**What's wrong.** `docs.yml` runs only on pushes to master and manual dispatch, and `ci.yml`'s `build` runs neither
Zensical nor Dokka. So a PR that breaks something in the docs build passes every check:
- a `--8<--` snippet;
- a Dokka setting;
- a link.

The break shows up only in the master deploy. A build error then fails the deploy, leaving jev4k.com on the old
version. A warning ships silently. Zensical also runs without `--strict`, so warnings never fail anything. The
broken anchor fixed earlier today was one of these.

**Fix.**
- Add `pull_request` to `docs.yml`, and skip the deploy job on PRs with `if: github.event_name != 'pull_request'`.
- Give PR runs their own concurrency group. In the shared `pages` group, a PR run can cancel a queued master
  deploy.
- Add `--strict` to `docs.yml`, `make site-build` and the CLAUDE.md instructions.

<a id="issue-14"></a>

#### 14. Install snippets name the unpublished 0.2.0 coordinates, and the docs deploy before Central has them

**Medium** · Release · small · `README.md:83`, `src/jvmTest/kotlin/website/GettingStarted.txt:9`,
`.github/workflows/docs.yml:3`

**Fixed (option b).** `docs.yml` deploys only when a release is published, or on a manual run, and the
`github-pages` environment now accepts release tags as well as `master`. The release checklist publishes to Central
from the release PR's branch, then merges, tags and publishes the release, which deploys the site.

**What's wrong.** On `kmp`, every install snippet names coordinates that aren't on Central yet: `jev4k:0.2.0` in
the new group, and `jev4k-jvm:0.2.0` for Maven. That covers the README, the Installation page (through
`GettingStarted.txt`) and the Maven Central badge. Central returns 404 for them (checked).

Meanwhile:
- `docs.yml` deploys jev4k.com on every master push;
- the release checklist pushes before it publishes (step 4 before step 5).

**Impact.** Merging `kmp`, or any release push, shows install lines that fail with "Could not find", and tells
0.1.0 users to upgrade to a version that doesn't exist yet.

**Fix.** Choose one:
- Keep the public snippets on the released coordinates until the release commit, and move the "coordinates have
  changed" warnings into that commit.
- Or deploy the site only on a release (`release: published` or a tag push, plus manual dispatch), and publish to
  Central before the push that deploys.

For `kmp` itself, don't merge until 0.2.0 is on Central, or merge and publish in one sitting. Update the checklist
(#69).

<a id="issue-15"></a>

#### 15. The README's MockK testing example fails

**Medium** · Docs · small · `README.md:561`

**Fixed.** The fixture answers `"TECHNICAL"`.

**What's wrong.** The README's `Team` enum has no `optionKey` override, so its keys are `BILLING`, `TECHNICAL` and
`SALES`. The testing snippet's fixture answers `"choice":"technical"`. Copying the recommended testing idiom fails
with `JevResponseValidationException: question 'team': unknown option 'technical'`.

**Fix.**
- Use `"TECHNICAL"` in the fixture.
- To stop this drifting again, move the snippet into compiled code and include it from there: a region in the
  website examples, or ConsumerTest.

<a id="issue-16"></a>

#### 16. Examples that build Choice options from caller data throw on ordinary inputs

**Medium** · Examples · small · `src/jvmTest/kotlin/website/RankingExamples.kt:63`,
`src/jvmTest/kotlin/website/ChoiceExamples.kt:111`, `website/jev4k/docs/patterns/search.md:26`

**Fixed.** `findAnswer` returns null for an empty document and requires at most 255 lines, with a comment on searching
longer ones in two passes; `categorize` adds `other` only when it's absent, and says up to 254 plus `other`;
`countFruits` returns 0 for no items. `search.md` has the 255-option caveat, and the Choice page the 254 figure.

**What's wrong.** Several examples turn caller data straight into Choice options:
- **`findAnswer`** (line search) makes one option per line. A document over 255 lines, or an empty one, throws
  `JevValidationException`.
- **`categorize`** appends `"other"` to a list the page says can hold up to 255 entries, and it duplicates an
  existing `"other"`.
- **The counting helpers** throw on empty input.

`search.md` is also the only pattern page without the 255-option caveat, and TypeSafe's cookbook suggests two
passes for long documents.

**Fix.**
- **`findAnswer`:** guard it with `require(lines.size in 1..MAX_CHOICE_OPTIONS)`, or use two passes (pick a
  window, then a line in it).
- **`categorize`:** add `"other"` only when it's absent, and say "up to 254, plus other".
- **Counting helpers:** return 0 or an empty result for empty input.
- **`search.md`:** add the 255-option caveat.

### Low

#### Client and HTTP

<a id="issue-17"></a>

#### 17. A `null` answer fails the whole response

**Low** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ResponseMapper.kt:95`

**Fixed.** `mapSystemOne` drops `null` answers before mapping, so one fails when read, as a missing key does, and the
others still map.

**What's wrong.** `mapSystemOne` maps every requested id present in `answers`, including keys whose value is JSON
`null`. `BodyReader.answer` then rejects the non-object. Everywhere else the mapper treats `null` as absent, and an
absent answer fails only when it's read. So `{"answers":{"a":null,"b":{…}}}` loses the valid answer to `b`.

**Fix.** Skip `JsonNull` answers in the id filter, so they fail on read like a missing key. Add a
ResponseMappingTest case.

<a id="issue-18"></a>

#### 18. The response mapper accepts NaN/Infinity and out-of-range Score level keys

**Low** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ResponseMapper.kt:156`, `:177`

**Fixed.** `number()` requires a finite value, and `byLevel` refuses a level outside the question's levels (or a
negative one when the levels are unknown), each with its field path. There are still no range or sum checks on
probabilities.

**What's wrong.** `number()` accepts `NaN` and `Infinity`: an unquoted `NaN` parses, and so does `1e999`. Score
probability and legend keys aren't checked against the declared levels either. The failures surface later, in the
accessors:
- `nearestLevel` throws `IllegalArgumentException` with no field path;
- `band` and `isTrue` give odd results without complaint.

**Fix.**
- Add `.takeIf { it.isFinite() }` in `number()`, failing with "expected a finite number".
- When the Score question's levels are known, reject keys outside `0 until levels.size`, with the field path.
- Don't add strict `[0, 1]` or sum-to-1 checks; floating-point rounding would trip them.

<a id="issue-19"></a>

#### 19. `JevResponseValidationException` from mapping drops the headers and the raw body

**Low** · Client · medium · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ResponseMapper.kt:87`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevResult.kt:93`

**Fixed.** `send` builds an internal `ResponseInfo` (text, status, headers, request id, endpoint), which `parseObject`,
the mappers and `JevResult` use for every `JevResponseValidationException`. `ResponseMappingTest` and `ClientTest` check
the raw body, the status and the headers.

**What's wrong.** For a malformed 2xx body, `BodyReader.fail` and `JevResult.invalid` build the exception from:
- the parsed JSON serialized again (`body.toString()`), not the raw body;
- a status that defaults to 200;
- empty headers.

`send` has the real text, status and headers but doesn't pass them on. The KDoc, README and errors page say `body`
is the raw response. This only affects diagnostics.

**Fix.** Pass `text`, `status` and `response.headers` from `send` into `parseObject`, `BodyReader` and `JevResult`.
Alternatively, correct the docs. Passing them also removes the re-serialization overflow in #8.

<a id="issue-20"></a>

#### 20. `models()` discards the request id of a successful call

**Low** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:69`

**Fixed.** `models()` returns a `ModelList`, a `List<ModelInfo>` carrying `requestId`, which equals any list of the same
models. The README, the calls page, the mocks and the ABI dumps are updated.

**What's wrong.** The spec and both SDKs expose `x-typesafe-request-id` on success as well as on error. `evaluate`
does, through `JevResult.requestId`. `models()` returns a bare `List<ModelInfo>`.

**Fix.** Either option works:
- Return a small `ModelList(models, requestId)`. That changes the ABI, so update the dumps, the README table and
  any mocks.
- Or add a `modelsWithMetadata()` with a default implementation, so `JevApi` doesn't break.

<a id="issue-21"></a>

#### 21. A blank per-call model is sent as `"model": ""`

**Low** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:61`

**Fixed.** A blank per-call `model` falls back to the configured default, as a blank `defaultModel` does.
`JevApi.evaluate`'s KDoc says so, and a ClientTest case pins it.

**What's wrong.** The builder treats a blank `defaultModel` as unset. `evaluate`, though, uses
`model ?: config.defaultModel`, so `model = ""` sends `"model": ""`. That most likely gets a 4xx back after a wasted
round trip.

**Fix.** Use `model?.takeIf { it.isNotBlank() } ?: config.defaultModel`, or reject a blank model outright. Add a
test, and state the rule in `JevApi.evaluate`'s KDoc.

<a id="issue-22"></a>

#### 22. A `Retry-After` HTTP-date is ignored

**Low** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/Retry.kt:48`

**Fixed.** `retryHint` falls back to `fromHttpToGmtDate()` for `Retry-After`, using the time left from a `now` clock (an
internal config hook, for tests), still capped by `maxRetryAfter`. The date moved out of RetryTest's junk list, which
now tests a future, a present, a past and a malformed date, and ClientTest reads one into `retryAfter`.

**What's wrong.** Only numeric retry hints are parsed, and the KDoc says this is deliberate. But the official Python
SDK parses the date form for both its retry delay and its `retry_after`, and CLAUDE.md says the retry rules
reproduce the SDKs. If TypeSafe sends a date, retries fire too early and `retryAfter` is null. TypeSafe doesn't
document which form it sends, hence low severity.

**Fix.**
- Fall back to `runCatching { value.fromHttpToGmtDate() }` (ktor-http, common code). Use the positive difference
  from now, still capped by `maxRetryAfter`.
- Move the date out of RetryTest's list of junk values, and test a future date, a past date and a malformed date.

<a id="issue-23"></a>

#### 23. With a supplied engine, `JevTimeoutException` quotes the wrong timeout

**Low** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:115`

**Fixed.** `config.timeout` is quoted only when it is the limit that fired; a supplied engine's own connect or
socket timeout is named instead. The KDoc, the README and the errors page say so.

**What's wrong.** Every timeout becomes "timed out after `${config.timeout}`". With a caller-supplied engine,
though, only the request timeout is jev4k's. A `ConnectTimeoutException` from the engine's own 2 s connect timeout is
reported as "timed out after 30s", which points the reader at the wrong setting.

**Fix.**
- Quote `config.timeout` only for `HttpRequestTimeoutException`, or when jev4k built the engine.
- Otherwise name the phase, for example "the supplied engine's connect timeout".
- Reword the `JevTimeoutException` KDoc, `errors.md:48` and README.md:542 to match.

<a id="issue-24"></a>

#### 24. A configured `Accept` header doesn't replace jev4k's

**Low** · Client · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:66`

**Fixed.** At first `ContentNegotiation` was given `ContentTypeMergeStrategy.SkipIfPresent`. Later the plugin was
removed altogether: its only remaining job was encoding the request body, which `evaluate` now does itself, so nothing
adds a second `Accept` and `jev4k-jvm` drops two runtime dependencies. ClientTest checks `headers.getAll(Accept)` with
and without a configured one, and pins the request's bytes.

**What's wrong.** The comment says a configured `Accept` replaces jev4k's. `ContentNegotiation` then appends
`application/json` to any other value, so the request carries two. A gateway that is strict about `Accept` could
reject it.

**Fix.** Either option works:
- Install `ContentNegotiation` with `acceptHeaderMergeStrategy = ContentTypeMergeStrategy.SkipIfPresent`
  (`defaultRequest` always sets `Accept`), and assert `headers.getAll(Accept)` in ClientTest.
- Or correct the comment.

<a id="issue-25"></a>

#### 25. Redirects are followed by default

**Low** · Security · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:32`

**Fixed.** `followRedirects = false` sits next to `expectSuccess = false`, so a 3xx is a `JevApiException`, on any
engine. A MockEngine test checks that a 302 is reported and not followed, and the docs say so.

**What's wrong.** `followRedirects` is left at Ktor's default, true. On a cross-host redirect, `HttpRedirect` strips
only `Authorization`:
- `models()`, a GET, forwards every configured header (for example a gateway `X-Api-Key`) to the other host.
- On Node, fetch also re-sends `evaluate()`'s POST body and headers cross-origin.

This needs a misconfigured or compromised endpoint, and nothing documents the redirect behavior.

**Fix.**
- Set `followRedirects = false` next to `expectSuccess = false`. That covers every engine, supplied ones included,
  and fetch then uses `redirect: "manual"`. A 3xx becomes a `JevApiException`.
- Add a MockEngine test.

<a id="issue-26"></a>

#### 26. Response bodies are read whole into memory, with no size cap

**Low** · Security · medium · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:86`

**Fixed.** As chosen, the declared-length check only: a receive-pipeline phase ahead of Ktor's `SaveBody` refuses a
`Content-Length` over 16 MiB before the body is read. A 2xx becomes a `JevResponseValidationException`, anything else
its status's exception with a null body and a message saying why. A chunked body stays uncapped, bounded by the timeout,
as the docs say. ClientTest covers both paths, and ClientJvmTest a real CIO engine.

**What's wrong.** `http.request` saves the whole body, and `bodyAsText()` then decodes it. Nothing checks
`Content-Length` or caps the size (the official SDKs don't either). A misbehaving endpoint can exhaust a small heap
within the 10 s timeout.

**Fix.** Optional hardening:
- Switch to `prepareRequest(...).execute {}` with a bounded read, capped at something like 8 to 16 MiB.
- Report an oversized 2xx body as a `JevResponseValidationException`.
- Report an oversized error body as the usual status-mapped exception, with the body truncated and marked as such.

<a id="issue-27"></a>

#### 27. No per-call timeout, retry or header overrides

**Low** · API design · medium · `src/commonMain/kotlin/com/pambrose/jev4k/JevApi.kt:13`

**Fixed.** `JevCallOptions` overrides the timeout, retry policy and headers per call and adds `extraBody` fields,
through new `JevApi` members with default implementations and `JevApi.withOptions`. Headers moved from `DefaultRequest`,
which merges rather than replaces, to each request. `CallOptionsTest` covers each option.

**What's wrong.** Both SDKs accept per-call timeout, retry and header options, and Python can add extra body
fields. jev4k fixes all three per `JevClient`, and its request DTO has no room for extra fields. So:
- A latency-sensitive call needs a second client. The engine can be shared.
- `withTimeout` gives `TimeoutCancellationException` rather than `JevTimeoutException`.

This isn't recorded as deliberate.

**Fix.** Choose one:
- Add a `JevCallOptions` through a new `JevApi` member with a default implementation, so existing mocks keep
  working.
- Or add a derived client: `JevClient.withOptions {}`.
- Or record the omission as deliberate, and document engine sharing plus `withTimeout` as the workaround.

#### Configuration and validation

<a id="issue-28"></a>

#### 28. A `baseUrl` with userinfo or a query string is accepted

**Low** · Config / security · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:117`

**Fixed.** A `baseUrl` with userinfo, a query or a fragment is rejected. The message points to `headers` for gateway
credentials and never quotes the URL. The configuration page states the rule.

**What's wrong.** Only the scheme prefix is checked.
- **Userinfo** (`https://user:pass@proxy`). CIO drops it, so an authenticating proxy answers 401. The credentials
  then appear in the exception message and in `JevConfig.toString()`, which the docs call safe to log.
- **A query string.** It's corrupted by the appended `/`: `?token=abc` becomes `token=abc/`.

**Fix.**
- Once the URL is parsed (#10), reject userinfo, a query and a fragment, with a message that redacts them.
- Point users with gateway credentials to `headers` instead.
- Add ConfigTest cases, and update `configuration.md:69`.

<a id="issue-29"></a>

#### 29. Plain `http://` is accepted for any host

**Low** · Config / security · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:117`

**Fixed.** Plain `http://` is accepted without an opt-in only for loopback hosts (`localhost`, `*.localhost`,
`127.x.x.x`, `::1`); anywhere else it needs the new `JevConfigBuilder.allowInsecureHttp`. The ABI dumps, README,
configuration page and release notes document it, including for an Ollaya server on another host.

**What's wrong.** http needs no opt-in, yet the only documented need for it is Ollaya on localhost. A dropped "s" in
`TYPESAFE_BASE_URL` sends the bearer key and every state in cleartext on each call. This is a design trade-off: the
official SDKs accept any URL.

**Fix.**
- Allow http only for loopback hosts, and otherwise require an explicit opt-in such as `allowInsecureHttp = true`.
  The opt-in keeps a Docker or LAN Ollaya usable.
- This changes the ABI.
- Document the setting next to "Running with Ollaya".

<a id="issue-30"></a>

#### 30. `RetryPolicy.retryStatuses` aliases the caller's set

**Low** · Config · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:129`

**Fixed.** `build()` stores the retry policy with a copy of its status set.

**What's wrong.** `build()` takes a copy of `headers` but stores `retry` by reference, and `retryStatuses` can be
the caller's `MutableSet`. The retry predicate re-reads it on every response. Mutating the set afterwards changes a
built client's behavior, which contradicts "a `JevClient` is immutable once built".

**Fix.** In `build()`, use `retry = retry.copy(retryStatuses = retry.retryStatuses.toSet())`.

<a id="issue-31"></a>

#### 31. A number or boolean state, and empty instructions, pass local validation

**Low** · Validation · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:60`,
`src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:118`

**Fixed.** `evaluate` rejects a number or boolean state, naming the allowed shapes, and instructions must be non-blank
text or a non-empty object or array. `state.md` is reworded.

**What's wrong.**
- **State.** The API types it as string, object or array, and both SDKs leave out bare numbers and booleans. But
  only `JsonNull` is rejected locally, so `ask(q, state = order.id)` with a `Long` gets sent. The docs overstate
  it too: `website/jev4k/docs/queries/state.md:31` says "Any `@Serializable` value works as state".
- **Instructions.** `isEmptyEntry` misses non-string primitives and empty `{}`/`[]`, against jev4k's own
  "non-empty instructions" rule.

**Fix.**
- Reject a non-string `JsonPrimitive` state, with a message naming the allowed shapes.
- Count non-string primitives and empty objects or arrays as empty instructions.
- Reword `state.md:31`, and add tests.

<a id="issue-32"></a>

#### 32. `QuestionSet.toJson()` throws a raw kotlinx exception for a non-finite number

**Low** · Validation · small · `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:106`

**Fixed.** `validate()` reports an entry holding NaN or an infinity, naming the question and the entry, so `toJson()`
can't meet one.

**What's wrong.** The `JsonElement` overloads accept NaN, for example `level(JsonPrimitive(Double.NaN))`, and
`validate()` doesn't look inside entries. `evaluate()` maps the resulting encoding failure to
`JevValidationException`. But `toJson()`, which is documented for logging, throws kotlinx's `JsonEncodingException`
instead.

**Fix.** Catch `SerializationException` in `toJson()` and rethrow it as `JevValidationException`. Better still,
flag non-finite numbers in `validate()`, naming the question and the field.

<a id="issue-33"></a>

#### 33. `jsonOf` rejects primitive arrays

**Low** · Validation · small · `src/commonMain/kotlin/com/pambrose/jev4k/Entries.kt:71`

**Fixed.** `jsonOf` converts every primitive array except `CharArray`, element by element through `jsonOf`, so a NaN is
still caught. The KDoc names them.

**What's wrong.** The KDoc lists arrays among the supported inputs, but only `Array<*>` and `Iterable<*>` match.
`IntArray`, `DoubleArray` and the other primitive arrays throw `JevValidationException`, and so does `entry(...)`.

**Fix.** Add branches for the primitive arrays, mapping each element through `jsonOf` so the NaN check still applies.
Alternatively, narrow the KDoc. Add an EntriesTest case either way.

<a id="issue-34"></a>

#### 34. `entry()`/`jsonOf()` errors escape a `JevQuery` object's initializer

**Low** · Validation · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt:38`

**Fixed.** `QuestionProvider.provideDelegate` catches a `JevValidationException` thrown while a question is built, from
a builder lambda or an enum option's `JevOption.entry`, and registers a stand-in `QuestionRef` carrying its problems,
whose own question isn't checked, so no knock-on count is reported. An inline `questions {}` builder throws at once,
since it runs in the caller's code. An argument evaluated before the builder runs, such as `noul(entry(...))`, still
throws from the initializer; the `JevQuery` KDoc and CLAUDE.md say so.

**What's wrong.** The codebase defers definition errors so they can't escape an `object`'s static initializer:
duplicate options, for instance, are recorded rather than thrown. `entry()`, `jsonOf()` and `jsonEntry()` still
throw immediately. So an unsupported value or a NaN inside a `JevQuery` object surfaces as:
- `ExceptionInInitializerError` the first time the object is used;
- `NoClassDefFoundError` after that, with no cause on JDK 17.

It is never a `JevException`. The KDoc and CLAUDE.md say an invalid definition fails on first use.

**Fix.**
- At minimum, qualify the KDoc and CLAUDE.md.
- Optionally, catch `JevValidationException` from builder lambdas in `provideDelegate`, and register a
  placeholder whose problems carry it.

#### DSL and results

<a id="issue-35"></a>

#### 35. A `JevQuery` silently drops questions declared after `questions` was first read

**Low** · DSL · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt:44`

**Fixed.** `questions` is rebuilt when more questions have registered since it was built, so a read during
initialization no longer loses the later ones. The KDoc explains it, and `DslTest` reads the set partway through a
class.

**What's wrong.** `questions` is a lazy snapshot of the registered questions. Reading it during initialization
freezes the set: from an `init` block or a property initializer partway down the class, or from a base class's
`init`. Questions declared after that point are registered but never sent. Reading one of them later fails with
"not part of this request", which says nothing about declaration order.

**Fix.** Either option works:
- Fail loudly: track late registrations, and have `questions` throw a `JevValidationException` naming them.
- Or rebuild the set when the registered count changes.

Add a test and a KDoc sentence.

<a id="issue-36"></a>

#### 36. `QuestionSet.ids` reads the caller's live list

**Low** · DSL · small · `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:94`

**Fixed.** `ids` is built from `this.questions`, the copy, and the test grows the source list after building the set.

**What's wrong.** In `val ids by lazy { questions.map { it.id } }`, `questions` resolves to the constructor
parameter, not the defensive copy (confirmed in the bytecode). If the source list grows before `ids` is first read,
`ids` disagrees with the size and iteration, and error messages contradict themselves. That can happen through #35,
or through a leaked `QueryBuilder`.

**Fix.** Use `this.questions.map { it.id }`, or rename the parameter so the name can't be shadowed. Add a test.

<a id="issue-37"></a>

#### 37. `QueryBuilder.question(id, q)` never checks the answer type

**Low** · DSL · small · `src/commonMain/kotlin/com/pambrose/jev4k/Builders.kt:157`

**Fixed.** `question()` picks `decodeNoul`, `decodeChoice` or `decodeScore` from the sealed subtype, so a mismatched
answer is a `JevResponseValidationException` with its field path.

**What's wrong.** Every other handle factory uses a type-checking decoder, but `question(id, question)` uses
`{ it }`. So `result[handle]` returns whatever the server sent: a `NoulAnswer` for a Choice question, or an
`UnknownAnswer`. That contradicts `JevResult`'s KDoc. The caller then hits a `ClassCastException` with no field path
or request id.

**Fix.** Choose the decoder from the sealed `Question` subtype (`is NoulQuestion -> ::decodeNoul`, and so on). Add a
DslTest case. Typed overloads are optional.

<a id="issue-38"></a>

#### 38. `QueryBuilder.question()` keeps the caller's `Map`/`List` by reference

**Low** · DSL · small · `src/commonMain/kotlin/com/pambrose/jev4k/Builders.kt:157`

**Fixed.** `question()` stores a copy of the options map or the levels list.

**What's wrong.** `ChoiceQuestion` and `ScoreQuestion` store the collection the caller gives them, and
`question()` passes it straight into the `QuestionSet`, which validates only once. Mutating the map afterwards
changes what is sent: an emptied map goes out as `"criteria":{}` and comes back as a 422. This mostly affects Java,
because the README's enum-Choice workaround uses this path.

**Fix.** Copy in `question()`: `copy(options = options.toMap())` and `copy(levels = levels.toList())`. Add a test.

<a id="issue-39"></a>

#### 39. A string-keyed Choice returns an undeclared option as is

**Low** · Results · small · `src/commonMain/kotlin/com/pambrose/jev4k/Answers.kt:131`

**Fixed.** Leniency is the policy, matching both official SDKs: the `JevResult.choice` KDoc and the Choice page say an
undeclared option is returned as sent, and `ResponseMappingTest` pins it.

**What's wrong.** String-keyed Choice reads return whatever `choice` the server sent, even one that isn't a declared
option. Enum-backed reads reject it. The official SDKs don't check either, so this is a policy gap: the behavior is
neither documented nor tested.

**Fix.** Pick a policy:
- Document the leniency and pin it with a test, which matches the SDKs.
- Or check against the declared options in `choiceRef` and in `JevResult.choice(id)`.

<a id="issue-40"></a>

#### 40. Inline and typed builders take id and instructions in opposite order

**Low** · DSL · medium · `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt:50`

**Fixed.** `typed.md` no longer says the typed builders are the inline ones minus the id; it says instructions come
first, and a warning tells readers to pass `id` by name. The `JevQuery` KDoc says the same. Making a positional id
impossible is left for before 1.0.

**What's wrong.** `QueryBuilder` builders take `(id, instructions)`. `JevQuery`'s take `(instructions, id = null)`.
Both are strings, so moving an inline question into a `JevQuery` with the id still first compiles and validates.
Jev then receives the id, say "urgent", as the question. `website/jev4k/docs/queries/typed.md:30` calls the typed
builders the inline ones "minus the id argument".

**Fix.**
- Correct `typed.md:30`, and add a KDoc note to always pass `id =` by name.
- Before 1.0, consider making the typed id impossible to pass positionally. That changes the ABI.

<a id="issue-41"></a>

#### 41. No stated policy for new subtypes of the sealed `Answer` and `Question`

**Low** · API design · small · `src/commonMain/kotlin/com/pambrose/jev4k/Answers.kt:9`,
`src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:30`

**Fixed.** The KDoc on `Answer` and `Question` and the CHANGELOG state the policy: new subtypes may come in a minor
release, so a `when` that must keep compiling ends in `else`.

**What's wrong.** Adding a subtype, for example when TypeSafe adds a question kind, breaks consumers' exhaustive
`when` expressions. No policy says whether that can happen in a minor release. `UnknownAnswer` handles forward
compatibility on the wire only, not in consumers' source.

**Fix.** State the policy in the KDoc and the CHANGELOG:
- New subtypes can appear when TypeSafe adds a question kind.
- Code that must compile across upgrades should keep an `else` or `is UnknownAnswer` branch.
- After 1.0, either reserve new subtypes for major versions or declare them exempt.

#### Java interop and public API

<a id="issue-42"></a>

#### 42. `BlockingJev` throws an undeclared checked `InterruptedException`

**Low** · Java interop · small · `src/jvmMain/kotlin/com/pambrose/jev4k/BlockingJev.jvm.kt:15`

**Fixed.** Every `BlockingJev` method is `@Throws(InterruptedException::class)`. `JavaInterop.java` catches it,
`BlockingJevTest` interrupts a blocked call and checks every public method declares it, and the CHANGELOG and release
notes list it as a Java source change.

**What's wrong.** Interrupting a thread blocked in `BlockingJev` makes `runBlocking` rethrow `InterruptedException`.
That's a checked exception, and no method declares it:
- Java callers can't write `catch (InterruptedException e)`; javac rejects it.
- `catch (JevException | RuntimeException e)` doesn't catch it either.

**Fix.** Either option works:
- Add `@Throws(InterruptedException::class)` to every public `BlockingJev` method. It's binary-compatible but
  source-breaking for Java, so update `JavaInterop.java` and the release notes. Best done in 0.2.0.
- Or document the behavior.

<a id="issue-43"></a>

#### 43. API exceptions aren't Java-serializable

**Low** · Java interop · small · `src/commonMain/kotlin/com/pambrose/jev4k/Errors.kt:46`, `:94`

**Fixed.** `bodyJson` is a plain getter, and `JevRateLimitException` stores its hint as nanoseconds behind the
`retryAfter` getter, so both exceptions serialize. `ErrorsJvmTest` round-trips a rate-limit error built by
`jevApiException` and one built by the client.

**What's wrong.** On the JVM every `Throwable` is `Serializable`, but two fields break that:
- `JevApiException.bodyJson` is a `by lazy`. Serializing it forces the parse and writes a `JsonElement`, which
  isn't serializable.
- `JevRateLimitException` stores `retryAfter` as a `Duration`.

**Impact.** Serializing an API error with a JSON body throws `NotSerializableException`, and that means every error
the real service returns. A rate-limit error with a hint does the same. Frameworks that serialize exceptions are
affected: RMI/EJB, JMS, and session or cache replication.

**Fix.**
- Make `bodyJson` a plain computed getter. Don't use `@delegate:Transient`, which gives an NPE after
  deserialization.
- Store the hint as a private `Long?` behind the public `retryAfter` getter.
- The getter signatures don't change. Add a round-trip test.

<a id="issue-44"></a>

#### 44. Blocking code can't wrap a fake `JevApi`

**Low** · API design · small · `src/commonMain/kotlin/com/pambrose/jev4k/BlockingJev.kt:10`

**Fixed.** `fun JevApi.blocking(): BlockingJev` (`BlockingJevKt.blocking(api)` from Java) returns a client's own view,
or wraps any other `JevApi`. The calls page and the README document it.

**What's wrong.** `BlockingJev`'s constructor is internal, and only `JevClient.blocking` returns one. So blocking and
Java code can't depend on a `JevApi` and be handed a fake, which is what `website/jev4k/docs/client/calls.md`
recommends.

**Fix.** Add a JVM factory such as `fun JevApi.blocking(): BlockingJev` (from Java: `BlockingJevKt.blocking(api)`).
Document it, and update the ABI dumps.

<a id="issue-45"></a>

#### 45. The docs say Java has "no route" to enum Choices

**Low** · Docs · small · `README.md:500`, `website/jev4k/docs/getting-started/installation.md:148`, `CLAUDE.md`

**Fixed.** The README, the Installation page and CLAUDE.md now say Java can see `enumChoiceRef`, `QueryBuilder.add` and
`JevResult.enumChoiceOf`, because inline code needs them public, but that they aren't supported API, so build a
`ChoiceQuestion`.

**What's wrong.** The README, the Installation page and CLAUDE.md say `enumChoiceRef` is internal, so Java can't
reach enum Choices. But `@PublishedApi` members compile to public, unmangled methods that javac can call, and IDEs
offer them: `enumChoiceRef`, `QueryBuilder.add` and `JevResult.enumChoiceOf`.

**Fix.** Reword: Java can see these because inline functions use them, but they aren't supported API, so build a
`ChoiceQuestion` instead.

<a id="issue-46"></a>

#### 46. `@PublishedApi` on `ValueJson` does nothing useful

**Low** · API design · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/Wire.kt:23`

**Fixed.** The annotation is gone, and `ValueJson` with it from both ABI dumps; `encodeValue`, the `@PublishedApi`
function that uses it, isn't inline.

**What's wrong.** No inline function references `ValueJson`. The annotation only puts a getter for an internal
kotlinx `Json` instance into both ABI dumps, where it's the only symbol from the `internal` package. Renaming it
would therefore show up as an API change.

**Fix.** Drop the annotation and run `make abi-update`.

<a id="issue-47"></a>

#### 47. The testing docs show only a fixed-QuestionSet mock

**Low** · Docs / testing · small · `src/commonMain/kotlin/com/pambrose/jev4k/Testing.kt:21`, `README.md:560`

**Fixed.** The `jevResult` KDoc, the README and the calls page show `answers { jevResult(body, secondArg()) }` for
questions built per call, and `JevResult.get` now says when a handle's id is in the request but the handle isn't.
`ConsumerTest` exercises both.

**What's wrong.** The only mock shown is `returns jevResult(body, X.questions)`. Results check handle identity, so
this throws "not part of this request" for code that builds its questions on each call: inline handles, or
`JevQuery` class instances. The docs teach several such patterns.

**Fix.**
- Show `answers { jevResult(body, secondArg()) }` for questions built per call.
- Consider a clearer message when the id matches but the handle differs.

<a id="issue-48"></a>

#### 48. `jevResult(String)` skips the client's body-level validation

**Low** · Testing API · small · `src/commonMain/kotlin/com/pambrose/jev4k/Testing.kt:42`

**Fixed.** `parseObject` moved to `ResponseInfo.parseObject()`, which `JevClient` and `jevResult(String)` share.
`ConsumerTest` checks `"not json"` and `"[]"`.

**What's wrong.** The client turns a non-JSON or non-object body into a `JevResponseValidationException`, in
`parseObject`. `jevResult(String)` instead calls `parseToJsonElement(body).jsonObject` directly, and throws
`SerializationException` or `IllegalArgumentException`. The KDoc, README.md:552 and `calls.md:81-83` say it
validates exactly as the client does. As things stand, it can't simulate a garbled response.

**Fix.** Move `parseObject` into an internal helper shared by `JevClient` and `jevResult`. Add ConsumerTest cases
for `"not json"` and `"[]"`.

<a id="issue-49"></a>

#### 49. `RetryPolicy`'s docs say the defaults match both official SDKs

**Low** · Docs · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:25`, `README.md:416`,
`website/jev4k/docs/client/errors.md:9`

**Fixed.** The KDoc, the README and the errors page say the defaults match the JS SDK, and that Python shares the
retries, backoff, jitter and statuses but doesn't cap hints and adds a 30 s budget per call, which jev4k lacks.

**What's wrong.** The defaults match the JS SDK only. Python adds a 30 s total budget per call and doesn't cap
retry hints. jev4k has no total budget, since its timeout applies per attempt, so a call can take about 150 s with
maximal hints.

**Fix.** Say the defaults match the JS SDK, and share retries, backoff, jitter and statuses with Python, which also
enforces a 30 s total budget. A total budget can be added later if it's wanted.

#### Tests

<a id="issue-50"></a>

#### 50. The "no tests discovered" guard is off for every test task

**Low** · Tests · small · `build.gradle.kts:461`

**Fixed.** A `gradle.taskGraph.whenReady` listener, registered after Kotest's, switches `failOnNoDiscoveredTests`
back on for every test task. A macOS test binary stripped of its specs now fails with "did not discover any tests".
The previous build script let the same binary pass.

**What's wrong.** Gradle 9's `failOnNoDiscoveredTests` would catch a test task that found no tests. The Kotest
plugin turns it off for every test task once the task graph is ready, which makes the build's own override
redundant. A Kotest, KSP or Kotlin upgrade that left a target's binary without specs would keep CI green while that
platform ran nothing.

**Fix.** Either option works:
- Re-enable the guard after Kotest, in a later `gradle.taskGraph.whenReady`, for the tasks expected to have specs.
  Verify each task once.
- Or add an independent check that each task's results report at least one test.

<a id="issue-51"></a>

#### 51. No test checks that `platformGetenv` returns a value

**Low** · Tests · small · `src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt:89`

**Fixed.** `PlatformEnvTest` asserts that `platformGetenv` returns `PATH`, which every test process has (checked on the
iOS and tvOS simulators, macOS, Node.js, Docker Linux, and the JVM; Windows looks it up whatever its case), and null for
an unset name. A variable set for the purpose on every test task was tried first, but needed each task type, a
`SIMCTL_CHILD_` special case and a Docker flag to agree.

**What's wrong.** Every test stubs `env`. On js, wasmJs and native, the only real call is `liveOptIn()`, and in
ordinary runs that returns null whether or not the lookup works.

**Impact.** A broken lookup would make `JevClient()` ignore `TYPESAFE_*` on that platform while CI stays green.
`make live-tests` would then show the probes as skipped rather than failed.

**Fix.**
- Set a known variable on every test task: `Test.environment`, `KotlinJsTest.environment`,
  `KotlinNativeTest.environment`, and the `SIMCTL_CHILD_` prefix on simulators.
- Assert that `platformGetenv` returns it, and null for an unset name.

<a id="issue-52"></a>

#### 52. `mapModels`'s rejection branches are untested

**Low** · Tests · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ResponseMapper.kt:67`

**Fixed.** `ResponseMappingTest` covers every rejection branch at its field path, table-driven, plus the optional fields
and an empty list.

**What's wrong.** Only the happy path is tested. The four rejection branches, each with its own field path, and the
optional fields aren't. `/v1/systemone` has that coverage.

**Fix.** Add a table-driven common test over the field paths, plus two success cases:
- `{"models":[{"name":"a"}]}` gives `ModelInfo("a", null, null)`;
- `{"models":[]}` gives an empty list.

<a id="issue-53"></a>

#### 53. The default `retryDelay` never runs in any test

**Low** · Tests · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:97`

**Fixed.** A ClientTest case keeps the real `retryDelay`. It asserts that the client waited at least the backoff,
and that cancelling a call during a 10 s `retry-after-ms` wait ends it within seconds.

**What's wrong.** Every test that retries replaces `retryDelay`, so the real `delay(...)` never runs. The suite
would still pass if it became a no-op, or if it stopped being cancellable.

**Fix.** Add two MockEngine tests on the default delay:
- one asserting a lower bound on the elapsed time only, so it can't be flaky;
- one cancelling the call during a long `retry-after-ms` wait.

<a id="issue-54"></a>

#### 54. No test checks that a supplied engine keeps its own timeouts

**Low** · Tests · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:55`

**Fixed.** `ClientTest` reads `HttpTimeoutCapability` off recorded MockEngine requests: only the request timeout is set,
the client's or the call's. `limitTo` is tested directly for jev4k's own engine.

**What's wrong.** jev4k sets the connect and socket timeouts only for its own engine, so a supplied engine keeps
its tuning, as documented. No test checks either branch.

**Fix.**
- Read `HttpTimeoutCapability` off a recorded MockEngine request: the request timeout should be set, and the
  connect and socket timeouts null.
- Test the default-engine branch through a small internal function.

<a id="issue-55"></a>

#### 55. No test pins the false side of the exact-class `IllegalStateException` rule

**Low** · Tests · small · `src/nativeMain/kotlin/com/pambrose/jev4k/internal/Platform.native.kt:16`

**Fixed.** RetryTest asserts, on every platform, that `ClientEngineClosedException`, `SendCountExceedException` and
a `CancellationException` are neither connection errors nor retried.

**What's wrong.** Matching the exact class keeps Ktor's own `IllegalStateException` subclasses from counting as
connection errors, for example `ClientEngineClosedException` and `SendCountExceedException`. Only the true side is
tested. Widening the check to `is IllegalStateException` would pass every test, and Linux and Windows would then
retry and mislabel those exceptions.

**Fix.** Add common assertions that `retriesOn` and `isConnectionError` are false for those subclasses and for
`CancellationException`.

<a id="issue-56"></a>

#### 56. The real-CIO timeout test's 1 s budget includes CIO's cold start

**Low** · Tests · small · `src/jvmTest/kotlin/com/pambrose/jev4k/ClientJvmTest.kt:47`

**Fixed.** The test warms CIO up against a `RawServer` first, so a cold start can't eat the first attempt's second.

**What's wrong.** The first attempt pays for CIO's cold start, and the server-side request count is the only
assertion that catches a wrong plugin order. So a loaded runner can fail the test intermittently with "server saw
2 requests".

**Fix.** Either option works:
- Warm up with a throwaway call first, and count requests relative to a baseline.
- Or raise the timeout to 2 to 3 s.

<a id="issue-57"></a>

#### 57. `make live-tests` passes without any real API call when the key is missing

**Low** · Tests · small · `Makefile:235`

**Fixed.** `LiveSmokeTest` is gated on `JEV4K_LIVE=1` alone, so a live run without a key fails, each test naming the
missing key, instead of skipping. That covers a direct `JEV4K_LIVE=1 ./gradlew jvmTest` as well as `make live-tests`,
with no Makefile guard to keep in step with how `.env` is parsed.

**What's wrong.** `live-tests` never checks for `TYPESAFE_API_KEY`. Without it, LiveSmokeTest skips every test and
LiveProbeTest, which uses a fake key, passes. The build succeeds without a single real call.

**Fix.** Fail fast in the recipe, using the same key check `all-tests` already has.

#### Build and CI

<a id="issue-58"></a>

#### 58. Branch protection requires only `build`

**Low** · CI · small · `.github/workflows/ci.yml:122`

**Fixed.** `ci.yml` has a `ci-ok` job that fails unless `build`, `test` and `native` all succeeded. Branch
protection on `master` now requires `ci-ok`, `docs` and GitGuardian.

**What's wrong.** Only `build` and the GitGuardian check are required on master (checked through the API). The JDK
17/21/25 matrix and both native jobs report their results but don't gate. A PR can merge green while it breaks:
- Apple compilation;
- the macOS, iOS or Windows tests;
- JDK 17.

`8c89c87` also renamed the native check names.

**Fix.** Add an aggregate `ci-ok` job (`needs: [build, test, native]`, `if: always()`) that fails unless every job
succeeded, and make it the required check.

<a id="issue-59"></a>

#### 59. CI's Apple row omits the tvOS and watchOS simulator tests

**Low** · CI · small · `.github/workflows/ci.yml:133`

**Fixed.** The Apple row also runs `tvosSimulatorArm64Test` and `watchosSimulatorArm64Test`, which are skipped where
the runner has no device.

**What's wrong.** The build now runs the tvOS and watchOS simulator tests wherever a device exists, and
`make tests` includes them. The CI Apple row still lists only macOS and iOS, so the published tvOS and watchOS
artifacts are never tested in CI.

**Fix.** Add both tasks to the Apple row; they report SKIPPED on a runner without a device. Alternatively, document
the omission.

<a id="issue-60"></a>

#### 60. linuxArm64 is published, but no CI job runs its tests

**Low** · CI · medium · `.github/workflows/ci.yml:19`, `docs/release-checklist.md:59`

**Fixed.** The CI `build` job sets up QEMU (`docker/setup-qemu-action`, pinned) and runs `make docker-linux-tests
LINUX_TEST_TARGETS=linuxArm64:arm64`, so linuxArm64's tests run on every push and PR without repeating linuxX64's. The
release checklist gives it a checkbox of its own.

**What's wrong.** linuxArm64 has no Gradle test task. Only the local `make docker-linux-tests` runs it, and the
release checklist mentions that in passing inside another checkbox.

**Fix.**
- Give it its own checklist checkbox.
- In CI, link on ubuntu-latest and run the binary under QEMU: `docker/setup-qemu-action` plus
  `make docker-linux-tests`, or upload the binary and run it on an arm64 runner.

<a id="issue-61"></a>

#### 61. `make build` never compiles `jvmTest`

**Low** · Build · small · `Makefile:68`

**Fixed.** `make build` also runs `jvmTestClasses`.

**What's wrong.** `assemble` doesn't compile the JVM test sources, so `make build` skips the website examples and
the `JavaInterop.java` guard. An API change can pass `make abi-update` and `make build` while breaking both. The
docs say `make build` compiles every target.

**Fix.** Add `jvmTestClasses` to the recipe, or reword the docs.

<a id="issue-62"></a>

#### 62. `check` links test binaries the host can never run

**Low** · Build · small · `build.gradle.kts:219`

**Fixed.** `build.gradle.kts` disables the test KSP, compile and link tasks of iosX64 unless the host is an Intel Mac,
of mingwX64 unless it is Windows, and of the tvOS and watchOS simulators when there's no device for them, in one block.
The Linux ones stay for `docker-linux-tests`.

**What's wrong.** Disabling a test task doesn't stop its link task. So `check` links debug test binaries (and
compiles their test klibs) that never run:
- iosX64 on Apple Silicon;
- mingwX64 on macOS and Linux.

**Fix.** Disable those link tasks, and optionally their compile and KSP tasks, for targets the host can't run. Leave
the two Linux links, which `make docker-linux-tests` needs.

<a id="issue-63"></a>

#### 63. javac builds the Java example without `--release 17`

**Low** · Build · small · `build.gradle.kts:241`

**Fixed.** `tasks.withType<JavaCompile>()` sets `options.release` from the catalog's `jvm-target`.

**What's wrong.** Kotlin gets `-Xjdk-release=17`, but javac only gets source and target 17. So `JavaInterop.java`
compiles against JDK 25's library, and a JDK 21+ API would slip through.

**Fix.** Add `tasks.withType<JavaCompile>().configureEach { options.release = 17 }`.

<a id="issue-64"></a>

#### 64. The JDK 21 gate on `-XX:+EnableDynamicAgentLoading` rests on a false premise

**Low** · Build · small · `build.gradle.kts:448`, `CLAUDE.md`

**Fixed.** The flag is passed on every JDK, and both comments now give the right reason: 21 added the warning, not the
option. JDK 11 and 17 were checked again.

**What's wrong.** The flag is passed only from JDK 21 on, "because the option arrived with JDK 21". But JDK 11 and
17 accept it too (checked); only the warning is new in 21. CLAUDE.md repeats the false reason.

**Fix.** Pass the flag unconditionally and correct both comments. Or keep the gate with the right reason.

<a id="issue-65"></a>

#### 65. A CI comment says coverage is uploaded when tests fail

**Low** · CI · small · `.github/workflows/ci.yml:48`

**Fixed.** The comment now says a JVM test failure leaves no coverage report, since Kover's reports depend on `jvmTest`.

**What's wrong.** The comment says `--continue` still uploads coverage when tests fail. Kover's report tasks depend
on `jvmTest`, so a JVM test failure skips every report.

**Fix.** Reword the comment.

<a id="issue-66"></a>

#### 66. A CI comment says every master commit is built

**Low** · CI · small · `.github/workflows/ci.yml:10`

**Fixed.** The comment now says a burst of master pushes can skip a queued middle commit, and that the latest is always
built.

**What's wrong.** GitHub keeps one pending run per concurrency group. A burst of pushes therefore cancels the queued
run for the middle commit.

**Fix.** Reword the comment, or key the group on the commit SHA for pushes.

<a id="issue-67"></a>

#### 67. The Kover comment says `build -x allTests` skips the tests

**Low** · Build · small · `build.gradle.kts:422`

**Fixed.** The comment now says "a build that skips the tests, such as `build -x jvmTest`".

**What's wrong.** `check` depends on each test task directly, so the tests still run. The Makefile gets this right.

**Fix.** Use `build -x jvmTest` in the comment, or generic wording.

<a id="issue-68"></a>

#### 68. `dependabot.yml` leaves out `website/uv.lock` without saying why

**Low** · CI · small · `.github/dependabot.yml:4`

**Fixed.** The header now says `website/uv.lock` is left out on purpose, managed with `make check-site` and `make
upgrade-site`.

**What's wrong.** The header explains why Gradle is left out, but not the docs site's lockfile. That file is
managed with `make check-site` and `make upgrade-site`.

**Fix.** Extend the comment, or add a `uv` entry for `/website`.

#### Release and documentation

<a id="issue-69"></a>

#### 69. The release checklist's version step names only README.md

**Low** · Release · small · `docs/release-checklist.md:75`

**Fixed.** Steps 3 and 7 name `GettingStarted.txt` and its snippets.

**What's wrong.** The Installation page takes its coordinates from `GettingStarted.txt`. Four of its snippets carry
the released version, and the composite-build snippet tracks `gradle.properties`. The checklist doesn't mention the
file, and nothing checks it.

**Fix.** Name `GettingStarted.txt` and its snippets in steps 3 and 7. A grep-based guard is optional.

<a id="issue-70"></a>

#### 70. The release checklist's intro and its Dokka line reference are stale

**Low** · Release · small · `docs/release-checklist.md:4`, `:7`

**Fixed.** The intro describes the one-time section as a record, and the Dokka reference names the call rather than
a line.

**What's wrong.**
- The intro still says nothing has reached Central, while the table below it records 0.1.0 as published.
- The Dokka reference cites `build.gradle.kts:246`. The call is now at line 297, and the reference has already gone
  stale twice.

**Fix.** Describe the one-time section as history, and refer to the Dokka call by its content, not its line number.

<a id="issue-71"></a>

#### 71. The release notes say JVM error handling "works unchanged elsewhere"

**Low** · Docs · small · `RELEASE_NOTES.md:67`, `CHANGELOG.md:22`

**Fixed.** The release notes and CHANGELOG say that on Linux and Windows any bare `IllegalStateException` during a
call is retried and wrapped, including one from a supplied engine or a `MockEngine` handler. The README and the
errors page carry the same note under their errors tables.

**What's wrong.** On Linux and Windows, any bare `IllegalStateException` during a call is retried and then wrapped as
`JevConnectionException`. That includes one from a supplied engine, from a MockEngine handler, or from `error()`.
The JVM rethrows the same exception, so shared tests built on MockEngine behave differently on those targets.

**Fix.**
- Qualify the release notes and CHANGELOG.
- Add a platform note next to `JevConnectionException` in the README and on the errors page.
- Gating the rule on the default engine is possible, at a cost; see step 3 of the plan.

<a id="issue-72"></a>

#### 72. The docs say jev4k writes nothing to stderr

**Low** · Docs · small · `README.md:453`, `website/jev4k/docs/getting-started/installation.md:117`

**Fixed.** Both pages now say jev4k never logs but Ktor uses SLF4J, which prints a three-line warning to stderr without
a binding (reproduced by building a client on the bare runtime classpath), and suggest `slf4j-nop`.

**What's wrong.** jev4k itself never logs. But Ktor's plugins initialize SLF4J on the JVM, and with no provider
SLF4J prints a three-line warning to stderr when the first `JevClient` is built. `make example` shows it.

**Fix.** Reword: jev4k never logs, but Ktor initializes SLF4J. Add a binding (for example `slf4j-nop`) to silence
the warning.

<a id="issue-73"></a>

#### 73. The development page says `.env` reaches every test task

**Low** · Docs · small · `website/jev4k/docs/guides/development.md:44`, `.env.example:8`

**Fixed.** The development page and `.env.example` say `.env` reaches the JVM test and example tasks only.

**What's wrong.** The envvar plugin configures only `Test` and `JavaExec` tasks, which are JVM-only. The js, wasmJs
and native test tasks see only the shell's environment.

**Fix.** Reword both files.

<a id="issue-74"></a>

#### 74. "Node.js only" is true of the tests, not of the artifacts

**Low** · Docs · small · `build.gradle.kts:151`, `README.md:108`,
`website/jev4k/docs/getting-started/installation.md:39`

**Fixed.** The README, the Installation page, the release notes and the build comment say the js and wasmJs artifacts
are built and tested for Node.js, would still load in a browser, and that a browser app should call a backend. No
browser check was added.

**What's wrong.** `nodejs()` shapes only jev4k's own test and run tasks. The published js and wasmJs artifacts carry
no Node-only attribute, so they load in a browser, and nothing refuses a browser the way the JS SDK does.

**Fix.**
- Say that jev4k is built and tested for Node only, that the artifacts still load in a browser, and that a
  browser app must go through a backend.
- Optionally, detect a browser and refuse.

#### Website examples

<a id="issue-75"></a>

#### 75. The model-router example caps uncertain prompts at LARGE

**Low** · Examples · small · `src/jvmTest/kotlin/website/RoutingExamples.kt:92`

**Fixed.** `pickModel` works out the tier first and then raises an uncertain estimate to `maxOf(tier, LARGE)`, so it
never caps a REASONING prompt. `routing.md` says so.

**What's wrong.** The `confidence < 0.5 -> LARGE` branch comes before the REASONING branch. So an uncertain prompt
that needs tools, or scores as hard, is capped at LARGE. The code comment and `routing.md:46` say uncertainty errs
toward the more capable model.

**Fix.** Reorder the branches, or compute the base tier and then use `maxOf(base, LARGE)` when the estimate is
uncertain.

<a id="issue-76"></a>

#### 76. Two concurrency examples fan out without a bound

**Low** · Examples · small · `src/jvmTest/kotlin/website/TypedQueryExamples.kt:109`,
`src/jvmTest/kotlin/website/CompositeScoringExamples.kt:66`

**Fixed.** `triageAll` bounds its fan-out with a `Semaphore`, and so does the resume assessment (#77).

**What's wrong.** Both examples launch one `async` per input, with no limit. That contradicts `calls.md`'s guidance
that about 8 concurrent requests already hit rate limits. In the typed-queries example it also contradicts the
sentence right after the example.

**Fix.** Use the `Semaphore`/`withPermit` pattern from `ClientExamples.kt`, or `triageBatch`.

<a id="issue-77"></a>

#### 77. The "Rank without new requests" example re-queries Jev

**Low** · Examples · small · `src/jvmTest/kotlin/website/CompositeScoringExamples.kt:67`

**Fixed.** `assessResumes` makes one request per resume, with bounded concurrency, and the pure `rankCandidates` ranks
the stored results; `shortlistBothRoles` ranks one set of results for both roles. The page describes it.

**What's wrong.** `shortlist` calls `jev.ask` for every resume each time it runs, and then throws the results away.
Ranking the same pool under a second weighting re-sends everything.

**Fix.** Split it in two:
- `assess`, which makes one request per resume, with bounded concurrency;
- a pure `rank(results, weights)`.

Then show both weightings applied to the same stored results.

<a id="issue-78"></a>

#### 78. The extraction examples leave out the `none` escape

**Low** · Examples · small · `src/jvmTest/kotlin/website/ExtractionExamples.kt:53`

**Fixed.** `DueDateQuery.month` is a string Choice of month names plus `none`, since an enum can't say none, and the
invoice total offers `none` too, which makes `overBudget` return null. The extraction page explains both.

**What's wrong.** The page says to always include a `none` option, but two examples don't:
- `DueDateQuery.month` uses `choice<Month>`, which can't say none;
- the invoice `total` Choice has no none option either.

So an absent value comes back as a confident but wrong month or amount.

**Fix.** Add a none escape to both, and treat a none answer as incomplete.

<a id="issue-79"></a>

#### 79. The guardrail example blocks an uncertain self-harm signal

**Low** · Examples · small · `src/jvmTest/kotlin/website/GuardrailExamples.kt:69`

**Fixed.** The code change: a review raised by the self-harm signal is kept out of severity escalation, and the page
says so, noting that TypeSafe's cookbook escalates it.

**What's wrong.** Any REVIEW with high severity becomes BLOCK, including one that came from an uncertain self-harm
signal. The page says self-harm routes to support. The behavior is inherited from TypeSafe's cookbook.

**Fix.** Either option works:
- Correct the prose, keeping parity with the cookbook.
- Or keep reviews that came from the support hazard out of severity escalation, and say so on the page.

<a id="issue-80"></a>

#### 80. The guardrail example can't switch policies without re-asking

**Low** · Examples · small · `src/jvmTest/kotlin/website/GuardrailExamples.kt:52`

**Fixed.** `decide(result, policy)` is pure code over one assessment, and `screenUnderBothPolicies` applies `strict` and
`permissive` to the same result, so `permissive` is used.

**What's wrong.** `screen` asks Jev and applies the policy in the same function, so switching policies means asking
again. `permissive` is declared but never used. The page says policies switch "without re-asking anything".

**Fix.** Split it into `assess` and a pure `decide(result, policy)`, and apply both policies to one result.

<a id="issue-81"></a>

#### 81. The verification example has no explicit criteria

**Low** · Examples · small · `src/jvmTest/kotlin/website/VerificationExamples.kt:73`

**Fixed.** Each check carries the cookbook's `whenTrue`/`whenFalse` criteria, an empty field gets only the
`absence_wrong` check, and an empty map escalates instead of throwing. The page adds the empty-field rule.

**What's wrong.** The page requires explicit criteria on every check, but the example's checks have none. Its
content checks also run on empty fields, which invites false TRUE answers, and an empty map throws.

**Fix.**
- Add `whenTrue`/`whenFalse` criteria from the cookbook.
- Ask only an absence check of empty fields.
- Handle an empty map.

### Nit

<a id="issue-82"></a>

#### 82. `JavaInterop.java`'s comment overstates what it pins

**Nit** · Docs · small · `src/jvmTest/java/website/JavaInterop.java:10`

**Fixed.** The comment lists what the file pins (the overloads it calls, `BlockingJev`'s `@Throws`, the millisecond
members) and says the ABI dump guards the rest; CLAUDE.md matches.

The comment says the file stops compiling if an `@JvmOverloads` or `@JvmSynthetic` annotation is lost. Only
`QueryBuilder.noul`'s `@JvmOverloads` is actually pinned; the ABI dump guards the rest, as CLAUDE.md says. Reword
the comment to match, or add calls that pin the other overloads.

<a id="issue-83"></a>

#### 83. `BlockingJevTest` matches the `QuestionSet` with `any()`

**Nit** · Tests · small · `src/jvmTest/kotlin/com/pambrose/jev4k/BlockingJevTest.kt:68`

**Fixed.** The query test matches the forwarded set with `match { it.ids == Triage.questions.ids }`.

The test never checks that the inline block's questions are forwarded. Use
`match { it.ids == Triage.questions.ids }`, or rename the test to what it checks.

<a id="issue-84"></a>

#### 84. CLAUDE.md says every type name follows the JS SDK

**Nit** · Docs · small · `CLAUDE.md:24`

**Fixed.** CLAUDE.md says the question types follow the JS SDK, the answer types and views the Python SDK, and
`ModelInfo`/`ModelList` are jev4k's own.

Only the question types follow the JS SDK. The answer types and the `nouls`/`choices`/`scores` views follow Python,
and `ModelInfo` is jev4k's own name. Say so.

<a id="issue-85"></a>

#### 85. README testing section formatting

**Nit** · Docs · small · `README.md:568`, `README.md:550`

**Fixed.** Both fixed: the space in "as `JevClient { … }`", and the paragraph above the builders' one ends with a
period.

- **README.md:568.** Add the missing space: "as `JevClient { … }`".
- **README.md:550.** End it with a period, so only the paragraph directly above the code block ends with a colon.

## Method

- **Scope.** The whole repository at `8c89c87`: the library, tests, build, Makefile, CI, README, docs site,
  CHANGELOG, release notes and `docs/`.
- **Review.** Eight reviewers each took one dimension:
  - conformance to the TypeSafe API (`jev-docs/`);
  - core logic;
  - concurrency and platforms;
  - public API and Java interop;
  - tests;
  - build and CI;
  - documentation accuracy;
  - security and robustness.
- **Gap sweeps.** Two further rounds used fresh lenses (a new maintainer's read, cross-artifact consistency,
  adversarial inputs), and each round was told what had already been found.
- **Verification.** Each finding went to a separate verifier, told to refute it from the source and to correct
  line numbers, severity and scope. High and critical findings had three verifiers and needed a majority.
  - The run used 123 agents.
  - Of 101 findings, 100 survived; 27 of those were corrected, several downgraded.
  - 15 duplicates across reviewers were merged, leaving the 85 above.
  - I also checked #1 (with `javap`), #58 (through the GitHub API) and #15 myself.
- **Rejected.** One finding claimed that the public data classes lock in their `copy`/`componentN` signatures, so
  adding a field later would break binary compatibility. A verifier compiled two versions of a data class and showed
  that callers compiled against the old one still link and run against the new one, so it isn't listed.
- **What held up.**
  - The wire format, validation limits and error-status mapping match the spec and both SDKs.
  - Retry statuses, backoff and hint handling match the JS SDK.
  - The `HttpRequestRetry`/`HttpTimeout` plugin order is correct.
  - The API key is redacted from `toString` and from jev4k's own messages.
  - Every engine verifies TLS.
  - The JVM ABI is compatible with 0.1.0.
  - Every `--8<--` include and site link resolves, and `llms.txt` lists every page.
