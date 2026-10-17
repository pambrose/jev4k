# Release notes

Narrative notes for each jev4k release, newest first. The itemized list of changes is in
[CHANGELOG.md](CHANGELOG.md).

## v0.3.0 — 2026-10-16

> [!WARNING]
> **One source break: `Question` and `Answer` have new subtypes.** `BoxQuestion` and `BoxAnswer` join them, so a
> Kotlin `when` or a Java pattern `switch` over either that lists every subtype, with no `else` or `default` branch,
> no longer compiles. In Kotlin, add an `else` branch, or a branch for the new type, which needs the
> `@ExperimentalJevImages` opt-in. In Java, add a `default ->` branch, or a `case` for the new type, which Java can
> name without any opt-in. Nothing else breaks: other code compiles unchanged, and the binary API only grows.

jev4k now sends images. TypeSafe's image support and its Box question type are experimental: TypeSafe calls them
"Dreamer-ready", meaning the request format, the answers and the limits can change between its releases. jev4k puts
them behind an opt-in, `@ExperimentalJevImages`, and may change them in any release too.

### Upgrading

- Gradle builds: `com.pambrose.jev4k:jev4k:0.3.0`
- Maven builds: `com.pambrose.jev4k:jev4k-jvm:0.3.0`

Apart from the source break above, code that compiled against 0.2.2 compiles against 0.3.0. `kotlinx-io-core`, which
Ktor already brought in, is now a declared dependency, so the JVM POM lists five compile dependencies.

### Images

A `JevImage` holds a PNG or a JPEG, from bytes, base64 or a file. It stands in your content as a reference string, so
it goes anywhere text does: in the state, in a question's instructions, and in its criteria. Attach it, and its bytes
are sent with the request:

```kotlin
@OptIn(ExperimentalJevImages::class)
suspend fun findMug(jev: JevApi, bytes: ByteArray) {
    val photo = JevImage.of(bytes)
    val result =
        jev.query(jsonOf(mapOf("photo" to photo)), model = "jev-preview") {
            attach(photo)
            noul("mug_visible", "Is there a coffee mug in `photo`?")
            box("mug", "the coffee mug in `photo`")
        }
    if (result.noul("mug_visible").isTrue()) println(result.box("mug").box)
}
```

- **Attaching.** A `JevQuery` can attach its own pictures, such as the examples that define a Choice's options, and
  `ask(query, state, images...)` adds a call's images after them. From Java, the blocking version is
  `askWithImages`.
- **Sending.** A request with images goes out as `multipart/form-data`, as the official SDKs send it, with the
  `TypeSafe-Danger-Zone-Images: true` header TypeSafe requires. A request without images is sent exactly as before.
- **Checking.** Before sending, jev4k reports an image it can't use, an attached image nothing references, a
  generated reference placed without its image attached, and a Box question with no image. The API enforces its own
  size and pixel limits.
- **Box.** A Box question answers with a `BoundingBox`, each edge a fraction of the image's size, and a confidence.
  It always returns a box, even for a target that isn't there, so pair it with a Noul. Box needs Jev 1.14
  (`jev-preview` or `jev-1.14.0`).

The [Images & Box](https://jev4k.com/queries/images/) page covers the rest, from chosen references to cropping to a
box, and has a Java example. For a quick try, `make hotdog-example` sends two bundled photos, a dog panting in the sun
on a hot day and an actual hot dog, and Jev tells them apart: "Not hotdog" for the dog, "Hotdog!" for the hot dog.

### Also in this release

- **Deep JSON fails before sending.** The API now rejects a state, or an instructions or criteria value, nested more
  than 64 levels deep. jev4k checks the same limit, so such a request fails at once with a `JevValidationException`
  instead of reaching the server for a 400.
- **Long context.** The README and the Making Calls page show TypeSafe's `TypeSafe-Danger-Zone-Long-Context: true`
  header, which raises both context limits to 262k tokens.
- **Jev 1.14.** TypeSafe released `jev-1.14.0` on 2026-10-14 as `jev-preview`, and `jev-latest` moves to it about a
  week later. Examples that pin a version now use `jev-1.14.0`. If you've tuned thresholds on `jev-1.13.0`, pin it
  until you've checked them against 1.14.

**Full Changelog**: https://github.com/pambrose/jev4k/compare/0.2.2...0.3.0

## v0.2.2 — 2026-10-12

A maintenance release. The library's API and behavior are exactly as in 0.2.1, and the one dependency that moves is
`kotlin-stdlib`, to 2.4.21, since jev4k is now built with Kotlin 2.4.21. Upgrading means changing the version number
and nothing else:

- Gradle builds: `com.pambrose.jev4k:jev4k:0.2.2`
- Maven builds: `com.pambrose.jev4k:jev4k-jvm:0.2.2`

The rest is tooling. A new zizmor workflow audits the GitHub Actions workflows on every pull request, and fixing
what it found hardened CI: no checkout leaves its token behind, the workflow that publishes jev4k.com restores no
cache that another run could have written, and Dependabot waits a week before proposing a new action release. The
build moves to Gradle 9.8.1, and the documentation site to Zensical 0.0.69. The changelog lists each change.

**Full Changelog**: https://github.com/pambrose/jev4k/compare/0.2.1...0.2.2

## v0.2.1 — 2026-10-04

A maintenance release with no changes to the library: its API, behavior and dependencies are exactly as in 0.2.0.
Upgrading means changing the version number and nothing else:

- Gradle builds: `com.pambrose.jev4k:jev4k:0.2.1`
- Maven builds: `com.pambrose.jev4k:jev4k-jvm:0.2.1`

The build behind it has moved forward: Gradle 9.8.0, Kover 0.9.11, newer CI actions, a newer Zensical for the
documentation site, and a patched `brace-expansion` in the Node.js test toolchain. It also no longer warns about
Dokka's deprecated `suppressInheritedMembers` setting. The changelog lists each version change.

**Full Changelog**: https://github.com/pambrose/jev4k/compare/0.2.0...0.2.1

## v0.2.0 — 2026-09-28

> [!WARNING]
> **Breaking change: the Maven coordinates have moved.** The group is now `com.pambrose.jev4k`, so every build
> has to change its dependency to upgrade: Gradle builds to `com.pambrose.jev4k:jev4k:0.2.0`, Maven builds to
> `com.pambrose.jev4k:jev4k-jvm:0.2.0`. 0.1.0 stays at `com.pambrose:jev4k`, and nothing newer will be published
> there.
>
> **Most code needs nothing else.** The client behaves as before, on Java 17 bytecode, with two fewer runtime
> dependencies (`ktor-client-content-negotiation` and `ktor-serialization-kotlinx-json`).
> Since every build has to change anyway, 0.2.0 also makes a few small API changes, each of which affects only some
> code:
>
> - A plain `http://` `baseUrl` on a host other than this machine now needs `allowInsecureHttp = true`.
> - `JevApiException.headers` has lowercased names on every platform: look up `"retry-after"`, not `"Retry-After"`.
> - `models()` returns a `ModelList`, which is a `List<ModelInfo>`. A fake or mock `JevApi` returns
>   `ModelList(listOf(...))`.
> - Java code calling `BlockingJev` catches or declares `InterruptedException`, which every blocking call now
>   declares.
> - `jevApiException` takes `headers` before `retryAfter`; calls that name their arguments are unaffected.
>
> Some failures that used to escape as raw Ktor or JDK exceptions are now `JevException`s; the changelog lists them.

jev4k is now a Kotlin Multiplatform library. The same questions, handles and typed answers work on the JVM, on
Apple platforms, on Linux and Windows, and on Node.js.

### Installing

Gradle builds, JVM or multiplatform, depend on the root module, and Gradle picks the artifact for each target:

```kotlin
dependencies {
    implementation("com.pambrose.jev4k:jev4k:0.2.0")
}
```

**Maven builds also switch to `jev4k-jvm`.** `com.pambrose.jev4k:jev4k` is the multiplatform root module, which
only Gradle knows how to resolve:

```xml
<dependency>
    <groupId>com.pambrose.jev4k</groupId>
    <artifactId>jev4k-jvm</artifactId>
    <version>0.2.0</version>
</dependency>
```

Because the group changed, tools that check for dependency updates won't offer 0.2.0 as an upgrade to
`com.pambrose:jev4k`. And if another library brings in 0.1.0 alongside 0.2.0, Gradle treats the two as unrelated
modules and puts both on the classpath, so exclude the old one:

```kotlin
configurations.all {
    exclude(group = "com.pambrose", module = "jev4k")
}
```

### New in the API

- **Per-call options.** `JevCallOptions` overrides the timeout, retry policy and headers for the calls that need
  something else, as both official SDKs allow, without a second client:

  ```kotlin
  val interactive = JevCallOptions {
      timeout = 2.seconds
      retry = RetryPolicy.NONE
  }
  val result = jev.withOptions(interactive).ask(Triage, state = ticket)
  ```

- **Request ids for `models()`.** It returns a `ModelList`, which carries the call's `requestId` as `evaluate`'s
  result does.
- **Blocking calls for any `JevApi`.** `api.blocking()` wraps a fake as readily as a client, so blocking and Java
  code can be tested without HTTP.
- **Java reaches every setting.** Each `Duration` setting has a millisecond counterpart (`setTimeoutMillis`,
  `RetryPolicy.withInitialBackoffMillis`, `getRetryAfterMillis`, …), and `RetryPolicy`'s `with…` methods build any
  policy.

### Platforms

| Platform | Default engine          |
|----------|-------------------------|
| JVM      | CIO                     |
| Apple    | Darwin (`NSURLSession`) |
| Linux    | Curl                    |
| Windows  | WinHttp                 |
| Node.js  | Js (`fetch`)            |

A few things differ by platform. Blocking calls (`jev.blocking`) exist on the JVM only. The js and wasmJs artifacts
are built and tested for Node.js; they would also load in a browser, but a page would hand the API key to every
visitor, so a browser app should call a backend of its own. On Linux, the Curl engine needs the system's CA
certificates, and on iOS, App Transport Security blocks a plain `http://` base URL, such as a local Ollaya server,
unless the app allows it.

Every engine's way of reporting a failed connection is mapped to `JevConnectionException` and retried, so error
handling written against the JVM client works unchanged elsewhere, with one difference. Curl and WinHttp report a
failed connection as a bare `IllegalStateException`, so on Linux and Windows any bare `IllegalStateException` raised
during a call is treated as one: retried, then reported as a `JevConnectionException` with it as the cause. That
includes one thrown by a caller-supplied engine or a `MockEngine` handler, which the JVM and Apple platforms rethrow
unchanged.

**Full Changelog**: https://github.com/pambrose/jev4k/compare/0.1.0...0.2.0

## v0.1.0 — 2026-09-20

The first release of jev4k, a Kotlin DSL and client for [TypeSafe](https://docs.typesafe.ai)'s **Jev** model.

Jev is a *System One* model: it doesn't generate text. You give it a **state** — a message, a document, a
record — and a set of typed **questions**, and it returns typed **answers** with calibrated probabilities that
your code can branch on, sort by, and threshold. jev4k lets you declare those questions in Kotlin, send them in
one request, and read the answers back as typed values.

### Two ways to ask

Questions can be written inline, with ids you choose:

```kotlin
val result = jev.query(state = ticket) {
    noul("refund", "Does the customer explicitly ask for a refund or credit?")
    score("severity", "How severe is the reported issue?") {
        levels("Cosmetic", "Broken but there's a workaround", "Blocking, no workaround")
    }
}
result.noul("refund").isTrue()
```

Or declared once as a reusable typed query, where each property is a handle to its own answer:

```kotlin
object Triage : JevQuery() {
    val urgent by noul("Does this message convey urgency?")
    val team by choice<Team>("Which team should handle this message?")
}

when (jev.ask(Triage, state = ticket)[Triage.team].choice) {
    Team.BILLING -> routeToBilling()
    Team.TECHNICAL -> pageOnCall()
    Team.SALES -> routeToSales()
}
```

Both styles produce the same validated `QuestionSet`, and both can go in the same request. Enum-valued Choices
come back as enum constants rather than strings.

### What's in it

- **Three question types.** Noul asks whether something is true, Choice picks among options, and Score places
  the state on ordered levels. Each has its own answer type, with probabilities and, for Choice and Score, a
  confidence summary.
- **Typed answers.** `result[handle]` decodes to the handle's type and verifies the handle belongs to the
  request. By-id accessors check that the question type matches what was asked.
- **Validation before the wire.** Every problem in a question set — blank ids, duplicate ids, empty
  instructions, too few Score levels — is collected into one `JevValidationException` before anything is sent.
- **A client that behaves under load.** Retries follow TypeSafe's official SDK rules: 408, 429 and 5xx,
  connection errors and timeouts, backoff from 0.5 s doubling to 5 s with jitter, and `Retry-After` hints
  honored up to 60 s. Non-2xx responses become typed exceptions that keep the raw body and the request id.
- **Structured state.** A state can be a `String`, a `JsonElement`, or any `@Serializable` value.
- **Testing helpers.** `jevResult(...)` and `jevApiException(...)` build results and failures without HTTP, so
  code that consumes jev4k can be tested against a mocked `JevApi`.
- **Java callers.** The inline builder DSL works from Java through `jev.getBlocking()`. Typed `JevQuery`
  objects and the reified `choice<E>()` helpers stay Kotlin-only; the README explains what that rules out.

### Installing

```kotlin
dependencies {
    implementation("com.pambrose:jev4k:0.1.0")
}
```

Set `TYPESAFE_API_KEY` in the environment, or pass an API key to `JevClient`. `TYPESAFE_BASE_URL` and
`TYPESAFE_DEFAULT_MODEL` override the defaults.

### Requirements

Java 17 or newer. The library ships Java 17 bytecode, built with a JDK 25 toolchain. It brings in Ktor's CIO
engine by default, which can be excluded in favor of another Ktor engine, and no logging binding.

### Documentation

The documentation site is at <https://jev4k.com/>, with KDocs at <https://jev4k.com/kdocs/> and an agent-readable
index at <https://jev4k.com/llms.txt>.

**Full Changelog**: https://github.com/pambrose/jev4k/commits/0.1.0
