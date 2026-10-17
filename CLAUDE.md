# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`jev4k` is a Kotlin Multiplatform DSL and client for TypeSafe's Jev "System One" model, built on the Ktor client and
kotlinx.serialization. You describe a state and typed questions (Noul, Choice, Score, and the experimental Box), and it
returns typed answers. The library is `com.pambrose.jev4k`, almost all of it common code under `src/commonMain/kotlin`,
for the JVM (the primary target), Apple platforms, Linux, Windows, and Node.js (js and wasmJs). The runnable examples
are `src/jvmTest/kotlin/com/pambrose/jev4k/examples/TriageExample.kt` (`make example`) and `HotDogExample.kt` next to
it (`make hotdog-example`), which sends the two CC0 photos in `src/jvmTest/resources/hotdog/`; `HotDogPhotosTest`
holds them to the API's image limits.

## TypeSafe / Jev API docs

This project works with TypeSafe's Jev model (`POST https://api.typesafe.ai/v1/systemone`). A cached, compressed copy of
its docs lives in `jev-docs/`. Read `jev-docs/jev-api.md` before writing or changing any code that calls the API. Use
`jev-docs/sdks.md` for client behavior (retries, timeouts, errors, typing) and `jev-docs/cookbooks.md` for proven
question designs. The raw pages under `jev-docs/pages/` aren't in git, because they're TypeSafe's copyrighted docs. Run
`make api-docs` (which runs `jev-docs/refresh.sh`) to download them after cloning. The notes are committed. Don't use
the legacy preview API shape (`/preview/evaluation`, `document`, `prompts`); `jev-api.md` §9 lists every renamed field.

## Architecture

The question types' names follow TypeSafe's JS SDK; the answer types and the `nouls`/`choices`/`scores` views follow
the Python SDK, and `ModelInfo`/`ModelList` are jev4k's own. A question definition is a `Question`, one of
`NoulQuestion`, `ChoiceQuestion`, `ScoreQuestion` or the experimental `BoxQuestion`, which hold only what is asked:
instructions and criteria. A `QuestionRef<A>` is the handle code holds. It pairs an id with a `Question`
(`ref.question`) and a decoder for the typed answer.

There are two DSL layers over one core model. Both produce a validated `QuestionSet`, and
`JevApi.evaluate(state, questionSet, model)` is the only call that sends questions to the network. `JevApi.models()`
(`GET v1/models`, returning a `ModelList`: a `List<ModelInfo>` carrying the request id) is the other network call.
Each has an overload taking `JevCallOptions`, with a default implementation that ignores the options, so fakes written
without them keep compiling.

- **Inline layer** (`Builders.kt`). `jev.query(state) { noul("id", "...") ... }` uses string ids. `QueryBuilder`
  functions return `QuestionRef<A>` handles, and `include(query)` merges a `JevQuery` into the same request.
- **Typed layer** (`JevQuery.kt`). `object X : JevQuery() { val urgent by noul("...") }`. A `PropertyDelegateProvider`
  takes the id from the property name (or `id =`) and registers questions in declaration order. `JevQuery.questions` is
  built on first read, so an invalid definition fails on first use, and rebuilt if more questions have registered since
  (a read from an `init` block or a base class would otherwise freeze a partial set). A read before any question has
  registered, such as from the `init` of a base class with no questions of its own, fails with `JevValidationException`,
  since a set needs one. Definition errors are carried on the `QuestionRef` rather than thrown, so they can't escape an
  `object`'s initializer: duplicate options, and a `JevValidationException` thrown while a question is built (`entry()`
  or `jsonOf()` in a builder lambda, or an enum option's `JevOption.entry`), which `QuestionProvider.provideDelegate`
  catches, registering a `failedQuestionRef` stand-in whose question isn't checked. An inline `questions {}` builder
  throws at once, since it runs in the caller's own code. An argument such as `noul(entry(...))` is evaluated before the
  builder runs, so that one still throws from the initializer. `choice<E>()` builds options from an enum. The option key
  is the constant's name unless `JevOption.optionKey` overrides it, and `JevOption.entry` becomes the description.
- **Answers** (`JevResult.kt`, `Answers.kt`). `result[handle]` decodes through the handle's `decode` function, and also
  checks that the handle belongs to the request. By-id accessors (`noul`/`choice`/`score`/`enumChoice`) check that the
  question type matches. Raw answers are stored string-keyed. Enum choices are mapped when read, and an unknown option
  becomes `JevResponseValidationException`; `enumChoice<E>(id)` first requires E to cover every declared option, since a
  mismatch is the caller's mistake (`IllegalArgumentException`), then decodes against the declared options only, so a
  broader enum's extra constant can't stand for an option the question never offered. A string-keyed read returns an
  undeclared option as sent, as both official SDKs do. `QueryBuilder.question(id, q)` copies the question's map or list
  and picks the type-checking decoder for its subtype.
- **Images and Box** (`JevImage.kt`, `internal/ImageRequest.kt`), all behind `@ExperimentalJevImages`, which the build
  opts the module into (`compilerOptions.optIn`), so every public declaration that names an image or Box type must
  carry the annotation by hand.
    - A `JevImage` stands in content as its `reference` (`toString()`, `jsonOf`, `JevImageSerializer`, the builders'
      image overloads); `JsonPrimitive` is sealed, so images can't be discovered in a JSON tree and must be attached:
      `QueryBuilder.attach`, `JevQuery.attach`, or `ask(query, state, images...)`. `QuestionSet.images` holds each
      image once (`distinctImages`): a second instance with the same reference and bytes is dropped.
    - The factories never throw for bad content: the problem rides on the image and `imageDefinitionProblems` reports it
      when the set is validated, keeping definition errors out of `JevQuery` initializers. Only a file read throws.
      Every factory flags an image over the API's 16 MiB (`MAX_IMAGE_BYTES`), and each `read` overload skips a regular
      file over it (`tooLarge`) and reads anything else through `readCapped`, so a device or a pipe can't fill memory. A
      reference inside another attached one is refused there too, since the API's plain-text matching can't tell them
      apart.
    - A generated reference's first UUID group is a per-process tag (`PROCESS_TAG`), so `GENERATED_REFERENCE` matches
      only this process's references: text from elsewhere that looks like one is sent as text, not refused.
    - `evaluate` runs `imageRequestProblems` (references matched as the API does, as plain text in string values, never
      keys; a hand-built `extraBody["assets"]`'s keys count as attached), adds `TypeSafe-Danger-Zone-Images: true` as a
      built-in header (for a hand-built `extraBody["assets"]` too), and sends a request with images as
      `MultiPartFormDataContent`: a `request` part with the JSON and a generated `assets` map, then `image-0`, … with a
      filename (Ktor quotes each part name). Without images the request is the same `TextContent` as before.
    - `BoxQuestion`/`BoxAnswer` are the fourth question and answer types. The mapper requires four finite edges;
      `decodeBox` checks them in 0..1 with `left < right` and `top < bottom` (and the confidence in 0..1) when the Box
      is read, through `box(id)`, a handle or `boxes`, so one bad Box doesn't take the paired Noul down with it.
      `AnswerDecodingException.field` gives the field path below `answers.<id>`.
- **Wire** (`internal/Wire.kt`).
    - Requests are `@Serializable` DTOs, and a sealed `WireQuestion` writes the `"type"` discriminator.
    - `JevJson` sets `encodeDefaults = false`, so unset optional fields (Noul criteria) are omitted. Explicit JSON
      `null`s, such as an undescribed Choice option, are still sent.
    - Caller-supplied `@Serializable` state and entries are encoded with `ValueJson` (`encodeDefaults = true`), so
      default-valued fields reach the model.
- **Response mapping** (`internal/ResponseMapper.kt`).
    - Responses are parsed by hand from `JsonObject`, so every error has a field path such as `answers.<id>.noul`.
    - An answer with no `type` is read as the type of question that was asked; an unknown type becomes `UnknownAnswer`.
    - Choice probabilities are reordered to the order the options were declared. Score keys `"0".."n"` become `Int`.
    - Absent answers fail when they are read, not when the response is parsed, and a `null` answer counts as absent.
    - Numbers must be finite (an unquoted `NaN` and `1e999` both parse), and a Score level key must be written as its
      number (`1`, not `01` or `+1`) and lie within the question's levels, or be non-negative for an answer nobody
      asked for, whose `levelCount` is its highest key plus one.
    - Every `JevResponseValidationException` from a 2xx body is thrown by `ResponseInfo.fail`, which carries the
      body's text as received, the status and the headers. `send` builds the `ResponseInfo`, and `JevResult` keeps
      it for failures at read time.
    - A value jev4k sends (the state, each question entry, each `extraBody` field) may nest `MAX_SEND_DEPTH` (64,
      `internal/JsonDepth.kt`) levels. A deeper state or entry fails with `JevValidationException`, and a deeper
      `extraBody` field fails `JevCallOptionsBuilder.build()` with `JevConfigException`. That is the API's request rule:
      it checks the state and each instructions or criteria value on its own, counting the outermost object or array as
      level 1 (a criteria map isn't a level), and answers a deeper one with a 400. A probe on 2026-10-15 pinned it.
    - JSON received may nest `MAX_JSON_DEPTH` (128) levels, refused before kotlinx.serialization recurses into it:
      a response body (scanned as text before parsing) with `JevResponseValidationException`, and an error body's
      `bodyJson` is null. Both checks are iterative, so they can't overflow themselves. Windows sets this limit: its
      1 MB main-thread stack crashed the mingwX64 test binary encoding 512 levels, so the limit is 128.
- **Client** (`JevClient.kt`, `internal/HttpClientFactory.kt`, `internal/Retry.kt`).
    - `HttpRequestRetry` reproduces the JS SDK's retry rules. `RetryPolicy` sets them: 408/429/5xx, connection
      errors, timeouts, 0.5 s doubling to 5 s with 25% jitter, and `retry-after-ms`/`Retry-After` hints up to 60 s.
      A `Retry-After` HTTP-date, which the Python SDK also reads, counts from the config's internal `now` clock.
    - `followRedirects = false`: a 3xx becomes a `JevApiException`, since Ktor strips only `Authorization` on a
      cross-host redirect and fetch would re-send the POST body.
    - No `ContentNegotiation` plugin: `evaluate` encodes the request with `JevJson` into a `TextContent` (merging
      `extraBody`), inside the request block so an encoding failure still becomes `JevValidationException`, and
      responses are read raw. So the only `Accept` is `setHeaders`' (or the caller's), and `jev4k-jvm` needs only
      CIO at runtime. `ClientTest` pins the request bytes the plugin used to produce.
    - `HttpClientFactory.limitBodySize` inserts a receive-pipeline phase ahead of `Before`, where Ktor's `SaveBody`
      reads the whole body into memory, and refuses a declared `Content-Length` over `MAX_RESPONSE_BYTES` (16 MiB) with
      an internal `OversizedResponseException`, which `execute` maps by status (a 2xx without the internal one as its
      cause, which holds Ktor types that can't be serialized). A body without a declared length isn't capped and the
      timeout bounds it; that includes a compressed one on Curl, Darwin and the Js engines, which decompress it and drop
      `Content-Length` first. Counting bytes would need Ktor's `@InternalAPI` `rawContent`, so the gap is documented on
      the README, Configuration and Errors pages instead. An `HttpSend` interceptor would be too late: the receive
      pipeline runs inside the send.
    - `HttpRequestRetry` must be installed **before** `HttpTimeout`, otherwise one timeout cancels every retry.
    - `expectSuccess = false`: non-2xx responses map to `JevApiException` subclasses (`apiException` in `Errors.kt`)
      after retries run out, keeping the raw body and the `x-typesafe-request-id` header. `JevApiException` lowercases
      header names in its constructor (CIO keeps the server's spelling, fetch lowercases) and holds only
      `Serializable` state: `bodyJson` is a plain getter, and `JevRateLimitException` stores its hint as nanoseconds.
    - `BlockingJev` (`jev.blocking`, or `api.blocking()` for any `JevApi`) wraps the suspend API in `runBlocking`, on
      the JVM only (see Platforms). Every method is `@Throws(InterruptedException::class)`.
    - Headers are set on each request (`JevClient.setHeaders`: built-in, then the client's, then the call's), not
      through `DefaultRequest`, which only sets the URL. `DefaultRequest` merges its headers with a request's own
      (KTOR-6946), so a header named in both would be sent twice.
    - `JevCallOptions` (`JevCallOptions.kt`) overrides a call's timeout and retry policy through Ktor's per-request
      `timeout {}` and `retry {}`, which `HttpClientFactory`'s `limitTo` and `follow` fill in exactly as the plugins are
      configured; a per-request retry config replaces the plugin's except for its `delay`. `extraBody` is merged into
      the encoded request inside the call, so an unencodable state still becomes `JevValidationException`, and can't set
      `state`, `model` or `questions`. `JevCallOptionsBuilder.build()` runs the same `headerProblem` checks as the
      config's (tokens, ASCII, `FRAMING_HEADERS`). `JevApi.withOptions` wraps an API so `query`/`ask` use the options.
    - `JevClient.execute` classifies a failed call in one place. `SerializationException` becomes
      `JevValidationException`. A `CancellationException` is caught next (first among the rest, because on
      Kotlin/Native it is also an `IllegalStateException`): a cancelled caller gets its own cancellation through
      `ensureActive()`, and one that isn't cancelled has lost a client closed as the call began, so it gets an
      `IllegalStateException`. Any other `Throwable` first goes through `ensureActive()` too, because Ktor unwraps a
      cancellation to its cause, and a caller cancelled when a sibling coroutine failed would otherwise get the
      sibling's exception as a Jev error. It then becomes `JevTimeoutException` or `JevConnectionException` if it is
      one, or is rethrown. `Throwable`, because the Js engine reports a failed fetch as a `kotlin.Error`.
      `isConnectionError` in `Retry.kt` uses the same predicate, so what is reported as a connection error is also
      what gets retried; `retriesOn` retries a cancellation only when it wraps a timeout, as Ktor's own rule does.
    - `send` checks the client isn't closed before anything else, and reads the body as raw bytes decoded with
      `decodeToString()`, not `bodyAsText()`: that parses the `Content-Type` (a malformed one throws) and, off the
      JVM, throws on bytes that aren't UTF-8.
    - A `JevTimeoutException` quotes `timeout` only when it is the limit that fired. A supplied engine gets only the
      request timeout, so its own connect or socket timeout is named instead.
- **Config** (`JevConfig.kt`). Each setting resolves as explicit value, then env var, then default. String settings
  are trimmed, and one that is blank after trimming counts as unset. The env vars are `TYPESAFE_API_KEY` (required),
  `TYPESAFE_BASE_URL` and `TYPESAFE_DEFAULT_MODEL`. Internal hooks (`env`, `retryDelay`, `random`, `now`) make tests
  deterministic; `env` defaults to `platformGetenv`.
    - `build()` checks what Ktor would otherwise reject on every request, with an exception that isn't a `JevException`
      and whose message quotes the value, and what engines would send differently. `baseUrl` is parsed once: http(s), a
      host, no userinfo, query or fragment, and plain `http://` only for a loopback host unless `allowInsecureHttp`. The
      API key must be visible ASCII (`'!'..'~'`). A header name must be an RFC 9110 token, its value ASCII without
      control characters but a tab, and `FRAMING_HEADERS` (`Content-Length`, `Content-Type`, `Transfer-Encoding`,
      `Upgrade`) are refused in any case: Ktor's `UnsafeHeadersList` holds only the last two, and a configured length or
      type contradicts the body's or is dropped. `setting()` drops a leading U+FEFF before trimming. A missing API key
      throws on its own, first; every other problem goes into one `JevConfigException`, whose messages never quote the
      key, a header value, or a URL that could hold credentials.
    - The retry policy is stored with a copy of its status set, since the caller's set may be mutable and the client
      re-reads it on every response. Every collection a built object holds (a config's and a call's headers, a call's
      `extraBody`, the status sets, a `QuestionSet`'s ids, questions and images, a Choice's options, a Score's levels, a
      result's answers) is a snapshot from `internal/ReadOnly.kt`, which copies through
      `buildMap`/`buildSet`/`buildList`. `toMap()` and friends give Java a `LinkedHashMap` or `ArrayList` it can change
      once there are two entries; the built ones reject every change, through their views and iterators too.
      `ReadOnlyJvmTest` casts each one as Java would. A blank per-call model falls back to the default, as a blank
      `defaultModel` does.
- **Platforms** (`internal/Platform.kt` and its actuals). Everything that differs between platforms is an `internal`
  expect: `platformGetenv`, `defaultEngine` (the engine factory; `JevConfig.toString` reports its class name),
  `isPlatformConnectionError`, and `Enum<*>.enumTypeName()` (which keeps `enumChoiceRef`'s `@PublishedApi` signature
  unchanged). Actual files carry a platform suffix (`Platform.jvm.kt`, `Engine.linux.kt`) so JVM facade names never
  clash. No engine gets a timeout of its own: `HttpTimeout` sets one on every request, and CIO, for one, ignores its
  `requestTimeout` whenever a request carries that capability.
    - On the JVM, `defaultEngine` is a getter in its own `Engine.jvm.kt`. As a stored value next to `platformGetenv` it
      would load CIO in the class initializer that every environment lookup runs, and a consumer who supplies an engine
      and excludes `ktor-client-cio` (as the README suggests) couldn't build a client. `CioExclusionTest` runs jev4k in
      a class loader that hides CIO and ktor-network-tls to pin this: a config reads the environment, and `CioFreeCalls`
      (a jvmTest helper, not a spec) builds a client on a `MockEngine`, makes a blocking call and maps a failed one.
    - Default engines: CIO on the JVM (`jvmMain`), Darwin (`appleMain`), Curl (`linuxMain`), WinHttp (`mingwMain`),
      and the Js engine bundled in `ktor-client-core` (`webMain`, shared by js and wasmJs). CIO can't be used
      natively: Ktor's native TLS fails with "TLS sessions are not supported on Native platform".
    - How each engine reports a refused connection: CIO and Darwin throw an `IOException`; Curl and WinHttp a bare
      `IllegalStateException` (matched by exact class, so a native `CancellationException` never counts); the Js
      engine `Error("Fail to fetch")`. `PlatformEngineTest` dials a dead loopback port on every platform to pin this.
      The bare-ISE rule is deliberately broad: Curl and WinHttp also throw one for local setup failures (a failed
      handle or proxy setup), which are then retried and reported as connection errors with the cause kept, and the
      rule follows the host's default engine rather than the engine in use. `ClientJvmTest` pins that the JVM
      treats a bare ISE as an ordinary failure.
    - Two more failures count as connection errors. On every platform, Ktor's own "Content-Length mismatch" check
      (a bare ISE from `SavedCall` when a body is cut short) is matched by exact class and wording. On the JVM, CIO
      reports an untrusted server certificate as a raw `CertificateException` and a response it can't parse as a
      `ParserException` (in ktor-http-cio, which ktor-client-core needs anyway, so excluding CIO still works); the
      JVM actual counts any `GeneralSecurityException` and `ParserException`. `ClientJvmTest` drives a real CIO
      engine against `RawServer` for all three, which also pins Ktor's wording.
    - `BlockingJev` is an `expect class`. The `jvmMain` actual is the real one; the `nativeMain` and `webMain` actuals
      are empty. `JevClient` keeps `val blocking = BlockingJev(this)` in common code, so the JVM class file, and
      Java's `jev.getBlocking()`, are exactly as before. `-Xexpect-actual-classes` silences the Beta warning.
    - `platformGetenv` on js/wasmJs is a `js()` call that must be the whole body of a top-level function (a
      Kotlin/Wasm rule) and needs `@OptIn(ExperimentalWasmJsInterop::class)`. It reads `process.env`, so it only
      works on Node.js, the only JS runtime targeted.
    - Common code can't use JVM-only APIs such as `Map.putIfAbsent`, and needs explicit `kotlin.jvm.JvmOverloads` /
      `kotlin.jvm.JvmSynthetic` imports (only the JVM imports `kotlin.jvm.*` by default).
- **Validation** (`Questions.kt`). Every problem is collected into one `JevValidationException` before anything is sent:
  at least one question, unique non-blank ids, instructions that are non-blank text or a non-empty object or array,
  criteria values that are text, an object or an array (or null for a Choice option), 1..255 Choice options, 2..10 Score
  levels, and no entry nested too deeply or holding NaN or an infinity (which kotlinx.serialization refuses to encode).
  `evaluate` also rejects a null, number or boolean state; the API takes a string, an object or an array.

## Documentation site

- **Layout.** A Zensical site lives in `website/jev4k`, configured by `zensical.toml` with pages under `docs/`. Its
  Python environment is `website/pyproject.toml` + `uv.lock`, Python 3.14 per `website/.python-version`. Dark mode (the
  `slate` palette) is the default. `docs/stylesheets/extra.css` widens the page grid from 61rem to 90rem so 120-column
  examples fit without horizontal scrolling. Emoji and icons come from Zensical's own extension, so `mkdocs-material`
  isn't needed.
- **Publishing.** `.github/workflows/docs.yml` runs the same steps as `make site-build` (Zensical build with
  `--strict`, Dokka, KDocs copied to `/kdocs`) in its `docs` job on every PR and `master` push, as a required check.
  It deploys to GitHub Pages only when a release is published, or on manual dispatch, so jev4k.com never shows a
  version that isn't on Maven Central yet; a separate deploy job can be re-run on its own. A release runs on its
  tag, so the `github-pages` environment allows tags matching `[0-9]*.[0-9]*.[0-9]*` as well as `master`.
  `zensical.toml` sets `site_url`, `repo_url` and `edit_uri`, and Dokka's `homepageLink` points at
  `github.com/pambrose/jev4k`; its source links open the ref in `jev4k.sourceRef` (see Build). The repository's Pages
  source must be set to "GitHub Actions".
- **Custom domain.** The site is served at <https://jev4k.com/>, not `pambrose.github.io/jev4k/`, so page URLs carry no
  path prefix. `docs/CNAME` holds the bare domain and Zensical copies it to `site/CNAME`; GitHub Pages reads the domain
  from that file, and without it an Actions-published site can lose the custom domain set under Settings → Pages on a
  later deploy. DNS: four apex `A` records to GitHub's `185.199.10[8-11].153`, four `AAAA` to `2606:50c0:800[0-3]::153`,
  and `www` as a `CNAME` to `pambrose.github.io`.
- **`llms.txt`.** `docs/llms.txt` follows the [llmstxt.org](https://llmstxt.org) convention: an H1, a blockquote
  summary, a few paragraphs of orientation, then annotated links to every page. Zensical copies it verbatim, so it is
  served at <https://jev4k.com/llms.txt>. Its links are absolute, so they resolve when an agent fetches the file on its
  own; update it when a page is added, renamed, or removed.
- **Grid cards need a four-space list body.** The card grids on `index.md` are a `<div class="grid cards" markdown>`
  wrapping a list, and each item must be written as `-` plus three spaces, with its `---`, description and link
  indented four spaces. At two spaces the `---` closes the list instead of becoming the card's divider, so every card
  breaks into a one-item list, a stray rule and two loose paragraphs, each landing in its own grid cell. The page
  still builds cleanly, so only the rendered HTML (or a look at the page) catches it: one `<ul>` holding every `<li>`
  is right, one `<ul>` per card is not.
- **Admonitions need an indented body.** Write `!!! warning "Title"`, a blank line, then the body indented four
  spaces. An unindented body builds cleanly but renders as an empty titled box followed by an ordinary paragraph;
  in the HTML, the `<div class="admonition">` holds nothing but its `admonition-title`.
- **Commands.** `make site` serves the site with live reload. `make site-build` builds it into `website/jev4k/site` and
  copies the Dokka KDocs to `site/kdocs`; the `KDocs` nav entry, `api.md`, is an ordinary page that links there.
  `make check-site` and `make upgrade-site` manage the Python dependencies.
- **Examples are never written inline in pages.** They live in `src/jvmTest/kotlin/website` (`package website`) and are
  pulled in with `--8<-- "File.kt:section"` inside a fenced block. `pymdownx.snippets` resolves them from that folder,
  the Java example's and the repository root (for `HotDogExample.kt`, which the Images page shows), with `check_paths`
  and `dedent_subsections` on.
    - A section is the region between `// --8<-- [start:section]` and `// --8<-- [end:section]` (`#` markers in `.txt`
      files).
    - To show a directive literally in a page, prefix it with `;`.
    - Never write marker text such as `--8<-- [start:x]` in page prose. The snippets extension deletes any line
      containing one, so the rest of that sentence disappears.
- **The Java example** lives in `src/jvmTest/java/website/JavaInterop.java` and is compiled by `compileJvmTestJava`, so
  it doubles as a guard on part of the Java-visible surface: the overloads it calls (the two-argument `qb.noul(...)`,
  `BlockingJev.query` with and without a model, `jevResult` and `jevApiException`), `BlockingJev`'s `@Throws` (it
  catches `InterruptedException`), every millisecond member, the image calls (`JevImage.of`, the vararg `attach`, a Box
  on `jev-preview`, `copyBytes`), and that `ask(query, state, null)` stays unambiguous next to `askWithImages`. The ABI
  dump in `api/` guards the rest. It doesn't guard `@JvmSynthetic`, because kotlinc marks reified inline functions
  synthetic on its own. `zensical.toml`'s snippet `base_path` includes that folder.
- **Example files are compiled and linted like any test source, but they aren't tests.** Keep Kotest and MockK out of
  them.
    - Top-level names share one package, so they must be unique across files, and only one `main` is allowed.
    - `make format` may re-flow end-of-line comments in `when` branches onto the wrong branch. Put branch comments on
      the line above.
    - **Trailing comments in an example are aligned in a column** within each run of consecutive lines, in the `website`
      examples and in the README's fenced blocks. `.editorconfig` disables ktlint's `no-multi-spaces` for
      `src/jvmTest/kotlin/website/*.kt` so the padding survives `make format`; the library sources keep the rule.
      Keep new examples aligned, and keep the padded line within 120 characters.
- **After changing an example or a page,** run `make tests` and
  `cd website/jev4k && uv run zensical build --clean --strict`. The build must report "No issues found"; `--strict`
  turns warnings, such as a link to a missing anchor, into failures, as CI's `docs` check does.

## Build

- Gradle 9.8.1 via the wrapper, single module (`rootProject.name = "jev4k"`). `group` and `version` live in
  `gradle.properties`; the version is always a release number, and `-PoverrideVersion=...` replaces it for snapshots.
- Kotlin Multiplatform (`kotlin-multiplatform` plugin; `java-library` is incompatible with it). Targets: `jvm`,
  `js { nodejs() }`, `wasmJs { nodejs() }`, `macosArm64`, `iosArm64`, `iosX64`, `iosSimulatorArm64`, `tvosArm64`,
  `tvosSimulatorArm64`, `watchosArm32`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `linuxX64`,
  `linuxArm64`, `mingwX64`, the same list as `~/git/common-utils`.
    - Source sets follow the default hierarchy: `commonMain`, `jvmMain`, `nativeMain` (split into `appleMain`,
      `linuxMain` and `mingwMain` for the engines), and `webMain` for js + wasmJs. Tests are `commonTest` and
      `jvmTest`.
    - Only a Mac links and runs every target. Linux compiles every Apple klib too (CI's build job does), but can't link
      or run an Apple binary, and `kotlin.native.ignoreDisabledTargets=true` keeps a host quiet about what it can't
      build (Windows wasn't checked). A Mac cross-compiles and links Linux and Windows binaries but can't run them
      itself; `make docker-linux-tests` runs the Linux ones in containers. Test binaries no host tool can run here
      (iosX64's off an Intel Mac, mingwX64's off Windows) have their test KSP, compile and link tasks disabled, so
      `check` doesn't build them; the Linux ones are kept for `docker-linux-tests`.
    - The User-Agent version is compiled in: a configuration-cache-safe `generateBuildInfo` task writes
      `internal const val JEV4K_VERSION` into `build/generated/buildinfo`, a `commonMain` source directory.
    - The JS toolchains' lockfiles live in `kotlin-js-store/` and are committed (`.gitignore` negates the global
      `*.lock` ignore for them). The `yarnResolutions` map in `build.gradle.kts` pins patched versions of vulnerable
      transitive npm packages, as in common-utils; after changing it, delete `build/js/package.json` and
      `build/wasm/package.json`, run `kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`, and check the lockfile diff.
- Publishing uses `com.vanniktech.maven.publish` with `KotlinMultiplatform(...)`.
    - The group is `com.pambrose.jev4k`, so every artifact lands under one `com/pambrose/jev4k/` directory, as with
      common-utils. 0.1.0 was published as `com.pambrose:jev4k`, and nothing newer goes there. The group is also
      part of the klib's unique name, which `api/jev4k.klib.api` records.
    - `com.pambrose.jev4k:jev4k` is the root module, which Gradle resolves per target; the JVM jar is `jev4k-jvm`,
      which is what Maven consumers name. Every other target gets its own artifact (`jev4k-js`, `jev4k-macosarm64`, …).
    - Releases are published from a Mac, the only host that links and tests the Apple targets as well as compiling
      them; the publishing Makefile targets refuse to run elsewhere.
    - The Dokka HTML is packaged as the javadoc jar, alongside a sources jar and POM metadata (Apache 2.0,
      github.com/pambrose/jev4k).
    - `publishToMavenCentral(automaticRelease = true)`.
    - Signing happens only when `signingInMemoryKey` is supplied. The Makefile's `GPG_ENV` supplies it from
      `GPG_SIGNING_KEY_ID`, and the passphrase from the macOS keychain (`gradle-signing-password` / `gpg-signing`).
      Every process the build starts inherits `GPG_ENV`, so step 5 of the release checklist stops the Gradle and Kotlin
      daemons after a publish.
    - Maven Central credentials come from `~/.gradle/gradle.properties`.
    - `make publish-snapshot` and `make publish-maven-central` upload to Maven Central, so never run them unasked.
      `make publish-local` and `make publish-local-snapshot` publish to `~/.m2`.
- Versions, libraries, and plugins are declared in the version catalog `gradle/libs.versions.toml` and referenced from
  `build.gradle.kts` as `libs.*` (e.g. `alias(libs.plugins.kotlin.serialization)`, `api(libs.ktor.client.core)`). Add
  new dependencies to the catalog, not as inline coordinates. Kotlin-family plugins share `version.ref = "kotlin"`.
- `ktor-client-core`, `kotlinx-serialization-json`, `kotlinx-coroutines-core` and `kotlinx-io-core` are `api`
  dependencies because their types appear in the public API (`HttpClientEngine`, `JsonElement`, suspend functions and
  inline `runBlocking` wrappers, and the kotlinx-io `Path` that `JevImage.read` takes). kotlinx-io is pinned to the
  version `ktor-io` brings.
- The catalog carries two JVM versions, because the build JDK and the shipped bytecode deliberately differ:
    - `jvm-toolchain = "25"` is what compiles the project (`jvmToolchain(...)`). The foojay resolver plugin in
      `settings.gradle.kts` downloads a matching JDK automatically if one isn't installed. That plugin keeps an inline
      version because the catalog isn't available in the settings `plugins {}` block.
    - `jvm-target = "17"` is the floor consumers need. It drives the jvm target's `compilerOptions.jvmTarget`,
      `java.source/targetCompatibility` (javac, for `JavaInterop.java`, must agree with kotlinc) and Dokka's
      `jdkVersion`. `-Xjdk-release=17` is also passed, so compiling on 25 can't link against an API that's missing on
      17 — `jvmTarget` alone would only set the class-file version. javac gets `options.release = 17` for the same
      reason.
    - The multiplatform plugin doesn't publish `org.gradle.jvm.version`, so `build.gradle.kts` sets it to 17 on
      `jvmApiElements` and `jvmRuntimeElements` itself. Without it Gradle can't warn a consumer on an older JDK.
    - jev4k is embedded in other applications, so don't raise the target without a reason: Java 25 bytecode makes the
      jar unusable on every JDK below 25. The sources compile cleanly as low as Java 8, so 17 is a choice, not a
      constraint.
- `kotlin.code.style=official` is set in `gradle.properties` (4-space indentation).
- `compilerOptions.optIn` carries `kotlinx.serialization.ExperimentalSerializationApi` for every compilation, so no
  source file needs an `@OptIn` for it (the `JsonArrayBuilder.addAll` overloads used in the website examples are the
  current reason). It also carries jev4k's own `com.pambrose.jev4k.ExperimentalJevImages`, which the library and its
  tests use throughout. Opt-in is compile-time only and doesn't propagate to consumers of the published jar.
- Detekt (`dev.detekt` 2.0.0-alpha, the line used in the author's other repos) runs as part of `check`, so `make tests`
  lints too.
    - The plain `detekt` task, the one `check` runs, defaults to `src/main` and `src/test`, which no longer exist, so
      its `source` is set to every `src/*/kotlin` directory. It runs without type resolution, as before the
      multiplatform move; the per-source-set `detekt<SourceSet>` tasks exist but aren't wired in.
    - `MatchingDeclarationName` lists `web` among its `multiplatformTargets`, for `BlockingJev.web.kt`.
    - Config: `config/detekt/detekt.yml`, generated by `detektGenerateConfig` with `buildUponDefaultConfig = true`.
    - Deliberate deviations from the defaults: `CyclomaticComplexMethod` 25, `LongMethod` 140, `LongParameterList`
      12/12, `TooManyFunctions` 20 (40 per class), `ReturnCount` max 3, and `EmptyFunctionBlock` and `MagicNumber` off.
    - Everything else is stock, including the 120-character `MaxLineLength`, `ThrowsCount` 2 and PascalCase
      `EnumNaming`.
    - Fix findings rather than baselining them; `config/detekt/baseline.xml` is wired in but doesn't exist.
- Kotlinter (ktlint) is applied through the author's convention plugin `com.pambrose.kotlinter` (catalog
  `gradle-plugins`, published to Maven Central, hence the `pluginManagement` repositories in `settings.gradle.kts`). It
  uses the checkstyle and plain reporters, and `lintKotlin` also runs as part of `check`.
    - Style comes from `.editorconfig`: ktlint's default `ktlint_official` code style with 4-space indentation and a
      120-character line limit. Function and class signatures with two or more parameters are wrapped one parameter per
      line.
    - The disabled ktlint rules match the author's other repos: `no-wildcard-imports`, `multiline-if-else`,
      `string-template-indent`, `indent`, `multiline-expression-wrapping`, `chain-method-continuation`,
      `no-trailing-spaces`, `import-ordering`.
    - Because the `indent` rule is off, `make format` can wrap code without re-indenting it. Check its output by eye.
    - Files under `build/` (Kotest's KSP-generated launchers, `BuildInfo.kt`) are excluded from lint. The exclusion
      spec reads a local `val`, not a script-level one: a lambda that touches the build script can't be stored in
      the configuration cache.
- A gitignored `.env` in the project root supplies environment variables to every `Test` and `JavaExec` task, through
  the author's `com.pambrose.envvar` convention plugin (same `gradle-plugins` catalog version as kotlinter).
  `.env.example` is the committed template; copy it and fill in `TYPESAFE_API_KEY` so `make example` and
  `make live-tests` work without exporting anything.
    - Only JVM tasks see `.env`; the JS and native test tasks aren't `Test` tasks. They inherit the shell's environment,
      which is how `JEV4K_LIVE=1` reaches them in `make live-tests`. The simulators only pass variables prefixed
      `SIMCTL_CHILD_`.
    - Editing `.env` invalidates the configuration cache, but `jvmTest`'s environment isn't a task input, so an
      up-to-date `jvmTest` won't re-run on its own. `make tests` and `make live-tests` pass `--rerun` to each test task,
      so they always see the current values. (`--rerun-tasks` would also recompile all sixteen targets.)
- Dokka (`org.jetbrains.dokka` 2.2.0) builds the KDoc site. `make kdocs` writes it to `build/dokka/html`.
    - The site documents the public and protected API of the main source sets (protected for `JevQuery`'s builders),
      with a tab per platform (common, jvm, native, web). Source sets whose names end in `Test` are suppressed, so no
      test class, fixture or example is included, and the `com.pambrose.jev4k.internal` package is excluded.
      `docs/packages.md` supplies the module and package overview pages, through `commonMain`'s source-set `includes`. A
      publication's `includes` is only read for a multi-module overview, so the file set there, as it was until 0.3.0,
      never reached the site.
    - kotlinx.serialization, Ktor and kotlinx-io types link to their online API docs through
      `externalDocumentationLinks`.
    - `moduleVersion` is `project.version`, every source set's `jdkVersion` comes from the catalog's `jvm-target`
      version (Dokka resolves a JDK link through whichever source set's links sort first, so setting it on `jvmMain`
      alone left every link at 25), and `suppressInheritedMembers` is on. Pages don't repeat inherited members: a
      subclass such as `JevRateLimitException` links to `JevApiException` for `status`, `body` and `requestId`.
    - `sourceLink` (rooted at `src/`, so it covers every source set) opens `tree/<jev4k.sourceRef>`, a Gradle
      property that defaults to `master`. A released javadoc jar can't be replaced while `master` moves on, so
      `docs.yml` passes the tag when it runs on one and `make publish-maven-central` passes the version, which step 6
      of the release checklist then tags. `homepageLink` points at `github.com/pambrose/jev4k`.
    - In a class comment, refer to a constructor property as `[name][Class.name]`. A bare `[name]` points at the
      constructor parameter, which Dokka leaves unlinked.
- Java interop is a deliberate, narrow contract: `@JvmOverloads` on `JevClient`'s builder constructor, `BlockingJev`'s
  calls and `QueryBuilder.noul` gives Java the overloads it needs (Kotlin only generates them for *trailing* defaults,
  so `query(state, model, block)` gets none), and `@JvmSynthetic` marks every public `inline reified` member (`query`/
  `ask` with `@Serializable` state on `JevApi` and `BlockingJev`, `QueryBuilder.choice<E>()`, `JevQuery.choice<E>()`,
  `JevResult.enumChoice<E>()`, `jsonEntry()`). That annotation is a marker for readers, not load-bearing: kotlinc
  already emits every reified inline function as `ACC_SYNTHETIC`, so javac can't resolve one either way. Keep it on any
  new reified member so the set stays uniform. `QueryBuilder.choice<E>()` is out of Java's reach, and the
  `@PublishedApi` functions it calls (`enumChoiceRef`, `QueryBuilder.add`, `JevResult.enumChoiceOf`) compile to
  public, callable methods but aren't supported API, so a Java caller gets an enum-backed Choice by building a
  `ChoiceQuestion` and passing it to `QueryBuilder.question(id, question)`; the README and the Installation page say
  so.
  Kotlin's inline `Duration` mangles every member that takes or returns one, and makes `RetryPolicy`'s constructor and
  `copy` synthetic, so each `Duration` setting has a millisecond twin for Java: `JevConfigBuilder.timeoutMillis`,
  `JevCallOptionsBuilder.timeoutMillis`, `JevDefaults.TIMEOUT_MILLIS`, `RetryPolicy.with…` (one per setting, plus
  `withBackoffMillis` for both backoff limits, which changed one at a time can fail on the way) and
  `JevRateLimitException.retryAfterMillis`. Add one alongside any new `Duration` setting. The `Duration` getters stay
  Kotlin-only. `JavaInterop.java` calls each twin, catches `BlockingJev`'s `InterruptedException` (javac rejects that
  catch if `@Throws` is dropped), and uses `BlockingJevKt.blocking` and `JevCallOptionsKt.withOptions`.
- The JVM jar's manifest carries `Implementation-Version` and
  `Automatic-Module-Name: com.pambrose.jev4k`, which pins the JPMS module name for consumers instead of letting it
  derive from the jar's file name. The README and the site's Installation page document what an embedding app inherits:
  six POM dependencies (in `jev4k-jvm`'s POM: five compile, CIO at runtime), no logging binding, and how to drop
  CIO when supplying another engine.
- KGP's ABI validation (`abiValidation()`) guards the public API. `api/jev4k.api` is the JVM surface, Java callers
  included, and `api/jev4k.klib.api` covers the other targets. `checkKotlinAbi` runs under `check`; `make abi-update`
  (`updateKotlinAbi`) rewrites the dumps after an intended change and must run on a Mac: a host that can't compile a
  target keeps that target's old declarations, and while Linux compiles the Apple klibs, only a Mac has been checked to
  write a complete klib dump. The JVM dump matched 0.1.0's through the multiplatform move; 0.2.0's API changes since
  (`ModelList`, `JevCallOptions`, the millisecond members and the rest, listed in the changelog) were recorded with
  `make abi-update`, as were 0.3.0's image and Box additions, and 0.4.0's `JevImage.copyBytes`,
  `RetryPolicy.withBackoffMillis` and `QuestionSet.toString`, which only add to the dumps.
- Coverage uses Kover (`org.jetbrains.kotlinx.kover`).
    - `.github/workflows/ci.yml` runs on every push to `master`. Its ubuntu `build` job runs `build koverVerify
      koverXmlReport koverLog` (compile, kotlinter, detekt, the ABI check, and the jvm, js, wasmJs and linuxX64 tests),
      then `make docker-linux-tests` with QEMU (`docker/setup-qemu-action`), which is the only place CI runs
      linuxArm64's tests, then uploads `build/reports/kover/report.xml` to Codecov with the `unittests` flag. A JVM test
      failure leaves no report to upload, since Kover's reports depend on `jvmTest`. The upload needs a `CODECOV_TOKEN`
      repository secret, and sets `disable_search` so koverLog's `build/kover/coverage.txt` isn't sent too. Codecov only
      reports: both its statuses are `informational` (the project one flags a drop of more than 1%), branch protection
      doesn't require them, and the upload can't fail CI, so the Kover floors are the coverage gate. A failed build
      uploads `build/docker-linux-tests/` with the test reports, since a container run's TeamCity messages are only
      there. The `binfmt` image QEMU installs runs privileged, so it is pinned by digest, as `LINUX_TEST_IMAGE` is in
      the Makefile; neither zizmor nor Dependabot tracks image digests, so both are bumped by hand.
    - A `native` matrix job runs the tests Linux can't: the macOS and iOS, tvOS and watchOS simulator tests on
      macos-latest and `mingwX64Test` on windows-latest. The Apple row passes `-Pjev4k.requireSimulators=true`, which
      fails the build when a simulator platform has no device, instead of letting its disabled tests report SKIPPED.
      iosX64 is compiled and published but its tests run nowhere: they need an Intel Mac. A final `ci-ok` job passes
      only when every other job succeeded; branch protection on `master` requires it, the `docs` check and GitGuardian,
      so the JDK matrix and the native jobs gate a merge.
    - Every job that compiles native code caches `~/.konan`, keyed on the Kotlin version alone (read from the catalog)
      with no `restore-keys`, so an old toolchain is never carried forward. `docs.yml` uses no cache at all, neither
      that one nor setup-gradle's or setup-uv's: it publishes the site, and zizmor's `cache-poisoning` audit accepts
      caching only when it is off for every run of a publishing workflow. Dokka downloads the Kotlin/Native toolchain
      itself, and the job still finishes before CI does. For the same reason setup-uv installs a pinned uv (`version`),
      bumped by hand after the 7-day wait Dependabot gives actions, when `make upgrade-site` moves the lockfile; the
      JDK's patch floats, for its security fixes. Both jobs set `timeout-minutes`.
    - `.github/workflows/zizmor.yml` audits the workflows and `dependabot.yml` with zizmor, as `make zizmor` does
      locally (with the brew-installed binary, and the gh CLI's token for the online audits). On a pull request
      its `zizmor` job fails on any finding and annotates the diff; pushes to `master`, a weekly run and manual runs
      upload SARIF to code scanning instead. The action's pinned SHA fixes the zizmor version (1.30.1 at
      zizmor-action v0.6.4). The rules it holds every workflow to: `persist-credentials: false` on each checkout
      (`artipacked`), a 7-day Dependabot `cooldown`, actions pinned to SHAs, and least-privilege `permissions`.
      Run `make zizmor` after editing anything under `.github`.
    - A `test` job in the same workflow runs `jvmTest` on JDK 17, 21 and 25. Tests otherwise run on the
      toolchain JVM whatever the runner uses, so `-PtestJavaVersion=<n>` repoints `jvmTest`'s `javaLauncher`.
      `-XX:+EnableDynamicAgentLoading` is passed on every JDK: 21 introduced the warning it silences, not the
      option, which 11 and 17 accept. `make test-jdk JDK=17` reproduces one row, `make all-tests` the whole set.
    - The `kover {}` block sets line and branch floors (`minLineCoveragePct`, `minBranchCoveragePct`) a few points below
      the measured totals. `koverVerify` is deliberately not wired into `check`, because it would fail every build
      that skips the tests at 0%. Run it with `make coverage-verify`. Raise the floors when coverage has moved up and
      stayed there.
    - Kover measures the JVM target's main code (`src/commonMain` plus `src/jvmMain`); `codecov.yml` flags the same
      paths. The website examples and test fixtures are never counted.
- Enum constants used as Choice options are UPPER_CASE. Their names are sent to the model as option keys unless
  `JevOption.optionKey` overrides them (the `Dept` test fixture sends lowercase keys that way).

The `Makefile` wraps the common Gradle invocations; `make` (or `make help`) lists every target.

```bash
make build                                            # clean build of every target + doc examples, lint, ABI; no tests
make tests                                            # lint + ABI check + every test this host runs, re-run
make jvm-tests                                        # the JVM tests only, the quickest loop
make js-tests                                         # the tests on Node.js (js and wasmJs)
make native-tests                                     # macOS + iOS/tvOS/watchOS simulators on a Mac, linuxX64 on Linux
make docker-linux-tests                               # linuxX64 + linuxArm64 tests in Docker containers (needs Docker)
make platform-tests                                   # jvm + js + native + docker-linux tests; no lint or ABI check
make test-jdk JDK=17                                  # run the JVM tests on one JDK, as CI's matrix does
make all-tests                                        # tests + the JDK matrix + coverage floors + Docker Linux + live tests
make lint                                             # kotlinter (lintKotlin) + detekt
make kdocs                                            # Dokka HTML site in build/dokka/html
make coverage-open                                    # Kover HTML coverage report, opened in a browser
make coverage-verify                                  # check coverage against the line and branch floors
make site                                             # serve the Zensical docs site (website/jev4k)
make site-build                                       # build the docs site, with KDocs under /kdocs
make publish-local-snapshot                           # publish <version>-SNAPSHOT to ~/.m2
make publish-maven-central                            # sign and release <version> to Maven Central (needs GPG_SIGNING_KEY_ID)
make format                                           # auto-format sources with ktlint (formatKotlin)
make detekt                                           # detekt static analysis only
make detekt-baseline                                  # regenerate config/detekt/baseline.xml
make zizmor                                           # audit .github's workflows and dependabot.yml, as CI does
make abi-check                                        # check the public API against api/
make abi-update                                       # rewrite the api/ dumps after an intended change (macOS)
make example                                          # run TriageExample against the live API (needs TYPESAFE_API_KEY)
make hotdog-example                                   # run HotDogExample, two photos, against the live API
make live-tests                                       # LiveSmokeTest (needs TYPESAFE_API_KEY) + every platform's probes
make tree                                             # dependency tree
make versions                                         # list only the outdated dependencies, plugins and Gradle
make versions-all                                     # the ben-manes plugin's full report, up-to-date entries included
make upgrade-wrapper                                  # regenerate the wrapper at the catalog's gradle-wrapper version
./gradlew jvmTest --tests "com.pambrose.jev4k.DslTest" # run a single test class on the JVM
```

To upgrade Gradle, bump `gradle-wrapper` in `gradle/libs.versions.toml`, then run `make upgrade-wrapper`.

## Testing

- Test classes are named `*Test`. Each is a Kotest `StringSpec()` with an `init {}` block.
    - Specs for common code go in `src/commonTest/kotlin/com/pambrose/jev4k/`, so they run on every platform the host
      supports. `src/jvmTest` keeps what needs the JVM: MockK specs, `BlockingJevTest`, `LiveSmokeTest`, `ClientJvmTest`
      (the blocking wrapper, and five cases on a real CIO engine: a timeout, an untrusted certificate, a body cut short,
      an oversized declared body and an unparseable response), `CioExclusionTest` with its `CioFreeCalls` helper,
      `ErrorsJvmTest`, `ReadOnlyJvmTest`, `JevImageJvmTest`, `HotDogPhotosTest`, `CropExampleTest`, `SilentServer` and
      `RawServer`, the two runnable examples (`TriageExample`, `HotDogExample`), and the website examples. A class name
      can't be in both, so a JVM-only remainder is named `…JvmTest`.
    - Kotest runs the non-JVM targets through the `io.kotest` plugin and KSP, which generate each target's entry
      point. The plugin only does that for test tasks the host can run, so `build.gradle.kts` adds the processor to
      `kspLinuxX64Test` and `kspLinuxArm64Test` itself; without that, a Linux test binary linked on a Mac holds no
      specs, and linuxArm64 (which has no test task) none anywhere.
    - `make docker-linux-tests` links the Linux test binaries named in `LINUX_TEST_TARGETS` (both, by default; CI
      passes only linuxArm64's, since its build job has run linuxX64's) and runs each in a `buildpack-deps:noble-curl`
      container of its own architecture (Ubuntu plus `ca-certificates`). A Kotest native binary exits 0 even when
      a test fails, since Gradle reads the verdict from its TeamCity messages, so the target fails on a
      `##teamcity[testFailed` line or a missing Kotest `Specs:` summary. Full logs go to `build/docker-linux-tests/`.
    - KGP runs a simulator test in the first available device of its platform. Xcode creates iOS devices by
      default but installs no tvOS or watchOS runtime, so `build.gradle.kts` reads `xcrun simctl list devices
      available --json` and disables `tvosSimulatorArm64Test`/`watchosSimulatorArm64Test` (and their KSP, compile and
      link tasks, in the same block that skips iosX64's and mingwX64's) when that platform has no device. The listing goes through a `ValueSource` that returns only the set of
      platforms with a device: the configuration cache re-checks that set on every build, so adding a device (a
      runtime from Xcode → Settings → Components, then `xcrun simctl create` if it made none) enables the tests on
      the next build, while the listing's sizes and timestamps, which change on every simulator run, don't discard
      the cache. Their `LiveProbeTest` stays skipped for the same `SIMCTL_CHILD_` reason as iOS.
    - Kotest's `autoClose` takes Kotest's own `AutoCloseable`, which is only `java.lang.AutoCloseable` on the JVM;
      common tests wrap a `kotlin.AutoCloseable` with `closeAfterSpec(...)` from `TestSupport.kt`.
    - Construct exceptions with common APIs: `kotlinx.io.IOException`, Ktor's `SocketTimeoutException(message)`
      factory (the class's native constructor is internal), `CancellationException(message, cause)` rather than
      `initCause`. A bare `IllegalStateException` counts as a connection failure on Linux and Windows, so don't use
      one as a generic "some other error".
- HTTP is tested with Ktor's `MockEngine` through `testJev(...)` in `TestSupport.kt`. It injects the engine and records
  retry delays instead of sleeping, and `NoJitter` makes backoff predictable. A client on a real engine gets the same
  settings from `testDefaults(delays)`; `triageJev()`, `PAYOUT_TICKET` and `liveOptIn()` are shared there too. `parts()`
  splits a MockEngine request's multipart body, for `ImageRequestTest`; `PNG_BYTES`/`JPEG_BYTES` are signature-only
  fixtures, and `Gestures` is a `JevQuery` that attaches example pictures. `foreignReference(generated)` rewrites a
  generated reference with another process's tag, for the `PROCESS_TAG` tests, and `nestedArrays`, `nestedObjects` and
  `nestedArrayText` build depth-limit fixtures. `SilentServer` (JVM) is a local socket that never responds, for testing
  real-CIO timeouts. `RawServer` (JVM) answers every request with fixed bytes, for responses no real server sends,
  optionally over TLS with a self-signed certificate made by the JDK's `keytool`. `PlatformEngineTest` drives each
  platform's real default engine against a dead loopback port.
- MockK is used where a dependency is mocked: `ConsumerTest` mocks `JevApi` to show how application code is tested
  without HTTP.
- `LiveSmokeTest` (JVM) makes real API calls and runs only when `JEV4K_LIVE=1` (`make live-tests` sets it), so
  ordinary runs never spend tokens. The opt-in alone gates it, not the key too: without `TYPESAFE_API_KEY` a live run
  fails, each test naming the missing key, instead of passing with no real call made.
- `PlatformEnvTest` (common) is the one test that doesn't stub the environment: it checks that `platformGetenv`
  reads `PATH`, which every process has (a simulator test spawned by `simctl` and a Docker container included; the
  Windows lookup ignores case), and returns null for a name that isn't set.
- A result answers only the handles of the set it was built for. A MockK mock of code that builds its questions per
  call returns `answers { jevResult(body, secondArg()) }`; `JevResult.get` names that case when a handle's id is
  in the request but the handle isn't. `jevResult(String)` checks the body with the client's own
  `ResponseInfo.parseObject`.
- `LiveProbeTest` (common) runs only with `JEV4K_LIVE=1` and spends no tokens: an invalid key must come back as
  `JevAuthenticationException` over TLS, and a 1 ms timeout as `JevTimeoutException`. It passes on the JVM, Node.js
  and macOS, and on linuxX64 and linuxArm64 under `JEV4K_LIVE=1 make docker-linux-tests`. A plain `ubuntu` image
  fails the TLS probe: without `ca-certificates` Curl can't verify the certificate and reports a
  `JevConnectionException`. A test binary spawned by `simctl` on the iOS simulator can't validate any TLS
  certificate (`NSURLErrorDomain -1202`, even for apple.com), so its probes stay skipped.
- Test JVMs run with `-XX:+EnableDynamicAgentLoading`, so MockK's agent loads without a warning on JDK 21 and later.
