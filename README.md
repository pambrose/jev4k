# jev4k

[![GitHub release](https://img.shields.io/github/v/release/pambrose/jev4k)](https://github.com/pambrose/jev4k/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose/jev4k)](https://central.sonatype.com/artifact/com.pambrose/jev4k)
[![CI](https://github.com/pambrose/jev4k/actions/workflows/ci.yml/badge.svg)](https://github.com/pambrose/jev4k/actions/workflows/ci.yml)
[![Documentation](https://github.com/pambrose/jev4k/actions/workflows/docs.yml/badge.svg)](https://jev4k.com/)
[![codecov](https://codecov.io/gh/pambrose/jev4k/branch/master/graph/badge.svg)](https://codecov.io/gh/pambrose/jev4k)
[![Kotlin version](https://img.shields.io/badge/kotlin-2.4.20-red?logo=kotlin)](http://kotlinlang.org)
[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081)](https://pinterest.github.io/ktlint/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A Kotlin DSL and client for [TypeSafe](https://docs.typesafe.ai)'s **Jev** model.

Jev is a *System One* model: it doesn't generate text. You give it a **state** (a message, a document, a record) and a
set of typed **questions**, and it returns typed **answers** with calibrated probabilities that your code can branch on,
sort by, and threshold. jev4k lets you declare those questions in Kotlin, send them in one request, and read the answers
back as typed values, including enum-valued choices.

```kotlin
object Triage : JevQuery() {
    val urgent by noul("Does this message convey urgency?")
    val team by choice<Team>("Which team should handle this message?")
    val frustration by score("How frustrated is the customer?") {
        levels("Calm, just stating facts", "Frustrated but civil", "Very angry, strong language")
    }
}

JevClient().use { jev ->
    val result = jev.ask(Triage, state = "Help! My payouts have been failing for 3 days.")
    when (result[Triage.team].choice) {
        Team.BILLING -> routeToBilling()
        Team.TECHNICAL -> pageOnCall()
        Team.SALES -> routeToSales()
    }
}
```

## Contents

- [Documentation](#documentation)
- [Quick start](#quick-start)
- [Questions and answers](#questions-and-answers)
- [Defining questions](#defining-questions)
- [State](#state)
- [Reading results](#reading-results)
- [Client and configuration](#client-and-configuration)
- [Embedding in an application](#embedding-in-an-application)
- [Errors](#errors)
- [Testing code that uses jev4k](#testing-code-that-uses-jev4k)
- [Writing good questions](#writing-good-questions)
- [Development](#development)
- [Thanks to TypeSafe](#thanks-to-typesafe)
- [License](#license)

## Documentation

📖 **The jev4k website is at <https://jev4k.com/>**. It has many more examples than this page.

|                                                               |                                                     |
|---------------------------------------------------------------|-----------------------------------------------------|
| [Documentation site](https://jev4k.com/)                      | Concepts, every question type, patterns, and guides |
| [Quick Start](https://jev4k.com/getting-started/quick-start/) | An API key and a first query in a few lines         |
| [API reference (KDocs)](https://jev4k.com/kdocs/)             | Dokka-generated docs for every public type          |
| [llms.txt](https://jev4k.com/llms.txt)                        | An index of the site for coding agents              |
| [Changelog](CHANGELOG.md)                                     | What changed in each release                        |
| [Release notes](RELEASE_NOTES.md)                             | Narrative notes for each release                    |

## Quick start

1. Get an API key from the [TypeSafe console](https://console.typesafe.ai/settings/keys) and export it:

   ```bash
   export TYPESAFE_API_KEY=ts-...
   ```

2. Ask questions about a piece of text:

   ```kotlin
   import com.pambrose.jev4k.JevClient
   import com.pambrose.jev4k.query

   suspend fun main() {
       JevClient().use { jev ->
           val result = jev.query(state = "Hi, my Stripe connection has failed for 3 days and I'm losing sales. Help ASAP!") {
               noul("urgent", "Does this message convey urgency or time-sensitivity?")
               choice("department", "Which team should handle this?") {
                   "billing" means "Payment or subscription issues"
                   "technical" means "Bugs or integration problems"
                   "sales" means "Pricing or account questions"
               }
               score("frustration", "How frustrated does the customer appear?") {
                   levels("Calm, just stating facts", "Frustrated but civil", "Very angry, strong language")
               }
           }

           println(result.noul("urgent").noul)         // 0.999: probability of yes
           println(result.choice("department").choice) // "technical"
           println(result.score("frustration").score)  // 1.04: position along the levels
       }
   }
   ```

All the questions in one request are answered in parallel, in about 100 ms, and each is judged independently. Asking one
more question costs only its tokens, not another round trip.

## Questions and answers

There are three question types. Each returns its own answer type.

| Question   | Asks                           | Answer            | Key fields                                                                                                               |
|------------|--------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------------|
| **Noul**   | Is this true?                  | `NoulAnswer`      | `noul`: probability of yes, 0 to 1                                                                                       |
| **Choice** | Which of these options?        | `ChoiceAnswer<K>` | `choice`, `probabilities` (one per option), `confidence`                                                                 |
| **Score**  | Where on these ordered levels? | `ScoreAnswer`     | `score` (probability-weighted level, can fall between levels), `probabilities` and `legend` keyed by level, `confidence` |

- A Noul value near 0.5 means yes and no are about equally likely. It doesn't mean "medium"; use a Score to measure
  degree.
- `confidence` (Choice and Score only) summarizes how concentrated the probabilities are. It isn't the winning option's
  probability. Nouls have no confidence.
- Every answer is limited to the options or levels you defined.

The question definitions themselves are `NoulQuestion`, `ChoiceQuestion` and `ScoreQuestion`, subtypes of `Question`,
named after TypeSafe's JS SDK. The DSL builds them for you.

## Defining questions

There are two styles, and both produce the same `QuestionSet`.

### Inline, with string ids

`query` takes a builder block. Each question has an id you choose. The id is only for your code and is never sent to the
model, so the instructions must state the whole question.

```kotlin
val result = jev.query(state = ticket) {
    noul("refund", "Does the customer explicitly ask for a refund or credit?") {
        whenTrue("Directly asks for money back or an account credit")
        whenFalse("A complaint or question with no requested remedy")
    }
    choice("tone", "What is the customer's tone?") {
        options("calm", "frustrated", "angry")          // undescribed options are sent as null
    }
    choice("topic", "Which returns topic is the customer asking about?") {
        "return_policy" means "Whether and how an item can be returned"
        option("return_status", "Progress of a return already sent")
        option("other")
    }
    score("severity", "How severe is the reported issue?") {
        level("Cosmetic; no impact to functionality")
        level("Broken or degraded feature, but a workaround exists")
        level("Blocking issue; no workaround exists")
    }
}
result.noul("refund").isTrue()
result.choice("topic").choice
```

Builder functions also return handles, so `val refund = noul(...)` followed by `result[refund]` works too. Loops work
naturally, for example one Noul per item in a list.

### Reusable, typed queries

Declare questions as properties of a `JevQuery`. Each property name becomes the question id, and the property is a typed
handle to its answer.

```kotlin
enum class Team(override val description: String) : JevOption {
    BILLING("Payments, invoicing, refunds"),
    TECHNICAL("Bugs, outages, integrations"),
    SALES("Pricing, upgrades, new accounts"),
}

object Triage : JevQuery() {
    val urgent by noul("Does this message convey urgency?") {
        whenTrue("Explicitly time-sensitive, or the customer is blocked right now")
    }
    val team by choice<Team>("Which team should handle this message?")
    val frustration by score("How frustrated is the customer?") {
        levels("Calm, just stating facts", "Frustrated but civil", "Very angry, strong language")
    }
    val tone by choice("What is the customer's tone?", id = "customer_tone") {
        options("calm", "frustrated", "angry")
    }
}

val result = jev.ask(Triage, state = ticket)
val team: ChoiceAnswer<Team> = result[Triage.team]      // team.choice is a Team
val urgent: NoulAnswer = result[Triage.urgent]
```

- `id =` overrides the property name.
- Questions keep their declaration order, and a subclass's questions come after its base class's.
- A query can be a `class` with constructor parameters, for example to build instructions per record.
- `include(Triage)` inside an inline `query { }` asks a query's questions in the same request as ad-hoc ones, and the
  typed handles still work on the result.

**Enum choices.** `choice<E>()` offers every constant of `E` as an option.

- The option key sent to the model is the constant's name.
- Implement `JevOption` to add a `description`, a structured `entry`, or an `optionKey` that sends a different key:

  ```kotlin
  enum class Plan : JevOption {
      FREE, PRO, TEAM;
      override val optionKey get() = name.lowercase()      // the model sees "free", "pro", "team"
  }
  ```

  Option names are part of what the model reads, so choose ones that describe the option.

### Structured instructions and criteria

Instructions, option descriptions, levels, and Noul criteria can be structured JSON instead of plain strings. The field
names are yours; the model sees both names and values. This is the documented way to sharpen boundaries between similar
options.

```kotlin
choice(
    "department",
    entry("question" to "Which team should handle this?", "focus" to "The customer's primary request")
) {
    "billing" means rubric(
        what = "Charges, invoices, refunds, or subscriptions",
        notFor = "Order tracking or account access",
        examples = listOf("I was charged twice", "Where is my refund?"),
    )
    "orders" means rubric("Order status, delivery, cancellation, or returns", notFor = "Charges or account access")
}
```

- `entry(...)` builds a JSON object from key/value pairs.
- `rubric(what, notFor, examples)` builds `{what, not_for, examples}`.
- `jsonOf(value)` converts plain Kotlin values.
- `jsonEntry(value)` encodes any `@Serializable` value.
- kotlinx `buildJsonObject { }` works as well.

To point a question at part of a structured state, name the field with a backticked path, as in
``"Does `ticket.messages[0].text` request a refund?"``.

### Limits

These are checked locally before anything is sent, and every problem is reported in one `JevValidationException`:

- at least one question per request
- unique, non-blank ids
- non-empty instructions
- a Choice has 1 to 255 options (`MAX_CHOICE_OPTIONS`)
- a Score has 2 to 10 levels (`MIN_SCORE_LEVELS`..`MAX_SCORE_LEVELS`)

## State

The state is what Jev reads. `query` and `ask` accept three forms:

```kotlin
jev.ask(Triage, state = "Plain text, e.g. a message or document")

jev.ask(Triage, state = buildJsonObject {                     // a JsonElement
    put("ticket", ticketText)
    put("refund_policy", policyText)
})

@Serializable
data class Order(val id: String, val status: String = "open")
jev.ask(Triage, state = Order("A-104"))                       // any @Serializable value
```

`@Serializable` values keep fields that equal their defaults (`"status": "open"` above), so the model sees them.

Prefer an object with named fields when the state has several parts. Send only what the questions need: unrelated detail
lowers accuracy. Jev reads text only, and English works best.

## Reading results

`query` and `ask` return a `JevResult`:

| Member                                  | Returns                                                                         |
|-----------------------------------------|---------------------------------------------------------------------------------|
| `result[ref]`                           | The typed answer for a handle, e.g. `ChoiceAnswer<Team>`                        |
| `noul(id)`, `choice(id)`, `score(id)`   | An answer by string id                                                          |
| `enumChoice<E>(id)`                     | A Choice by id, as constants of `E`                                             |
| `nouls`, `choices`, `scores`, `answers` | All answers, by id                                                              |
| `model` / `requestedModel`              | The model that answered, as reported, and the name you sent (e.g. `jev-latest`) |
| `usage`                                 | `inputTokens` / `outputTokens`                                                  |
| `requestId`                             | The `x-typesafe-request-id` header, for support tickets and logs                |
| `raw`                                   | The response body as received                                                   |

The answer types have helpers for common decisions:

```kotlin
result[Triage.urgent].isTrue(threshold = 0.7) // noul > 0.7
result[Triage.urgent].band()                  // NO (< 0.30), UNCERTAIN, or YES (> 0.70)

val team = result[Triage.team]
team.topProbability          // largest option probability
team.probability(Team.SALES) // 0.0 if absent
team.ranked()                // options, most likely first

val frustration = result[Triage.frustration]
frustration.normalized      // score / (levels - 1), 0..1, for weighting Scores together
frustration.nearestLevel    // score rounded to a level
frustration.mostLikelyLevel // level with the highest probability
frustration.legendText(2)   // "Very angry, strong language"
```

Asking for an id or handle that wasn't in the request, or reading a Noul as a Choice, throws `IllegalArgumentException`.
A missing or malformed answer from the server throws `JevResponseValidationException`, with a field path such as
`answers.urgent.noul`.

**Gate on confidence where it matters.** Choose thresholds per action, tuned on your own data. A read-only action can go
ahead at lower confidence than a destructive one:

```kotlin
val action = result[Intent.action]
when {
    action.confidence < 0.6 -> routeToHuman()
    action.choice == Action.CHECK_BALANCE -> showBalance()
    action.choice == Action.APPROVE_TRANSFER && action.confidence > 0.85 -> approve()
    else -> askUserToConfirm()
}
```

## Client and configuration

`JevClient` implements `JevApi`. Its calls are `suspend` functions, and `jev.blocking` offers the same calls for
scripts, `main`, and tests.

| Suspending                         | Blocking                                    |
|------------------------------------|---------------------------------------------|
| `jev.ask(query, state)`            | `jev.blocking.ask(query, state)`            |
| `jev.query(state) { ... }`         | `jev.blocking.query(state) { ... }`         |
| `jev.evaluate(state, questionSet)` | `jev.blocking.evaluate(state, questionSet)` |
| `jev.models()`                     | `jev.blocking.models()`                     |

`evaluate` is the single call the others build on. It takes a JSON state and a `QuestionSet` from `questions { ... }` or
`someQuery.questions`. `models()` lists the model names your account can use.

Each call accepts `model = "..."` to override the default for that request. `jev-latest` points at the newest stable
model. If you've tuned thresholds against a particular version, pin it by name, e.g. `jev-1.13.0`, because an alias can
move to a new model.

Close the client when done. `use { }` does this for you.

### Configuration

```kotlin
val jev = JevClient {
    apiKey = "ts-..."                   // default: TYPESAFE_API_KEY (required)
    baseUrl = "https://api.typesafe.ai" // default: TYPESAFE_BASE_URL, then this
    defaultModel = "jev-latest"         // default: TYPESAFE_DEFAULT_MODEL, then this
    timeout = 10.seconds                // per HTTP attempt
    retry = RetryPolicy(maxRetries = 3)
    headers["X-Team"] = "support"       // extra headers on every request
}
```

Each setting resolves as: the explicit value, then the environment variable, then the default. Blank environment
variables are ignored, and `JevConfig.toString()` never prints the key.

### Running with Ollaya

jev4k works with [Ollaya](https://ollaya.dev), which serves the same API from your own machine. Ollaya runs Laya, a
different model from Jev, so answers can differ from TypeSafe's. No code changes are needed; point the client at it
with three environment variables:

```bash
TYPESAFE_API_KEY=demo
TYPESAFE_BASE_URL=http://localhost:11435
TYPESAFE_DEFAULT_MODEL=laya
```

### Retries and timeouts

`RetryPolicy`'s defaults match TypeSafe's official Python and JS SDKs:

| Setting                                     | Default                                                         |
|---------------------------------------------|-----------------------------------------------------------------|
| `maxRetries`                                | 2 retries after the first attempt                               |
| `retryStatuses`                             | 408, 429, and 500–599 (including 529 Overloaded)                |
| `retryOnConnectionError` / `retryOnTimeout` | `true` / `true`                                                 |
| `initialBackoff` / `maxBackoff` / `jitter`  | 0.5 s, doubling up to 5 s, minus up to 25% jitter               |
| `respectRetryAfter` / `maxRetryAfter`       | Honor the server's `retry-after-ms` / `Retry-After`, up to 60 s |

`RetryPolicy.NONE` disables retries.

### Concurrency

The suspend API lets you run requests concurrently, but keep the fan-out modest. TypeSafe's cookbooks report that about
8 concurrent requests on one key already hit rate limits.

```kotlin
val limit = Semaphore(4)
val results = coroutineScope {
    tickets.map { t -> async { limit.withPermit { jev.ask(Triage, state = t) } } }.awaitAll()
}
```

## Embedding in an application

jev4k is meant to be embedded in an application, so it keeps out of the host's way.

### What it puts on your classpath

Four `compile` dependencies (`ktor-client-core`,
`kotlinx-serialization-json`, `kotlinx-coroutines-core`, `kotlin-stdlib`) and three `runtime` ones (`ktor-client-cio`,
`ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`). Nothing else: no test
framework, no logging backend.

### Logging

jev4k never logs. It writes nothing to stdout or stderr, installs no Ktor `Logging` plugin, and ships
no SLF4J binding, so it can't interfere with your logging setup. `slf4j-api` reaches the classpath through Ktor,
not jev4k; supply your own binding if you want Ktor's own output.

### Your own engine

Pass one as `engine` and jev4k uses it instead of CIO. Closing a `JevClient` never closes an
engine you supplied, so several clients can share one. If you do supply an engine, CIO can be dropped:

```kotlin
dependencies {
    implementation("com.pambrose:jev4k:0.1.0") {
        exclude(group = "io.ktor", module = "ktor-client-cio-jvm")
    }
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
}
```

### Calling from Java

jev4k is a Kotlin library, but the inline builder DSL works from Java through `jev.getBlocking()`:

```java
JevClient jev = new JevClient(builder -> {
    builder.setApiKey(System.getenv("TYPESAFE_API_KEY"));
    return Unit.INSTANCE;
});

JevResult r = jev.getBlocking().query("The payout failed again and I need this fixed today.", null, qb -> {
    qb.noul("urgent", "Does this message convey urgency?");
    return Unit.INSTANCE;
});

double urgency = r.noul("urgent").getNoul();
```

That exact code is [`JavaInterop.java`](src/test/java/website/JavaInterop.java), compiled with the test sources
so it can't drift.

Two Kotlin features don't cross to Java: property delegates, which a typed `JevQuery` is built from, and
`inline reified` functions, which the Kotlin compiler emits as synthetic members that javac can't resolve. So
four things are out of reach from Java:

- **Typed `JevQuery` objects** can't be declared. One declared in Kotlin can still be passed to `ask`.
- **`@Serializable` states.** A state must be a `String` or a `JsonElement`; the reified `query`, `ask` and
  `jsonEntry` overloads are hidden rather than compiling into a runtime failure.
- **Enum Choices through the DSL.** `QueryBuilder.choice<E>()` is reified and `enumChoiceRef` is `internal`, so
  there's no route to one. Build a `ChoiceQuestion` with the option keys you want and add it with
  `QueryBuilder.question(id, question)` instead.
- **`JevResult.enumChoice<E>(id)`** is reified too. Read that answer with `result.choice(id)`, which is keyed by
  option string.

Everything else is callable: `evaluate`, `models`, the inline `noul`, `choice` and `score` builders, the other
result accessors, and enums implementing `JevOption`.

### Module name

The jar declares `Automatic-Module-Name: com.pambrose.jev4k` for JPMS builds.

### Java version

The class files are Java 17 (`org.gradle.jvm.version = 17` in the published metadata), and the
compiler is held to the Java 17 API, so nothing newer can slip in.

### Threads

A `JevClient` is immutable once built and safe to share across coroutines. `jev.blocking` wraps the
suspend calls in `runBlocking`, so call it from ordinary threads, never from inside a coroutine.

## Errors

Every failure of a request or a response is a `JevException`:

| Exception                           | When                                                                                                                  |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `JevConfigException`                | Bad configuration, e.g. no API key                                                                                    |
| `JevValidationException`            | The request broke a [limit](#limits); `problems` lists every issue. Nothing was sent.                                 |
| `JevApiException`                   | A non-2xx response after retries. Carries `status`, `body` (raw), `bodyJson`, `headers`, `requestId`, and `endpoint`. |
| ↳ `JevBadRequestException`          | 400                                                                                                                   |
| ↳ `JevAuthenticationException`      | 401: missing or invalid API key                                                                                       |
| ↳ `JevPermissionDeniedException`    | 403                                                                                                                   |
| ↳ `JevNotFoundException`            | 404                                                                                                                   |
| ↳ `JevUnprocessableEntityException` | 422: the server rejected the request; `body` names the field                                                          |
| ↳ `JevRateLimitException`           | 429; `retryAfter` is the server's hint                                                                                |
| ↳ `JevInternalServerException`      | 5xx                                                                                                                   |
| ↳ ↳ `JevOverloadedException`        | 529: TypeSafe is temporarily overloaded                                                                               |
| ↳ `JevResponseValidationException`  | A 2xx body that was malformed or didn't match the questions; `fieldPath` locates it                                   |
| `JevConnectionException`            | No response: DNS, TLS, or a refused or dropped connection                                                             |
| ↳ `JevTimeoutException`             | An attempt exceeded `timeout`                                                                                         |

Misusing a result is a programming error, not a `JevException`: asking for an id or handle that wasn't in the
request, or reading a Noul as a Choice, throws `IllegalArgumentException`.

## Testing code that uses jev4k

Depend on the `JevApi` interface rather than `JevClient`. `query` and `ask` are extension functions over
`JevApi.evaluate`, so a mock of that one method covers them all:

Build the result the mock returns with `jevResult`, which runs a response body through the same mapping the
client uses, so a recorded response replays exactly as it arrived. `jevApiException` does the same for the
error path:

```kotlin
suspend fun route(jev: JevApi, ticket: Ticket): Team = jev.ask(Triage, state = ticket)[Triage.team].choice

val jev = mockk<JevApi>()
coEvery { jev.evaluate(any(), any(), any()) } returns
    jevResult("""{"answers":{"team":{"type":"choice","choice":"technical","confidence":0.9}}}""", Triage.questions)
route(jev, ticket) shouldBe Team.TECHNICAL

// The same for error handling: 429 gives a JevRateLimitException, 401 a JevAuthenticationException, and so on.
coEvery { jev.evaluate(any(), any(), any()) } throws jevApiException(429, retryAfter = 2.seconds)
```

To exercise the real client without the network, pass a Ktor `MockEngine` as`JevClient { engine = MockEngine { ... } }`.
This project's own tests do both.

## Writing good questions

These points are condensed from TypeSafe's documentation. A compressed copy of the docs is in [
`jev-docs/`](jev-docs/README.md).

- **Ask one snap judgment per question.** "Does this message convey urgency?" is good; "analyze this and decide what to
  do" isn't. Split a broad judgment into narrow questions and combine them in code with weights you control. For Scores,
  `normalized` puts different level counts on the same 0–1 scale.
- **Put every question about a state in one request**, including speculative ones that only matter on some code paths.
  The extra ones cost only their tokens.
- **Describe situations, not degrees.** A Score level such as "Broken, but a workaround exists" works; "moderately
  severe" doesn't. Each level is judged on its own.
- **Offer a way out.** Add an `other` or `none` option when the list might not cover every input. Pair a "which one?"
  Choice with an "is there any?" Noul, because Choice probabilities always sum to 1.
- **Keep arithmetic, counting, and date math in code.** Jev reads meaning, not numbers: ask it for date parts or
  candidate spans, then compute in code.
- **Treat results as probabilistic.** Answers can drift slightly between identical requests, and calibration holds
  across many answers, not for any single one. Validate thresholds against labeled examples from your own domain.

## Development

Building jev4k needs JDK 25; Gradle's toolchain support downloads it if it's missing. The jar itself targets Java
17, so applications on 17 or newer can embed it.

Copy [`.env.example`](.env.example) to `.env` (gitignored) and set `TYPESAFE_API_KEY` in it. Gradle loads that
file into the environment of the test and example tasks, so `make example` and `make live-tests` work without
exporting anything.

```bash
make build                  # compile, without running tests
make tests                  # kotlinter + detekt + unit tests
make lint                   # kotlinter + detekt only
make format                 # auto-format with ktlint
make kdocs                  # API docs in build/dokka/html
make example                # run the example against the live API (needs TYPESAFE_API_KEY)
make live-tests             # smoke tests against the live API (needs TYPESAFE_API_KEY)
make api-docs               # refresh the cached TypeSafe docs in jev-docs/
make site                   # serve the documentation site (website/jev4k) locally
make publish-local-snapshot # publish <version>-SNAPSHOT to ~/.m2
```

The runnable example is [`TriageExample.kt`](src/test/kotlin/com/pambrose/jev4k/examples/TriageExample.kt). The unit
tests use Kotest, MockK, and Ktor's `MockEngine`; one timeout test drives the real CIO engine against a loopback socket,
so no traffic ever leaves the machine. Live tests run only when `TYPESAFE_API_KEY` is set and `JEV4K_LIVE=1`, which
`make live-tests` sets.

## Thanks to TypeSafe

Jev, and the System One idea behind it, come from [TypeSafe](https://typesafe.ai), who have done a great job with
both. Their documentation at [docs.typesafe.ai](https://docs.typesafe.ai) is excellent. jev4k
follows Jev's concepts and naming, so what you learn there carries straight over to this library.

## License

Copyright 2026 Paul Ambrose. Licensed under the [Apache License, Version 2.0](LICENSE).
