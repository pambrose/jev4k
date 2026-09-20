---
icon: lucide/route
---

# Routing

A fast, cheap classification in front of expensive resources: deterministic code handles what it can,
specialist LLMs get only what they need, and people see the cases that deserve them.

## Intent routing

Classify the intent and how complex the request is:

```kotlin
--8<-- "RoutingExamples.kt:intent"
```

Then pick a handler:

```kotlin
--8<-- "RoutingExamples.kt:intent-routing"
```

One intent goes to deterministic code with no LLM at all. Two go to different specialist LLMs, each loaded with
its own context. Complaints use the complexity Score, and its confidence, to decide between an LLM and a person.

## Confidence-gated routing

The answer says *what*; confidence says *whether to act*. Riskier actions deserve a higher bar:

```kotlin
--8<-- "ConfidenceExamples.kt:risk-scaled"
```

Checking a balance at 0.6 confidence is fine: at worst, the user hears the wrong screen read out. Approving a
transfer on the same evidence isn't. See [Confidence & Thresholds](../results/confidence.md).

## Model routing

The same idea chooses which LLM handles a prompt, sending easy prompts to cheap models:

```kotlin
--8<-- "RoutingExamples.kt:model-router"
```

When the difficulty estimate itself is uncertain, the router errs toward the more capable model.
