# jev4k dependency upgrade review

Reviewed 2026-11-09: the uncommitted version changes in the working tree on top of `68dd995` (the 0.4.0 release).
The review covers the version catalog, the Gradle wrapper and the documentation site's `website/uv.lock`. The method
and the evidence are at the end.

| Area                                       | From        | To            |
|--------------------------------------------|-------------|---------------|
| Gradle wrapper (`gradle-wrapper`)          | 9.8.1       | 9.9.0         |
| Dokka                                      | 2.2.0       | 2.3.0         |
| Ktor (`api`)                               | 3.6.0       | 3.6.1         |
| kotlinx.serialization (`api`)              | 1.11.0      | 1.12.0        |
| kotlinx-io (`api`)                         | 0.9.1       | 0.9.2         |
| KSP                                        | 2.3.12      | 2.3.13        |
| MockK (tests)                              | 1.14.11     | 1.14.12       |
| ben-manes versions plugin                  | 0.66.0      | 0.67.0        |
| Convention plugins (`gradle-plugins`)      | 1.1.7       | 1.1.9         |
| Zensical (site)                            | 0.0.69      | 0.1.0         |
| pymdown-extensions (site)                  | 12.1        | 13.0          |
| Markdown, click (site)                     | 3.11, 8.5.0 | 3.11.3, 8.5.1 |
| zensical-ui (site, new) / deepmerge (gone) | — / 3.0.1   | 0.0.41 / —    |

The upgrade itself is sound. Every test this Mac runs passes on it. The checksums match. The site's article text is
the same on all 31 pages. `make versions` reports everything up to date. What follows is one pin that broke its
documented rule, two Gradle 10 blockers to watch, and bookkeeping.

Every issue has a number. When one is fixed, tick its box in the summary and in the plan, and update the count
below in the same change.

**Status: 4 of 8 fixed.** No high, no medium, 4 low, 4 nit.

## Summary

### Low

- [x] [#1](#issue-1) kotlinx-io moves to 0.9.2, but ktor-io 3.6.1 brings 0.9.1, and the catalog comment still names
  Ktor 3.6.0
- [x] [#2](#issue-2) No CHANGELOG or release-notes entry yet, though three bumped libraries reach consumers' classpaths
- [ ] [#3](#issue-3) Gradle 10 will fail the build on an enum attribute in KGP 2.4.21, the newest Kotlin plugin
- [ ] [#4](#issue-4) Kover's `koverLog` calls `Task.getAnt()`, which Gradle 10 removes; Kover and Dokka 2.3.0 call
  `setVisible`, which Gradle 11 removes

### Nit

- [x] [#5](#issue-5) CLAUDE.md still says Gradle 9.8.1 and Dokka 2.2.0
- [ ] [#6](#issue-6) `docs.yml` says to bump the uv pin when the lockfile moves, but the next uv is a breaking 0.13
- [x] [#7](#issue-7) An IntelliJ rewrite of `.idea/vcs.xml` sits in the working tree next to the bumps
- [ ] [#8](#issue-8) Dokka can't resolve `[images]` in `distinctImages`' KDoc (it couldn't on 2.2.0 either)

## Plan: order and grouping of fixes

Each group is one pull request, and its issues are listed in the order to fix them. Tick an issue's box here and in
the summary in the same change, and tick a group's box once all of its issues are ticked.

Group 1 is the upgrade PR itself, so the fixes ride with the bumps. It ends with `make tests` and
`cd website/jev4k && uv run zensical build --clean --strict`. CI then covers what this review couldn't run here:
linuxX64, linuxArm64 (Docker), mingwX64 and JDK 21.

### Now, with the upgrade

- [x] **1. Land the upgrade.** The catalog, the wrapper and `website/uv.lock`, with these fixes.
  - [x] [#1](#issue-1) Set `kotlinx-io` back to `0.9.1` and correct the comment to ktor-io 3.6.1 (or keep 0.9.2 on
    purpose and reword the rule; see the issue)
  - [x] [#5](#issue-5) Update CLAUDE.md to Gradle 9.9.0 and Dokka 2.3.0
  - [x] [#7](#issue-7) Leave `.idea/vcs.xml` out of the commit

### Next release

- [x] **2. Record the upgrade.** When the next version opens.
  - [x] [#2](#issue-2) List the bumps in CHANGELOG and RELEASE_NOTES, with Ktor, kotlinx.serialization and kotlinx-io
    called out as consumer-visible

### Before Gradle 10

- [ ] **3. Clear the Gradle 10 blockers.** No release fixes either yet, so this group waits for upstream.
  - [ ] [#3](#issue-3) After each Kotlin release, re-run the check in the issue; move to Gradle 10 only once it's gone
  - [ ] [#4](#issue-4) After each Kover release, re-run the same check for `getAnt`

### Any time

- [ ] **4. Small cleanups.**
  - [ ] [#8](#issue-8) Replace `[images]` with plain text in `distinctImages`' KDoc
  - [ ] [#6](#issue-6) Decide on uv 0.13: move the local uv and the `docs.yml` pin together, or reword the comment

## Issues

### Low

<a id="issue-1"></a>

#### 1. kotlinx-io moves to 0.9.2, but ktor-io 3.6.1 brings 0.9.1, and the catalog comment still names Ktor 3.6.0

**Low** · Build · small · `gradle/libs.versions.toml:11`, `CLAUDE.md:333-334`

**What's wrong.** CLAUDE.md says "kotlinx-io is pinned to the version `ktor-io` brings", and the catalog line says the
same thing about its value:

```toml
kotlinx-io = "0.9.2"  # the version ktor-io 3.6.0 brings; kotlinx.io.files.Path is in jev4k's public API
```

`ktor-io-3.6.1.pom` on Maven Central declares `kotlinx-io-core` **0.9.1**. So the value breaks the rule, and the
comment is wrong twice: Ktor is now 3.6.1, and neither Ktor release brings 0.9.2.

**Impact.** kotlinx-io is an `api` dependency, so `jev4k-jvm`'s POM would declare 0.9.2. Gradle and Maven both resolve
a consumer's classpath to 0.9.2, under a Ktor that was built and tested against 0.9.1. It's a patch release, so a
break is unlikely. But 0.9.2 reimplements native `FileSource` and `FileSink` (kotlinx-io's changelog: `O_CLOEXEC`,
buffering, zero and negative byte counts). `JevImage.read` uses them on native. The Apple tests pass on it, but the
Linux and Windows tests haven't run here.

**Fix.** Either:
- Set `kotlinx-io = "0.9.1"` and change the comment to "ktor-io 3.6.1". This follows the rule, and the next Ktor
  release will likely bring 0.9.2 on its own. (Recommended.)
- Or keep 0.9.2 for its native file fixes, and reword the rule in the comment and in CLAUDE.md:333-334 to "at least
  the version ktor-io brings". Then let CI's Linux and Windows jobs vouch for `JevImage.read`.

<a id="issue-2"></a>

#### 2. No CHANGELOG or release-notes entry yet, though three bumped libraries reach consumers' classpaths

**Low** · Release docs · small · `CHANGELOG.md`, `RELEASE_NOTES.md`

**What's wrong.** `gradle.properties` is still `0.4.0` and `CHANGELOG.md` opens at `[0.4.0]`, so nothing records the
upgrade yet. Earlier releases did record theirs. For example, `RELEASE_NOTES.md:150` says "The build moves to Gradle
9.8.1, and the documentation site to Zensical 0.0.69".

**Impact.** Ktor, kotlinx.serialization and kotlinx-io are `api` dependencies, so the next release changes what an
embedding application resolves. The README and the Installation page tell that audience what jev4k puts on their
classpath. They're the readers who need the line.

**Fix.** When the next version opens, list the bumps under it. Call out Ktor 3.6.1, kotlinx.serialization 1.12.0 and
kotlinx-io (per #1) as consumer-visible, and the rest as tooling.

<a id="issue-3"></a>

#### 3. Gradle 10 will fail the build on an enum attribute in KGP 2.4.21, the newest Kotlin plugin

**Low** · Build · watch · KGP `KotlinNativeBundleArtifactFormat.kt:25`

**What's wrong.** Gradle 9.9.0 deprecates enums as attribute values unless they implement `Named`:

> Using the enum type KotlinNativeBundleArtifactsTypes as an attribute value type has been deprecated. This will fail
> with an error in Gradle 10.

The message blames "the Kotlin Gradle Plugin 2.0.x line" and says to upgrade to KGP 2.1.0. With
`-Dorg.gradle.deprecation.trace=true`, the trace shows the build's own KGP 2.4.21 calling `Attribute.of`
in the static initializer of `KotlinNativeBundleArtifactFormat` (`KotlinNativeBundleArtifactFormat.kt:25`, reached
from the commonizer setup). 2.4.21 is the newest KGP on Maven Central, so there is nothing to upgrade to yet. The
warning prints once per fresh daemon, which is why a second build in the same daemon doesn't show it.

**Impact.** None on Gradle 9.x. Moving to Gradle 10 before a KGP release fixes this would break configuration.

**Fix.** Nothing now. Before any move to Gradle 10, run
`./gradlew help --no-daemon --warning-mode all --no-configuration-cache` and confirm the warning is gone. Repeat after
each Kotlin release.

<a id="issue-4"></a>

#### 4. Kover's `koverLog` calls `Task.getAnt()`, which Gradle 10 removes; Kover and Dokka 2.3.0 call `setVisible`

**Low** · Build · watch · Kover 0.9.11, Dokka 2.3.0

**What's wrong.** Gradle 9.9.0 prints two more deprecations under `--warning-mode all`:
- "The Task.getAnt() method has been deprecated. This is scheduled to be removed in Gradle 10." It's printed by
  `:koverLog`, which CI's build job runs.
- "The Configuration.setVisible(boolean) method has been deprecated. This is scheduled to be removed in Gradle 11." The
  trace attributes it to Kover (`ConfigurationsKt.asBucket`, `Configurations.kt:112`) and to Dokka 2.3.0's
  `DokkaFormatPlugin`, 75 times per configuration.

Kover 0.9.11 is the newest release, and Dokka 2.3.0 is the one this upgrade moves to.

**Impact.** None on Gradle 9.x. `getAnt` joins #3 as a Gradle 10 blocker; `setVisible` only matters at Gradle 11.

**Fix.** Nothing now. Re-run the check from #3 after each Kover release, and look for `getAnt` as well.

### Nit

<a id="issue-5"></a>

#### 5. CLAUDE.md still says Gradle 9.8.1 and Dokka 2.2.0

**Nit** · Maintainer notes · trivial · `CLAUDE.md:289`, `CLAUDE.md:389`

**What's wrong.** Line 289 reads "Gradle 9.8.1 via the wrapper", and line 389 reads "Dokka (`org.jetbrains.dokka`
2.2.0)". The other old version numbers in the repository are in dated documents (earlier reviews, the image-support
plan and spec, and past release notes). Those describe their own time and should stay as written.

**Fix.** Change them to 9.9.0 and 2.3.0, in the same commit as the bump.

<a id="issue-6"></a>

#### 6. `docs.yml` says to bump the uv pin when the lockfile moves, but the next uv is a breaking 0.13

**Nit** · CI · small · `.github/workflows/docs.yml:62-65`

**What's wrong.** The comment on the pin says "Bump it by hand, after the same 7-day wait Dependabot gives the
actions, when `make upgrade-site` moves the lockfile". This lockfile moved. But CI's `0.12.24` is the last 0.12
release. The newest past the 7-day wait is 0.13.6 (2026-10-27), and uv's 0.13.0 notes list breaking changes. They
also make Python 3.15 the default, which `website/.python-version` (3.14) overrides. The local uv is 0.12.19, older
than CI's pin.

**Impact.** None today: `uvx --from uv==0.12.24 uv lock --check` passes on the new lockfile, so CI's
`uv run --locked` builds it.

**Fix.** Either move the local uv and the pin to 0.13.6 together, after `uv lock --check` passes with it, or reword the
comment to say a minor uv release is a separate decision.

<a id="issue-7"></a>

#### 7. An IntelliJ rewrite of `.idea/vcs.xml` sits in the working tree next to the bumps

**Nit** · Repository · trivial · `.idea/vcs.xml:4`

**What's wrong.** The IDE rewrote `<mapping directory="$PROJECT_DIR$" vcs="Git" />` as
`<mapping directory="" vcs="Git" />`. It isn't a version change, but a `git add -A` would sweep it into the upgrade
commit.

**Fix.** Leave it out (`git restore .idea/vcs.xml`). If the IDE keeps writing it, commit it on its own.

<a id="issue-8"></a>

#### 8. Dokka can't resolve `[images]` in `distinctImages`' KDoc (it couldn't on 2.2.0 either)

**Nit** · KDoc · trivial · `src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt:253`

**What's wrong.** Dokka 2.3.0 warns: "Couldn't resolve link: [images] in … JevImage.kt:254:4". The KDoc on
`internal fun List<JevImage>.distinctImages()` opens with `[images]`, which names nothing in scope. The warning isn't
new. The 0.4.0 `docs.yml` run on `master` (run 39102460544, Dokka 2.2.0) printed it too.

**Fix.** Write it as plain text, such as "These images, each once:".

## Method

- **Upstream metadata.** Read the published POMs on Maven Central:
  - ktor-io 3.6.1 brings kotlinx-io 0.9.1 and coroutines 1.11.0, on Kotlin 2.3.21.
  - kotlinx-serialization-core-jvm 1.12.0 is built on Kotlin 2.4.10, which the project's 2.4.21 reads.
  - kotlinx-io-core 0.9.2 is built on Kotlin 2.3.0.
  - Also read kotlinx-io's changelog for 0.9.2 and uv's release list.
- **Checksums.** The `distributionSha256Sum` and `gradle/wrapper/gradle-wrapper.jar` match
  `services.gradle.org`'s published SHA-256 for 9.9.0.
- **Build and tests**, on Gradle 9.9.0 with `--warning-mode all`.
  - `make tests` passed: lint, detekt, the ABI check (dumps unchanged) and the yarn lock stores (no lockfile change).
  - Test cases, all with 0 failures: jvm 333, js 286, wasmJs 285, macosArm64 285, iosSimulatorArm64 285,
    tvosSimulatorArm64 285. watchOS was skipped, since there's no device.
  - `make test-jdk JDK=17` passed: 333 cases, 0 failures.
  - `dokkaGeneratePublicationHtml` passed with one warning (#8).
  - `make versions` reports every dependency up to date.
- **Deprecations.** Traced with `-Dorg.gradle.deprecation.trace=true`, in a fresh daemon for #3.
- **Site.**
  - Built with `--strict` under both toolchains: Zensical 0.0.69 with pymdown-extensions 12.1, and 0.1.0 with 13.0.
    Both report no issues.
  - Article text is the same on all 31 pages, code examples included, and the file set is the same.
  - The page chrome changed in two small ways: the drawer and search buttons' `aria-label`s became visually hidden
    text, and the meta description's apostrophe is now an entity.
  - Under 0.1.0, the Context7 widget loads once and stays across two instant navigations (checked in Chrome).
  - CI's uv 0.12.24 accepts the new lockfile.
- **Not run here.**
  - linuxX64 and linuxArm64 (`make docker-linux-tests`), mingwX64, the JDK 21 row and the live tests. CI's PR
    jobs cover all but the live tests.
