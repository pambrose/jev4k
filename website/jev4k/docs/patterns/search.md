---
icon: lucide/search
---

# Search & Ranking

## Re-ranking a shortlist

A cheap retriever such as BM25 or embeddings builds a shortlist. One Noul per (query, candidate) pair then
supplies a relevance score to sort by:

```kotlin
--8<-- "RankingExamples.kt:rerank"
```

On TypeSafe's legal-citation benchmark, re-ranking a 30-passage BM25 shortlist this way raised top-1 accuracy
from 5% to 18% and top-10 from 38% to 62%.

- **Criteria define relevance.** The `whenFalse` criterion rules out passages that are "merely on a similar
  topic", one standard applied to every pair.
- **Recall bounds the result.** Re-ranking only reorders the shortlist; it can't recover a passage the
  retriever missed.

## Line search

To find *where* a document answers a question, tag each line with an id and offer the ids as Choice options. Pair
that with an independent "exists" Noul:

```kotlin
--8<-- "RankingExamples.kt:line-search"
```

The pairing matters. Choice probabilities always sum to 1, so *some* line wins even when nothing in the document
answers the question. In TypeSafe's example, a question the terms of service didn't cover still put 0.86
probability on the closest line, while `exists` was only 0.14.

## Filtering RAG context

Between retrieval and generation, ask a few narrow questions about each (query, passage) pair, and keep the
include/exclude decision in code:

```kotlin
--8<-- "RankingExamples.kt:rag-filter"
```

- **Order matters.** Injection comes first because it's a security decision. Contradiction comes before
  evidence, because a passage that denies the query's premise usually also looks like usable evidence.
- **Keep conflicting passages separate.** Give them to the answering model in their own block, so it can push
  back on a false premise rather than answer it.
- **A filter, not a firewall.** The injection check lowers risk, but every passage should still be treated as
  untrusted text by the model that answers.
