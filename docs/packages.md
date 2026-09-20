# Module jev4k

A Kotlin DSL and client for TypeSafe's Jev "System One" model, built on the Ktor client and
kotlinx.serialization. You send a *state* (text, JSON, or any `@Serializable` value) and a set of typed *questions*. Jev
answers each question with a typed judgment and probabilities that code can branch on,
without generating any text.

There are three question types:

| Question | Answer                                                                                                           | Use it when                             |
|----------|------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| Noul     | [NoulAnswer][com.pambrose.jev4k.NoulAnswer]: the probability of yes                                              | a yes/no condition                      |
| Choice   | [ChoiceAnswer][com.pambrose.jev4k.ChoiceAnswer]: the option chosen, per-option probabilities, confidence         | one of a known set of options           |
| Score    | [ScoreAnswer][com.pambrose.jev4k.ScoreAnswer]: a probability-weighted level, per-level probabilities, confidence | a position on ordered, described levels |

## Two ways to ask

Declare a reusable [JevQuery][com.pambrose.jev4k.JevQuery], where property names become question ids and
handles read typed answers:

```kotlin
object Triage : JevQuery() {
    val urgent by noul("Does this message convey urgency?")
    val department by choice<Dept>("Which team should handle this?")
    val frustration by score("How frustrated is the customer?") {
        levels("Calm", "Frustrated but civil", "Very angry")
    }
}

JevClient().use { jev ->
    val result = jev.ask(Triage, state = ticket)
    when (result[Triage.department].choice) { /* ... */ }
}
```

Or ask inline with string ids through [query][com.pambrose.jev4k.query]:

```kotlin
val result = jev.query(state = ticket) {
    noul("refund", "Does the customer explicitly request a refund?")
    include(Triage)
}
result.noul("refund").noul
```

## Entry points

- [JevClient][com.pambrose.jev4k.JevClient] is the client, configured with
  [JevConfigBuilder][com.pambrose.jev4k.JevConfigBuilder] or the `TYPESAFE_API_KEY`, `TYPESAFE_BASE_URL` and
  `TYPESAFE_DEFAULT_MODEL` environment variables. [JevClient.blocking][com.pambrose.jev4k.JevClient.blocking]
  offers the same calls without coroutines.
- [JevApi][com.pambrose.jev4k.JevApi] is the interface to depend on (and mock in tests);
  [evaluate][com.pambrose.jev4k.JevApi.evaluate] is the call every question goes through, and
  [models][com.pambrose.jev4k.JevApi.models] is its only other network call.
- [JevResult][com.pambrose.jev4k.JevResult] holds the answers, the reported model, token usage and the request id.
- [RetryPolicy][com.pambrose.jev4k.RetryPolicy] sets retries; the defaults match the official SDKs.
- [JevException][com.pambrose.jev4k.JevException] is the base of every error: local validation, API status
  errors, malformed responses and connection failures.

# Package com.pambrose.jev4k

The whole public API: the question DSL, typed answers, the client, configuration and errors.
