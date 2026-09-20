---
icon: lucide/box
---

# Typed Queries

A `JevQuery` declares questions as properties. Each property name becomes the question id, and each property
is a typed handle (`QuestionRef<A>`) that reads its answer back from a result.

```kotlin
--8<-- "TypedQueryExamples.kt:object-query"
```

`Team` is an ordinary enum; see [Enum Choices](enums.md).

## Asking and reading

`jev.ask(query, state)` sends every question in the query as one request. `result[handle]` returns the answer,
already typed:

```kotlin
--8<-- "TypedQueryExamples.kt:ask"
```

A handle only works on results of requests that included it. Reading one from an unrelated result throws
`IllegalArgumentException`.

## The builder functions

A `JevQuery` has the same builders as the [inline DSL](inline.md), minus the id argument, which comes from the
property name:

| Function                       | Returns a handle to    |
|--------------------------------|------------------------|
| `noul(instructions) { ... }`   | `NoulAnswer`           |
| `choice(instructions) { ... }` | `ChoiceAnswer<String>` |
| `choice<E>(instructions)`      | `ChoiceAnswer<E>`      |
| `score(instructions) { ... }`  | `ScoreAnswer`          |

## Custom ids

`id =` sends a different id than the property name:

```kotlin
--8<-- "TypedQueryExamples.kt:id-override"
```

## Inheritance

Queries can extend other queries. The base class's questions come first, then the subclass's, each in
declaration order:

```kotlin
--8<-- "TypedQueryExamples.kt:inheritance"
```

## Parameterized queries

A query can be a `class`, so its questions can depend on runtime values. Here there's one query per policy:

```kotlin
--8<-- "TypedQueryExamples.kt:parameterized"
```

## Combining with ad-hoc questions

`include()` inside an inline query asks a typed query's questions alongside one-off questions, in one request:

```kotlin
--8<-- "TypedQueryExamples.kt:include"
```

## Many states

The same query works for any number of states. Here they run concurrently:

```kotlin
--8<-- "TypedQueryExamples.kt:many-states"
```

Keep concurrency modest on a shared key; see [Making Calls](../client/calls.md#concurrency).

## When definitions are checked

A query's questions are validated the first time they're used, typically on the first `ask`, not when the
object is created. Invalid definitions, such as blank instructions or duplicate ids, surface as a
`JevValidationException` listing every problem.
