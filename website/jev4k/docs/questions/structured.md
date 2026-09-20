---
icon: lucide/braces
---

# Structured Criteria

Instructions, Choice option descriptions, Score levels, and Noul `whenTrue`/`whenFalse` criteria all accept
JSON, not just strings. Start with plain strings. Reach for structure when guidance would otherwise blur
together in a dense sentence:

- **what** an option covers, **what it doesn't**, and a few **examples**
- a **field** being checked, described by name, type and unit
- supporting data that's already structured, such as a schema, a taxonomy or a database row

The field names are yours; none are reserved. The model reads both names and values, so use short names that
label what follows.

## Helpers

| Helper                           | Builds                                                                                                        |
|----------------------------------|---------------------------------------------------------------------------------------------------------------|
| `entry("a" to x, "b" to y)`      | a JSON object from pairs, keeping their order; values can be strings, numbers, booleans, lists, maps, or JSON |
| `rubric(what, notFor, examples)` | `{"what", "not_for", "examples"}`, the contrastive shape TypeSafe recommends                                  |
| `jsonOf(value)`                  | JSON from plain Kotlin values                                                                                 |
| `jsonEntry(value)`               | JSON from any `@Serializable` value                                                                           |

kotlinx.serialization's `buildJsonObject { }` works as well.

## Structured instructions

```kotlin
--8<-- "StructuredExamples.kt:entry-instructions"
```

## Contrastive options

When the model confuses two options, tell it what each one *isn't*:

```kotlin
--8<-- "StructuredExamples.kt:rubric-options"
```

Use the same field names on every option, so the model compares like with like.

## Describing a field

A single `field` object can drive several kinds of question. Here it drives a Noul that verifies a value and a
Score that buckets a magnitude:

```kotlin
--8<-- "StructuredExamples.kt:field-object"
```

## A taxonomy as option values

An option's description can be its whole subtree, so the model can see what lives under a branch before
committing to it:

```kotlin
--8<-- "StructuredExamples.kt:taxonomy"
```

For a deep tree, walk it one level per request; see [Classification](../patterns/classification.md).

## Noul criteria

```kotlin
--8<-- "StructuredExamples.kt:noul-criteria"
```

## @Serializable values

`jsonEntry` turns any `@Serializable` value into structured JSON, keeping fields that equal their defaults:

```kotlin
--8<-- "StructuredExamples.kt:json-entry"
```

## Pointing at part of the state

When the state is a JSON object with several parts, name the part a question is about with a backticked
dot-and-index path, such as `` `ticket.messages[0].text` ``.
See [State](../queries/state.md#pointing-questions-at-fields).
