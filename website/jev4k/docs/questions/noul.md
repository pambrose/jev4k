---
icon: lucide/circle-check
---

# Noul

A **Noul** asks whether something is true. Its answer is a single number, `noul`: the probability, from 0 to
1, that the answer is yes.

```kotlin
--8<-- "NoulExamples.kt:basic"
```

Phrase the question so that a high value means "yes". Near 1 is a strong yes, near 0 a strong no, and near
0.5 means both are about equally likely. Nouls have no separate `confidence`; the probability itself is the
signal.

## Defining yes and no

When the boundary is subtle, spell out what each outcome means with `whenTrue` and `whenFalse`:

```kotlin
--8<-- "NoulExamples.kt:criteria"
```

Try your questions with and without criteria on real inputs; sometimes the instruction alone is clearer. Keep
the criteria consistent with the instruction. A Noul whose `whenTrue` describes a "no" confuses the model.

Criteria can be structured, too; see [Structured Criteria](structured.md#noul-criteria).

## Questions or statements

A Noul can be a question or a statement for the model to judge. Try both on your data:

```kotlin
--8<-- "NoulExamples.kt:statement"
```

## Turning probabilities into decisions

`isTrue(threshold)` gives a boolean. `band(no, yes)` gives a three-way decision, so you can send the uncertain
middle to a person instead of guessing:

```kotlin
--8<-- "NoulExamples.kt:thresholds"
```

The default band (below 0.30 is *no*, above 0.70 is *yes*) comes from TypeSafe's examples. Tune both
thresholds on labeled examples from your own domain.

## One Noul per item

Jev doesn't count reliably, and a Choice can only pick *one* option. When several things might each be true,
ask one Noul per thing and combine the answers in code:

```kotlin
--8<-- "NoulExamples.kt:count-in-code"
```

## In a typed query

```kotlin
--8<-- "NoulExamples.kt:typed"
```

!!! tip "Negations don't add up"
`P(refund)` and `P(not a refund)`, asked as two Nouls, needn't sum to 1, and a Noul and a yes/no Choice on
the same question can differ. Ask each question the way you mean it, and don't reuse a threshold tuned for
one form with another.
