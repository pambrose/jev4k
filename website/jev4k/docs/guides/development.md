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

- **Checks.** `make tests` runs kotlinter (ktlint), detekt, and the Kotest suite.
- **JDK coverage.** The tests run on the build toolchain by default. CI also runs them on JDK 17, 21 and 25,
  since jev4k ships Java 17 bytecode and compiling against the 17 API doesn't prove it behaves there.
  `make test-jdk JDK=17` reproduces one of those rows, and `make all-tests` runs every test target there is.
- **Coverage.** `make coverage-open` builds the Kover report and opens it; `make coverage-verify` checks the
  line and branch floors. CI uploads the same report to
  [Codecov](https://codecov.io/gh/pambrose/jev4k).
- **No traffic leaves the machine in unit tests.** They exercise the client through Ktor's `MockEngine`, apart
  from one timeout test that drives the real CIO engine against a loopback socket.
- **Live tests are opt-in.** They run only when `TYPESAFE_API_KEY` is set and `JEV4K_LIVE=1`, which
  `make live-tests` sets.
- **`.env` supplies the key.** Copy `.env.example` to `.env` (gitignored) and set `TYPESAFE_API_KEY`. Gradle
  loads it into every test and example task, so nothing needs exporting in your shell.

## An index for agents

The site publishes an [`llms.txt`](../llms.txt) at its root, following the
[llmstxt.org](https://llmstxt.org) convention: a short description of the library followed by an annotated link
to every page. Point a coding agent at `https://jev4k.com/llms.txt` and it can find the rest.

## Project layout

| Path                                 | Contents                                      |
|--------------------------------------|-----------------------------------------------|
| `src/main/kotlin/com/pambrose/jev4k` | the library                                   |
| `src/test/kotlin/com/pambrose/jev4k` | the Kotest tests and the runnable example     |
| `src/test/kotlin/website`            | the code examples used by this site           |
| `src/test/java/website`              | the Java example used by this site            |
| `website/jev4k`                      | this documentation site                       |
| `jev-docs/`                          | a compressed copy of TypeSafe's documentation |

## This site

The site is built with [Zensical](https://zensical.org) from `website/jev4k`:

```bash
--8<-- "Development.txt:site"
```

### Publishing

The site is published to GitHub Pages at <https://jev4k.com/> by `.github/workflows/docs.yml`. The workflow runs on
every push to `master`, or on demand from the Actions tab. It builds the site from the locked `website/uv.lock`, builds
the KDocs with Dokka, copies them under `/kdocs`, and deploys the result: the same steps as `make site-build`.

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

Code examples aren't written into the pages. They live in `src/test/kotlin/website` as ordinary Kotlin, with the
Java example in `src/test/java/website`, so they compile against the real API and pass the same lint checks as
the rest of the code. A page includes a region of a file with a snippet directive:

````markdown
```kotlin
;--8<-- "NoulExamples.kt:basic"
```
````

That includes the region of `NoulExamples.kt` fenced by a pair of comment markers, a `start:basic` marker and an
`end:basic` marker, dedented. (The markers use the same `--8<--` prefix as the directive; see any file in
`src/test/kotlin/website`.) The site's `zensical.toml` sets `check_paths`, so a reference to a missing file fails
the build. Non-Kotlin snippets, such as shell commands, live in `.txt` files next to them.

The examples are compiled with the test sources, but they aren't tests, and nothing runs them.
