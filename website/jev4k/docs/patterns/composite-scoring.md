---
icon: lucide/chart-bar
---

# Composite Scoring

Asking one question for a complex judgment ("rate this candidate") hides several judgments behind one number.
Instead, score each dimension separately and combine them with weights in code. You can see exactly how every
result was computed, and when the ranking doesn't match your team's judgment, you change a weight, not a
prompt.

## Score each dimension

```kotlin
--8<-- "CompositeScoringExamples.kt:resume"
```

## Combine with weights

`normalized` puts every Score on 0–1, whatever its number of levels, so weights mean what they say:

```kotlin
--8<-- "CompositeScoringExamples.kt:weights"
```

## Rank without new requests

The answers are reusable data. Re-weighting, re-ranking or filtering reads the stored results; it never calls
Jev again:

```kotlin
--8<-- "CompositeScoringExamples.kt:ranking"
```

## Weighted yes/no signals

The same approach works with Nouls. Here several independent spam signals replace one vague "is this spam?":

```kotlin
--8<-- "CompositeScoringExamples.kt:signals"
```

A weighted sum suits signals that compensate for each other. For "any serious violation" logic, where one
signal alone should trigger, use separate conditions or a maximum instead; see
[Verification](verification.md#extraction-checks).

## Learned weights

With labeled outcomes, Jev's probabilities become features for a classical model such as gradient boosting.
TypeSafe's feature-discovery cookbook predicted wine critics' scores from tasting notes this way. An LLM
proposed questions, Jev answered them for every note, and a CatBoost model learned the weights. With 38
questions, that beat asking Jev for the score directly: an RMSE of 1.77 against 2.15.
