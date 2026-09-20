---
icon: lucide/sliders-horizontal
---

# Confidence & Thresholds

The answer tells you *what*; the probabilities tell you *whether to act on it*. A system that can say "I'm not
sure" is one you can build safe automation on.

## Probability versus confidence

- **Noul**: `noul` is the probability of yes. There's no separate confidence; a value near 0.5 is the
  model saying "I can't tell".
- **Choice / Score**: `probabilities` is the full distribution, and `confidence` (0–1) summarizes how
  concentrated it is. A single peak gives high confidence; probability spread across options gives low
  confidence.

Confidence is not the winner's probability. A winner at 0.45 with a runner-up at 0.44 and a winner at 0.45 with
the rest scattered thinly are different situations, and `confidence` is what separates them.

## Three bands

A useful starting point divides confidence into three ranges, each with its own behavior:

```kotlin
--8<-- "ConfidenceExamples.kt:bands"
```

For a Noul, `band()` does the same with the probability of yes; see
[Noul](../questions/noul.md#turning-probabilities-into-decisions).

## Thresholds scale with risk

A threshold isn't one number. Different actions in the same system deserve different bars, depending on what
it costs to get them wrong:

```kotlin
--8<-- "ConfidenceExamples.kt:risk-scaled"
```

## Falling back to a coarser answer

With hierarchical labels, an uncertain fine-grained answer can still yield a confident coarse one. In
TypeSafe's SEC-filing example, answers above 0.9 confidence were right 90% of the time and those below only
40%. Reporting the uncertain half at the parent level raised the useful answers from 39 of 60 to 48 of 60.

```kotlin
--8<-- "ConfidenceExamples.kt:fallback"
```

## Combining answers

When a decision uses several answers, its certainty is bounded by the weakest one:

```kotlin
--8<-- "ConfidenceExamples.kt:weakest-link"
```

## Choosing thresholds

- **Start conservative, then tune on labeled examples.** Plot confidence against accuracy on your own data.
- **Tie thresholds to a model version.** When you pin a model, tuned thresholds stay valid; moving to a new
  release means re-checking them. See [Choosing a model](../client/calls.md#choosing-a-model).
- **Expect small run-to-run variation.** Identical requests usually return identical values, but borderline
  answers can shift slightly, so don't set a threshold razor-close to your typical values.
- **Ignore uncertainty that doesn't matter.** A low-confidence answer to a
  [speculative question](../patterns/fan-out.md) your code isn't using needs no handling. Likewise, when several
  options would be acceptable, low confidence is harmless.
