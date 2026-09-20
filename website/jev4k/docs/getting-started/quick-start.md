---
icon: lucide/play
---

# Quick Start

## Your first query

With `TYPESAFE_API_KEY` set, this complete program asks three questions about a support message in one
request:

```kotlin
--8<-- "QuickStart.kt:first-query"
```

A few things to notice:

- **One request, three answers.** The questions are answered in parallel against the same state, so adding a
  question barely changes latency.
- **Ids are for your code.** `"urgent"`, `"department"` and `"frustration"` label the answers. The model never
  sees them, so each instruction states the whole question.
- **Answers are typed.** `noul(...)` returns a `NoulAnswer`, `choice(...)` a `ChoiceAnswer<String>`, and
  `score(...)` a `ScoreAnswer`.
- **Close the client when you're done.** `use { }` does it for you.

## A reusable, typed query

For questions you ask repeatedly, declare them once as properties of a `JevQuery`. Each property name becomes
the question id, and the property is a typed handle to its answer. Here the team is an enum:

```kotlin
--8<-- "Shared.kt:team"
```

```kotlin
--8<-- "FirstTriage.kt:typed-query"
```

`result[FirstTriage.team].choice` is a `Team`, so the `when` is exhaustive: add a constant to `Team` and the
compiler points at every place that needs to handle it.

## Without coroutines

jev4k's calls are `suspend` functions. From ordinary code (a script, `main`, a Java caller), use the blocking
mirror on `jev.blocking`:

```kotlin
--8<-- "FirstTriage.kt:blocking"
```

## Where to next

- [Choosing a question type](../questions/index.md): Noul, Choice, or Score?
- [Inline DSL](../queries/inline.md) and [Typed Queries](../queries/typed.md): the two ways to build requests.
- [Reading Results](../results/reading.md): everything a `JevResult` gives you.
- [Patterns](../patterns/index.md): end-to-end examples of common designs.
