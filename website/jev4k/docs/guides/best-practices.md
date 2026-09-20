---
icon: lucide/lightbulb
---

# Writing Good Questions

Question design matters more than anything else in a Jev integration. These guidelines are condensed from
TypeSafe's documentation and cookbooks.

## One snap judgment per question

Broad questions hide several judgments behind one answer:

```kotlin
--8<-- "BestPracticeExamples.kt:broad"
```

Atomic questions expose them, so you can inspect, tune and combine each one:

```kotlin
--8<-- "BestPracticeExamples.kt:atomic"
```

Decomposing doesn't add round trips: questions about the same state run in parallel in one request.

## Say exactly what you mean

Jev answers the question you wrote, not the one you meant. Scoping words, negations and implied conditions are
read at face value:

```kotlin
--8<-- "BestPracticeExamples.kt:literal"
```

When you look at a wrong answer and find yourself explaining what you really meant, that explanation is the
missing half of the instruction. Avoid double negatives and multi-hop phrasing ("a property of a property"); name
the part of the state you mean with a backticked path instead.

## Describe levels as situations

```kotlin
--8<-- "BestPracticeExamples.kt:levels"
```

## Use the right type for degree

```kotlin
--8<-- "BestPracticeExamples.kt:degree"
```

## Ask narrowly for the deciding fact

In TypeSafe's text-reformatting example, "are these two lines part of the same paragraph?" merged unrelated list
items, because the topic carried over. "Does this line pick up mid-sentence?" asked for the one fact that decides
the question, and worked.

## Give options an escape

Add `other` or `none` when the options might not cover every input. Pair a "which one?" Choice with an "is there
any?" Noul, because Choice probabilities always sum to 1.

## Keep computation in code

- **Counting.** Jev doesn't count reliably. Ask one Noul per item and count in code.
- **Arithmetic and magnitudes.** Let Jev identify values; compute with them in code. Don't interpolate exact
  numbers from a Score.
- **Dates.** Jev reads dates as text. Extract the parts and compare them in code.
- **Numeric representations.** It judges "red" better than `#FF0000`. Convert to a name or a bucket first.

## Send focused state

Accuracy falls as the state fills with detail the question doesn't need. Retrieve and filter in code, or filter
with Nouls, before asking.

## Keep instructions and criteria aligned

Treat criteria as an extension of the instruction. A Noul whose `whenTrue` describes a "no", or options that
contradict the instruction, lowers accuracy.

## Keep questions and thresholds reviewable

The questions and the threshold constants are what people need to review. Keep them together in a small number
of `JevQuery` objects and constants, not scattered through the code.

## Validate on your own data

Typed output guarantees the *shape* of an answer, not its truth. Calibration holds across many answers, not for
any single one. Before relying on thresholds:

- label a representative sample of your own inputs
- compare answers and confidence against the labels
- re-check when you move to a new model version

## Known limits of jev-1.13

| Weakness                                               | Do this instead                                               |
|--------------------------------------------------------|---------------------------------------------------------------|
| Literal reading                                        | Write the exact condition; put boundary cases in the criteria |
| Math and counting                                      | Keep arithmetic and counting in code                          |
| Date and time comparison                               | Extract date parts; compare in code                           |
| Indirection                                            | Reduce hops; point at the relevant state                      |
| Large, irrelevant state                                | Filter first; send only what the question needs               |
| Adversarial content                                    | Write precise criteria; test edge cases before deploying      |
| Contradictory instructions and criteria                | Align the two                                                 |
| Structural invariants (`P(q) + P(not q)` needn't be 1) | Ask each decision one way; enforce identities in code         |
| Generation                                             | Use a generative model; let Jev choose among its outputs      |
