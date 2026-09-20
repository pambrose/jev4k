# TypeSafe / Jev API — compressed reference

Condensed from https://docs.typesafe.ai (fetched 2026-09-18). The full page mirror is in `pages/`; paths below are
relative to it.
Lines marked **(inferred)** are my reading, not a statement in the docs.

## 1. What Jev is

- **System One model**: evaluates a `state` against typed `questions`; returns typed answers + probabilities. No text
  generation, no reasoning traces, no code. Named after Kahneman's fast "System 1" thinking.
- Trained with **RLCD** (reinforcement learning for calibrated decisions): probabilities are calibrated across groups of
  predictions (p=0.8 outcomes happen ~80% of the time); not a per-answer guarantee.
- Same weights for every account; no fine-tuning/LoRA. Customize only via `state`, `instructions`, `criteria`, and
  decomposition.
- Text only (string / JSON object / array of text). English best; other languages incl. CJK work but worse.
- Typical latency ~100 ms (use-case page says real-time ~150 ms). Questions in one request run in parallel and in
  isolation — adding questions barely changes latency, and there is no cross-question context rot.
- Not trained on customer data. ZDR available for enterprise (privacy@typesafe.ai).
- **Not fully deterministic, and no temperature parameter.** Identical requests mostly return identical values; some
  questions drift ~0.005–0.01, and borderline Nouls moved up to ~0.1 across repeats (cookbooks). Don't assume exact
  reproducibility; cache on (model, state, questions).

## 2. HTTP API (v1)

```
POST https://api.typesafe.ai/v1/systemone
Authorization: Bearer <API_KEY>        # key from console.typesafe.ai/settings/keys
Content-Type: application/json
```

Official SDK environment variables (explicit option > env var > default; blank values ignored): `TYPESAFE_API_KEY`
(required), `TYPESAFE_BASE_URL` (default `https://api.typesafe.ai`), `TYPESAFE_DEFAULT_MODEL` (default `jev-latest`),
`TYPESAFE_LOG_LEVEL`. Some cookbooks use `TYPESAFE_ENDPOINT` for the base URL; that is only a cookbook convention.
Responses carry an `x-typesafe-request-id` header (on errors too); log it.

### Request

| Field       | Type                      | Req        | Notes                                                                                    |
|-------------|---------------------------|------------|------------------------------------------------------------------------------------------|
| `state`     | string \| object \| array | yes        | Content to judge. Prefer an object with named fields.                                    |
| `model`     | string                    | yes (HTTP) | `"jev-latest"`; SDKs default to it.                                                      |
| `questions` | map<id, Question>         | yes        | Ids are yours; **ids are never sent to the model** — put full meaning in `instructions`. |

Question (discriminated by `type`):

| `type`     | `instructions`      | `criteria`                                                                                        |
|------------|---------------------|---------------------------------------------------------------------------------------------------|
| `"noul"`   | EntryType, required | optional `{ "true": EntryType, "false": EntryType }` (each optional)                              |
| `"choice"` | EntryType, required | required map `option → EntryType \| null`; **≤ 255 options**                                      |
| `"score"`  | EntryType, required | required ordered array of level EntryTypes, low→high; **2–10 levels**; level number = array index |

**EntryType** = `string | object | array | null` (arbitrary JSON). Field names inside objects are free-form (e.g.
`question`, `focus`, `inspect`, `compare`, `what`, `not_for`, `examples`, `signals`) — none reserved; the model sees
names and values.

The API reference marks `instructions` required, but both SDKs type it as optional/nullable (JS `noul()` defaults it to
`null`). Always send it. The docs don't say whether absent optional fields should be omitted or sent as `null`; omitting
is the safe choice **(inferred)**. The SDKs can forward extra keys (Python `extra_body`, raw question dicts); whether
the server accepts unknown fields is undocumented.

### Response

```json
{
  "model": "jev-latest",
  "answers": {
    "is_urgent":  { "type": "noul",   "noul": 0.92 },
    "department": { "type": "choice", "choice": "technical",
                    "probabilities": { "billing": 0.08, "technical": 0.85, "sales": 0.07 },
                    "confidence": 0.82 },
    "frustration":{ "type": "score",  "score": 1.6,
                    "legend": { "0": "Calm", "1": "Frustrated", "2": "Very angry" },
                    "probabilities": { "0": 0.05, "1": 0.3, "2": 0.65 },
                    "confidence": 0.78 }
  },
  "usage": { "input_tokens": 312, "output_tokens": 48 }
}
```

- `answers` keyed by your question ids; each answer carries `type`.
- Noul: `noul` ∈ [0,1] = P (yes). **No `confidence`.**
- Choice: `choice` = argmax option; `probabilities` over every option (sums to 1); `confidence` ∈ [0,1].
- Score: `score` = Σ level·p (can fall between levels, range 0..n-1); `legend` level-string → the **original criteria
  entry** (a string, or the object if you sent structured levels); `probabilities` keyed by level **as string** (
  "0","1",…); `confidence`.
- Key order inside `probabilities`/`answers` is not the request order in the examples — don't rely on it.
- `model`: docs say it reports the **versioned ID** that answered (for logging), but every example shows `"jev-latest"`.
  Treat as a string and log it.
- Doc inconsistency: the quick-start example's score answer omits `probabilities`; the API reference marks it required.
  **(inferred)** Deserialize defensively.

### Models endpoint

`GET https://api.typesafe.ai/v1/models` (Bearer auth) → `{ "models": [ { "name", "description", "release_date" } ] }`.
Currently lists aliases only; versioned IDs are accepted in `model` even if unlisted.

### Errors

JSON body + standard status. Documented: `401` bad/missing key · `422` validation failure (body names the offending
field; e.g. sending legacy `document`) · `429` rate limit · `529` overloaded. The SDKs also map 400/403/404/5xx to error
classes. Error bodies have no documented schema; keep them raw.

Retry behavior both official SDKs share (details and error class trees in `sdks.md`):

| Setting      | Default                                                                                      |
|--------------|----------------------------------------------------------------------------------------------|
| Max retries  | 2 (after the first attempt)                                                                  |
| Retried      | HTTP 408, 429, 500–599 (includes 529), connection errors, timeouts; other 4xx never          |
| Backoff      | 0.5 s doubling, capped at 5 s; jitter subtracts up to 25% of each delay                      |
| Server hints | Honor `Retry-After` and `retry-after-ms` (JS ignores hints > 60 s and falls back to backoff) |
| Timeout      | 10 s per attempt; Python also caps the whole call (attempts + delays) at 30 s                |

## 3. Models, limits, pricing

|             | Jev 1.13 (`jev-1.13.0`)                                                                                                                                                       |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Price       | $0.042 per million **input** tokens ($42/Btok); **output tokens free**                                                                                                        |
| Rate limits | 250,000 tokens/s and 1,200 requests/min (dynamic, can change without notice; exceeding → 429). Cookbooks report ~8 concurrent requests on a shared key already hitting limits |
| Context     | 64k tokens per request (state + all questions); **32k for state + the single longest question** (primitives page: "~32,000 tokens ≈ 150,000 chars of English")                |
| Input       | Text only                                                                                                                                                                     |

Aliases: `jev-latest` → `jev-1.13.0` (latest stable; SDK default) · `jev-preview` → `jev-1.13.0` (no preview build right
now). Aliases move on release; **pin the versioned ID if you tuned thresholds**. The jaggedness page uses the short form
`jev-1.13` in an SDK call.

## 4. Choosing and writing questions

- **Choice**: one of an unordered known set → maps to code branches. Give the *full* option list (options are cheap),
  add `other` / `none of the above` when coverage is uncertain. `null` descriptions are fine when names are
  self-explanatory. Option names and descriptions are both sent to the model.
- **Score**: position on a spectrum you can describe in steps → maps to thresholds/ranking. Levels must **describe
  situations, not degrees** ("Broken, but workaround exists", not "moderately severe"). Each level is judged on its own;
  the model doesn't see level numbers or neighbors, so "worse than previous" and numeric-only levels fail (numeric
  levels `["0","1","2"]` gave 0.57 @ conf 0.35 vs 0.0 @ 1.0 with descriptions). One dimension per Score. Give a rare
  extreme its own top level. No in-between → use Choice or several Nouls.
- **Noul**: clean yes/no where the probability itself is the signal → maps to `if`. Phrase so high = yes. 0.5 means
  "equally likely yes/no", **not** "medium intensity" — measure degree with a Score. Optional `criteria.true/false` pins
  subtle boundaries; test with and without.
- Prefer the type whose answer your code acts on directly.
- **One snap judgment per question** ("a knowledgeable person decides in a second"). Decompose broad questions ("is this
  spam?") into atomic ones (credentials requested? unexpected reward? time pressure? sender/domain mismatch? link
  mismatch?) and combine in code.
- Point at parts of structured state with **backticked dot/index paths**:
  `` Does `ticket.messages[0].text` request a refund? ``.
- Use **structured EntryTypes** when guidance blurs: contrastive Choice options `{what, not_for, examples}` with the
  same field names on every option; Score levels `{what, examples}` / `{summary, signals}`; Noul `criteria.true/false`
  as objects; instructions `{question, focus, inspect, compare}`. Examples only help when they resemble real inputs;
  higher confidence ≠ more correct — validate against labeled cases.
- Taxonomy walking: Choice per level where each option's value is its subtree (trim big subtrees to children + sample
  leaves); beam-search over probabilities for close splits.
- Structured extraction: build one question per field with a shared `field` object (`name`, `type`, `unit`,
  `description`) — Noul to verify a value, Choice to pick among candidate spans, Score to bucket magnitude.

## 5. Composition rules

- **Ask everything together.** Every question over the same state goes in one request, including **speculative** ones
  only relevant on some branches; code ignores the irrelevant ones. (Cookbook: 13 questions batched = ~12× cheaper, ~10×
  faster, identical answers.) Only cost is question tokens.
- Answers are independent — one answer is never context for another. Make a **second request only when you truly can't
  build it without the first answer** (need to fetch new evidence, construct new state, or choose next options).
  Examples: skill-suggestion (rank 182, then judge top-3 full texts), structure recovery (merge lines, then classify
  resulting blocks), hierarchical classification.
- **Composite scoring**: normalize each Score by `len(criteria) - 1` → [0,1], combine with weights in code; tune
  weights, not prompts. Raw judgments are reusable features (also for downstream classical ML, e.g. CatBoost on Jev
  probabilities).
- Keep deterministic rules, arithmetic, lookups, side effects and control flow in code. Send only the context the
  questions need.
- Put questions and threshold constants in **one reviewable place** — they are what humans need to review.

## 6. Confidence

- On Choice and Score only; a statistic over `probabilities` (peaked → high, flat → low). v1's exact formula isn't
  documented; preview used `1 − normalized Shannon entropy` (`1 - H(p)/ln(n)`), which you can compute yourself from
  `probabilities`.
- `confidence` is **not** the winner's probability (observed 0.43 with a top probability of 0.53). It separates "0.45 vs
  runner-up 0.44" from "0.45 with the rest scattered". Some cookbooks gate on the top probability instead; pick one
  deliberately.
- Low Choice confidence: no clear winner (can be harmless when several options are acceptable). Low Score confidence:
  overlapping levels, multi-dimensional question, or thin state.
- Three bands: high → act automatically; medium → confirm/flag/gather more; low → don't act (human, clarification,
  fallback model).
- **Thresholds scale with risk** per action (examples: floor 0.5–0.6 → human; read-only action OK above floor;
  destructive action needs >0.85–0.9 or explicit confirmation). Tune on your own data (plot confidence vs accuracy);
  re-tune when the model version changes.
- If you only need the best option, take `choice` — no threshold needed. For statistical logic, use `probabilities`
  directly. Ignore uncertainty on unused speculative branches.
- Don't transfer a threshold tuned on a Noul to a Choice (or vice versa).

## 7. Patterns (docs `patterns/`)

| Pattern                  | Idea                                                                                | Example numbers from docs                                                                |
|--------------------------|-------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| Speculative fan-out      | All questions in one call, code picks relevant answers                              | bug `score > 1.5 && repro.noul > 0.6` → escalate; `refund.noul > 0.7` → flag             |
| Confidence-gated routing | Answer = what, confidence = whether to act                                          | `conf < 0.6` → human; `approve_transfer` needs `> 0.85` else confirm                     |
| Composite scoring        | Atomic Scores ÷ top level, weighted sum                                             | IC = .4 py + .1 lead + .4 arch + .1 general                                              |
| Intent routing           | Cheap Choice (+ complexity Score) picks deterministic code / specialist LLM / human | `intent.conf < 0.5` → human; complaint with `complexity.score > 1` or conf < 0.5 → human |

Smart-home demo: long speculative question list per utterance (category, domain/room, device, action); a Noul "multiple
distinct actions?" triggers an LLM to split the request, then each part is re-evaluated; general-chat category falls
back to a generative LLM.

## 8. Known weaknesses of jev-1.13 (`model-jaggedness/jev-1.13.md`, reviewed 2026-09-17)

1. **Literal reading** — answers the words, not the intent; state exact conditions, put boundary cases in criteria,
   split interpretations into literal questions.
2. **Math/numbers** — no arithmetic, **no counting** (count in code: one Noul per item, sum thresholded answers). Worse
   on numeric representations (hex colors, RGB, assembly) than semantic ones — convert/bucket in code. Don't interpolate
   exact magnitudes from Score expectations; thresholds on them are OK.
3. **Dates** — can't order/diff dates or check windows; extract components via Choices (month, day, year, with a "not
   stated" option), do date math in code.
4. **Indirection** — double negatives and multi-hop "property of a property" hurt; name the state part directly.
5. **Large irrelevant state** — accuracy falls with distractors; filter in code first or pre-filter with Nouls.
6. **Adversarial content** — state isn't treated as hostile; injected instructions can move answers. Be explicit in
   criteria; test.
7. **Contradictory instructions vs criteria** — e.g. Noul with `true` meaning "no" performs worse; keep aligned.
8. **No structural invariants** — Noul vs yes/no Choice on the same question differ (0.22 vs 0.01); P (q) + P (not q) ≠
   1 (0.72 + 0.47). Choice is relative (which option), per-option Nouls are absolute (can all be low).
9. **Generation** — not a generator; extract candidates with regex/an LLM and let Jev choose.

## 9. Legacy preview API — do not use

Old `POST /preview/evaluation` is gone. v1 renames: `document`→`state` (sending `document` now fails 422), `prompts[]`
with `key` → `questions{}` map, Choice `options[]` → `criteria{}`, Score `levels[{level,description}]` → `criteria[]`
(no skipped levels), `responses[]` → `answers{}`, answer fields `probability`→`noul`, `chosen`→`choice`, `expectation`→
`score`, Choice `probabilities` array → map, Score gained `probabilities`, `usage.billing_units` → `input_tokens`/
`output_tokens`, confidence recomputed. Python package `typesafe-client` is dead → `typesafe-sdk`.

## 10. Implications for a Kotlin client (inferred)

- Model `EntryType` (instructions, option descriptions, levels, Noul true/false) as arbitrary JSON (e.g.
  kotlinx.serialization `JsonElement`) with string convenience builders.
- Question/Answer are sealed hierarchies discriminated by `"type"` (`noul`/`choice`/`score`). Choice option keys and
  Score level keys are strings on the wire; expose Score levels as `Int` and `legend` values as JSON.
- Validate client-side against the documented bounds: Choice ≤ 255 options, Score 2–10 levels (the docs give no minimum
  option count for Choice; 11 Score levels returns a server error). Both SDKs also reject an empty `questions` map.
- Mirror the SDK retry table in §2 and the env-var precedence; expose `x-typesafe-request-id` on responses and errors.
  The JS SDK's generics (criteria keys → typed `choice`, score tuple → typed `legend`) are the closest model for an
  idiomatic typed Kotlin API; see `sdks.md` §4.
- Keep the API key out of source; read `TYPESAFE_API_KEY`.
