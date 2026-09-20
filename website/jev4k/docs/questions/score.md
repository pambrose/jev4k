---
icon: lucide/gauge
---

# Score

A **Score** places the state on ordered levels that you describe, from low to high. Its answer is a
position, `score`, that can fall *between* levels, together with a probability per level and a `confidence`.

```kotlin
--8<-- "ScoreExamples.kt:basic"
```

## Levels

Each level is one point on the spectrum, described in words. A level's number is its position: the first
`level(...)` is level 0. A Score needs 2 to 10 levels.

The model sees each level's description and nothing else, and judges each level on its own. It doesn't see
the level numbers or the neighboring levels, so "worse than the previous level" means nothing to it.

## Reading a Score

```kotlin
--8<-- "ScoreExamples.kt:reading"
```

`score` is the probability-weighted mean of the level numbers, so different distributions can produce the same
score. 1.0 might mean all probability is on level 1, or half on each of levels 0 and 2. Read `probabilities` or
`confidence` alongside the score when the difference matters.

Low confidence on a Score usually means one of three things:

- the levels overlap for this state
- the question measures more than one thing
- the state doesn't say enough to place it

## Writing good levels

**Describe situations, not degrees.** "Broken or degraded feature, but a workaround exists" gives the model
something to match against. "Moderately severe" doesn't, and levels made only of numbers perform worst:

```kotlin
--8<-- "BestPracticeExamples.kt:levels"
```

- **Keep each Score to one dimension.** If a level says "punctual and smart and experienced", split it into one
  Score per quality and combine them in code.
- **Give a rare extreme its own level** when you'd act on it differently, such as "abusive or threatening" above
  "very angry".
- **Use as many levels as you can describe distinctly,** up to 10. Three is fine.

When the model keeps landing between two levels on inputs you think are clear, add a few example situations to
each level:

```kotlin
--8<-- "ScoreExamples.kt:structured-levels"
```

Examples only help when they resemble your real inputs.

## Combining Scores

A judgment that depends on several things works best as one Score per thing, combined in code with weights you
control. Normalize each Score first: `normalized` divides by the top level number, putting every Score on 0–1
regardless of its level count.

```kotlin
--8<-- "ScoreExamples.kt:composite"
```

See [Composite Scoring](../patterns/composite-scoring.md) for more.

## Levels as actions

When the ordered outcomes *are* the actions (reject, review, accept), write one level per action and round to
the nearest level. There's no threshold to tune:

```kotlin
--8<-- "ScoreExamples.kt:levels-as-actions"
```

!!! warning "Don't interpolate numbers"
A score of 1.5 between "$1,000" and "$10,000" doesn't mean $5,500. Score levels aren't numerically
calibrated. Use scores for thresholds and ranking, and extract exact values another way; see
[Extraction](../patterns/extraction.md).
