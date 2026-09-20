---
icon: lucide/send
---

# Making Calls

## Suspending calls

`JevClient` implements the `JevApi` interface. Its calls are `suspend` functions, so call them from a coroutine:

```kotlin
--8<-- "ClientExamples.kt:suspend"
```

| Call                                 | Does                                                                   |
|--------------------------------------|------------------------------------------------------------------------|
| `evaluate(state, questions, model?)` | sends a `QuestionSet` about a JSON state; everything else builds on it |
| `query(state, model?) { ... }`       | builds questions inline, then evaluates them                           |
| `ask(query, state, model?)`          | evaluates a `JevQuery`'s questions                                     |
| `models()`                           | lists the model names your account can use                             |

`query` and `ask` are extension functions on `JevApi`. Import them with
`import com.pambrose.jev4k.query` and `import com.pambrose.jev4k.ask`; IDEs add these automatically.

```kotlin
--8<-- "ClientExamples.kt:evaluate"
```

## Blocking calls

`jev.blocking` mirrors every call without coroutines, for scripts, `main`, tests and Java callers:

```kotlin
--8<-- "ClientExamples.kt:blocking"
```

Blocking calls block the calling thread. Don't use them from inside a coroutine.

## Choosing a model

Every call takes an optional `model`, which overrides the client's `defaultModel` for that request:

```kotlin
--8<-- "ClientExamples.kt:model"
```

`jev-latest` always points at the newest stable model, so its answers can shift when TypeSafe ships a release.
Once you've tuned thresholds against a model, pin its version (for example `jev-1.13.0`) and move to a new one
deliberately.

## Concurrency

Suspending calls make it easy to process many states at once. Bound the parallelism, though: TypeSafe's
examples found that about 8 concurrent requests on a shared key already hit rate limits.

```kotlin
--8<-- "ClientExamples.kt:concurrency"
```

Rate-limited requests are retried automatically (see [Retries & Errors](errors.md)), but staying under the
limit is faster than being retried.

Before reaching for concurrency, check whether the questions could go in one request instead. Questions about
the same state are answered in parallel for the cost of one request; see
[Speculative Fan-Out](../patterns/fan-out.md).

## Designing for testability

Depend on the `JevApi` interface rather than `JevClient`, and pass it in:

```kotlin
--8<-- "ClientExamples.kt:testable"
```

`query` and `ask` both go through `JevApi.evaluate`, so a fake or mock that implements `evaluate` covers all
three, without a network. `models()` is the interface's other network call; stub it too if your code lists
models. To exercise the real client without a network, give it a Ktor `MockEngine` through the `engine` setting.

Build what the fake returns with `jevResult(body, questions)`, which maps a response body exactly as the client
does, and `jevApiException(status)` for the error path. Recording a real response and replaying it keeps a
fixture honest, and a malformed one still raises `JevResponseValidationException`.
