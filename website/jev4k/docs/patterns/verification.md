---
icon: lucide/badge-check
---

# Verification

Jev is cheap and fast enough to check the work of other systems, including LLMs, extraction pipelines and
agents, on every request. Keep the deterministic checks in code, ask narrow questions about the rest, and
escalate only what fails.

## Citation checks

A quote that doesn't appear in the source is fabricated, and code can tell that without a model. But a verbatim
quote can still fail to support the claim, so judge the quote *in its context*:

```kotlin
--8<-- "VerificationExamples.kt:citation"
```

In TypeSafe's RFC 7519 example, all four accurate citations were verified at 0.93 confidence or higher, and all
four planted failures were caught. The two with low confidence were exactly the "says nothing" cases, and
the confidence gate sent them to review.

## Extraction checks

A cheap model extracts the fields; Jev checks each field, and only the flagged records go to an expensive
reasoning model:

```kotlin
--8<-- "VerificationExamples.kt:extraction-gate"
```

What makes these checks work:

- **Narrow and grounded.** Each check asks one yes/no question about one field against the source. Vague
  "is this record good?" questions give mushy scores.
- **Bad = TRUE.** Phrase every check so that "yes" means "something is wrong", with explicit criteria.
- **Max, not mean.** One confident red flag should escalate, not be averaged away.

## Tool-call traces

Verify an agent's tool calls with one question per property rather than one question about the whole trace:

```kotlin
--8<-- "VerificationExamples.kt:tool-trace"
```

Each failing check names the specific problem, which is easier to act on than a single low score.
