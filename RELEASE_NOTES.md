# Release notes

Narrative notes for each jev4k release, newest first. The itemized list of changes is in
[CHANGELOG.md](CHANGELOG.md).

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
