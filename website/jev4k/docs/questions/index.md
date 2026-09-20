---
icon: lucide/list-checks
---

# Choosing a Question Type

Every question asks for one judgment about the state. Pick the type that matches the *shape* of the answer
your code needs.

| Type                | Use it when the answer is…                            | Returns                                                           | Maps onto                |
|---------------------|-------------------------------------------------------|-------------------------------------------------------------------|--------------------------|
| [Noul](noul.md)     | yes or no                                             | the probability of yes                                            | an `if`                  |
| [Choice](choice.md) | one of a known set of options, in no particular order | the chosen option, a probability per option, confidence           | a `when` over code paths |
| [Score](score.md)   | a position on a spectrum you can describe in steps    | a probability-weighted level, per-level probabilities, confidence | a threshold or a ranking |

## Decision guide

- **Is it a yes/no condition?** Use a **Noul**, for example "does this message ask for a refund?".
  Define the condition precisely: the answer is the probability that it holds.
- **Is it one of several options?** Use a **Choice**, for example routing to a team or classifying a document.
  Give the full list of options, and add `other` or `none` when the list might not cover every input.
- **Is it a degree?** Use a **Score**, for example severity, frustration, or skill. Describe what each level
  looks like.

!!! warning "A Noul is not a degree"
A Noul value of 0.5 means yes and no are equally likely. It doesn't mean "medium". "Is the candidate
strong in Python?" is a poor Noul, because "strong" isn't defined. Either ask a clean yes/no ("Does the
resume state the candidate used Python at work?") or measure degree with a Score whose levels describe
each amount of experience.

```kotlin
--8<-- "BestPracticeExamples.kt:degree"
```

## One snap judgment per question

System One models are built for the judgment a knowledgeable person makes in a second, given the right
context. "Does this message convey urgency?" is a good question. "Analyze this message and decide what to do"
isn't: that needs deliberation, and it's a signal to split the task into narrow questions and combine their
answers in code. [Writing Good Questions](../guides/best-practices.md) covers this in depth.

## Richer instructions and criteria

Instructions, option descriptions, levels and Noul criteria can all be structured JSON rather than plain
strings, for example with contrastive `what` / `not_for` / `examples` fields. See
[Structured Criteria](structured.md).

## Limits

jev4k checks these locally and reports every problem at once, before anything is sent:

| Rule                  | Limit                                                      |
|-----------------------|------------------------------------------------------------|
| Questions per request | At least one; ids unique and non-blank                     |
| Instructions          | Must not be blank                                          |
| Choice options        | 1 to 255 (`MAX_CHOICE_OPTIONS`); keys unique and non-blank |
| Score levels          | 2 to 10 (`MIN_SCORE_LEVELS`..`MAX_SCORE_LEVELS`)           |

A request's state plus all its questions must also fit Jev's context window: 64k tokens in total, and 32k
for the state plus the single longest question.
