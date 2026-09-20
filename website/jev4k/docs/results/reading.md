---
icon: lucide/file-check
---

# Reading Results

`ask`, `query` and `evaluate` return a `JevResult`: one typed answer per question, plus details about the call.

## By handle

With a [typed query](../queries/typed.md), index the result with the question's handle:

```kotlin
--8<-- "ResultExamples.kt:typed"
```

## By id

With the [inline DSL](../queries/inline.md), read answers by the ids you gave them:

```kotlin
--8<-- "ResultExamples.kt:by-id"
```

## Answer helpers

```kotlin
--8<-- "ResultExamples.kt:helpers"
```

| Answer            | Fields                                                         | Helpers                                                              |
|-------------------|----------------------------------------------------------------|----------------------------------------------------------------------|
| `NoulAnswer`      | `noul`                                                         | `isTrue(threshold = 0.5)`, `band(no = 0.30, yes = 0.70)`             |
| `ChoiceAnswer<K>` | `choice`, `probabilities`, `confidence`                        | `topProbability`, `probability(option)`, `ranked()`                  |
| `ScoreAnswer`     | `score`, `probabilities`, `legend`, `confidence`, `levelCount` | `normalized`, `nearestLevel`, `mostLikelyLevel`, `legendText(level)` |

- **Choice probabilities** are in the order the options were declared.
- **Score probabilities and legend** are keyed by level number, and the legend holds the level entries that
  were sent.

## All answers

```kotlin
--8<-- "ResultExamples.kt:collections"
```

## Call details

```kotlin
--8<-- "ResultExamples.kt:metadata"
```

| Property         | Meaning                                                                         |
|------------------|---------------------------------------------------------------------------------|
| `model`          | The model that answered, as reported by the API; falls back to `requestedModel` |
| `requestedModel` | The name that was sent, possibly an alias such as `jev-latest`                  |
| `usage`          | `inputTokens` / `outputTokens`; either may be `null`                            |
| `requestId`      | The `x-typesafe-request-id` response header; quote it in support requests       |
| `raw`            | The response body exactly as received                                           |
| `questions`      | The `QuestionSet` that was asked                                                |

Log `model` alongside results: an alias such as `jev-latest` can move to a newer model.

## The raw response

```kotlin
--8<-- "ResultExamples.kt:raw"
```

## When an answer is missing

A malformed answer to a known question fails while the response is being read. A missing answer fails
lazily, when you read it. Either way you get a `JevResponseValidationException` whose `fieldPath` points at
the problem, such as `answers.team.choice`:

```kotlin
--8<-- "ResultExamples.kt:missing"
```

An answer of a type jev4k doesn't recognize is kept as an `UnknownAnswer` in `answers`, rather than failing the
whole response.
