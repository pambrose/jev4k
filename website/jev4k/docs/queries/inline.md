---
icon: lucide/code
---

# Inline DSL

`jev.query(state) { ... }` builds and sends a request in one step. Inside the block, `noul`, `choice` and
`score` each add a question under an id you choose:

```kotlin
--8<-- "InlineDslExamples.kt:query-block"
```

Read answers back by id with `result.noul(id)`, `result.choice(id)` and `result.score(id)`. Asking for an
id that wasn't in the request, or reading a Noul as a Choice, throws `IllegalArgumentException`.

## The builder functions

| Function                           | Adds                                   | Criteria block                                                    |
|------------------------------------|----------------------------------------|-------------------------------------------------------------------|
| `noul(id, instructions) { ... }`   | a Noul                                 | optional: `whenTrue(...)`, `whenFalse(...)`                       |
| `choice(id, instructions) { ... }` | a Choice with string options           | `"key" means "..."`, `option(key, desc?)`, `options(vararg keys)` |
| `choice<E>(id, instructions)`      | a Choice over the constants of an enum | none: the enum is the option list                                 |
| `score(id, instructions) { ... }`  | a Score                                | `level(...)`, `levels(vararg ...)`                                |
| `question(id, question)`           | a prebuilt `Question` value            | none                                                              |
| `include(query)`                   | every question of a `JevQuery`         | none                                                              |

Every `instructions` argument can be a `String` or a `JsonElement`; see
[Structured Criteria](../questions/structured.md).

## Typed handles

Each builder function also returns a typed `QuestionRef`, which reads its answer without repeating the string
id:

```kotlin
--8<-- "InlineDslExamples.kt:handles"
```

For questions you reuse, a [typed query](typed.md) is usually tidier.

## Generating questions

The block is ordinary Kotlin, so loops and conditionals work:

```kotlin
--8<-- "InlineDslExamples.kt:loops"
```

## Reusing a question set

`questions { ... }` builds a validated `QuestionSet` without sending it. Pass it to `evaluate` to ask the same
questions about many states:

```kotlin
--8<-- "InlineDslExamples.kt:evaluate"
```

## Building questions directly

The DSL builds `NoulQuestion`, `ChoiceQuestion` and `ScoreQuestion` values, named after TypeSafe's JS SDK. You can
build them yourself, for example from configuration, and add them with `question(id, value)`:

```kotlin
--8<-- "InlineDslExamples.kt:programmatic"
```

## Mixing in a typed query

`include(query)` adds a [typed query](typed.md)'s questions to the same request, and their handles still work
on the result:

```kotlin
--8<-- "TypedQueryExamples.kt:include"
```

## Validation

jev4k checks the request before sending it and reports every problem at once in a `JevValidationException`:
at least one question, unique non-blank ids, non-empty instructions, 1–255 Choice options, and 2–10 Score
levels. A Choice option key offered twice, including two enum constants with the same `optionKey`, is
reported there too, alongside anything else that is wrong.
