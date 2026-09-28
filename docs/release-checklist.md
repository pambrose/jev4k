# Release checklist

How to cut a jev4k release. The one-time setup steps are complete and kept below as a record; every release
follows the steps after them.

This file is for maintainers and isn't part of any published site: Zensical builds `website/jev4k/docs`, and
Dokka includes only `docs/packages.md` (the `includes.from("docs/packages.md")` call in `build.gradle.kts`).

## Current state

Verified 2026-09-20.

| Item                                  | State                                                       |
|---------------------------------------|-------------------------------------------------------------|
| `pambrose/jev4k` on GitHub            | ✅ exists, public, default branch `master`                  |
| Pages source                          | ✅ GitHub Actions (`build_type: workflow`)                  |
| Custom domain `jev4k.com`             | ✅ serving, certificate approved for the apex and `www`     |
| Enforce HTTPS                         | ✅ on; `http://` 301s to `https://`                         |
| `CODECOV_TOKEN` repository secret     | ✅ set                                                      |
| `master` on the remote                | ✅ pushed, with `ci.yml` green on it                        |
| Published site                        | ✅ live at <https://jev4k.com/>, KDocs included             |
| Site deploys                          | on a published GitHub release, or a manual `docs.yml` run   |
| Required checks on `master`           | `ci-ok`, `docs` and GitGuardian                             |
| Git tags                              | ✅ `0.1.0` at `b7c2b5e`, with a `v0.1.0` GitHub release      |
| `com.pambrose:jev4k` on Maven Central | ✅ 0.1.0 published, signed, and resolvable                   |
| `gradle.properties` version           | `0.2.0`, the next release number (the multiplatform move)   |
| `gradle.properties` group             | `com.pambrose.jev4k` from 0.2.0 (0.1.0 was `com.pambrose`)  |
| `CHANGELOG.md` / `RELEASE_NOTES.md`   | 0.1.0 dated 2026-09-20; 0.2.0 dated 2026-09-28              |

## Before the first release, once

1. [x] **Push `master`** so the workflows run for the first time.
2. [x] **Add the `CODECOV_TOKEN` secret** (repository → Settings → Secrets and variables → Actions). Without
   it the `ci.yml` upload step fails on every run.
3. [x] **Get a green `docs.yml` run.** The first one failed at `actions/configure-pages` because Pages wasn't
   enabled yet. Re-running it once Pages was configured deployed the site, and pushes to `master` have
   published it since. Nothing in the workflow needed changing.
4. [x] **Point `jev4k.com` at Pages.** The apex carries the four `185.199.*.153` A records and the four
   `2606:50c0:800*::153` AAAA records, `www` is a CNAME to `pambrose.github.io`, and GitHub has issued a
   certificate for both names. `pambrose.github.io/jev4k` redirects to the new domain.
5. [x] **Turn on Enforce HTTPS** (Settings → Pages). GitHub leaves the switch off until a certificate is
   provisioned and doesn't flip it afterwards, so it needed setting by hand once the certificate landed.
6. [x] **Check the badges render.** All eight in `README.md:3-10` resolve now that CI, the docs deploy, the
   Central upload and the release all exist: `release: v0.1.0`, `maven-central: v0.1.0` and the Codecov
   percentage were the three waiting on something.
7. [x] **Deploy the site from releases, not from `master` (2026-09-27).** `docs.yml` builds the site on every PR
   and `master` push but deploys it only when a release is published, so jev4k.com never shows a version that
   isn't on Central yet. A release runs on its tag, so the `github-pages` environment allows tags matching
   `[0-9]*.[0-9]*.[0-9]*` as well as the `master` branch (Settings → Environments → github-pages).
8. [x] **Require every CI job (2026-09-27).** Branch protection on `master` requires `ci-ok`, a `ci.yml` job that
   passes only when every other job (the build, the JDK matrix and both native jobs) did, plus the `docs` build
   and GitGuardian.

## Every release

### 1. Pick the version

- [ ] Set `version=` in `gradle.properties`. It is always a plain release number; `-PoverrideVersion=`
  supplies snapshot versions, so no `-SNAPSHOT` is ever committed.
- [ ] Follow SemVer. Anything that changes the public API (`src/commonMain` or any other main source set) is at
  least a minor bump while the library is pre-1.0. The dumps in `api/` show exactly what changed: `make abi-check`
  fails on any difference, and `make abi-update`, run on a Mac, records an intended one.

### 2. Verify the tree

- [ ] `make tests` on a Mac — kotlinter, detekt, the ABI check, and the suite on the JVM, Node.js (js and wasmJs),
  macOS and the iOS simulator (and the tvOS and watchOS simulators where a device is installed), forced to re-run.
  The Windows tests run in CI (step 4).
- [ ] `make docker-linux-tests` — linuxX64 and linuxArm64 in Docker. linuxArm64 has no Gradle test task, so this
  and CI's QEMU step are the only places its tests run.
- [ ] `make coverage-verify` — the line and branch floors in `build.gradle.kts`.
- [ ] `cd website/jev4k && uv run zensical build --clean --strict` — must report "No issues found".
- [ ] `make site-build` — the site plus Dokka KDocs under `/kdocs`.
- [ ] Optional: `make live-tests` and `make example`, which call the real API and spend tokens. Worth doing
  when the request or response mapping changed.
- [ ] `git status` is clean apart from the release edits.

### 3. Update the release documents

- [ ] `CHANGELOG.md`: replace `## [<version>] — unreleased` with `## [<version>] - YYYY-MM-DD`, and confirm
  the link reference at the bottom points at the tag.
- [ ] `RELEASE_NOTES.md`: replace `## v<version> — unreleased` with `## v<version> — YYYY-MM-DD`, and set the
  **Full Changelog** link. The first release has no predecessor, so it links to
  `https://github.com/pambrose/jev4k/commits/<tag>`; later releases use
  `https://github.com/pambrose/jev4k/compare/<previous-tag>...<tag>`.
- [ ] `README.md`: update any version shown in a dependency snippet.
- [ ] `src/jvmTest/kotlin/website/GettingStarted.txt`: update the released version in the `dependency-gradle`,
  `dependency-kmp`, `dependency-maven` and `exclude-cio` snippets, which the site's Installation page shows. The
  `composite-dependency` snippet tracks `gradle.properties` instead (step 7).
- [ ] Add a new `## [Unreleased]` section to `CHANGELOG.md` only if work continues before the next release.

### 4. Commit and open the release PR

- [ ] Commit the version bump and the release documents together, on a branch.
- [ ] Open a PR and wait for the required checks: `ci-ok` (build, tests, Kover, Codecov, the JDK matrix and the
  native jobs), `docs` (Zensical and Dokka) and GitGuardian. Don't publish from a tree that CI hasn't accepted.
- [ ] Don't merge yet. Merging puts the new version's install lines in the README on GitHub, so the artifacts
  go to Central first (step 5).

### 5. Publish to Maven Central, from the release branch

Signing and credentials, all outside the repository:

- [ ] `GPG_SIGNING_KEY_ID` is exported and `gpg --list-secret-keys "$GPG_SIGNING_KEY_ID"` finds the key.
- [ ] The macOS keychain holds the passphrase (service `gradle-signing-password`, account `gpg-signing`).
- [ ] `~/.gradle/gradle.properties` holds `mavenCentralUsername` and `mavenCentralPassword`.

Then:

- [ ] Publish from a Mac, with the release branch checked out at the commit CI accepted. Only macOS builds the
  Apple targets, and a release that left them out would publish a root module pointing at artifacts that don't
  exist; the publishing targets refuse to run anywhere else.
- [ ] `make publish-maven-central`. It runs `publishAndReleaseToMavenCentral`, and
  `publishToMavenCentral(automaticRelease = true)` promotes the staging repository on its own, so there is
  nothing to click in the Central portal.
- [ ] Confirm the artifact is really there before tagging:

  ```bash
  curl -sI https://repo1.maven.org/maven2/com/pambrose/jev4k/jev4k-jvm/<version>/jev4k-jvm-<version>.jar | head -1
  curl -sI https://repo1.maven.org/maven2/com/pambrose/jev4k/jev4k/<version>/jev4k-<version>.module | head -1
  ```

  Central's indexes take a few minutes to catch up, so the jar may land before search does. Don't merge until
  both return `200`.
- [ ] Check the published set. `jev4k` is the root module (`.module`, POM, metadata jar); `jev4k-jvm` carries the
  jar; `jev4k-js`, `jev4k-wasm-js` and one artifact per native target (`jev4k-macosarm64`, `jev4k-linuxx64`,
  `jev4k-mingwx64`, …) carry klibs. Each has a sources jar, a Dokka HTML javadoc jar, a POM, and a `.asc` for
  every file.

### 6. Merge, tag and create the GitHub release

- [ ] Squash-merge the release PR, then update `master` locally (`git switch master && git pull`) and delete the
  release branch. The squash commit has the same tree as the commit that was published.
- [ ] Tag the merge commit on `master` with the bare version number, no `v` prefix:

  ```bash
  git tag <version> && git push origin <version>
  ```

- [ ] Create the release with the `v`-prefixed title and the notes for this version:

  ```bash
  gh release create <version> --title "v<version>" --notes-file <notes>
  ```

  `<notes>` is the body of this version's section of `RELEASE_NOTES.md`, ending with the **Full Changelog**
  link.

- [ ] If the release breaks anything, the notes open with a `> [!WARNING]` callout saying what breaks and what
  doesn't, and it is the first thing on the release page (GitHub renders it as a Warning box). 0.2.0's is the
  move to the `com.pambrose.jev4k` group. Check the page after publishing: `gh release view <version> --web`.
- [ ] Publishing the release starts `docs.yml`, which deploys jev4k.com from the tag. Watch it finish:
  `gh run list --workflow docs.yml --limit 1`, then `gh run watch <run-id>`.

### 7. After

- [ ] The `[<version>]` link at the bottom of `CHANGELOG.md` now resolves.
- [ ] <https://jev4k.com/> and <https://jev4k.com/kdocs/> show the new version.
- [ ] The GitHub release and Maven Central badges in the README show the new number. Both are cached by
  shields.io for a few minutes.
- [ ] A dependency on the new coordinates resolves from a scratch project.
- [ ] Bump `gradle.properties` to the next planned version when development resumes, together with the
  `composite-dependency` snippet in `src/jvmTest/kotlin/website/GettingStarted.txt`, which names that version.
