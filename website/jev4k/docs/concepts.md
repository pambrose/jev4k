---
icon: lucide/book-open
---

# Concepts

## System One models

Large language models produce text for people to read. When code needs a *decision*, that means coaxing a text
generator into structured output and parsing it back. Jev is TypeSafe's **System One** model, named after the
fast, intuitive "System 1" thinking described in *Thinking, Fast and Slow*. It never generates text. It
evaluates typed questions against a state and returns typed answers with probabilities.

|                        | Text LLM                       | Jev                                                  |
|------------------------|--------------------------------|------------------------------------------------------|
| Output                 | Prose to parse                 | Typed answers, constrained to the options you define |
| Uncertainty            | Implicit, often overconfident  | Calibrated probabilities on every answer             |
| Speed                  | Seconds                        | About 100 ms per request                             |
| Cost of more questions | Longer prompts, longer answers | A few extra input tokens; output is free             |

Jev is trained with *reinforcement learning for calibrated decisions*: across many answers, outcomes given
probability 0.8 happen about 80% of the time. That's a property of groups of answers, not a guarantee about any
single one.

## State, questions, and answers

Every request has three parts.

**State**
:   The content Jev reads: a message, a document, a JSON record, or any `@Serializable` value. See
[State](queries/state.md).

**Questions**
:   Typed judgments about that state, each with an id you choose. The id is only for your code; it's
never sent to the model, so each question's instructions must state the whole question.

**Answers**
:   One typed answer per question, returned under the same id.

Here's one request with a question of each type:

```kotlin
--8<-- "ConceptsExamples.kt:one-request"
```

And here's what comes back:

```kotlin
--8<-- "ConceptsExamples.kt:answer-shapes"
```

## Questions run in parallel

All questions in a request are evaluated **in parallel and independently** against the same state.

- **Asking more barely changes latency.** It costs only the tokens of the extra questions. That's why
  jev4k encourages putting every question about a state into one request, including
  [speculative ones](patterns/fan-out.md) that only matter on some code paths.
- **Answers don't influence each other.** One question's answer is never context for another. If a later
  question truly depends on an earlier answer (to fetch more evidence, or to pick the next options), make a
  second request.

## Probabilities and confidence

Every answer is a probability distribution over the answers you allowed. The model can't return anything
outside it.

- A **Noul** answer is one number, the probability of yes. Near 0.5 means yes and no are about equally
  likely. It does *not* mean "medium"; measure degree with a Score.
- **Choice** and **Score** answers carry the full distribution plus `confidence`, a 0–1 summary of how
  concentrated that distribution is. Confidence is *not* the winner's probability: 0.45 against a runner-up
  of 0.44 is a very different situation from 0.45 with the rest scattered thinly.

[Confidence & Thresholds](results/confidence.md) shows how to act on these values.

## What goes over the wire

jev4k builds exactly the JSON the TypeSafe API expects. This query:

```kotlin
--8<-- "WireRequest.kt:dsl"
```

is sent as this request body to `POST https://api.typesafe.ai/v1/systemone`:

```json
--8<-- "WireExamples.txt:request"
```

and comes back as:

```json
--8<-- "WireExamples.txt:response"
```

jev4k maps that response to typed answers. It also keeps the raw body, the reported `model`, token `usage`,
and the `x-typesafe-request-id` header on the [`JevResult`](results/reading.md).

## Models

| Name          | Meaning                                                          |
|---------------|------------------------------------------------------------------|
| `jev-latest`  | The latest stable release (the default). Currently `jev-1.13.0`. |
| `jev-preview` | The latest release, whether or not it's official.                |
| `jev-1.13.0`  | A pinned version.                                                |

Aliases move when TypeSafe ships a new release. If you've tuned thresholds against a particular version, pin it
by name; see [Making Calls](client/calls.md#choosing-a-model).

Jev reads text only: strings, JSON objects, and arrays of text. English is its primary language; other
languages work, but test them on your own data. It isn't trained on customer requests.
