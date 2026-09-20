---
icon: lucide/list
---

# Choice

A **Choice** picks one option from a set you define. Its answer has the most likely option (`choice`), the
probability of every option (`probabilities`, summing to 1), and a `confidence` from 0 to 1 that summarizes how
concentrated those probabilities are.

```kotlin
--8<-- "ChoiceExamples.kt:basic"
```

Option names and their descriptions are both sent to the model, so write descriptions that separate the
options from each other.

## Undescribed options

When an option's name says it all, leave it undescribed; it's sent as `null`:

```kotlin
--8<-- "ChoiceExamples.kt:undescribed"
```

In a builder, `"key" means "description"`, `option(key, description)`, `option(key)` and `options(vararg keys)`
all add options. Option keys must be unique.

## Always offer a way out

Some option always wins a Choice, because the probabilities sum to 1. If the list might not cover every input,
add an explicit `other` or `none` option:

```kotlin
--8<-- "ChoiceExamples.kt:escape"
```

To know whether *any* option applies, pair the Choice with an independent Noul; see
[Search & Ranking](../patterns/search.md#line-search).

## Reading the distribution

The runner-up often matters as much as the winner:

```kotlin
--8<-- "ChoiceExamples.kt:distribution"
```

`confidence` is *not* the winner's probability. It describes the shape of the whole distribution: 0.45 with a
runner-up at 0.44 is far less decisive than 0.45 with the rest scattered thinly. See
[Confidence & Thresholds](../results/confidence.md).

## Speculative questions

Ask the follow-up Choices you *might* need in the same request, and let code read only the relevant one:

```kotlin
--8<-- "ChoiceExamples.kt:speculative"
```

This costs a few extra input tokens and saves a second round trip. See
[Speculative Fan-Out](../patterns/fan-out.md).

## Many options

Options are cheap. Give the model the full list of categories, teams or products (up to 255) rather than a
shortlist:

```kotlin
--8<-- "ChoiceExamples.kt:many-options"
```

TypeSafe reports that a Choice works reliably up to about 240 options. For larger sets, go level by level
through a hierarchy or rank in stages; see [Classification](../patterns/classification.md).

## Enum options

`choice<E>()` offers every constant of an enum and answers with the constant itself:

```kotlin
--8<-- "Shared.kt:team"
```

```kotlin
--8<-- "TypedQueryExamples.kt:object-query"
```

See [Enum Choices](../queries/enums.md) for descriptions, custom option keys and structured entries.
