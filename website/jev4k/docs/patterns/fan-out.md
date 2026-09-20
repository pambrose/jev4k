---
icon: lucide/git-fork
---

# Speculative Fan-Out

Questions in one request are evaluated in parallel, so asking more of them barely changes latency, and costs
only their input tokens. Put *every* question your code might need into one request, including ones that only
matter on some code paths, and let code decide which answers to use.

TypeSafe measured this on a 13-question compliance briefing. One batched request was **12.2 times cheaper** and **10
times faster** than 13 separate requests, with the same answers: the state is sent and read once instead of
13 times.

## Support triage

Classify the ticket, and ask the bug- and billing-specific questions up front:

```kotlin
--8<-- "FanOutExamples.kt:support"
```

Then route in code, reading only the answers that apply:

```kotlin
--8<-- "FanOutExamples.kt:support-routing"
```

If the ticket turns out to be a feature request, the bug-severity answer is simply ignored. Its uncertainty
doesn't matter either.

## A smart-home assistant

Asking sequentially ("is this a command?", then "which device?", then "what should the lights do?") costs a
round trip per step. Speculative questions answer them all at once:

```kotlin
--8<-- "FanOutExamples.kt:smart-home"
```

The assistant pairs Jev with a generative LLM only where text is actually needed:

- **Compound requests.** When the "multiple actions" Noul fires, an LLM splits the request into single
  commands, and each is evaluated again.
- **General questions and small talk.** When the request isn't a command, it goes to a conversational LLM.

Jev's response is so fast that it adds little latency in front of the LLM.

## When a second request is right

Answers are independent: one question never sees another's answer. Make a second request only when you can't
build it without the first answer, for example to:

- **fetch new evidence** for the state, such as the full text of the top three matches
- **construct state that didn't exist before**, such as blocks assembled from the first pass
- **choose the next options**, such as the children of the chosen category in a taxonomy

[Classification](classification.md) has examples of each.
