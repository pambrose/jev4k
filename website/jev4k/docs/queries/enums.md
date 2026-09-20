---
icon: lucide/list-tree
---

# Enum Choices

`choice<E>()` turns an enum into a Choice question: every constant is an option, and the answer's `choice` is
the constant itself.

```kotlin
--8<-- "EnumChoiceExamples.kt:plain-enum"
```

## Exhaustive handling

Because the answer is an enum constant, a `when` over it is checked by the compiler:

```kotlin
--8<-- "EnumChoiceExamples.kt:when"
```

`probabilities` is keyed by constant, in declaration order, and `ranked()`, `probability(...)` and
`topProbability` work as they do for string options.

## Descriptions

Implement `JevOption` to describe each option. The description is sent with the option key:

```kotlin
--8<-- "Shared.kt:team"
```

## Option keys

By default, the option key sent to the model, and matched in the answer, is the constant's `name`. Option names
are part of what the model reads, so they should describe the option. Override `optionKey` to send something
else:

```kotlin
--8<-- "EnumChoiceExamples.kt:option-key"
```

## Structured descriptions

Override `entry` to send structured JSON, such as a contrastive rubric, instead of a plain description:

```kotlin
--8<-- "EnumChoiceExamples.kt:structured-entry"
```

## `JevOption` at a glance

| Member        | Default                               | Purpose                                                               |
|---------------|---------------------------------------|-----------------------------------------------------------------------|
| `description` | `null`                                | plain-text description of the option                                  |
| `entry`       | `description` as JSON, or `null`      | what's sent as the option's description; override for structured JSON |
| `optionKey`   | `null`, meaning the constant's `name` | the key sent to the model and matched in the answer                   |

Enums don't have to implement `JevOption`; without it, options are sent undescribed under their constant names.

## In the inline DSL

`choice<E>(id, instructions)` works in inline queries too. Read the answer back with `enumChoice<E>(id)`:

```kotlin
--8<-- "EnumChoiceExamples.kt:inline"
```

If the server ever returns an option the enum doesn't define, reading the answer throws a
`JevResponseValidationException`.
