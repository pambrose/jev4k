---
icon: lucide/network
---

# Classification

## Small and medium option sets

One Choice handles up to 255 options, reliably up to about 240. For most classification tasks, a single Choice
plus a confidence gate is all you need; see [Choice](../questions/choice.md#many-options) and the
[parent-label fallback](../results/confidence.md#falling-back-to-a-coarser-answer).

## Walking a taxonomy

For deep hierarchies (patent classes, product catalogs, medical subject headings), ask one Choice per level.
Each option's description is its subtree, so the model sees what lives under a branch before committing to it:

```kotlin
--8<-- "ClassificationExamples.kt:taxonomy-walk"
```

- **Stop at the deepest confident level.** Returning a correct parent is more useful than a guessed leaf.
- **Beware catch-all nodes.** "Other" nodes and near-synonym siblings can trap a greedy walk early. TypeSafe's
  beam search, which keeps the best 3 paths at each level scored by the geometric mean of their probabilities,
  classified 4 of 4 test documents correctly, against 2 of 4 for the greedy walk.
- **Freeze the taxonomy.** Option order is part of the question, so walk a fixed snapshot.

## Large option sets in two stages

When the options are many and their short descriptions look alike, rank them all cheaply, then judge the top few
against their full details:

```kotlin
--8<-- "ClassificationExamples.kt:shortlist-rerank"
```

- **The Choice and the Nouls do different jobs.** The Choice settles *which* option; the independent "fits" Nouls
  decide *whether* to suggest one at all, and they can all come back low.
- **The gate asks about action, not topic.** Subject-matter questions can't tell "explain what a monad is" from a
  request to run a tool.
- **The second stage can only reject.** It can't recover an option the first stage left out.

In TypeSafe's example, picking one of 182 agent skills this way more than halved both wrong loads (16.8% to 7.3%)
and needless loads (9.8% to 4.0%).
