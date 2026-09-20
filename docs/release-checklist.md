# Release checklist

How to cut a jev4k release. The first release has extra steps, listed separately, because nothing has been
pushed or published yet.

This file is for maintainers and isn't part of any published site: Zensical builds `website/jev4k/docs`, and
Dokka includes only `docs/packages.md` (`build.gradle.kts:88`).

## Current state

Verified 2026-09-19.

| Item                                    | State                                                            |
|-----------------------------------------|------------------------------------------------------------------|
| `pambrose/jev4k` on GitHub              | ✅ exists, public, default branch `master`                       |
| Pages source                            | ✅ GitHub Actions (`build_type: workflow`)                       |
| `CODECOV_TOKEN` repository secret       | ✅ set                                                           |
| Local commits                           | ✅ the initial commit is pushed, and `ci.yml` passed on it       |
| `docs.yml` on that commit               | ❌ failed: `configure-pages` ran before Pages was enabled        |
| Git tags                                | ❌ none                                                          |
| `com.pambrose:jev4k` on Maven Central   | ❌ not published (`maven-metadata.xml` returns 404)              |
| `gradle.properties` version             | `0.1.0`                                                          |
| `CHANGELOG.md` / `RELEASE_NOTES.md`     | both mark 0.1.0 as `— unreleased`                                |

## Before the first release, once

1. [x] **Push `master`** so the workflows run for the first time.
2. [x] **Add the `CODECOV_TOKEN` secret** (repository → Settings → Secrets and variables → Actions). Without
   it the `ci.yml` upload step fails on every run.
3. [ ] **Get a green `docs.yml` run.** The first one failed at `actions/configure-pages` because Pages wasn't
   enabled yet. Pages is configured now, so re-running that workflow, or any later push to `master`, publishes
   the site. Nothing in the workflow needs changing.
4. [ ] **Rewrite the "Building from source" section of
   `website/jev4k/docs/getting-started/installation.md`.** It currently says "No release has reached Maven
   Central yet, so there are no coordinates to depend on" and sends readers to a composite build. The moment
   0.1.0 is published that contradicts `RELEASE_NOTES.md`, which hands out
   `implementation("com.pambrose:jev4k:0.1.0")`. Lead with the coordinates and keep the composite build as the
   alternative for working against an unreleased checkout.
5. [ ] **Check the badges render** after the first successful CI run, docs deploy and Central upload. The
   GitHub release, Maven Central and Codecov badges in `README.md:3-7` all show "not found" until their
   backing thing exists.

## Every release

### 1. Pick the version

- [ ] Set `version=` in `gradle.properties`. It is always a plain release number; `-PoverrideVersion=`
  supplies snapshot versions, so no `-SNAPSHOT` is ever committed.
- [ ] Follow SemVer. Anything that changes the public API of `src/main` is at least a minor bump while the
  library is pre-1.0.

### 2. Verify the tree

- [ ] `make tests` — kotlinter, detekt and the full suite, forced to re-run.
- [ ] `make coverage-verify` — the line and branch floors in `build.gradle.kts`.
- [ ] `cd website/jev4k && uv run zensical build --clean` — must report "No issues found".
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
- [ ] Add a new `## [Unreleased]` section to `CHANGELOG.md` only if work continues before the next release.

### 4. Commit and push

- [ ] Commit the version bump and the release documents together.
- [ ] Push to `master` and wait for `ci.yml` (build, tests, Kover, Codecov) and `docs.yml` (Zensical build,
  Dokka, Pages deploy) to go green. Don't publish from a tree that CI hasn't accepted.

### 5. Publish to Maven Central

Signing and credentials, all outside the repository:

- [ ] `GPG_SIGNING_KEY_ID` is exported and `gpg --list-secret-keys "$GPG_SIGNING_KEY_ID"` finds the key.
- [ ] The macOS keychain holds the passphrase (service `gradle-signing-password`, account `gpg-signing`).
- [ ] `~/.gradle/gradle.properties` holds `mavenCentralUsername` and `mavenCentralPassword`.

Then:

- [ ] `make publish-maven-central`. It runs `publishAndReleaseToMavenCentral`, and
  `publishToMavenCentral(automaticRelease = true)` promotes the staging repository on its own, so there is
  nothing to click in the Central portal.
- [ ] Confirm the artifact is really there before tagging:

  ```bash
  curl -sI https://repo1.maven.org/maven2/com/pambrose/jev4k/<version>/jev4k-<version>.jar | head -1
  ```

  Central's indexes take a few minutes to catch up, so the jar may land before search does.
- [ ] Check the published set: jar, sources jar, Dokka HTML javadoc jar, POM, and a `.asc` for each.

### 6. Tag and create the GitHub release

- [ ] Tag with the bare version number, no `v` prefix:

  ```bash
  git tag <version> && git push origin <version>
  ```

- [ ] Create the release with the `v`-prefixed title and the notes for this version:

  ```bash
  gh release create <version> --title "v<version>" --notes-file <notes>
  ```

  `<notes>` is the body of this version's section of `RELEASE_NOTES.md`, ending with the **Full Changelog**
  link.

### 7. After

- [ ] The `[<version>]` link at the bottom of `CHANGELOG.md` now resolves.
- [ ] <https://pambrose.github.io/jev4k/> and <https://pambrose.github.io/jev4k/kdocs/> show the new version.
- [ ] The GitHub release and Maven Central badges in the README show the new number. Both are cached by
  shields.io for a few minutes.
- [ ] A dependency on the new coordinates resolves from a scratch project.
- [ ] Bump `gradle.properties` to the next planned version when development resumes.
