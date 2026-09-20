# TypeSafe API docs cache

A local copy of the TypeSafe docs (https://docs.typesafe.ai), which cover the Jev System One model this project builds
against, plus compressed notes. Fetched 2026-09-18, when `jev-latest` was `jev-1.13.0`, the Python SDK was 0.7.0 and the
JS SDK was 0.6.0.

| File           | What it is                                                                                                                                                                                                                        |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `jev-api.md`   | **Start here.** The HTTP contract, question and answer types, limits, models and pricing, confidence, question-writing rules, patterns, known jev-1.13 weaknesses, the legacy preview API to avoid, and notes for a Kotlin client |
| `sdks.md`      | The official Python and JS SDKs as a reference design: env vars, retry and timeout defaults, error classes, typed question/answer modeling, and where the SDKs disagree with the API reference                                    |
| `cookbooks.md` | All 18 cookbooks: an index table, cross-cutting lessons, then per-cookbook decomposition, verbatim question wording, thresholds, results and gotchas                                                                              |
| `pages/`       | The full doc set as Markdown (110 pages), mirroring the site's paths; `pages/llms.txt` is the site index. **Not in git** (it's TypeSafe's copyrighted content): run `make api-docs` after cloning to download it                  |
| `refresh.sh`   | Re-downloads `pages/` and removes page boilerplate. It doesn't touch the compressed notes                                                                                                                                         |

The compressed notes are the working set. When one of them is unclear or might be stale, check the page it cites under
`pages/`, or the live site. The cookbooks mostly ran `jev-1.12`, so re-check their thresholds against the current model.

To refresh: `make api-docs` (runs `jev-docs/refresh.sh`), then update the notes for anything that changed. Git doesn't
track `pages/`, so to see what changed, copy the old folder aside before refreshing and diff the two.
