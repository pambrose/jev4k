# jev4k code review

Reviewed 2026-10-16, at commit `2f90671` on `master`: the 0.3.0 release, tagged `0.3.0` and published to Maven
Central the same day. The review covers the whole repository: library, tests, build, Makefile, CI, README,
documentation site, release documents and maintainer notes. The previous review (`docs/code-review-2026-09-27.md`)
closed all 85 of its issues, and none of them has regressed. The method is described at the end.

Every issue has a number. When one is fixed, tick its box in the summary and in the plan, and update the count
below in the same change.

**Status: 5 of 72 fixed.** No high, 1 medium, 44 low, 27 nit.

## Summary

### Medium

- [x] [#1](#issue-1) **Docs.** Three site pages say Choice `confidence` tells a close runner-up from a scattered tail;
  TypeSafe's formula uses only the top probability, so it can't

### Low

Images and Box

- [ ] [#2](#issue-2) Text shaped like a generated image reference fails a request locally, even one with no images
- [ ] [#3](#issue-3) The "attached but unreferenced" check is a substring test, so a reference inside another passes
- [ ] [#4](#issue-4) A generated reference whose bytes go in a hand-built `extraBody["assets"]` can't be sent at all
- [ ] [#5](#issue-5) Two images with the same chosen reference and identical bytes are rejected as "two different
  images"
- [ ] [#6](#issue-6) `JevImage.fromBase64` rejects any whitespace, even a trailing newline
- [ ] [#7](#issue-7) `JevImage.read` reads a file of any size into memory
- [ ] [#8](#issue-8) One out-of-range Box answer fails the whole response, paired Noul included
- [ ] [#9](#issue-9) The docs say a cache key must cover each image's bytes, but `JevImage` exposes none
- [ ] [#10](#issue-10) The per-attempt timeout covers an image upload, and the Images page doesn't say so

DSL and results

- [ ] [#11](#issue-11) `enumChoice<E>(id)` maps an undeclared option to an extra constant of `E` instead of rejecting
  it
- [ ] [#12](#issue-12) `QuestionSet.toJson()`, offered for cache keys, compares equal across Choice option orders
- [ ] [#13](#issue-13) Criteria values aren't type-checked: a number, boolean or null option or level is sent
- [ ] [#14](#issue-14) Reading `JevQuery.questions` before any question has registered throws

Client and configuration

- [ ] [#15](#issue-15) An oversized 2xx's `JevResponseValidationException` can't be Java-serialized
- [ ] [#16](#issue-16) The 16 MiB response cap doesn't apply to compressed responses on Curl, Darwin and Node
- [ ] [#17](#issue-17) `build()` accepts engine-owned headers; `Transfer-Encoding` or `Upgrade` breaks every call
- [ ] [#18](#issue-18) A non-ASCII character in the API key or a header passes `build()`
- [ ] [#19](#issue-19) Built "immutable" objects hand Java mutable collections
- [ ] [#20](#issue-20) Java's `RetryPolicy.with…` chain fails for some valid targets, depending on call order

Java interop and the KDocs

- [x] [#21](#issue-21) The 0.3.0 notes miss a Java source break: an exhaustive pattern `switch` over `Answer`
- [ ] [#22](#issue-22) The KDocs leave out every `JevQuery` builder (`noul`, `choice`, `score`, `box`, `attach`)
- [ ] [#23](#issue-23) The KDocs link JDK types to the Java 25 docs, not Java 17's
- [ ] [#24](#issue-24) KDoc source links point at `master`, so released javadoc jars land on the wrong lines

Tests

- [ ] [#25](#issue-25) The per-call retry tests vary only `maxRetries`
- [ ] [#26](#issue-26) No test checks the images header for a hand-built `extraBody["assets"]` without a Box
- [ ] [#27](#issue-27) The depth tests use only nested arrays and strings without brackets or escapes
- [ ] [#28](#issue-28) `CropExampleTest` can't catch swapped axes or a wrong origin
- [ ] [#29](#issue-29) `CioExclusionTest` pins only `JevConfigBuilder.build()`, not a client making a call

Build, CI and release

- [ ] [#30](#issue-30) The publish targets put the GPG secret key and passphrase in the environment of forked processes
- [ ] [#31](#issue-31) The tvOS and watchOS simulator tests can drop out of CI silently
- [ ] [#32](#issue-32) Codecov's coverage gate gates nothing, yet the release checklist counts it under `ci-ok`
- [ ] [#33](#issue-33) CI runs an unpinned `binfmt` image with `--privileged`
- [ ] [#34](#issue-34) iosX64 is published, but its tests run nowhere
- [ ] [#35](#issue-35) `refresh.sh` writes wherever the downloaded `llms.txt` says, `../` included
- [ ] [#36](#issue-36) The docs deploy installs whatever uv release is newest

Documentation and examples

- [x] [#37](#issue-37) The Java Box example asks on the default model, which can't answer Box yet
- [ ] [#38](#issue-38) Two image examples send each image twice, against the sentence above them
- [ ] [#39](#issue-39) The Images page leaves out the total-pixel minimum, so 256 × 256 looks valid
- [ ] [#40](#issue-40) The due-date example ignores a stated year
- [ ] [#41](#issue-41) The function-calling example has no route Choice, no escape and no confidence gate
- [ ] [#42](#issue-42) The parameterized-query example sends one request per policy, and can't be merged as written
- [ ] [#43](#issue-43) Two classification examples don't guard the 255-option limit
- [x] [#44](#issue-44) CHANGELOG and CLAUDE.md name the wrong exception for an over-deep `extraBody` field
- [ ] [#45](#issue-45) The jev-1.13 limits table lists a weakness TypeSafe dropped, and misses Choice option order

### Nit

- [ ] [#46](#issue-46) No test pins that jev4k's own engine gets the connect and socket timeouts
- [ ] [#47](#issue-47) An `ImageRequestTest` title promises call headers it doesn't test
- [ ] [#48](#issue-48) The tool-trace example asks Jev checks the page says belong in code
- [ ] [#49](#issue-49) Maintainer notes and `zensical.toml` still describe the state before 0.3.0
- [ ] [#50](#issue-50) Three places still say the retry defaults match "the official SDKs"
- [ ] [#51](#issue-51) Two unresolved KDoc links: `QuestionSet.toJson`'s `[images]` and kotlinx-io's `Path`
- [x] [#52](#issue-52) README says `confidence` is "Choice and Score only", below a Box row that has one
- [ ] [#53](#issue-53) The Installation page lists three `api` dependencies; kotlinx-io-core is a fourth
- [ ] [#54](#issue-54) "A missing file fails the object's first use" understates a permanent failure on the JVM
- [ ] [#55](#issue-55) `isPlatformConnectionError`'s common KDoc says the JVM needs no rule of its own
- [ ] [#56](#issue-56) `jevApiException` ignores the request-id header; `jevResult(String)` rejects a BOM
- [ ] [#57](#issue-57) Score level keys parse loosely, and an unasked Score's `levelCount` counts keys
- [ ] [#58](#issue-58) `QuestionSet` has no `toString`
- [ ] [#59](#issue-59) `cropToBox` truncates the edge but rounds the size, dropping a column inside the box
- [ ] [#60](#issue-60) Three places say Java can't call `RetryPolicy`'s constructor, then call `new RetryPolicy()`
- [ ] [#61](#issue-61) README and a KDoc say every config problem is reported at once; a missing key is reported alone
- [ ] [#62](#issue-62) `raw` is called "the response body exactly as received"; it's a parsed `JsonObject`
- [ ] [#63](#issue-63) The Score example shows confidence 0.54; TypeSafe's formula gives 0.55
- [ ] [#64](#issue-64) One run of trailing comments is misaligned
- [ ] [#65](#issue-65) Three places describe one real-CIO test; `ClientJvmTest` has five
- [ ] [#66](#issue-66) The README and the development page leave out the hot dog example
- [ ] [#67](#issue-67) Box and `attach` are missing from several summaries and tables
- [ ] [#68](#issue-68) Several places say Linux skips the Apple targets; it compiles all of their klibs
- [ ] [#69](#issue-69) The `docs.yml` jobs have no `timeout-minutes`
- [ ] [#70](#issue-70) CI's failure artifacts leave out the Docker Linux test logs
- [ ] [#71](#issue-71) The Codecov upload also sends `build/kover/coverage.txt`
- [ ] [#72](#issue-72) The release checklist's "Current state" table always lags a release behind

## Plan: order and grouping of fixes

Each group is one pull request, and its issues are listed in the order to fix them. Tick an issue's box here and in
the summary in the same change, and tick a group's box once all of its issues are ticked.

Four rules set the order:

1. Guidance readers act on comes first: wrong advice on the site, an example that fails today, and release notes
   that understate a break.
2. Changes to the same file are batched, so each file is opened once.
3. Every change to the public API is batched into one `make abi-update` on a Mac, for 0.4.0 (new public members make
   it a minor release).
4. A test lands with the fix it pins; the gaps that need only a test get a group of their own.

Groups 1 to 5 come first, in order. Groups 6 to 9 can land in any order, alongside or between the releases, and
group 10 is 0.4.0. Each group ends with `make tests`. Groups that touch the site also end with
`cd website/jev4k && uv run zensical build --clean --strict`, which must report "No issues found".

### Now, without a release

- [x] **1. Correct what readers act on now.** The confidence pages and the README line they share with #52, the Java
  Box example, and the 0.3.0 entries in RELEASE_NOTES and CHANGELOG (and the GitHub release text, which repeats the
  warning). None changes code. The site deploys only on a release or a manual `docs.yml` run, so dispatch one from
  `master` once these land, since `master` then holds nothing unreleased. #37 also stops failing on its own when
  `jev-latest` moves to 1.14, about 2026-10-21, but not for callers who pin `jev-1.13.0`.
  - [x] [#1](#issue-1) Say a Choice's confidence depends only on the top probability, on all three pages
  - [x] [#52](#issue-52) Qualify README's "Choice and Score only" for Box, in the same edit as #1
  - [x] [#37](#issue-37) Ask the Java Box example on `jev-preview`, and read the box only when the mug is visible
  - [x] [#21](#issue-21) Name the Java pattern `switch` break and its remedy in the notes, KDocs and release text
  - [x] [#44](#issue-44) Name `JevConfigException` for an over-deep `extraBody` field, in CHANGELOG and CLAUDE.md

### 0.3.1

- [ ] **2. Image checks in `ImageRequest.kt` and `JevImage.kt`.** #2 and #4 change the same lines of
  `imageRequestProblems`, so do them together. #3 and #5 add to `imageDefinitionProblems`. #6 and #7 are the
  factories. Add #26's header assertion while the image tests are open.
  - [ ] [#2](#issue-2) Recognize only references this process generated, and warn about chosen ones on the Images page
  - [ ] [#4](#issue-4) Count a hand-built `extraBody["assets"]`'s keys as attached
  - [ ] [#3](#issue-3) Reject an attached reference that occurs inside another
  - [ ] [#5](#issue-5) Merge images that share a reference and have equal bytes
  - [ ] [#6](#issue-6) Strip ASCII whitespace in `fromBase64`
  - [ ] [#7](#issue-7) Read at most 16 MiB + 1 bytes in `JevImage.read`, and carry the size as the image's problem
  - [ ] [#26](#issue-26) Assert the images header for a hand-built `extraBody["assets"]` without a Box
- [ ] **3. Results and the DSL.** `ResponseMapper.kt` (#8, #57), `JevResult.kt` (#11), `Questions.kt` (#13), the
  `JevQuery` KDoc (#14) and `Testing.kt` (#56). Each gets a common test. #11, #13 and #57 refuse what 0.3.0
  accepted, so name them in the 0.3.1 notes.
  - [ ] [#8](#issue-8) Move the Box range and order checks into `decodeBox`, so they fail only when the Box is read
  - [ ] [#11](#issue-11) Decode `enumChoice` against the declared options only
  - [ ] [#13](#issue-13) Accept only text, an object or an array as a criteria value (or `null` for a Choice option)
  - [ ] [#14](#issue-14) Say in the `JevQuery.questions` KDoc that a read before the first question throws
  - [ ] [#56](#issue-56) Read the request id from the headers, and strip a leading BOM, in `Testing.kt`
  - [ ] [#57](#issue-57) Accept only canonical Score level keys, and size an unasked Score by its highest key
- [ ] **4. Client and configuration hardening.** #17 and #18 are one pass through `headerProblem` and the key check
  in `JevConfig.kt`. #15 drops a field or the cause in `JevClient.oversized`. For #16, decide between a
  byte-counting bound and a docs change; #10 is a docs change on the Images page.
  - [ ] [#15](#issue-15) Drop the cause, or give `OversizedResponseException` serializable fields
  - [ ] [#17](#issue-17) Reject `Content-Length`, `Content-Type`, `Transfer-Encoding` and `Upgrade` in `headerProblem`
  - [ ] [#18](#issue-18) Reject non-ASCII characters in the key, header names and header values
  - [ ] [#16](#issue-16) Bound the bytes actually read, or say on the three pages that a compressed body isn't capped
  - [ ] [#10](#issue-10) Say on the Images page and in the image `ask` KDocs that `timeout` covers the upload
- [ ] **5. KDocs and publishing.** The javadoc jar ships with each release, and signing runs only when one is
  published, so this group lands before 0.3.1 goes out. One edit to the `dokka {}` block, then check
  `build/dokka/html`: builder pages for `JevQuery`, only `javase/17` links, tag-based source links and no
  `data-unresolved-link`. The publish targets that set #24's ref are the ones #30 changes. Confirm that signing
  still works locally, without uploading anything.
  - [ ] [#22](#issue-22) Document protected members as well as public ones
  - [ ] [#23](#issue-23) Set `jdkVersion` to the catalog's `jvm-target` for every source set
  - [ ] [#24](#issue-24) Pass the source-link ref in as a property, set by `docs.yml` and the publish targets
  - [ ] [#51](#issue-51) Link `[images][QuestionSet.images]`, and register kotlinx-io's API docs
  - [ ] [#30](#issue-30) Sign through gpg-agent instead of exporting the key into the environment

### Any time

- [ ] **6. Test gaps.** #59 changes `cropToBox` and #28 is its test, so do them together. The rest are test-only.
  - [ ] [#25](#issue-25) Vary the backoff, the statuses and `respectRetryAfter` in the per-call retry tests
  - [ ] [#27](#issue-27) Add depth cases for nested objects, brackets inside a string and an escaped quote
  - [ ] [#28](#issue-28) Crop with a box whose axes differ, and check the crop's origin
  - [ ] [#59](#issue-59) Compute both edges the same way in `cropToBox`: floor the start, ceil the end
  - [ ] [#29](#issue-29) Make a call, not just `build()`, through the CIO-less class loader
  - [ ] [#46](#issue-46) Share one `timeoutConfig` between the two call sites, and test it
  - [ ] [#47](#issue-47) Rename the images-header test, or add the call-header case it names
- [ ] **7. Build, CI and release hygiene.** `ci.yml` (#31, #33, #70, #71), `docs.yml` (#36, #69), Codecov and the
  checklist (#32, #72), `refresh.sh` (#35) and wording (#34, #68). Run `make zizmor` after the workflow edits.
  - [ ] [#31](#issue-31) Fail CI's Apple row when a simulator platform has no device
  - [ ] [#33](#issue-33) Pin the `binfmt` image and `LINUX_TEST_IMAGE` by digest
  - [ ] [#70](#issue-70) Upload `build/docker-linux-tests/` with the failure artifacts
  - [ ] [#71](#issue-71) Set `disable_search: true` on the Codecov upload
  - [ ] [#36](#issue-36) Pin uv's version on the setup-uv steps
  - [ ] [#69](#issue-69) Give the `docs` and `deploy` jobs a `timeout-minutes`
  - [ ] [#32](#issue-32) Require `codecov/project`, or mark it informational and say the Kover floors are the gate
  - [ ] [#72](#issue-72) Add a checklist step that records the release just made in "Current state"
  - [ ] [#35](#issue-35) Refuse a page path in `llms.txt` that could leave `jev-docs/pages`
  - [ ] [#34](#issue-34) Test iosX64 on an Intel runner, or say it's compiled but never tested
  - [ ] [#68](#issue-68) Say Linux compiles the Apple klibs, and recheck the reasons given for the macOS guards
- [ ] **8. Examples.** Code under `src/jvmTest/kotlin/website` and the pages that include it.
  - [ ] [#38](#issue-38) Place each image once in the two image examples
  - [ ] [#40](#issue-40) Use a stated year in the due-date example
  - [ ] [#41](#issue-41) Add a route Choice with a `none_of_these` function, and a confidence gate
  - [ ] [#42](#issue-42) Ask every policy check in one request, with distinct ids
  - [ ] [#43](#issue-43) Guard the 255-option limit in the two classification examples
  - [ ] [#48](#issue-48) Say the schema and exact-match checks run in code first
  - [ ] [#62](#issue-62) Call `raw` the parsed response body
  - [ ] [#63](#issue-63) Correct the Score example's confidence to 0.55
  - [ ] [#64](#issue-64) Align the trailing comments in `CompositeScoringExamples.kt`
- [ ] **9. Documentation text.** Text-only changes to the README, the site, KDoc, CLAUDE.md and the maintainer
  notes.
  - [ ] [#39](#issue-39) Add the total-pixel limits to the Images page
  - [ ] [#45](#issue-45) Replace the dropped weakness with Choice option order in the jev-1.13 table
  - [ ] [#49](#issue-49) Bring `jev-api.md`, the API check, the image spec and plan, `zensical.toml` and CLAUDE.md
    up to 0.3.0
  - [ ] [#50](#issue-50) Say the retry defaults follow the JS SDK
  - [ ] [#53](#issue-53) List kotlinx-io-core among the `api` dependencies
  - [ ] [#54](#issue-54) Say a failed `object` stays unusable on the JVM
  - [ ] [#55](#issue-55) Say what the JVM's `isPlatformConnectionError` adds
  - [ ] [#60](#issue-60) Say "the full constructor" where Java can't call `RetryPolicy`'s
  - [ ] [#61](#issue-61) Qualify "every problem at once" for a missing key
  - [ ] [#65](#issue-65) Describe all five of `ClientJvmTest`'s real-CIO cases
  - [ ] [#66](#issue-66) Mention `HotDogExample` and `make hotdog-example`
  - [ ] [#67](#issue-67) Add Box and `attach` to the summaries and tables, marked experimental

### 0.4.0

- [ ] **10. Public API, one `make abi-update`.** Read-only wrappers for the built collections (#19), a bytes copy or
  digest on `JevImage` with the reworded cache-key advice (#9 and #12 share the `toJson()` KDoc and one sentence on
  the Images page), `withBackoffMillis` (#20) and `QuestionSet.toString()` (#58). #19 makes mutators that Java can
  call today throw, so it waits for the minor release too. Update `JavaInterop.java` once for all of them.
  - [ ] [#19](#issue-19) Store the built collections behind read-only wrappers
  - [ ] [#9](#issue-9) Expose a bytes copy or digest on `JevImage`, or narrow the cache-key advice
  - [ ] [#12](#issue-12) Say to key a cache on `toJson().toString()`, whose text keeps the option order
  - [ ] [#20](#issue-20) Add `withBackoffMillis(initial, max)`
  - [ ] [#58](#issue-58) Add `QuestionSet.toString()`

## Issues

### Medium

<a id="issue-1"></a>

#### 1. Three pages say Choice `confidence` tells a close runner-up from a scattered tail; TypeSafe's formula can't

**Medium** · Docs · small · `website/jev4k/docs/results/confidence.md:18-19`,
`website/jev4k/docs/questions/choice.md:49-51`, `website/jev4k/docs/concepts.md:70-72`

**What's wrong.** Three pages make the same claim:
- `results/confidence.md:18-19`: "A winner at 0.45 with a runner-up at 0.44 and a winner at 0.45 with the rest
  scattered thinly are different situations, and `confidence` is what separates them."
- `questions/choice.md:49-51`: confidence "describes the shape of the whole distribution: 0.45 with a runner-up at
  0.44 is far less decisive than 0.45 with the rest scattered thinly."
- `concepts.md:70-72`: the same contrast.

TypeSafe's confidence page gives the Choice formula as `(p_max − 1/n) / (1 − 1/n)` and says "Only the top probability
counts, so (0.6, 0.3, 0.1) and (0.6, 0.2, 0.2) both have confidence 0.4" (`jev-docs/pages/confidence.md`, "How
confidence is calculated"). For a near-tie it suggests a different measure, the top-to-second ratio. jev4k's own notes
were corrected on 2026-10-15 (`jev-docs/jev-api.md` §6, `docs/api-check-2026-10-15.md:164`); the site pages weren't.
The claim came from an older TypeSafe cookbook, which `jev-docs/cookbooks.md:2026-2028` still quotes without a caveat.

**Impact.** (0.45, 0.44, 0.11) and (0.45, 0.275, 0.275) both have confidence 0.175. For a fixed option count, Choice
confidence is a linear rescaling of the winner's probability. A reader who gates on `confidence` to catch near-ties,
as these pages teach, gets no signal for exactly that case. (Score confidence does depend on the spread, so the claim
holds there.)

**Fix.**
- On all three pages, say that a Choice's confidence depends only on the top probability and the number of options,
  and that a near-tie shows in the top two entries of `ranked()` (TypeSafe's top-to-second ratio). Keep the "not the
  winner's probability" point for Score.
- Tighten the looser "how concentrated" wording while there: the `ChoiceAnswer` KDoc (`Answers.kt:45-47`),
  `README.md:165`, `choice.md:8-9`, `confidence.md:14-16` and the comment at `ConceptsExamples.kt:31`.
- Add a caveat to `jev-docs/cookbooks.md:2026-2028`, since CLAUDE.md sends contributors there.
- Optionally add a `ChoiceAnswer` helper for the top-to-second margin.

### Low

<a id="issue-2"></a>

#### 2. Text shaped like a generated image reference fails a request locally, even one with no images

**Low** · Images · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ImageRequest.kt:72-77`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:94`, `website/jev4k/docs/queries/images.md:69-71`

**What's wrong.** `evaluate` runs `imageRequestProblems` on every request, and it searches every string in the state
and the questions for anything matching `GENERATED_REFERENCE` (`jev4k-image-` and a lowercase UUID). Any match that
isn't attached is reported as "image '…' is placed in the request but not attached; attach() it". A text-only request
(no images, no opt-in) whose state reads "Customer wrote: jev4k-image-00000000-0000-0000-0000-000000000000" fails with
`JevValidationException` and is never sent (probed against the 0.3.0 jar).

**Impact.** 0.3.0 added a failure path for callers who never opted into images. The likely route is accidental: an
image app that stores earlier states or answers holding generated references (a Score `legend` echoes them, and so do
jev4k's own error messages) and later sends that text without the image. A user who types the pattern can also make
their own item fail, though untrusted input can already do that in other ways (a state nested 65 levels fails the
same way, and an oversized one gets a 400).

**Fix.**
- Recognize only references this process generated, for example by deriving part of the UUID from a per-process
  random key, so text arriving from outside doesn't match. Or offer an escape hatch.
- Don't narrow the check to requests that attach images or ask a Box: forgetting `attach()` entirely is the main
  mistake it exists to catch, and the API would otherwise read the UUID as plain text.
- On the Images page, say that only generated references are safe next to untrusted text: a user can type a chosen
  reference and have the image inserted again.
- Do this together with #4, which changes the same lines.

<a id="issue-3"></a>

#### 3. The "attached but unreferenced" check is a substring test, so a reference inside another passes

**Low** · Images · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ImageRequest.kt:48-53`, `:63-71`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt:195-216`

**What's wrong.** Each attached image is checked with `content.strings.none { image.reference in it }`. The API's rule
is stricter: "a string that is exactly a reference becomes that image", and longer strings are replaced "left to
right, and matches never overlap" (`jev-docs/pages/images.md:95-96`). Every asset must be referenced, or the API
answers 400. The KDoc and CLAUDE.md say references are "matched as the API does".

**Impact.** Both of these probes passed the local check and were sent:
- With chosen references `img1` and `img10` and only the text "Compare img10 with the shelf", `img1` counts as
  referenced. Whichever reference the API matches at that position, the other goes unreferenced and the API answers
  400. Where the tie-break matters, shortest-first would silently put the wrong image in place of `img10`.
- With `dog` and `dog-poster` and a state `{"photo": "dog-poster"}`, the whole value is `dog-poster`, so `dog` is
  unreferenced.

A chosen reference such as `image` sits inside every generated reference, so mixing chosen and generated references
can trigger this too. Delimited references (`<img1>`, `<img10>`), which the site recommends, can't nest.

**Fix.** In `imageDefinitionProblems`, reject an attached reference that occurs inside another attached one: "image
reference 'img1' occurs inside 'img10'; the API can't tell them apart". Narrow the KDoc and CLAUDE.md wording to match.

<a id="issue-4"></a>

#### 4. A generated reference whose bytes go in a hand-built `extraBody["assets"]` can't be sent at all

**Low** · Images · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ImageRequest.kt:72-77`, `:82-88`

**What's wrong.** The Box check trusts a hand-built `extraBody["assets"]` ("supplies images jev4k can't see", line 82),
but the placed-but-not-attached check three lines above it doesn't. The probe reproduces a loop:
- `"Photo: $img"` with the bytes in `extraBody["assets"][img.reference]` fails: "placed in the request but not
  attached".
- Adding `attach(img)`, as the message says, fails: "extraBody can't set 'assets' when the request attaches images".
- The same request with a chosen reference (`<p>`) is sent.

**Impact.** The spec (§1) and CLAUDE.md keep `extraBody["assets"]` as the route for base64 by hand. A `JevImage` with a
generated reference can't take it, and neither message suggests the workaround.

**Fix.** When `extraBody["assets"]` is a `JsonObject`, add its keys to `attached` before filtering. Do it with #2.

<a id="issue-5"></a>

#### 5. Two images with the same chosen reference and identical bytes are rejected as "two different images"

**Low** · Images · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt:211-215`,
`src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:110`

**What's wrong.** `QuestionSet.images` deduplicates by identity (`images.distinct()`, and `JevImage` doesn't override
`equals`), and any reference still shared is an error: "two different images share the reference '…'". Identity
dedup is documented ("each instance once"), but its consequence isn't.

**Impact.** Two `JevQuery` objects each attach a shared picture from a helper,
`fun logo() = JevImage.read(Path("logo.png"), "<logo>")`, which returns a new instance on each call. A request that
`include`s both fails, though the bytes match and the API needs only one asset. The message's "different" is wrong.

**Fix.** Merge images with the same reference whose bytes are `contentEquals`, keeping the first, and report only a
real conflict. At the least, reword the message to "two image instances" and say to share one instance.

<a id="issue-6"></a>

#### 6. `JevImage.fromBase64` rejects any whitespace, even a trailing newline

**Low** · Images · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt:68-69`, `:79-93`, `:173-174`,
`src/commonTest/kotlin/com/pambrose/jev4k/JevImageTest.kt:43-44`

**What's wrong.** The decoder already accepts missing padding, because "the bytes are decoded here and sent as they
are". Whitespace is harmless for the same reason, yet a trailing `\n`, a trailing `\r\n` or a line break mid-text each
give the `BAD_BASE64` problem (probed). The rule the message cites, "no whitespace", is the API's rule for
`data_base64` on the wire, which jev4k never sends. Python's `Image.from_base64` documents that whitespace is ignored
(`jev-docs/pages/sdk/python/api/types/assets.md:313`).

**Impact.** `JevImage.fromBase64(File("photo.b64").readText())` fails for a file written by the `base64` or
`openssl base64` tools (a trailing newline, and wrapping at 76 or 64 columns), and so does Java's MIME encoder output.

**Fix.** Strip ASCII whitespace before decoding, and keep rejecting a `data:` prefix and the URL-safe alphabet. Drop
"with no whitespace" from `BAD_BASE64` and update the test that pins it.

<a id="issue-7"></a>

#### 7. `JevImage.read` reads a file of any size into memory

**Low** · Images · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt:50-52`, `:95-101`,
`src/jvmMain/kotlin/com/pambrose/jev4k/JevImage.jvm.kt:11-22`

**What's wrong.** All three `read` overloads read the whole file before any check (kotlinx-io `readByteArray()`,
`Files.readAllBytes`, `File.readBytes`). The API rejects any image over 16 MiB.

**Impact.** A user-chosen or mistaken path to a large video, or to `/dev/zero`, ends in an `OutOfMemoryError`, and a
FIFO with no writer blocks, instead of the carried problem the factories promise for bad content. Short of that, jev4k
uploads the whole file before the API's 413.

**Fix.** Read at most 16 MiB + 1 bytes (or check `Files.size` first for a regular file), and carry "is larger than
the API's 16 MiB limit" as the image's problem. Then change the KDoc's "the API's size and pixel limits are left to
the API", since jev4k would enforce one of them.

<a id="issue-8"></a>

#### 8. One out-of-range Box answer fails the whole response, paired Noul included

**Low** · Results · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ResponseMapper.kt:43`, `:176-202`,
`README.md:337-340`, `src/commonTest/kotlin/com/pambrose/jev4k/BoxTest.kt:57-62`

**What's wrong.** `mapSystemOne` maps every answer when the response is parsed, and `boxAnswer` refuses an edge or
confidence outside 0..1, and `left >= right` or `top >= bottom`. No other answer type is range-checked (a Noul of 1.2
is accepted). The same file chose to degrade rather than fail for metadata and for null answers.

**Impact.** Probed: a `right` of 1.0000000000000002, or a zero-width box (`left == right`), throws
`JevResponseValidationException` from the call, so the paired `mug_visible` Noul, which would have said "no mug",
can't be read. TypeSafe's Box page states the same invariant jev4k enforces (`jev-docs/pages/primitives/box.md:73`),
and nothing shows the server breaking it, so this is hardening. The likelier trigger is a tiny target with edges
rounded to equal values (TypeSafe's examples use two decimals). Box is the type TypeSafe calls "Dreamer-ready", and
the docs pair it with a Noul because it is unreliable.

**Fix.** Keep the presence and finite-number checks at parse time, and move the range and order checks into
`decodeBox`, so they fail only when the Box is read. Add a test for equal edges either way.

<a id="issue-9"></a>

#### 9. The docs say a cache key must cover each image's bytes, but `JevImage` exposes none

**Low** · Images · small · `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:133-136`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevImage.kt:59-63`, `website/jev4k/docs/queries/images.md:112-113`

**What's wrong.** `QuestionSet.toJson()`'s KDoc says "a cache key for a set with images must cover each image's bytes
too", and the Images page repeats it. A `JevImage`'s only public member is `reference`; `bytes` and `format` are
`internal` (`api/jev4k.api:283-293`).

**Impact.** A caching `JevApi` decorator around `evaluate`, the wrapping pattern `withOptions` uses, receives only the
`QuestionSet` and can't follow the advice. With a chosen reference reused per request
(`JevImage.of(upload, "<photo>")`, the page's "Your own references" style), different photos give the same
`(model, state, toJson())` key, and the cache serves the first photo's answer. Generated references go the other way:
every instance gets a fresh UUID, so the cache never hits for identical bytes.

**Fix.** Expose a copy of the bytes or a content digest on `JevImage` (under the opt-in), or narrow the advice to "the
code that creates the images must add their bytes to the key". Say that generated references make every key unique
per instance. Fix it with #12, in the same KDoc and sentence.

<a id="issue-10"></a>

#### 10. The per-attempt timeout covers an image upload, and the Images page doesn't say so

**Low** · Client / Docs · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:104-114`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevApi.kt:87-113`, `website/jev4k/docs/queries/images.md`

**What's wrong.** `timeout` (10 s by default) is the per-attempt request timeout, so it covers writing the body as
well as the answer. `retryOnTimeout` defaults to true, and the multipart body is rebuilt for each attempt. Before
0.3.0 every request body was small JSON; the API now takes up to 32 MiB of images. Neither the Images page nor the
image `ask` KDocs mention timeouts. This matches the JS SDK's per-attempt rule, so it is a guidance gap, not a
conformance one.

**Impact.** Two maximum-size photographic PNGs (5 to 8 MiB each), or 2.5 MiB on a 2 Mbit/s uplink, can't upload
inside 10 s. Each attempt times out, the body is uploaded three times over about 31.5 s, and the caller gets
`JevTimeoutException` though no attempt could have finished. jev4k leaves pixel limits to the API, so an oversized
full-resolution photo is uploaded in full too, and on a slow link times out three times instead of getting a 400.

**Fix.** On the Images page and in the image `ask` KDocs, say that `timeout` covers the upload, and show
`jev.withOptions(JevCallOptions { timeout = 60.seconds })` for image calls. Optionally suggest `retryOnTimeout = false`
for large uploads.

<a id="issue-11"></a>

#### 11. `enumChoice<E>(id)` maps an undeclared option to an extra constant of `E` instead of rejecting it

**Low** · Results · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevResult.kt:70-74`, `:83-90`,
`src/commonMain/kotlin/com/pambrose/jev4k/Answers.kt:186-195`, `website/jev4k/docs/queries/enums.md:69-72`

**What's wrong.** The KDoc promises that "an option the server returns that the question never declared is a
[JevResponseValidationException]", and `enums.md:71` says the same. `enumChoiceOf` builds `byKey` from every constant
of `E`, and only requires `E` to cover the declared options; a superset enum is endorsed (`DslTest`'s `MoreDepts`). So
an undeclared option that matches an extra constant is mapped, not rejected. Probe: a question declaring `billing` and
`technical`, read as an enum that also has `SALES`, with the server answering `"sales"`, returned
`ChoiceAnswer(choice=SALES, probabilities={BILLING=0.1, TECHNICAL=0.2, SALES=0.7})`.

**Impact.** Code that reads a Choice by id as a broader shared enum routes to an option the question never offered,
with no error. It needs the server to break its contract, which nobody has seen, so it is low. The typed-handle path
is exact.

**Fix.** Decode against `byKey.filterKeys { it in declared }`, keeping the superset only for the coverage check. Add a
test with `MoreDepts` and a `"legal"` answer.

<a id="issue-12"></a>

#### 12. `QuestionSet.toJson()`, offered for cache keys, compares equal across Choice option orders

**Low** · DSL / Docs · small · `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:133-138`,
`website/jev4k/docs/queries/images.md:112-113`

**What's wrong.** The KDoc offers `toJson()` "e.g. for logging or cache keys". The `JsonObject` it returns compares as
a `Map`, which ignores key order: for `options("a", "b")` and `options("b", "a")`, the two `toJson()` values are equal
and have equal hash codes, while their `toString()`s differ (probed). TypeSafe documents that option order changes
jev-1.13's answers, and recommends reordering the options as a check (`model-jaggedness/jev-1.13.md:112-116`).

**Impact.** A cache keyed on `(model, state, toJson())` returns the cached answer for the reordered Choice, so the
check TypeSafe recommends silently passes without a second call.

**Fix.** Say in the KDoc and on the Images page to key on `toJson().toString()`, whose text keeps the order. Fix it
with #9.

<a id="issue-13"></a>

#### 13. Criteria values aren't type-checked: a number, boolean or null option or level is sent

**Low** · DSL · small · `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:149-158`, `:182-214`

**What's wrong.** jev4k checks the type of the state and of the instructions, but each criteria value only goes
through `sendProblem` (depth and non-finite numbers). The API reference types Noul `true`/`false` as
`string | object | array`, Choice options as `string | object | array | null`, and Score levels as
`array<string | object | array>` (`jev-docs/pages/api.md:100-104`, `:137-141`, `:175`), and both SDKs' content types
exclude bare numbers and booleans. The probe captured these bodies being sent: `"criteria":[1,2,3,4,5]`,
`"criteria":[null,"High"]`, `"criteria":{"a":true,"b":3}` and a Noul's `"criteria":{"true":1}`.

**Impact.** `score("rating", "How many stars?") { (1..5).forEach { level(jsonOf(it)) } }` is a natural way to write
star levels, and it passes local validation. Assuming the server enforces its types (nothing in `jev-docs` shows the
response), the caller gets a 422 at run time instead of the local problem list. CLAUDE.md's Validation list doesn't
claim this check, so it's a gap rather than a broken promise.

**Fix.** In `validate(id, question)`, next to `isEmptyEntry`, reject a non-string primitive in a Noul definition or a
Choice option, and a non-string primitive or `null` in a Score level ("level 0 must be text, a JSON object or a JSON
array").

<a id="issue-14"></a>

#### 14. Reading `JevQuery.questions` before any question has registered throws

**Low** · DSL · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt:44-45`, `:58-64`,
`src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:169`

**What's wrong.** The KDoc says `questions` "can be read at any time. A read before every property is initialized,
from an `init` block or a base class, sees only the questions declared so far." A read before any question has
registered builds an empty `QuestionSet`, which fails validation ("A request needs at least one question"). A base
class's `init` always runs before the subclass's delegates, so a base class with no questions of its own always hits
this case.

**Impact.** `abstract class AuditedQuery : JevQuery() { init { registry += this to questions.ids } }` makes every
subclass `object` fail with `ExceptionInInitializerError`, the failure the carried-problems design exists to prevent
(probed). `DslTest`'s `EarlyRead` reads only after one question has registered.

**Fix.** Narrow the KDoc (a read before the first question throws), or have the getter return an unvalidated empty
view while nothing has registered. The KDoc change is the smaller one.

<a id="issue-15"></a>

#### 15. An oversized 2xx's `JevResponseValidationException` can't be Java-serialized

**Low** · Client (JVM) · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:229-238`,
`src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:27-31`,
`src/commonMain/kotlin/com/pambrose/jev4k/Errors.kt:35`

**What's wrong.** For a 2xx that declares a body over 16 MiB, `oversized()` passes the internal
`OversizedResponseException` as the cause (`cause = e`, line 237). That exception holds Ktor's `Headers` and
`HttpStatusCode`, neither of which is `Serializable`. `Errors.kt:35` says `JevApiException` is serializable "provided
any `headers` map passed in is", and CLAUDE.md says it "holds only `Serializable` state".

**Impact.** Reproduced on real CIO: serializing the exception throws `NotSerializableException:
io.ktor.http.HeadersImpl`. Anything that Java-serializes exceptions (RMI, JMS, session or cache replication, a job
framework that stores failures) then fails with an error that hides the original one. This is the only exception path
with the problem: the non-2xx oversized path passes no cause, and a "body is not JSON" failure serializes fine.

**Fix.** Drop `cause = e`, since the detail already says why, or give `OversizedResponseException` an `Int` status and
a `Map<String, List<String>>`. Add an oversized case to `ErrorsJvmTest`.

<a id="issue-16"></a>

#### 16. The 16 MiB response cap doesn't apply to compressed responses on Curl, Darwin and Node

**Low** · Client · medium · `src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:47-61`,
`README.md:489-490`, `website/jev4k/docs/client/configuration.md:98-99`, `website/jev4k/docs/client/errors.md:56-58`

**What's wrong.** `limitBodySize` checks only the declared `Content-Length`. Three engine families ask for compression
themselves, decompress the body, and then remove `Content-Length` with `Content-Encoding` before jev4k's phase runs:
- Curl sets `CURLOPT_ACCEPT_ENCODING ""` and calls `dropCompressionHeaders` (Ktor 3.6.0 `CurlMultiApiHandler.kt:112`,
  `CurlClientEngine.kt:45`).
- Darwin's `readHeaders` calls it (`DarwinResponseUtils.kt:17`).
- The Js and Wasm engines call it on Node (`JsClientEngine.kt:157-160`, `WasmJsClientEngine.kt:173-176`).

Probes on this Mac: NSURLSession and Node 26's fetch both sent `Accept-Encoding: gzip, deflate` and turned a 20 KiB
gzip body into 20 MiB. Only CIO (the JVM default) and WinHttp don't request compression, so only they keep the cap.

**Impact.** On Linux, Apple platforms and Node, a broken or hostile server or proxy can send a small compressed body
that inflates to gigabytes in memory, bounded only by the timeout. The README, the configuration page and the errors
page say "A response declaring a body over 16 MiB is refused before the body is read", which holds only on the JVM and
Windows. TypeSafe's docs don't say whether the API compresses responses; real Jev bodies are small, so this matters
only for a misbehaving server.

**Fix.** Bound the bytes actually read: wrap the response content in a channel that fails past `MAX_RESPONSE_BYTES`,
which also covers chunked bodies. Sending `Accept-Encoding: identity` doesn't help: Node and NSURLSession still decode
an unsolicited gzip body, and Ktor still drops the length (probed). If the bound isn't worth it, say on the three pages
that a compressed response on those engines counts as one sent without a length.

<a id="issue-17"></a>

#### 17. `build()` accepts engine-owned headers; `Transfer-Encoding` or `Upgrade` breaks every call

**Low** · Config · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:263-274`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevCallOptions.kt:91`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:178-179`, `:215`

**What's wrong.** `headerProblem` checks only characters, and `setHeaders` copies every configured or per-call header
onto the request. Each of these was probed:
- `Transfer-Encoding` or `Upgrade` builds, then every call throws Ktor's `UnsafeHeaderException` (an
  `IllegalArgumentException`), unwrapped and not a `JevException`.
- A lowercase `transfer-encoding` passes Ktor's case-sensitive check, and CIO then sends `Content-Length` and
  `transfer-encoding: chunked` together, a framing conflict a server may reject (RFC 9112 §6.3).
- `Content-Length: 5` replaces the real length: CIO sent a 92-byte body declaring 5 bytes. Spelled `content-length`,
  it went out twice.
- `Content-Type` is dropped without a word; the body keeps `application/json`.

**Impact.** The documented promise that `build()` checks "the settings Ktor would otherwise reject on every request"
(CLAUDE.md, `configuration.md:77`) is broken by the first two. Nobody sets these headers on purpose, so it is
hardening.

**Fix.** In `headerProblem`, shared by both builders, reject `Content-Length`, `Content-Type`, `Transfer-Encoding` and
`Upgrade` case-insensitively, saying that jev4k or the engine sets them. Add cases to `ConfigTest` and
`CallOptionsTest`.

<a id="issue-18"></a>

#### 18. A non-ASCII character in the API key or a header passes `build()`

**Low** · Config · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:118-120`, `:185-186`, `:263-274`,
`src/webMain/kotlin/com/pambrose/jev4k/internal/Platform.web.kt:22-23`

**What's wrong.** `build()` rejects only control characters in the key, and only what Ktor's ASCII-only checks reject
in headers. `trim()` doesn't remove U+FEFF or U+200B. A key `"﻿secret​"`, a header named `X-Équipe` and a
header value holding U+0100 all passed `build()` (probed).

**Impact.**
- On the JVM, CIO sends the key as UTF-8 bytes, and the real endpoint answers 401 for a key that prints the same as
  the right one. A key read from a file saved with a byte-order mark is the likely route, and the KDoc invites reading
  one from a file.
- On Node, fetch throws `TypeError` for such a value or name, Ktor reports every rejected fetch as `Error("Fail to
  fetch")`, and the web actual counts that as a connection error. So the call is retried and ends as
  `JevConnectionException`, which blames the network (traced through the code, not run on Node).
- A value character from U+0080 to U+00FF passes fetch as Latin-1 but goes out as UTF-8 on CIO, so engines disagree.

The Python SDK rejects a non-ASCII key up front for this reason (`jev-docs/sdks.md:110-111`).

**Fix.** Reject any key character outside visible ASCII without echoing the key. In `headerProblem`, reject header
names that aren't RFC 9110 tokens and values above U+007E. Optionally strip a leading U+FEFF in `setting()`.

<a id="issue-19"></a>

#### 19. Built "immutable" objects hand Java mutable collections

**Low** · Java interop · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevCallOptions.kt:21-22`, `:98`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:47`, `:109`, `:113`, `:198`,
`src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:103`, `:110`, `:122`, `:253`, `:264`,
`src/commonMain/kotlin/com/pambrose/jev4k/Builders.kt:211-212`

**What's wrong.** The defensive copies are made with `toMap()`, `toSet()`, `toList()`, `map` and `distinct()`. With two
or more entries these return a plain `LinkedHashMap`, `LinkedHashSet` or `ArrayList`, which Java (and Groovy, Scala or
any other JVM language) sees as an ordinary mutable `java.util` collection. With one entry the same call throws
`UnsupportedOperationException`, so behavior depends on the entry count. Run against the 0.3.0 jar:
- A built `JevCallOptions` with two headers accepted `getHeaders().put("X-Bad", "line1\nline2")`, skipping header
  validation. Its KDoc says options "can't change afterwards, so one instance can be shared". The call then threw
  Ktor's raw `IllegalHeaderValueException`, which quotes the value: the failure the previous review's #9 closed.
- A built client's `getRetry().getRetryStatuses().add(400)` succeeded, changing a live client's retry rule, which
  the previous review's #30 was meant to rule out.
- `RetryPolicy`'s default status set is shared by `RetryPolicy.NONE` and every copy of it, so a Java
  `NONE.getRetryStatuses().add(...)` changes every policy built from it later.
- `QuestionSet.ids` is an `ArrayList` at every size, and `iterator().remove()` leaves the set inconsistent (`size` 1,
  `ids` still `[a, b]`). `ChoiceQuestion.options` and `ScoreQuestion.levels` can be changed after validation.

From Kotlin this takes a downcast that breaks the read-only contract, so it isn't a Kotlin bug.

**Fix.** Store snapshots behind read-only wrappers, such as a private `class ReadOnlyMap<K, V>(m: Map<K, V>) :
Map<K, V> by m`. kotlinc then generates mutators that throw for Java, as `ModelList` already gets through delegation.
Apply them to every location above, and give `QuestionSet` an iterator that can't remove.

<a id="issue-20"></a>

#### 20. Java's `RetryPolicy.with…` chain fails for some valid targets, depending on call order

**Low** · Java interop · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:38-40`, `:57`, `:81`, `:84`

**What's wrong.** Java can't call `RetryPolicy`'s full constructor or `copy`, so the KDoc sends it to the `with…`
methods, one setting at a time. Each one copies and re-runs `init`, which checks `maxBackoff >= initialBackoff`
against the other setting's current value.

**Impact.** `new RetryPolicy().withMaxBackoffMillis(250).withInitialBackoffMillis(100)` throws at its first step
("maxBackoff (250ms) must be >= initialBackoff (500ms)"), and so does
`withInitialBackoffMillis(10_000).withMaxBackoffMillis(60_000)`. The reversed orders work, and the message names both
values, so the workaround is easy to find. Kotlin callers pass both to the constructor and never hit it.

**Fix.** Add `withBackoffMillis(initial, max)`, or say in the KDoc which order to use when both move past each other.

<a id="issue-21"></a>

#### 21. The 0.3.0 notes miss a Java source break: an exhaustive pattern `switch` over `Answer`

**Low** · Java interop / Release · small · `RELEASE_NOTES.md:8-11`, `CHANGELOG.md:12-13`, `CHANGELOG.md:41-42`,
`src/commonMain/kotlin/com/pambrose/jev4k/Answers.kt:10-13`,
`src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:32-33`

**What's wrong.** The release notes' warning says the new subtypes break only "a `when` over either that lists every
subtype and has no `else` branch", and "Nothing else breaks: other code compiles unchanged". The CHANGELOG and the
`Answer`/`Question` KDocs say the same. kotlinc writes a `PermittedSubclasses` attribute for the sealed interfaces, so
javac 21+ treats them as sealed. A Java pattern `switch` over `Answer` (four cases) or `Question` (three cases) with no
`default` compiles against `jev4k-jvm-0.2.2.jar` and fails against 0.3.0: "the switch expression does not cover all
possible input values" (reproduced with `javac --release 21`).

**Impact.** A Java 21 caller who reads the upgrade notes is told nothing breaks, and their build fails. The notes'
remedy goes through the Kotlin opt-in, which doesn't apply to Java. A class compiled against 0.2.2 throws
`MatchException` only when it meets a `BoxAnswer`, which takes asking Box questions, so "the binary API only grows"
still holds in practice.

**Fix.** In the release notes' warning, the CHANGELOG's summary and Changed entry, the `Answer`/`Question` KDocs and
the GitHub release text, say "a Kotlin `when` or a Java pattern `switch`", and give the Java remedy: a `default ->`
branch, or a `case BoxAnswer`, which Java can name without any opt-in.

<a id="issue-22"></a>

#### 22. The KDocs leave out every `JevQuery` builder (`noul`, `choice`, `score`, `box`, `attach`)

**Low** · Docs (Dokka) · small · `build.gradle.kts:319`, `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt:57`,
`:76-138`

**What's wrong.** Every Dokka source set gets `documentedVisibilities(VisibilityModifier.Public)`, and the typed
layer's builders are `protected`, since subclasses call them. They are public API (`api/jev4k.api:341-357` lists them
as `protected final fun`). The generated `-jev-query/` folder, locally and on jev4k.com, holds only the class page, the
constructor and `questions`, and the `questions` KDoc's `[attach]` renders as `data-unresolved-link`. CLAUDE.md says
the KDocs cover the public API.

**Impact.** On jev4k.com/kdocs, a reader looking up how to declare a question in a `JevQuery` finds none of the
builders: no signatures, nothing on the instructions-then-`id` order (the class KDoc warns it's the reverse of
`QueryBuilder`'s), and none of the KDoc written for 0.3.0's `box` and `attach`. That's half of the DSL.

**Fix.** `documentedVisibilities(VisibilityModifier.Public, VisibilityModifier.Protected)`. `JevQuery.kt` is the only
main source file with protected members, so nothing else changes. Check for `noul.html`, `choice.html`, `score.html`,
`box.html` and `attach.html` in the output.

<a id="issue-23"></a>

#### 23. The KDocs link JDK types to the Java 25 docs, not Java 17's

**Low** · Docs (Dokka) · small · `build.gradle.kts:349-352`, `build.gradle.kts:315-340`

**What's wrong.** `dokkaSourceSets.named("jvmMain") { jdkVersion = jvmTargetVersion.toInt() }` is there to "link JDK
types to the docs for the oldest JDK this library supports", and CLAUDE.md repeats it. Every JDK link in
`build/dokka/html`, in the published 0.3.0 javadoc jar and on jev4k.com (`File`, `Path`, `InterruptedException`) goes
to `javase/25`. The likely cause is dokka-base's `DefaultLocationProvider`: it builds one package index from every
source set's links, in sorted source-set order, and the first match wins regardless of the declaration's own source
set. `appleMain` and `commonMain` sort before `jvmMain` and carry the toolchain's 25.

**Impact.** A Java 17 consumer who follows a JDK link lands on Java 25's page, which can list members 17 lacks (Java
25's `Path` has `resolve(String, String...)`). The setting does nothing.

**Fix.** Set `jdkVersion = jvmTargetVersion.toInt()` in the script's existing `dokkaSourceSets.configureEach {}`, for
every source set. Then `grep -rhoE 'javase/[0-9]+' build/dokka/html | sort -u` should print only `javase/17`.

<a id="issue-24"></a>

#### 24. KDoc source links point at `master`, so released javadoc jars land on the wrong lines

**Low** · Docs (Dokka) / Release · small · `build.gradle.kts:326-331`, CLAUDE.md (Dokka and Publishing sections)

**What's wrong.** `remoteUrl("$repoUrl/tree/master/src")` with `remoteLineSuffix = "#L"`. CLAUDE.md records the
choice, but the failure it causes has already happened: the 0.2.2 javadoc jar on Maven Central, which can't be
replaced, has 252 distinct source anchors, and 67 of them now open the wrong line. `Usage` (`Answers.kt#L102`) opens
on `val right: Double,` in `BoxAnswer`. The same will happen to 0.3.0's jars and to jev4k.com between releases.

**Impact.** "Source" links in released docs drift with every edit on `master`, and give a 404 once a file moves. The
site deploys only on a release so that it matches the released version; the source links undo that.

**Fix.** Pass the ref in explicitly: a property such as `-Pjev4k.sourceRef=<tag>`, set by `docs.yml` on a release
(from `github.ref_name`) and by the publish targets, defaulting to `master`. A plain `tree/${project.version}` won't
do: the committed version is bumped early in each cycle, so a mid-cycle manual deploy from `master` would link to a
tag that doesn't exist yet. Update CLAUDE.md to match.

<a id="issue-25"></a>

#### 25. The per-call retry tests vary only `maxRetries`

**Low** · Tests · small · `src/commonTest/kotlin/com/pambrose/jev4k/CallOptionsTest.kt:34-61`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:149`

**What's wrong.** `JevCallOptions.retry` is documented as "replacing the client's policy as a whole". The tests pair
each call policy with a client policy that differs only in `maxRetries` (`RetryPolicy.NONE` is
`RetryPolicy(maxRetries = 0)`), so statuses, backoff, hint handling and the exception rules are the same on both
sides. Ktor 3.6.0 does honor every per-request attribute, so the code works today.

**Impact.** A `send` that built `follow(config.retry.copy(maxRetries = policy.maxRetries), config)`, or a Ktor upgrade
that kept only the per-request `maxRetries`, would pass every test.
`JevCallOptions { retry = RetryPolicy(retryStatuses = setOf(429)) }` would then still retry a 503.

**Fix.** Pair a client `RetryPolicy()` with a call `RetryPolicy(initialBackoff = 100.milliseconds, retryStatuses =
setOf(429))`: send a 503 (expect one request), then a 429 (expect delays `[100, 200]`). Add a `respectRetryAfter =
false` case against a `retry-after-ms` hint.

<a id="issue-26"></a>

#### 26. No test checks the images header for a hand-built `extraBody["assets"]` without a Box

**Low** · Tests · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ImageRequest.kt:46`,
`src/commonTest/kotlin/com/pambrose/jev4k/ImageRequestTest.kt:141-156`, `:242-255`

**What's wrong.** `needsImagesHeader` is `images.isNotEmpty() || ASSETS in extraBody || <a Box is asked>`. The only
two tests with a hand-built `assets` either ask a Box, which sets the header by itself (`:141`), or check the body but
not the header (`:253`).

**Impact.** Deleting `ASSETS in extraBody` passes every test. The CHANGELOG promises the header in this case, and the
API answers 400 without it.

**Fix.** After `ImageRequestTest.kt:254`, add `jev.requests.single().headers[IMAGES_HEADER] shouldBe "true"`.

<a id="issue-27"></a>

#### 27. The depth tests use only nested arrays and strings without brackets or escapes

**Low** · Tests · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/JsonDepth.kt:34`, `:54-57`,
`src/commonTest/kotlin/com/pambrose/jev4k/TestSupport.kt:27-34`,
`src/commonTest/kotlin/com/pambrose/jev4k/ClientTest.kt:560-583`

**What's wrong.** Every at-the-limit input is `nestedArrays` or `nestedArrayText`. The object branch of the send-side
check runs (on any object state), but its depth arithmetic is never tested. On the receive side, no test body holds a
JSON backslash or a bracket inside a string, so the escape handling and the in-string skip never run. A mutation probe
found four mutants that give the expected result on every suite input: objects treated as leaves, objects not counted
as a level, no string skipping, and no escape handling.

**Impact.** 0.3.0's "nested more than 64 levels fails before anything is sent" is pinned only for arrays, though real
states are usually objects. A broken string scan would either refuse a valid body whose strings hold brackets, losing
every answer, or let a deep body through to kotlinx.serialization's recursion.

**Fix.** Add `nestedObjects(depth)`: 65 levels refused and 64 sent, for the state. Add a response whose string value
holds 200 `[` (must parse), and a deep body with an escaped quote (`"\""`) before a 129-deep array (must be refused);
only the second catches the no-escape mutant.

<a id="issue-28"></a>

#### 28. `CropExampleTest` can't catch swapped axes or a wrong origin

**Low** · Tests / Examples · small · `src/jvmTest/kotlin/com/pambrose/jev4k/CropExampleTest.kt:11-22`,
`src/jvmTest/kotlin/website/ImageExamples.kt:142-151`

**What's wrong.** Both cases check only the crop's width and height. The test box spans 0.25 on both axes, and the
edge case is symmetric. Swapping the axes, taking x from `right`, or taking y from `bottom` all pass both cases
(probed). The spec holds this example to working because readers copy it.

**Fix.** Use a box with different spans per axis, such as top 0.1, bottom 0.5, left 0.5, right 0.75, which catches the
swap. It doesn't catch either origin mutant, so also check the origin: paint a marker pixel at (200, 30) and compare
`crop.getRGB(0, 0)`. Do it with #59, which changes the same function.

<a id="issue-29"></a>

#### 29. `CioExclusionTest` pins only `JevConfigBuilder.build()`, not a client making a call

**Low** · Tests · small · `src/jvmTest/kotlin/com/pambrose/jev4k/CioExclusionTest.kt:16-31`, `:43-50`,
`README.md:524-533`

**What's wrong.** The test loads `JevConfigBuilder` in a class loader that hides CIO and calls `build()`. It never
builds a `JevClient` with a supplied engine or makes a call. A probe ran the 0.3.0 jar with `ktor-client-cio-jvm` and
`ktor-network-tls-jvm` off the classpath: a client on a `MockEngine`, `models()`, `blocking.models()`, an evaluate and
an `IOException` mapped to `JevConnectionException` all worked, so this is a test gap only.

**Impact.** A CIO reference added at client construction (a stored `defaultEngine` in `HttpClientFactory`, say)
would pass every test and bring back the previous review's #1 for anyone who followed the README's exclusion.

**Fix.** Add a helper in the test sources that builds a client on a `MockEngine` and calls `jev.blocking.models()`
twice, once succeeding and once with the engine throwing `IOException`, and run it through the CIO-less loader. Hide
`io.ktor.network.tls.` there too, which only CIO brings in.

<a id="issue-30"></a>

#### 30. The publish targets put the GPG secret key and passphrase in the environment of forked processes

**Low** · Build / Security · small · `Makefile:47-50`, `Makefile:271-275`, `build.gradle.kts:393-397`

**What's wrong.** `GPG_ENV` exports the whole armored secret key (`gpg --armor --export-secret-keys`, primary key and
every subkey) and its passphrase from the keychain as `ORG_GRADLE_PROJECT_signingInMemoryKey*` variables of
`./gradlew`. Traced through Gradle 9.8.1:
- The Gradle client holds them for the whole build, visible to `ps -E`.
- A daemon that the publish starts gets the client's full environment and keeps it for life (3 hours idle by
  default). A reused daemon gets the values only in memory, and they are restored after the build.
- Every process the build forks inherits them, including Dokka 2.2.0's worker (process isolation is its default),
  and worker daemons live until their build daemon stops.

A check on this Mac today, printing variable names only, found no process holding either variable now.

**Impact.** During a release, and for as long as a daemon or worker it started lives, any process of the same user
can read the signing key and its passphrase together from the process table. Normally the key sits behind gpg-agent
and the passphrase behind a keychain ACL. A same-user process can already read `~/.gradle/gradle.properties`, and
this is the route vanniktech documents, hence low.

**Fix.** Sign through gpg-agent (`signing { useGpgCmd() }` with `signing.gnupg.keyName`) and drop the export. If the
in-memory key stays, export only the signing subkey (`--export-secret-subkeys <id>!`) and run the publish with
`--no-daemon`.

<a id="issue-31"></a>

#### 31. The tvOS and watchOS simulator tests can drop out of CI silently

**Low** · CI · small · `build.gradle.kts:112-131`, `build.gradle.kts:223-236`, `.github/workflows/ci.yml:137-151`

**What's wrong.** `SimulatorPlatformsWithDevices.obtain()` turns any failure into an empty set
(`runCatching { … }.getOrDefault(emptySet())`) and logs nothing. A platform missing from the set disables its test,
KSP, compile and link tasks, and a disabled task named on the command line just reports SKIPPED. Run 38966787970 ran
all four Apple test tasks, but nothing asserts that they ran.

**Impact.** If `macos-latest` moves to an image without a watchOS runtime, or `simctl` fails on the runner, the Apple
row and `ci-ok` stay green while tvOS or watchOS go untested and are published anyway. This Mac has no watchOS
simulator device, so CI is the only place `watchosSimulatorArm64Test` runs. The previous review's #59 added these
tasks; this lets that fix regress unnoticed.

**Fix.** Make a missing device fatal in CI only, for example with `-Pjev4k.requireSimulators=true` passed by the Apple
row, or a pre-Gradle step that checks `xcrun simctl list devices available`. Log the exception `runCatching` swallows.

<a id="issue-32"></a>

#### 32. Codecov's coverage gate gates nothing, yet the release checklist counts it under `ci-ok`

**Low** · CI / Release · small · `codecov.yml:11-16`, `.github/workflows/ci.yml:79-87`,
`docs/release-checklist.md:97-98`

**What's wrong.** `codecov.yml` fails the project status on a drop of more than 1%, and Codecov does post
`codecov/project`. But `master`'s protection requires only `GitGuardian Security Checks`, `ci-ok` and `docs`, and the
upload step has `fail_ci_if_error: false`, so it can't fail `ci-ok` either. CLAUDE.md describes this accurately; the
checklist's step 4 doesn't: "`ci-ok` (build, tests, Kover, Codecov, …)".

**Impact.** A PR that takes line coverage from 95.99% to 92.1% stays above the Kover floor of 92, passes `ci-ok`, and
merges with `codecov/project` red. The releaser believes Codecov was enforced.

**Fix.** Either require `codecov/project` in branch protection, or mark it `informational: true` and reword
`codecov.yml` and the checklist to say the Kover floors are the gate and Codecov only reports.

<a id="issue-33"></a>

#### 33. CI runs an unpinned `binfmt` image with `--privileged`

**Low** · CI · small · `.github/workflows/ci.yml:63-65`, `Makefile:95`

**What's wrong.** Every action is pinned to a SHA, but `docker/setup-qemu-action` has no `image:` input. The build
job's log shows `docker run --rm --privileged docker.io/tonistiigi/binfmt:latest --install arm64`, with `latest`
resolved at run time (repo digest `sha256:400a4873…66f0` in run 38966787970). The Linux test image,
`buildpack-deps:noble-curl`, is pulled by tag. zizmor doesn't audit image references, and Dependabot's
`github-actions` ecosystem doesn't update them.

**Impact.** Whatever is next pushed to `binfmt:latest` runs as root with host access on every PR and `master` build.
The job's token is read-only and releases are built on a Mac, so the reach is the `CODECOV_TOKEN` and the caches this
job writes (konan, Gradle, the binfmt image cache), which later `ci.yml` runs restore. The `buildpack-deps` container
is unprivileged, with a read-only mount and no secrets, so pinning it is about reproducibility.

**Fix.** `with: image: docker.io/tonistiigi/binfmt@sha256:400a4873…` (the full digest from the log) with a version
comment, and pin `LINUX_TEST_IMAGE` by digest. Both then need updating by hand.

<a id="issue-34"></a>

#### 34. iosX64 is published, but its tests run nowhere

**Low** · CI / Docs · small · `build.gradle.kts:159`, `build.gradle.kts:224`, `.github/workflows/ci.yml:137-151`,
`Makefile:25-26`, `website/jev4k/docs/guides/development.md:21-23`

**What's wrong.** `iosX64()` publishes `jev4k-iosx64`, but its test tasks run only on an Intel Mac. The Apple CI row
is on arm64 `macos-latest`, `NATIVE_TESTS` leaves it out, and the build log shows `iosX64Test` SKIPPED. CLAUDE.md says
the binary can't run off an Intel Mac, but `development.md:23` says "CI runs the rest". It's the gap the previous
review's #60 closed for linuxArm64.

**Impact.** A defect specific to the x86_64 simulator build ships without having run once. The risk is low: the
sources are iosSimulatorArm64's, and the klib is compiled on both Linux and macOS.

**Fix.** Add an Apple row on GitHub's Intel image (`macos-15-intel`, while it lasts) that runs `iosX64Test`, or say on
the development page and in CLAUDE.md that iosX64 is compiled but never tested.

<a id="issue-35"></a>

#### 35. `refresh.sh` writes wherever the downloaded `llms.txt` says, `../` included

**Low** · Build / Security · small · `jev-docs/refresh.sh:15`, `:18-28`

**What's wrong.** Page paths are taken from the downloaded index with `grep -oE "\($BASE/[^)]+\.md\)"`, which accepts
`..`, and used as output paths (`mkdir -p "$tmp/pages/$(dirname "$page")"`, `> "$tmp/pages/$page"`). A probe with a
crafted entry wrote a `CLAUDE.md` outside `$tmp/pages`. The write happens inside the loop, before the failure check,
so even a run that later fails leaves the file.

**Impact.** If docs.typesafe.ai or its generated `llms.txt` were compromised, `make api-docs` could create or overwrite
any `*.md` file the developer can write: this repository's or the global `CLAUDE.md` (standing instructions for every
later agent session), or `website/jev4k/docs/*.md` (published at the next release). Such an attacker can already put
text into the pages agents read; the extra harm is the persistent write outside the cache.

**Fix.** Skip and fail on any page not matching `^[A-Za-z0-9][A-Za-z0-9._/-]*\.md$`, or containing `..`, or starting
with `/`.

<a id="issue-36"></a>

#### 36. The docs deploy installs whatever uv release is newest

**Low** · CI · small · `.github/workflows/docs.yml:58-61`, `.github/workflows/docs.yml:66-69`,
`website/.python-version:1`

**What's wrong.** `docs.yml` pins every action to a SHA, takes action updates only after a 7-day cooldown, and turns
off every cache so nothing it didn't pin can change what's deployed. But `astral-sh/setup-uv` gets no `version`, and
nothing in the repository sets `required-version`, so each run installs the newest uv (assumed from setup-uv's
documented default; its source wasn't available). uv then picks the Python 3.14 patch from its own release's list.
The packages don't float: `uv run --locked` installs from `uv.lock`. `setup-java` with `java-version: 25` floats to
the newest 25.x in the same job, which builds the deployed KDocs.

**Impact.** A bad or compromised uv release reaches the job that builds the Pages artifact the day it ships,
bypassing the cooldown the repository applies everywhere else.

**Fix.** Set `version: "x.y.z"` on the setup-uv step (a `required-version` in `website/pyproject.toml` alone would
make uv refuse to run rather than pin it, since setup-uv reads the repository root), and bump it with
`make upgrade-site`. Pin Java's patch too, or accept both.

<a id="issue-37"></a>

#### 37. The Java Box example asks on the default model, which can't answer Box yet

**Low** · Examples / Java interop · small · `src/jvmTest/java/website/JavaInterop.java:106-115`,
`website/jev4k/docs/queries/images.md:81-82`, `:151-153`

**What's wrong.** `findMug` calls `blocking.query("The photo: " + …, qb -> { …; qb.box("mug", …); … })` with no model,
so it sends `jev-latest`. Box needs Jev 1.14 (`jev-docs/pages/primitives/box.md:11`), and `jev-latest` is
`jev-1.13.0` until TypeSafe moves it, announced for about 2026-10-21. The page says so two screens up, and every
Kotlin Box example passes `model = "jev-preview"`. The example also asks `mug_visible` and returns the box without
reading it, against the page's advice and the Kotlin version.

**Impact.** A Java reader who copies the example gets an API error instead of a box until `jev-latest` moves, and for
good if they pin `jev-1.13.0`. The exact response Jev 1.13 gives a Box question hasn't been observed.

**Fix.** Call the model-taking overload, `blocking.query(state, "jev-preview", qb -> …)`, and return the box only when
`r.noul("mug_visible").isTrue()`. `perCall` at line 78 still pins the model-less two-argument overload, so the ABI
guard loses nothing. Optionally say "needs Jev 1.14" in the `box()` and `BoxQuestion` KDocs.

<a id="issue-38"></a>

#### 38. Two image examples send each image twice, against the sentence above them

**Low** · Examples · small · `src/jvmTest/kotlin/website/ImageExamples.kt:45-47`, `:113-115`,
`website/jev4k/docs/queries/images.md:41-42`

**What's wrong.** The page says "An image several questions use belongs in the state, where it is sent once; every
place a reference appears adds another copy of the image to what the model reads." The example below it puts both
references in the state and again in the question:

```kotlin
jev.query("A seller uploaded $upload for a listing whose catalog photo is $catalog.") {
    attach(upload, catalog)
    noul("same", "Does $upload show the same product as $catalog?")
}
```

`matchesCatalog`, the "Your own references" example, does the same with `<delivered>` and `<catalog>`.

**Impact.** The model reads four copies of the images instead of two, so image tokens double, and every copy counts
against the 32 MiB expanded-request limit (`jev-docs/pages/images.md:339`). These are the examples readers copy, and
they teach the opposite of the advice above them.

**Fix.** Place each image once. TypeSafe's patterns table recommends the photo in the state and the known picture in
the instructions (`pages/images.md:322`): for example, the upload in the state and "Is the uploaded product the one
shown in $catalog?" in the question.

<a id="issue-39"></a>

#### 39. The Images page leaves out the total-pixel minimum, so 256 × 256 looks valid

**Low** · Docs · small · `website/jev4k/docs/queries/images.md:105-106`

**What's wrong.** The page gives "each side 256 to 1568 pixels, 16 MiB per file, 32 MiB per request". TypeSafe's
limits also require 78,400 to 2,458,624 pixels in total, and say "A 256 × 256 image is too small"
(`jev-docs/pages/images.md:336`). `HotDogPhotosTest.kt:28` already checks the minimum, but no jev4k page states it, and
jev4k leaves the limits to the API.

**Impact.** A reader who scales thumbnails to the 256-pixel side the page gives gets a 400 for every square one.

**Fix.** Add "and 78,400 to 2,458,624 pixels in total (a 256 × 256 image is too small)". The "all images ≤ 32 MiB
after processing" and expanded-request limits are worth a clause too.

<a id="issue-40"></a>

#### 40. The due-date example ignores a stated year

**Low** · Examples · small · `src/jvmTest/kotlin/website/ExtractionExamples.kt:41-64`, `:110-124`,
`website/jev4k/docs/patterns/extraction.md:24-39`

**What's wrong.** The page says to "ask which date parts the text states … and do the calendar math in code".
`DueDateQuery` asks the mode, month, day and relative day, but no year, and `nextDate` always uses `today.year`,
moved ahead a year if that date has passed. TypeSafe's cookbook, which the page adapts, has a `year` Choice with
`none` and `out_of_range`, and uses `today.year` only when the year is `none` (`jev-docs/cookbooks.md:1535-1586`). The
RELATIVE mode is described to the model as "today, tomorrow, or a named weekday", but `RelativeDay` has no weekday.

**Impact.** "Return the form by August 14, 2027", read on 2026-07-30, gives 2026-08-14 with every part confident and
`needsReview = false`: a wrong date, silently accepted. The weekday case is milder: "next Thursday" most likely comes
back `NONE`, a null date marked for review.

**Fix.** Add the cookbook's `year` Choice and use a stated year. Drop "or a named weekday" from the RELATIVE
description, or add the weekday and week-offset Choices. Or state the simplification on the page.

<a id="issue-41"></a>

#### 41. The function-calling example has no route Choice, no escape and no confidence gate

**Low** · Examples · small · `src/jvmTest/kotlin/website/ExtractionExamples.kt:138-161`,
`website/jev4k/docs/patterns/extraction.md:46-50`

**What's wrong.** The page's first bullet is "a Choice picks the function", and the code comment says "One request
routes the call", but `PlotCommand` has no function Choice. Its `symbol` Choice offers only NVDA, AAPL and TSLA, and
`runPlotCommand` calls `plotPrice` on every input without reading a confidence. The cookbook routes with a `__tool__`
Choice and gates the call on the minimum confidence of its parts (`jev-docs/cookbooks.md:21`, `:695-706`), and
`choice.md` says to always offer a way out.

**Impact.** "Plot MSFT for the last week", or "what's the weather?", plots one of the three tickers, and nothing tells
the caller the request didn't fit.

**Fix.** Add a route Choice with a `none_of_these` function (and skip the call when it wins), and gate on the minimum
confidence of the parts used, as the date example on the same page does.

<a id="issue-42"></a>

#### 42. The parameterized-query example sends one request per policy, and can't be merged as written

**Low** · Examples · small · `src/jvmTest/kotlin/website/TypedQueryExamples.kt:69-83`,
`website/jev4k/docs/queries/typed.md:64-70`

**What's wrong.** `policyViolations` runs `policyChecks.map { jev.ask(check, state = message) … }`: N sequential
requests about one state, each also asking a `severity` Score nobody reads. Every `PolicyCheck` declares the ids
`violates` and `severity`, so two `include`s fail with "Duplicate question id 'violates'". The fan-out page, the
patterns index and the best practices all say to put every question about a state into one request.

**Impact.** Readers copy a pattern that costs N round trips and N copies of the state's tokens, and the page shows no
way out of the id clash.

**Fix.** Give the class's questions distinct ids (`noul(…, id = "violates_$slug")`), ask them all in one
`query { policyChecks.forEach { include(it) } }`, and drop the unread Score.

<a id="issue-43"></a>

#### 43. Two classification examples don't guard the 255-option limit

**Low** · Examples · small · `src/jvmTest/kotlin/website/ClassificationExamples.kt:37-45`, `:62-66`,
`website/jev4k/docs/patterns/classification.md:28-31`

**What's wrong.** The previous review's #16 added guards to `findAnswer`, `categorize` and `countFruits`. Two examples
still build one option per item of caller data:
- `suggestSkill` sits under "Large option sets in two stages", but its first stage is one Choice over the whole
  roster, so a 300-skill roster fails "a Choice needs 1..255 options".
- `pickChild` walks taxonomies the page names (patent classes, product catalogs, medical subject headings), whose
  levels can have more than 255 children, so it can fail mid-walk.

**Impact.** Both throw a `JevValidationException` that names the limit, on inputs the page invites. It fails loudly,
not wrongly, hence low. An empty roster, or `violatedClauses` (`InlineDslExamples.kt:64-74`) with no clauses, fails
the same way on degenerate input.

**Fix.** Do what #16 did: `require(children.size <= MAX_CHOICE_OPTIONS)` or chunk the level, make `suggestSkill`'s
first stage stay under the limit, and return null or an empty list for empty input.

<a id="issue-44"></a>

#### 44. CHANGELOG and CLAUDE.md name the wrong exception for an over-deep `extraBody` field

**Low** · Docs / Release · small · `CHANGELOG.md:56-59`, `CLAUDE.md:91-92`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevCallOptions.kt:88-97`

**What's wrong.** CHANGELOG 0.3.0 says "A state, question entry or `extraBody` field nested more than 64 levels deep
fails with `JevValidationException` before anything is sent", and CLAUDE.md says the same. `extraBody` is checked only
in `JevCallOptionsBuilder.build()`, which throws `JevConfigException("Invalid call options: …")`, and `evaluate`
doesn't check it again. `CallOptionsTest.kt:104-120` pins `JevConfigException`. RELEASE_NOTES and `calls.md:81` are
right.

**Impact.** `JevConfigException` and `JevValidationException` are sibling subclasses, so a caller who follows the
changelog with `catch (e: JevValidationException)` misses the failure, which comes earlier, when the options are built.

**Fix.** "A state or question entry … fails with `JevValidationException`; an `extraBody` field fails when the
`JevCallOptions` is built, with `JevConfigException`." Same in CLAUDE.md.

<a id="issue-45"></a>

#### 45. The jev-1.13 limits table lists a weakness TypeSafe dropped, and misses Choice option order

**Low** · Docs · small · `website/jev4k/docs/guides/best-practices.md:94-106`

**What's wrong.** Row 105 is "Structural invariants (`P(q) + P(not q)` needn't be 1)". TypeSafe's jaggedness page now
has item 8 as Choice option order: jev-1.13 "leans toward the option that comes first", so reorder the options to
double-check (`jev-docs/pages/model-jaggedness/jev-1.13.md:13-23`, `:112-116`). `jev-api.md:334-337` records the
change. `classification.md:26` says option order is part of the question, but no page mentions the lean or the
reorder check.

**Impact.** Readers designing Choices, especially enum and many-option ones whose order is fixed, never learn that the
first option is favored.

**Fix.** Replace the row with "Choice option order | Reorder the options and check the answer holds". Optionally add a
line on the Choice page.

### Nit

<a id="issue-46"></a>

#### 46. No test pins that jev4k's own engine gets the connect and socket timeouts

**Nit** · Tests · small · `src/commonTest/kotlin/com/pambrose/jev4k/ClientTest.kt:708-713`,
`src/commonMain/kotlin/com/pambrose/jev4k/internal/HttpClientFactory.kt:76`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevClient.kt:148`

The test calls `limitTo(…, ownEngine = true)` directly, and never checks that the two call sites pass
`ownEngine = config.engine == null`. Hard-coding `false` at either site passes every test. The per-attempt request
timeout still bounds each attempt, so nothing can hang; a configured `timeout` over 5 s just wouldn't lengthen CIO's
own 5 s connect timeout. `JevClient.http` is private, so an `HttpSend` interceptor reaches only the plugin site: share
a small internal `timeoutConfig(config, options)` between both sites and test that.

<a id="issue-47"></a>

#### 47. An `ImageRequestTest` title promises call headers it doesn't test

**Nit** · Tests · small · `src/commonTest/kotlin/com/pambrose/jev4k/ImageRequestTest.kt:64-74`

"The client's or the call's headers can replace the images header" sets only a client header. The header order itself
is pinned (this test pins built-in before client, and `CallOptionsTest.kt:64` pins client before call), so only a
special case for this one header could slip through. Rename the test, or add a `withOptions(JevCallOptions {
headers[IMAGES_HEADER] = "false" })` case.

<a id="issue-48"></a>

#### 48. The tool-trace example asks Jev checks the page says belong in code

**Nit** · Examples · small · `src/jvmTest/kotlin/website/VerificationExamples.kt:130-133`,
`website/jev4k/docs/patterns/verification.md:8`, `website/jev4k/docs/patterns/index.md:24-25`

The verification page says "Keep the deterministic checks in code", and principle 1 names schema validation and date
math, yet the example asks Nouls whether the arguments violate the schema and whether `unit` and `date` differ from
the request's. The example is a faithful adaptation of TypeSafe's "Decomposed questions (good)" example
(`jev-docs/pages/concepts/how-to-build-with-system-one.md:380-426`), so this is two pieces of advice in conflict, not a
broken example. Add a sentence ("in production, run the schema and exact-match checks in code first"), or keep only
the semantic Nouls.

<a id="issue-49"></a>

#### 49. Maintainer notes and `zensical.toml` still describe the state before 0.3.0

**Nit** · Docs · small · `jev-docs/jev-api.md:355`, `docs/api-check-2026-10-15.md:15`, `:30-33`, `:79`,
`docs/superpowers/specs/2026-10-15-image-support-design.md:3`, `docs/superpowers/plans/2026-10-15-image-support.md`,
`website/jev4k/zensical.toml:22`, `CLAUDE.md:232-233`, `CLAUDE.md:478-480`

- `jev-api.md:355` says "jev4k decodes `box` that way (`UnknownAnswer`)"; since 0.3.0 it's a `BoxAnswer`, as line 362
  of the same section says. CLAUDE.md tells contributors to read this file before changing API code.
- The API check still reads "Status: 7 of 10 fixed", with #4 (images) and #5 (Box) unticked. Its own rule (line 13)
  makes it a live tracker: tick both and make it 9 of 10, with #7 left open. Line 79 is optional.
- The spec's status line still says "awaiting review of this written spec", and the plan's 57 boxes are all unticked.
- `zensical.toml:22` says the site is published "on every push to master"; `docs.yml` deploys only on a release or a
  manual run.
- CLAUDE.md's list of `src/jvmTest` files names "the example" and leaves out `HotDogExample`, `HotDogPhotosTest`,
  `JevImageJvmTest`, `CropExampleTest`, `ErrorsJvmTest` and `RawServer`. Its note that snippets resolve from
  `src/jvmTest/kotlin/website` misses the repository root, which `zensical.toml:170` also lists and `images.md` uses
  for `HotDogExample.kt`.

<a id="issue-50"></a>

#### 50. Three places still say the retry defaults match "the official SDKs"

**Nit** · Docs · small · `docs/packages.md:58`, `website/jev4k/docs/index.md:59-60`,
`website/jev4k/docs/client/configuration.md:33`

The previous review's #49 corrected this to the JS SDK in the `RetryPolicy` KDoc, the README and the errors page: the
Python SDK doesn't cap hints and adds a 30 s budget per call. These three were missed. `docs/packages.md` is the KDoc
landing page, which 0.3.0 put on the site, and `index.md`'s "the official SDKs' retry and timeout rules" is the most
wrong, since Python's budget is a timeout rule jev4k doesn't have. `CLAUDE.md:100` and the test name at
`ConfigTest.kt:260` are imprecise in the same way. Leave `CHANGELOG.md:328`, a 0.1.0 entry.

<a id="issue-51"></a>

#### 51. Two unresolved KDoc links: `QuestionSet.toJson`'s `[images]` and kotlinx-io's `Path`

**Nit** · Docs (Dokka) · small · `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:135`, `build.gradle.kts:333-338`

`[images]` in `toJson()`'s KDoc resolves to the constructor parameter, not the property, and renders unlinked: the
case CLAUDE.md warns about. Write `[images][QuestionSet.images]`. kotlinx-io became an `api` dependency in 0.3.0, but
only kotlinx.serialization and Ktor are registered as external documentation links, so `Path` in `JevImage.read` is
unlinked. Register `https://kotlinlang.org/api/kotlinx-io/`, after checking that it serves a `package-list`.

<a id="issue-52"></a>

#### 52. README says `confidence` is "Choice and Score only", below a Box row that has one

**Nit** · Docs · small · `README.md:161`, `README.md:165`

0.3.0 added a Box row ("`confidence`, or null") to the answers table but kept the bullet "`confidence` (Choice and
Score only) summarizes how concentrated the probabilities are". `BoxAnswer.confidence` exists, and TypeSafe doesn't
define it as a concentration measure (`jev-docs/pages/primitives/box.md:99`), so the bullet needs a Box qualifier, not
just a third name. Fix with #1, which touches the same line.

<a id="issue-53"></a>

#### 53. The Installation page lists three `api` dependencies; kotlinx-io-core is a fourth

**Nit** · Docs · small · `website/jev4k/docs/getting-started/installation.md:15-17`, `:111`

Lines 15-17 name Ktor core, kotlinx.serialization-json and kotlinx.coroutines. 0.3.0 added `api(libs.kotlinx.io.core)`,
since `JevImage.read` takes its `Path`, and the same page's classpath paragraph (line 113) already lists it. Add it,
with "the `Path` that `JevImage.read` takes". The heading at line 111, "What it puts on your classpath.", is the only
one on the site that ends with a period.

<a id="issue-54"></a>

#### 54. "A missing file fails the object's first use" understates a permanent failure on the JVM

**Nit** · Docs (KDoc) · small · `src/commonMain/kotlin/com/pambrose/jev4k/JevQuery.kt:72-73`,
`website/jev4k/docs/queries/images.md:58-59`

On the JVM a failed `object` initializer is permanent: every later access throws `NoClassDefFoundError: Could not
initialize class …` until the process restarts. A server whose `JevQuery` reads a picture that's missing at startup
fails every request that touches it, not just the first. The KDoc is in common code, so say "the object becomes
unusable" or name the JVM.

<a id="issue-55"></a>

#### 55. `isPlatformConnectionError`'s common KDoc says the JVM needs no rule of its own

**Nit** · Docs (KDoc) · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/Platform.kt:18-21`

It says "The JVM, Darwin and CIO engines throw an `IOException`, which is already recognized". The JVM actual returns
`cause is GeneralSecurityException || cause is ParserException`, because CIO reports an untrusted certificate and an
unparseable response that way (the previous review's #3 and #4). A reader could "simplify" the actual to `false`.
Say what the JVM actual adds, and drop "the JVM" from the list of engines.

<a id="issue-56"></a>

#### 56. `jevApiException` ignores the request-id header; `jevResult(String)` rejects a BOM

**Nit** · Testing helpers · small · `src/commonMain/kotlin/com/pambrose/jev4k/Testing.kt:52-61`, `:79-89`

- `jevApiException` reads the retry hint from `headers` "as the client reads it", but takes the request id only from
  its parameter; the client reads it from `x-typesafe-request-id`. `jevApiException(500, body = "oops", headers =
  mapOf("x-typesafe-request-id" to listOf("r1")))` gives `requestId = null`.
- `jevResult(String)` parses the text as is, while `send` first drops a leading BOM (`JevClient.kt:158`, pinned by
  `ClientTest.kt:520`). That breaks the file's promise of a result that "behaves exactly like one that arrived over
  the wire".

Fall back to the header lookup (case-insensitive, through the `headersOf(…)` value the function already builds), and
strip a leading U+FEFF before parsing. No ABI change.

<a id="issue-57"></a>

#### 57. Score level keys parse loosely, and an unasked Score's `levelCount` counts keys

**Nit** · Results · small · `src/commonMain/kotlin/com/pambrose/jev4k/internal/ResponseMapper.kt:122`, `:212-226`

`key.toIntOrNull()` accepts a leading `+` and leading zeros, and `toMap()` keeps the last of equal keys, so
`{"0":0.1,"01":0.2,"+1":0.7}` becomes `{0=0.1, 1=0.7}` and the 0.2 is dropped silently, though the mapper is otherwise
strict about level keys. For a Score nobody asked for, `levelCount = maxOf(legend.size, probabilities.size)` counts
keys, so `{"0":0.0,"2":1.0}` with `score` 2.0 gives a `normalized` of 2.0, outside its documented 0..1. A conforming
server never sends either. Accept only canonical keys (`key == level.toString()`), fail on a repeated level, and use
the highest key plus one for an unasked Score.

<a id="issue-58"></a>

#### 58. `QuestionSet` has no `toString`

**Nit** · DSL / Testing · small · `src/commonMain/kotlin/com/pambrose/jev4k/Questions.kt:99-139`

MockK's "no answer found for JevApi(#1).evaluate(…, com.pambrose.jev4k.QuestionSet@6d3a…)" is where a user meets the
per-call-handles trap the testing docs warn about, and identity hashes don't show which set is which. Add
`override fun toString() = "QuestionSet(ids=$ids, images=${images.size})"`, which holds nothing secret. It changes the
ABI dumps, so it belongs in group 10's `make abi-update`.

<a id="issue-59"></a>

#### 59. `cropToBox` truncates the edge but rounds the size, dropping a column inside the box

**Nit** · Examples · small · `src/jvmTest/kotlin/website/ImageExamples.kt:146-149`

`x = (box.left * W).toInt()` truncates, while `width = ((box.right - box.left) * W).roundToInt()` rounds, so the right
edge can fall up to 1.5 px short. A 10 px image with left 0.19 and right 0.41 (the box spans 1.9 to 4.1) crops
`[1, 3)`, dropping column 3, which lies wholly inside the box. The y and height pair has the same flaw. TypeSafe's
example rounds each edge on its own (`jev-docs/pages/primitives/box.md:88-94`). Compute both edges the same way and
subtract (`floor` for the start and `ceil` for the end never cuts the target), and add the 10 px case to
`CropExampleTest` with #28.

<a id="issue-60"></a>

#### 60. Three places say Java can't call `RetryPolicy`'s constructor, then call `new RetryPolicy()`

**Nit** · Docs · small · `README.md:558-559`, `website/jev4k/docs/getting-started/installation.md:143-144`,
`src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:38-39`

Each says Java can't call the constructor, then the code below calls `new RetryPolicy()`. `api/jev4k.api:569` has a
public no-argument constructor; only the two full constructors are synthetic. Say "the full constructor" or "the
`Duration` constructor" in all three.

<a id="issue-61"></a>

#### 61. README and a KDoc say every config problem is reported at once; a missing key is reported alone

**Nit** · Docs · small · `README.md:455-456`, `src/commonMain/kotlin/com/pambrose/jev4k/JevConfig.kt:120`, `:174-177`

"The builder reports every problem at once" (README) and "[build] reports every problem it finds at once" (KDoc). But
`build()` throws "No TypeSafe API key" before it collects anything else, so a bad `baseUrl` shows up only once the key
is fixed. `configuration.md:77-78`, CLAUDE.md and `JevCallOptions.kt:62` are scoped to the settings Ktor would reject,
which really are collected together, so they're accurate. Qualify the two that aren't.

<a id="issue-62"></a>

#### 62. `raw` is called "the response body exactly as received"; it's a parsed `JsonObject`

**Nit** · Docs · small · `website/jev4k/docs/results/reading.md:59`, `src/jvmTest/kotlin/website/ResultExamples.kt:73`

A parsed object has lost the whitespace and any duplicate keys; only the exceptions carry the body's text as received.
Say "the parsed response body". `README.md:361` and the KDoc at `JevResult.kt:24` say "as received" without "exactly",
which is optional to change.

<a id="issue-63"></a>

#### 63. The Score example shows confidence 0.54; TypeSafe's formula gives 0.55

**Nit** · Examples · small · `src/jvmTest/kotlin/website/ScoreExamples.kt:29`

For probabilities (0, 0.7, 0.3), the most likely level is 1 and the spread is 0.3. With three levels the uniform
spread is 2/3, so confidence = 1 − 0.3 / (2/3) = 0.55 (`jev-docs/pages/confidence.md:130-153`). The comment says
"0.54". It's rendered on `questions/score.md`.

<a id="issue-64"></a>

#### 64. One run of trailing comments is misaligned

**Nit** · Examples · small · `src/jvmTest/kotlin/website/CompositeScoringExamples.kt:91-93`

The `//` on lines 91 to 93 sits at columns 63, 63 and 64, against CLAUDE.md's alignment rule. Add one space before the
comments on lines 91 and 92. A script scan of every website example, `JavaInterop.java` and the README's code blocks
found no other misaligned run.

<a id="issue-65"></a>

#### 65. Three places describe one real-CIO test; `ClientJvmTest` has five

**Nit** · Docs · small · `README.md:713-714`, `website/jev4k/docs/guides/development.md:42-45`, `CLAUDE.md:479`

They say one timeout test drives the real CIO engine. `ClientJvmTest` has five real-CIO cases: the timeout, an
untrusted certificate, a body cut short, an oversized declared body and an unparseable response. "No traffic leaves
the machine" stays true; all of them use loopback servers.

<a id="issue-66"></a>

#### 66. The README and the development page leave out the hot dog example

**Nit** · Docs · small · `README.md:698-712`, `website/jev4k/docs/guides/development.md:66`

The README's make list and "The runnable example is `TriageExample.kt`" leave out `HotDogExample.kt` and
`make hotdog-example`; the README never mentions it. The development page's layout table says "the example" too.
The site's `Development.txt` and the Makefile have the target.

<a id="issue-67"></a>

#### 67. Box and `attach` are missing from several summaries and tables

**Nit** · Docs · small · `website/jev4k/docs/index.md:25`, `website/jev4k/docs/queries/inline.md:19-26`, `:61`,
`website/jev4k/docs/queries/typed.md:33-38`, `website/jev4k/docs/api.md:11-37`,
`website/jev4k/docs/results/reading.md:31-35`, `README.md:169`

- The home page's diagram reads "Questions: Noul, Choice, Score".
- The builder tables on the inline and typed pages list neither `box` nor `attach`, and neither page mentions them.
- `api.md`, which `llms.txt` calls "a guided map of the API reference", has no `JevImage`, `BoxAnswer` or
  `JevCallOptions`.
- `inline.md:61` and `README.md:169` name only the three older question classes, and `reading.md`'s answer table has
  no `BoxAnswer` row.

Mark each addition experimental and link it to Images & Box.

<a id="issue-68"></a>

#### 68. Several places say Linux skips the Apple targets; it compiles all of their klibs

**Nit** · Build / Docs · small · `gradle.properties:10`, `CLAUDE.md:269-270`, `CLAUDE.md:395-396`,
`build.gradle.kts:138-139`, `Makefile:211`, `README.md:690-691`

`gradle.properties` says hosts skip "Apple targets on Linux or Windows", and the README says Linux and Windows "build
everything but the Apple ones". The ubuntu job of run 38966787970 ran `compileAppleMainKotlinMetadata` and every
Apple `compileKotlin…` task; only linking and running are Mac-only. The macOS guards on `abi-update` and the publish
targets are harmless, but their stated reason is out of date, and CI does compile-check every Apple target. Reword,
then recheck the guards' wording. Windows wasn't checked.

<a id="issue-69"></a>

#### 69. The `docs.yml` jobs have no `timeout-minutes`

**Nit** · CI · small · `.github/workflows/docs.yml:39`, `:88`

Every `ci.yml` and `zizmor.yml` job sets one; `docs` and `deploy` inherit GitHub's 6-hour default, so a hung Dokka run
blocks the required `docs` check for hours. The release run took 3m21s and 9s, so 30 and 10 minutes are generous.

<a id="issue-70"></a>

#### 70. CI's failure artifacts leave out the Docker Linux test logs

**Nit** · CI · small · `.github/workflows/ci.yml:69-77`, `Makefile:96`, `Makefile:108-115`

`make docker-linux-tests` writes each target's full log to `build/docker-linux-tests/<target>.log`, the only place the
TeamCity messages appear, and says so on failure. The upload takes only `build/reports/tests/` and
`build/test-results/`, which a container run doesn't write. Add `build/docker-linux-tests/` to the paths.

<a id="issue-71"></a>

#### 71. The Codecov upload also sends `build/kover/coverage.txt`

**Nit** · CI · small · `.github/workflows/ci.yml:79-87`

`files:` doesn't turn off the uploader's search, and the log says "Found 2 coverage files to report", the second being
`koverLog`'s one-line summary. Codecov's report still has the expected 25 files, so it's noise. Add
`disable_search: true`.

<a id="issue-72"></a>

#### 72. The release checklist's "Current state" table always lags a release behind

**Nit** · Release · small · `docs/release-checklist.md:11`, `:24-27`, `:158-166`

With 0.3.0 tagged and on Central, the table still shows tags through 0.2.2, Central at 0.2.2 and "`0.3.0`, the next
release number", and line 11 still dates the release rows 2026-10-12. Each release PR (#13, #15, #16) updated the rows
to record the previous release, and no step in 3 or 7 mentions the table. Add a step 7 item, or a follow-up commit,
that records the release just made and updates the date.

## Method

- **Scope.** The whole repository at `2f90671`: the library, tests, build, Makefile, CI, README, docs site,
  CHANGELOG, release notes, `docs/` and `jev-docs/`.
- **Review.** Nine reviewers each took one dimension, and were told to read CLAUDE.md first, treat its recorded
  decisions as deliberate, and not re-raise the previous review's 85 fixed issues unless they had regressed:
  - conformance to the TypeSafe API (`jev-docs/`);
  - core logic (the DSL, validation and results);
  - the 0.3.0 image and Box code;
  - the HTTP client, configuration, concurrency and platforms;
  - the public API, binary compatibility, Java interop and KDoc;
  - tests;
  - build, CI and release;
  - documentation and examples;
  - security and robustness.
- **Evidence.** Reviewers and verifiers worked read-only on the tree. They compiled probes with `kotlinc` against the
  built 0.3.0 jar, compiled Java with `javac --release 21` against the published 0.2.2 and 0.3.0 jars, ran Node 26's
  fetch and NSURLSession against local servers, read the Ktor 3.6.0, Dokka 2.2.0 and Gradle 9.8.1 sources from the
  Gradle cache, and read CI logs, branch protection and Codecov data through read-only `gh` calls.
- **Verification.** The 85 raw findings were merged into 73. Six verifiers each took a batch, told to refute each
  finding from the source and to correct line numbers, severity and scope. The run used 15 agents.
  - 72 survived. 17 were corrected: three downgraded (#2 and #30 from medium to low, #48 from low to nit), and the
    rest narrowed in scope, scenario or fix. Two reviewers found #22 independently, one rating it medium; it stays
    low.
  - I also checked #1 (TypeSafe's formula), #2 and #11 (the code paths), #63 (the arithmetic) and #30 (the process
    environment, by variable name only) myself.
- **Rejected.**
  - That the `zizmor` check isn't required. The checklist says so plainly, nothing merges unattended (auto-merge is
    off), the admin could bypass a required check anyway, and requiring it would turn every open PR red on a new
    advisory against a pinned action. It's a one-line settings change if wanted.
  - Half of #42: `TriageExample` re-asks `SupportTriage` in a second request, but its header and the Installation page
    present it as a deliberate demo of both DSL styles.
  - Part of #43: `receiptAddress` already guards empty input, and the "stay under 255 candidates" advice is the next
    bullet on its page.
  - The security reviewer's claim that Gradle workers on this Mac still held the signing key. A check that excluded
    its own command found no process with any `ORG_GRADLE_PROJECT_*` variable; #30 keeps only the mechanism.
- **What held up.**
  - The wire format, the multipart layout (checked against Ktor 3.6.0's `formData`), the images header, the local
    limits with their exact bounds, the response mapping, the error classes and the retry rules all match the API docs
    and the SDKs.
  - `execute`'s exception classification, the retry-before-timeout plugin order, per-call `retry {}`/`timeout {}`,
    hint parsing, and redirects off on every engine all hold. Every engine verifies TLS.
  - The API key never appears in `toString`, a message, an endpoint string or the User-Agent, and
    `allowInsecureHttp` can't be set from the environment.
  - The depth scan agrees with the strict parser, and every check on large input is iterative and linear.
  - The ABI dumps only gain lines from 0.2.2 to 0.3.0. Kotlin calls from 0.2.2 resolve to the same overloads, the
    README snippets compile without the images opt-in, `@Throws` covers every `BlockingJev` method (`askWithImages`
    included), and `@ExperimentalJevImages` is on every public declaration that names an image or Box type.
  - The POM has five compile dependencies and CIO at runtime, the JVM 17 floor holds (`jvmTarget`, `-Xjdk-release`,
    javac `release`, `org.gradle.jvm.version`), and the manifest is right.
  - All 150 `--8<--` includes resolve, no page has inline code, every admonition and the home grid render, and the
    nav and `llms.txt` list all 31 pages.
  - `make docker-linux-tests` catches crashes, empty binaries and failed tests; the no-tests guard covers native and
    JS tasks; every launcher registers all 17 common specs; the live tests are gated; the socket tests don't race.
  - Every action is SHA-pinned, checkouts don't keep credentials, permissions are least-privilege, no workflow runs
    untrusted code with secrets, and `docs.yml` restores no cache.
  - None of the previous review's fixes has regressed, including the build and CI items (#13, #14, #50, #58 to #68)
    and the example fixes (#75 to #81).
