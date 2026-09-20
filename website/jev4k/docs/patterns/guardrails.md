---
icon: lucide/shield
---

# Guardrails & Moderation

Rules in a system prompt are exactly what a jailbreak talks its way past, and a second LLM acting as a guard costs
a full call per turn. A Jev check runs on every message for a fraction of that cost and returns probabilities
your policy can threshold.

## Screening messages

Split "is this out of bounds?" into one Noul per hazard, plus an ordinal severity Score:

```kotlin
--8<-- "GuardrailExamples.kt:hazards"
```

## Policies in code

Map each hazard to an action with two thresholds, let severity escalate reviews to blocks, and resolve by
precedence. A policy is a named set of thresholds, so you can switch between them without re-asking anything:

```kotlin
--8<-- "GuardrailExamples.kt:policy"
```

- **Graded actions beat a plain block.** A self-harm signal routes to *support*, which is "the difference between
  helping someone and hanging up on them".
- **Severity escalates.** On its own it never triggers anything; it turns a review into a block.
- **Screen outputs too.** An ordinary-looking prompt can still produce a harmful reply, so run a similar battery
  on the model's response.

## Moderation with an uncertain middle

For a single yes/no policy, `band()` automates the clear cases and sends the rest to a moderator:

```kotlin
--8<-- "GuardrailExamples.kt:moderation-band"
```

Set the band from labeled examples of your own traffic, and watch the automation rate alongside accuracy.

!!! warning "Adversarial content"
Jev doesn't treat the state as hostile by default. Text written to steer it, such as injected instructions or
content arguing for its own classification, can move the answer. Be explicit in your criteria, and test edge
cases before deploying.
