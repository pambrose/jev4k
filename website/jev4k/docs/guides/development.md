---
icon: lucide/wrench
---

# Development

## Building and testing

The `Makefile` wraps the common Gradle tasks; `make help` lists them all.

```bash
--8<-- "Development.txt:make-targets"
```

To run a single test class:

```bash
--8<-- "Development.txt:single-test"
```

- **Checks.** `make tests` runs kotlinter (ktlint), detekt, the ABI check, and the Kotest suite on every platform
  the host can run: the JVM, Node.js (js and wasmJs), and the native targets (macOS and the iOS simulator on a
  Mac, linuxX64 on Linux). CI runs the rest, on a macOS and a Windows runner. The tvOS and watchOS simulator tests
  run on a Mac that has a simulator device for them (Xcode installs neither by default) and are skipped otherwise.
- **Linux tests anywhere.** `make docker-linux-tests` links the linuxX64 and linuxArm64 test binaries and runs each
  in a Docker container of its own architecture, so a Mac can run the Linux tests too. It is the only place
  linuxArm64's tests run. `JEV4K_LIVE=1 make docker-linux-tests` adds the live probes.
- **API dumps.** `api/jev4k.api` (the JVM surface, Java callers included) and `api/jev4k.klib.api` (every other
  target) record the public API, and `make tests` fails if it changes. After an intended change, run
  `make abi-update` on a Mac, the only host that compiles every target, and commit the new dumps.
- **JDK coverage.** The JVM tests run on the build toolchain by default. CI also runs them on JDK 17, 21 and 25,
  since jev4k ships Java 17 bytecode and compiling against the 17 API doesn't prove it behaves there.
  `make test-jdk JDK=17` reproduces one of those rows, and `make all-tests` runs every test target there is.
- **Coverage.** `make coverage-open` builds the Kover report and opens it; `make coverage-verify` checks the
  line and branch floors. CI uploads the same report to
  [Codecov](https://codecov.io/gh/pambrose/jev4k).
- **No traffic leaves the machine in unit tests.** They exercise the client through Ktor's `MockEngine`, apart
  from one timeout test that drives the real CIO engine against a loopback socket, and one test per platform
  that dials a loopback port nothing listens on, to check how that platform's default engine reports a refused
  connection.
- **Live tests are opt-in.** They run only when `JEV4K_LIVE=1`, which `make live-tests` sets. The JVM smoke
  tests also need `TYPESAFE_API_KEY`, and fail, naming it, when it's missing; the probes that run on
  every platform send an invalid key and a 1 ms timeout, so they spend no tokens.
- **`.env` supplies the key.** Copy `.env.example` to `.env` (gitignored) and set `TYPESAFE_API_KEY`. Gradle
  loads it into the JVM test and example tasks, so `make example` and the JVM smoke tests need nothing exported in
  your shell. The js, wasmJs and native test tasks see only the shell's environment.

## An index for agents

The site publishes an [`llms.txt`](../llms.txt) at its root, following the
[llmstxt.org](https://llmstxt.org) convention: a short description of the library followed by an annotated link
to every page. Point a coding agent at `https://jev4k.com/llms.txt` and it can find the rest.

## Project layout

| Path                                       | Contents                                                          |
|--------------------------------------------|-------------------------------------------------------------------|
| `src/commonMain/kotlin/com/pambrose/jev4k` | the library, shared by every platform                             |
| `src/jvmMain`, `src/nativeMain`, …         | per-platform code: `BlockingJev`, the default engines, `getenv`   |
| `src/commonTest/kotlin/com/pambrose/jev4k` | the Kotest tests that run on every platform                       |
| `src/jvmTest/kotlin/com/pambrose/jev4k`    | the JVM-only tests (MockK, blocking, live smoke) and the example  |
| `src/jvmTest/kotlin/website`               | the code examples used by this site                               |
| `src/jvmTest/java/website`                 | the Java example used by this site                                |
| `api/`                                     | the public API dumps checked by `make tests`                      |
| `website/jev4k`                            | this documentation site                                           |
| `jev-docs/`                                | a compressed copy of TypeSafe's documentation                     |

## This site

The site is built with [Zensical](https://zensical.org) from `website/jev4k`:

```bash
--8<-- "Development.txt:site"
```

### Publishing

The site is published to GitHub Pages at <https://jev4k.com/> by `.github/workflows/docs.yml`. On every pull request
and push to `master` it builds the site from the locked `website/uv.lock` in strict mode, builds the KDocs with Dokka
and copies them under `/kdocs`: the same steps as `make site-build`. It deploys the result only when a release is
published, or when run by hand from the Actions tab, so the site never shows a version that isn't on Maven Central
yet.

The repository needs one setting before the first deploy: under **Settings → Pages → Build and deployment**, set
**Source** to **GitHub Actions**. After a failed deploy, re-run just the failed deploy job; the build job's artifact
is reused.

### The custom domain

The site answers on `jev4k.com` rather than `pambrose.github.io/jev4k`, so its URLs carry no path prefix. Two things
keep that working:

- `docs/CNAME` holds the bare domain. Zensical copies it verbatim into `site/`, and GitHub Pages reads the domain from
  the deployed artifact — a site published by Actions can otherwise lose the domain set under **Settings → Pages**.
- DNS points the apex at GitHub's Pages servers: four `A` records (`185.199.108.153` through `185.199.111.153`), four
  `AAAA` records (`2606:50c0:8000::153` through `2606:50c0:8003::153`), and `www` as a `CNAME` to `pambrose.github.io`.

Links that leave the site — `llms.txt`, the README, the release checklist — are absolute, so they name `jev4k.com`
directly and have to be updated together if the domain ever changes.

### Code examples

Code examples aren't written into the pages. They live in `src/jvmTest/kotlin/website` as ordinary Kotlin, with the
Java example in `src/jvmTest/java/website`, so they compile against the real API and pass the same lint checks as
the rest of the code. A page includes a region of a file with a snippet directive:

````markdown
```kotlin
;--8<-- "NoulExamples.kt:basic"
```
````

That includes the region of `NoulExamples.kt` fenced by a pair of comment markers, a `start:basic` marker and an
`end:basic` marker, dedented. (The markers use the same `--8<--` prefix as the directive; see any file in
`src/jvmTest/kotlin/website`.) The site's `zensical.toml` sets `check_paths`, so a reference to a missing file fails
the build. Non-Kotlin snippets, such as shell commands, live in `.txt` files next to them.

The examples are compiled with the test sources, but they aren't tests, and nothing runs them.
