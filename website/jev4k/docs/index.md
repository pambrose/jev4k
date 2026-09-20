---
icon: lucide/rocket
---

# jev4k

**A Kotlin DSL and client for TypeSafe's Jev model.**

[Jev](https://docs.typesafe.ai) is a *System One* model: fast, calibrated judgments instead of generated text.
You give it a **state** (a message, a document, a record) and a set of typed **questions**, and it returns
typed **answers** with probabilities your code can branch on, sort by, and threshold.

jev4k lets you declare those questions in Kotlin, send them in one request, and read the answers back as
typed values, down to enum-valued choices that work with an exhaustive `when`.

```kotlin
--8<-- "FirstTriage.kt:typed-query"
```

## How it works

``` mermaid
graph TD
  S[State: text, JSON, or @Serializable] --> R
  Q[Questions: Noul, Choice, Score] --> R
  R[jev.ask / jev.query] -->|one HTTP request| J[Jev]
  J -->|typed answers + probabilities| A[JevResult]
  A --> C[Your code: branch, rank, threshold, escalate]
```

1. **Declare questions** with the inline DSL or as a reusable, typed `JevQuery`.
2. **Ask them about a state** in one request. Jev answers every question in parallel, in about 100 ms.
3. **Read typed answers**: a probability for a Noul, an option (or enum constant) for a Choice, a position on
   your levels for a Score, each with the probabilities behind it.
4. **Decide in code.** Control flow, arithmetic, thresholds and side effects stay in your program.

## Three kinds of question

| Question                          | Asks                           | Answer                                                                   |
|-----------------------------------|--------------------------------|--------------------------------------------------------------------------|
| [**Noul**](questions/noul.md)     | Is this true?                  | `noul`: the probability of yes, 0 to 1                                   |
| [**Choice**](questions/choice.md) | Which of these options?        | `choice`, a probability per option, `confidence`                         |
| [**Score**](questions/score.md)   | Where on these ordered levels? | `score` (can fall between levels), per-level probabilities, `confidence` |

## Features

- **Two DSL styles**: quick [inline queries](queries/inline.md) with string ids, or reusable
  [typed queries](queries/typed.md) whose property names become question ids.
- **Typed enum choices**: `choice<Team>()` answers with a `Team`, so the compiler checks every branch.
- **Structured criteria**: contrastive rubrics, examples, and JSON field specs in
  [instructions and options](questions/structured.md).
- **Any state**: text, JSON, or any `@Serializable` value.
- **Suspend-first client** on Ktor, with a [blocking mirror](client/calls.md) for scripts and `main`.
- **SDK-parity retries**: the official SDKs' [retry and timeout rules](client/errors.md), including
  server `Retry-After` hints.
- **Validation before sending**: every problem with a request is reported at once, without a network call.

<div class="grid cards" markdown>

- :lucide-play: **Quick Start**

  ---

  Set an API key and run your first query in a few lines.

  [:octicons-arrow-right-24: Get started](getting-started/quick-start.md)

- :lucide-book-open: **Concepts**

  ---

  State, questions, answers, and what calibrated confidence means.

  [:octicons-arrow-right-24: Learn the model](concepts.md)

- :lucide-layers: **Patterns**

  ---

  Routing, composite scoring, verification, extraction, ranking, and guardrails.

  [:octicons-arrow-right-24: Browse patterns](patterns/index.md)

- :lucide-lightbulb: **Writing Good Questions**

  ---

  The habits that make Jev's answers accurate and useful.

  [:octicons-arrow-right-24: Read the guide](guides/best-practices.md)

- :lucide-graduation-cap: **The Jev Docs**

  ---

  TypeSafe's own documentation: concepts, primitives, and cookbooks.

  [:octicons-arrow-right-24: docs.typesafe.ai](https://docs.typesafe.ai)

- :lucide-file-code: **API Reference**

  ---

  Every public type and function, generated from the source by Dokka.

  [:octicons-arrow-right-24: Browse the KDocs](api.md)

</div>

## Thanks to TypeSafe

Jev, and the System One idea behind it, come from [TypeSafe](https://typesafe.ai), who have done a great job with
both. Their documentation at [docs.typesafe.ai](https://docs.typesafe.ai) is excellent. jev4k
follows Jev's concepts and naming, so what you learn there carries straight over to this library.
