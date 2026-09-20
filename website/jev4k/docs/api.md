---
icon: material/book-open-page-variant
---

# API Reference (KDocs)

The full API reference is generated from the source with Dokka and published alongside this site.

[:material-book-open-page-variant: Open the KDocs](kdocs/index.html){ .md-button .md-button--primary }

## Entry points

| Declaration                                                              | What it is                                                            |
|--------------------------------------------------------------------------|-----------------------------------------------------------------------|
| [`JevClient`](kdocs/jev4k/com.pambrose.jev4k/-jev-client/index.html)     | The client: builds the HTTP stack, holds the config, and runs queries |
| [`BlockingJev`](kdocs/jev4k/com.pambrose.jev4k/-blocking-jev/index.html) | `jev.blocking`, the non-suspending mirror of the same calls           |
| [`JevConfig`](kdocs/jev4k/com.pambrose.jev4k/-jev-config/index.html)     | Resolved settings: API key, base URL, model, timeout, retries         |
| [`RetryPolicy`](kdocs/jev4k/com.pambrose.jev4k/-retry-policy/index.html) | Which failures are retried, and how long the backoff waits            |

## Defining questions

| Declaration                                                                | What it is                                                             |
|----------------------------------------------------------------------------|------------------------------------------------------------------------|
| [`JevQuery`](kdocs/jev4k/com.pambrose.jev4k/-jev-query/index.html)         | Base class for a reusable, typed query object                          |
| [`QueryBuilder`](kdocs/jev4k/com.pambrose.jev4k/-query-builder/index.html) | The receiver inside `jev.query(state) { ... }`                         |
| [`QuestionRef`](kdocs/jev4k/com.pambrose.jev4k/-question-ref/index.html)   | The handle a property or builder call returns, used to read its answer |
| [`JevOption`](kdocs/jev4k/com.pambrose.jev4k/-jev-option/index.html)       | Implemented by an enum so `choice<E>()` can describe its constants     |

## Reading answers

| Declaration                                                                | What it is                                                                  |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| [`JevResult`](kdocs/jev4k/com.pambrose.jev4k/-jev-result/index.html)       | One response: `result[ref]`, the by-id accessors, model and usage           |
| [`NoulAnswer`](kdocs/jev4k/com.pambrose.jev4k/-noul-answer/index.html)     | A probability, plus `isTrue()` and `band()`                                 |
| [`ChoiceAnswer`](kdocs/jev4k/com.pambrose.jev4k/-choice-answer/index.html) | The selected option, the distribution over options, and confidence          |
| [`ScoreAnswer`](kdocs/jev4k/com.pambrose.jev4k/-score-answer/index.html)   | A position on the levels, with `normalized` and the per-level probabilities |
| [`JevException`](kdocs/jev4k/com.pambrose.jev4k/-jev-exception/index.html) | The root of the error hierarchy every call can throw                        |

The published KDocs live at
[pambrose.github.io/jev4k/kdocs](https://pambrose.github.io/jev4k/kdocs/), next to this site.

!!! note "Serving the docs locally"

    `make site-build` copies the KDocs into the site, so the links above resolve. `make site` (live reload)
    serves only the Markdown pages: run `make kdocs` and open `build/dokka/html/index.html` separately.
