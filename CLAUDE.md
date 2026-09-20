# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`jev4k` is a Kotlin DSL and client for TypeSafe's Jev "System One" model, built on the Ktor client (CIO engine) and
kotlinx.serialization. You describe a state and typed questions (Noul, Choice, Score), and it returns typed answers. The
library is `com.pambrose.jev4k` under `src/main/kotlin`. A runnable example is
`src/test/kotlin/com/pambrose/jev4k/examples/TriageExample.kt`.

## TypeSafe / Jev API docs

This project works with TypeSafe's Jev model (`POST https://api.typesafe.ai/v1/systemone`). A cached, compressed copy of
its docs lives in `jev-docs/`. Read `jev-docs/jev-api.md` before writing or changing any code that calls the API. Use
`jev-docs/sdks.md` for client behavior (retries, timeouts, errors, typing) and `jev-docs/cookbooks.md` for proven
question designs. The raw pages under `jev-docs/pages/` aren't in git, because they're TypeSafe's copyrighted docs. Run
`make api-docs` (which runs `jev-docs/refresh.sh`) to download them after cloning. The notes are committed. Don't use
the legacy preview API shape (`/preview/evaluation`, `document`, `prompts`); `jev-api.md` §9 lists every renamed field.

## Architecture

Type names follow TypeSafe's JS SDK. A question definition is a `Question`, one of `NoulQuestion`, `ChoiceQuestion` or
`ScoreQuestion`, which hold only what is asked: instructions and criteria. A `QuestionRef<A>` is the handle code holds.
It pairs an id with a `Question` (`ref.question`) and a decoder for the typed answer.

There are two DSL layers over one core model. Both produce a validated `QuestionSet`, and
`JevApi.evaluate(state, questionSet, model)` is the only call that sends questions to the network. `JevApi.models()`
(`GET v1/models`) is the other network call.

- **Inline layer** (`Builders.kt`). `jev.query(state) { noul("id", "...") ... }` uses string ids. `QueryBuilder`
  functions return `QuestionRef<A>` handles, and `include(query)` merges a `JevQuery` into the same request.
- **Typed layer** (`JevQuery.kt`). `object X : JevQuery() { val urgent by noul("...") }`. A `PropertyDelegateProvider`
  takes the id from the property name (or `id =`) and registers questions in declaration order. `JevQuery.questions` is
  built lazily, so an invalid definition fails on first use. `choice<E>()` builds options from an enum. The option key
  is the constant's name unless `JevOption.optionKey` overrides it, and `JevOption.entry` becomes the description.
- **Answers** (`JevResult.kt`, `Answers.kt`). `result[handle]` decodes through the handle's `decode` function, and also
  checks that the handle belongs to the request. By-id accessors (`noul`/`choice`/`score`/`enumChoice`) check that the
  question type matches. Raw answers are stored string-keyed. Enum choices are mapped when read, and an unknown option
  becomes `JevResponseValidationException`.
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
    - Absent answers fail when they are read, not when the response is parsed.
- **Client** (`JevClient.kt`, `internal/HttpClientFactory.kt`, `internal/Retry.kt`).
    - `HttpRequestRetry` reproduces the official SDKs' retry rules. `RetryPolicy` sets them: 408/429/5xx, connection
      errors, timeouts, 0.5 s doubling to 5 s with 25% jitter, and `retry-after-ms`/`Retry-After` hints up to 60 s.
    - `HttpRequestRetry` must be installed **before** `HttpTimeout`, otherwise one timeout cancels every retry.
    - `expectSuccess = false`: non-2xx responses map to `JevApiException` subclasses (`apiException` in `Errors.kt`)
      after retries run out, keeping the raw body and the `x-typesafe-request-id` header.
    - `BlockingJev` (`jev.blocking`) wraps the suspend API in `runBlocking`.
- **Config** (`JevConfig.kt`). Each setting resolves as explicit value, then env var, then default; blank env values are
  ignored. The env vars are `TYPESAFE_API_KEY` (required), `TYPESAFE_BASE_URL` and `TYPESAFE_DEFAULT_MODEL`. Internal
  hooks (`env`, `retryDelay`, `random`) make tests deterministic.
- **Validation** (`Questions.kt`). Every problem is collected into one `JevValidationException` before anything is sent:
  at least one question, unique non-blank ids, non-empty instructions, 1..255 Choice options, 2..10 Score levels.

## Documentation site

- **Layout.** A Zensical site lives in `website/jev4k`, configured by `zensical.toml` with pages under `docs/`. Its
  Python environment is `website/pyproject.toml` + `uv.lock`, Python 3.14 per `website/.python-version`. Dark mode (the
  `slate` palette) is the default. `docs/stylesheets/extra.css` widens the page grid from 61rem to 90rem so 120-column
  examples fit without horizontal scrolling. Emoji and icons come from Zensical's own extension, so `mkdocs-material`
  isn't needed.
- **Publishing.** `.github/workflows/docs.yml` publishes the site to GitHub Pages at <https://pambrose.github.io/jev4k/>
  on every push to `master`, or on manual dispatch. It runs the same steps as `make site-build` (Zensical build, Dokka,
  KDocs copied to `/kdocs`) in a build job, and a separate deploy job can be re-run on its own. `zensical.toml` sets
  `site_url`, `repo_url` and `edit_uri`, and Dokka's `sourceLink`/`homepageLink` point at `github.com/pambrose/jev4k` on
  `master`. The repository's Pages source must be set to "GitHub Actions".
- **`llms.txt`.** `docs/llms.txt` follows the [llmstxt.org](https://llmstxt.org) convention: an H1, a blockquote
  summary, a few paragraphs of orientation, then annotated links to every page. Zensical copies it verbatim, so it is
  served at <https://pambrose.github.io/jev4k/llms.txt>. Its links are absolute, so they resolve when an agent fetches
  the file on its own; update it when a page is added, renamed, or removed.
- **Commands.** `make site` serves the site with live reload. `make site-build` builds it into `website/jev4k/site` and
  copies the Dokka KDocs to `site/kdocs`; the `KDocs` nav entry, `api.md`, is an ordinary page that links there.
  `make check-site` and `make upgrade-site` manage the Python dependencies.
- **Examples are never written inline in pages.** They live in `src/test/kotlin/website` (`package website`) and are
  pulled in with `--8<-- "File.kt:section"` inside a fenced block. `pymdownx.snippets` resolves them from that folder,
  with `check_paths` and `dedent_subsections` on.
    - A section is the region between `// --8<-- [start:section]` and `// --8<-- [end:section]` (`#` markers in `.txt`
      files).
    - To show a directive literally in a page, prefix it with `;`.
    - Never write marker text such as `--8<-- [start:x]` in page prose. The snippets extension deletes any line
      containing one, so the rest of that sentence disappears.
- **The Java example** lives in `src/test/java/website/JavaInterop.java` and is compiled by `compileTestJava`, so it
  doubles as a guard on the Java-visible surface: its two-argument `qb.noul(...)` call stops compiling if
  `@JvmOverloads` is dropped. It doesn't guard `@JvmSynthetic`, because kotlinc marks reified inline functions synthetic
  on its own. `zensical.toml`'s snippet `base_path` includes that folder.
- **Example files are compiled and linted like any test source, but they aren't tests.** Keep Kotest and MockK out of
  them.
    - Top-level names share one package, so they must be unique across files, and only one `main` is allowed.
    - `make format` may re-flow end-of-line comments in `when` branches onto the wrong branch. Put branch comments on
      the line above.
    - **Trailing comments in an example are aligned in a column** within each run of consecutive lines, in the `website`
      examples and in the README's fenced blocks. `.editorconfig` disables ktlint's `no-multi-spaces` for
      `src/test/kotlin/website/*.kt` so the padding survives `make format`; `src/main` keeps the rule. Keep new examples
      aligned, and keep the padded line within 120 characters.
- **After changing an example or a page,** run `make tests` and `cd website/jev4k && uv run zensical build --clean`. The
  build must report "No issues found".

## Build

- Gradle 9.7.1 via the wrapper, single module (`rootProject.name = "jev4k"`). `group` and `version` live in
  `gradle.properties`; the version is always a release number, and `-PoverrideVersion=...` replaces it for snapshots.
- Publishing uses `com.vanniktech.maven.publish`.
    - The Dokka HTML is packaged as the javadoc jar, alongside a sources jar and POM metadata (Apache 2.0,
      github.com/pambrose/jev4k).
    - `publishToMavenCentral(automaticRelease = true)`.
    - Signing happens only when `signingInMemoryKey` is supplied. The Makefile's `GPG_ENV` supplies it from
      `GPG_SIGNING_KEY_ID`, and the passphrase from the macOS keychain (`gradle-signing-password` / `gpg-signing`).
    - Maven Central credentials come from `~/.gradle/gradle.properties`.
    - `make publish-snapshot` and `make publish-maven-central` upload to Maven Central, so never run them unasked.
      `make publish-local` and `make publish-local-snapshot` publish to `~/.m2`.
- Versions, libraries, and plugins are declared in the version catalog `gradle/libs.versions.toml` and referenced from
  `build.gradle.kts` as `libs.*` (e.g. `alias(libs.plugins.kotlin.serialization)`, `api(libs.ktor.client.core)`). Add
  new dependencies to the catalog, not as inline coordinates. Kotlin-family plugins share `version.ref = "kotlin"`.
- `ktor-client-core`, `kotlinx-serialization-json` and `kotlinx-coroutines-core` are `api` dependencies because their
  types appear in the public API (`HttpClientEngine`, `JsonElement`, suspend functions and inline `runBlocking`
  wrappers).
- The catalog carries two JVM versions, because the build JDK and the shipped bytecode deliberately differ:
    - `jvm-toolchain = "25"` is what compiles the project (`jvmToolchain(...)`). The foojay resolver plugin in
      `settings.gradle.kts` downloads a matching JDK automatically if one isn't installed. That plugin keeps an inline
      version because the catalog isn't available in the settings `plugins {}` block.
    - `jvm-target = "17"` is the floor consumers need. It drives `compilerOptions.jvmTarget`,
      `java.source/targetCompatibility` (hence `org.gradle.jvm.version = 17` in the published metadata) and Dokka's
      `jdkVersion`. `-Xjdk-release=17` is also passed, so compiling on 25 can't link against an API that's missing on
      17 — `jvmTarget` alone would only set the class-file version.
    - jev4k is embedded in other applications, so don't raise the target without a reason: Java 25 bytecode makes the
      jar unusable on every JDK below 25. The sources compile cleanly as low as Java 8, so 17 is a choice, not a
      constraint.
- `kotlin.code.style=official` is set in `gradle.properties` (4-space indentation).
- `compilerOptions.optIn` carries `kotlinx.serialization.ExperimentalSerializationApi` for the whole project, so no
  source file needs an `@OptIn` for it (the `JsonArrayBuilder.addAll` overloads used in the website examples are the
  current reason). Opt-in is compile-time only and doesn't propagate to consumers of the published jar.
- Detekt (`dev.detekt` 2.0.0-alpha, the line used in the author's other repos) runs as part of `check`, so `make tests`
  lints too.
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
- A gitignored `.env` in the project root supplies environment variables to every `Test` and `JavaExec` task, through
  the author's `com.pambrose.envvar` convention plugin (same `gradle-plugins` catalog version as kotlinter).
  `.env.example` is the committed template; copy it and fill in `TYPESAFE_API_KEY` so `make example` and
  `make live-tests` work without exporting anything.
    - Editing `.env` invalidates the configuration cache, but the `test` task's environment isn't a task input, so an
      up-to-date `test` won't re-run on its own. `make tests` and `make live-tests` pass `--rerun-tasks`, so they always
      see the current values.
- Dokka (`org.jetbrains.dokka` 2.2.0) builds the KDoc site. `make kdocs` writes it to `build/dokka/html`.
    - The site documents the public API of `src/main` only. Every other Dokka source set is suppressed, so nothing under
      `src/test` (test classes, fixtures, the example) is included, and the `com.pambrose.jev4k.internal` package is
      excluded. `docs/packages.md` supplies the module and package overview pages.
    - kotlinx.serialization and Ktor types link to their online API docs through `externalDocumentationLinks`.
    - `moduleVersion` is `project.version`, `jdkVersion` comes from the catalog's `jvm-target` version, and
      `suppressInheritedMembers` is on. Pages don't repeat inherited members: a subclass such as `JevRateLimitException`
      links to `JevApiException` for `status`, `body` and `requestId`.
    - `sourceLink` and `homepageLink` point at `github.com/pambrose/jev4k` on `master`.
    - In a class comment, refer to a constructor property as `[name][Class.name]`. A bare `[name]` points at the
      constructor parameter, which Dokka leaves unlinked.
- Java interop is a deliberate, narrow contract: `@JvmOverloads` on `JevClient`'s builder constructor, `BlockingJev`'s
  calls and `QueryBuilder.noul` gives Java the overloads it needs (Kotlin only generates them for *trailing* defaults,
  so `query(state, model, block)` gets none), and `@JvmSynthetic` marks every public `inline reified` member (`query`/
  `ask` with `@Serializable` state on `JevApi` and `BlockingJev`, `QueryBuilder.choice<E>()`, `JevQuery.choice<E>()`,
  `JevResult.enumChoice<E>()`, `jsonEntry()`). That annotation is a marker for readers, not load-bearing: kotlinc
  already emits every reified inline function as `ACC_SYNTHETIC`, so javac can't resolve one either way. Keep it on any
  new reified member so the set stays uniform. Because Java can reach neither `QueryBuilder.choice<E>()` nor the
  `internal` `enumChoiceRef`, a Java caller can only get an enum-backed Choice by building a `ChoiceQuestion` and
  passing it to `QueryBuilder.question(id, question)`; the README and the Installation page say so.
- The jar manifest carries `Implementation-Version` (used in the client's User-Agent) and
  `Automatic-Module-Name: com.pambrose.jev4k`, which pins the JPMS module name for consumers instead of letting it
  derive from the jar's file name. The README and the site's Installation page document what an embedding app inherits:
  seven POM dependencies, no logging binding, and how to drop CIO when supplying another engine.
- Coverage uses Kover (`org.jetbrains.kotlinx.kover`).
    - `.github/workflows/ci.yml` runs on every push to `master`: `build -x test` (compile, kotlinter, detekt), then
      `test koverVerify koverXmlReport koverLog`, and uploads `build/reports/kover/report.xml` to Codecov with the
      `unittests` flag. The upload needs a `CODECOV_TOKEN` repository secret. `codecov.yml` fails the project status on
      a drop of more than 1% and reports patch coverage without gating on it.
    - The `kover {}` block sets line and branch floors (`minLineCoveragePct`, `minBranchCoveragePct`) a few points below
      the measured totals. `koverVerify` is deliberately not wired into `check`, because it would fail every
      `build -x test` at 0%. Run it with `make coverage-verify`. Raise the floors when coverage has moved up and stayed
      there.
    - Kover measures `src/main` only. The website examples and test fixtures are never counted.
- Enum constants used as Choice options are UPPER_CASE. Their names are sent to the model as option keys unless
  `JevOption.optionKey` overrides them (the `Dept` test fixture sends lowercase keys that way).

The `Makefile` wraps the common Gradle invocations; `make` (or `make help`) lists every target.

```bash
make build                                            # clean build, skipping tests
make tests                                            # kotlinter + detekt + all tests, forcing re-execution
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
make example                                          # run TriageExample against the live API (needs TYPESAFE_API_KEY)
make live-tests                                       # run LiveSmokeTest against the live API (needs TYPESAFE_API_KEY)
make tree                                             # dependency tree
make versions                                         # report newer dependency/plugin/Gradle versions (ben-manes)
make upgrade-wrapper                                  # regenerate the wrapper at the catalog's gradle-wrapper version
./gradlew test --tests "com.pambrose.jev4k.DslTest"   # run a single test class
```

To upgrade Gradle, bump `gradle-wrapper` in `gradle/libs.versions.toml`, then run `make upgrade-wrapper`.

## Testing

- Test classes are named `*Test`. Each is a Kotest `StringSpec()` with an `init {}` block, under
  `src/test/kotlin/com/pambrose/jev4k/`.
- HTTP is tested with Ktor's `MockEngine` through `testJev(...)` in `TestSupport.kt`. It injects the engine and records
  retry delays instead of sleeping, and `NoJitter` makes backoff predictable. `SilentServer` is a local socket that
  never responds, for testing real-CIO timeouts.
- MockK is used where a dependency is mocked: `ConsumerTest` mocks `JevApi` to show how application code is tested
  without HTTP.
- `LiveSmokeTest` makes real API calls and runs only when `TYPESAFE_API_KEY` is set and `JEV4K_LIVE=1`
  (`make live-tests` sets it), so ordinary runs never spend tokens.
- Test JVMs run with `-XX:+EnableDynamicAgentLoading` for MockK on JDK 25.
