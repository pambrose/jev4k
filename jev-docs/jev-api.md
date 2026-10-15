# TypeSafe / Jev API — compressed reference

Condensed from https://docs.typesafe.ai: fetched 2026-09-18, then updated 2026-10-15 for Jev 1.14, the request rules,
compression, images and Box questions, with release facts from TypeSafe's Jev 1.14 announcement
(https://typesafe.ai/blog/jev-1-14, 2026-10-14). The full page mirror is in `pages/`; paths below are relative to it.
Lines marked **(inferred)** are my reading, not a statement in the docs.

## 1. What Jev is

- **System One model**: evaluates a `state` against typed `questions`; returns typed answers + probabilities. No text
  generation, no reasoning traces, no code. Named after Kahneman's fast "System 1" thinking.
- Trained with **RLCD** (reinforcement learning for calibrated decisions): probabilities are calibrated across groups of
  predictions (p=0.8 outcomes happen ~80% of the time); not a per-answer guarantee.
- Same weights for every account; no fine-tuning/LoRA. Customize only via `state`, `instructions`, `criteria`, and
  decomposition.
- Input is text: a string, JSON object or array. Images are accepted experimentally (§2, "Images and Box"); audio and
  video aren't. English best; other languages incl. CJK work but worse.
- Typical latency ~100 ms (use-case page says real-time ~150 ms). Questions in one request run in parallel and in
  isolation — adding questions barely changes latency, and there is no cross-question context rot.
- Not trained on customer data. ZDR available for enterprise (privacy@typesafe.ai).
- **Not fully deterministic, and no temperature parameter.** Identical requests mostly return identical values; some
  questions drift ~0.005–0.01, and borderline Nouls moved up to ~0.1 across repeats (cookbooks). Don't assume exact
  reproducibility; cache on (model, state, questions).

## 2. HTTP API (v1)

```
POST https://api.typesafe.ai/v1/systemone
Authorization: Bearer <API_KEY>           # key from console.typesafe.ai/settings/keys
Content-Type: application/json            # multipart/form-data when uploading images
Content-Encoding: zstd | gzip             # optional; see "Request rules"
TypeSafe-Danger-Zone-Images: true         # optional; required by any request with an image or a Box question
TypeSafe-Danger-Zone-Long-Context: true   # optional; raises both context limits to 262k (§3)
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
| `assets`    | map<reference, Asset>     | no         | Images only; experimental. See "Images and Box" below.                                   |

Question (discriminated by `type`):

| `type`     | `instructions`      | `criteria`                                                                                        |
|------------|---------------------|---------------------------------------------------------------------------------------------------|
| `"noul"`   | EntryType, required | optional `{ "true": EntryType, "false": EntryType }` (each optional)                              |
| `"choice"` | EntryType, required | required map `option → EntryType \| null`; **≤ 255 options**                                      |
| `"score"`  | EntryType, required | required ordered array of level EntryTypes, low→high; **2–10 levels**; level number = array index |
| `"box"`    | EntryType           | none. Experimental; needs an image and Jev 1.14. See "Images and Box" below                       |

**EntryType** = `string | object | array` (arbitrary JSON); a Choice option may also be `null`. The API reference now
types every criteria value this way; in September it said `string` for Noul `true`/`false` and Score levels. Field
names inside objects are free-form (e.g. `question`, `focus`, `inspect`, `compare`, `what`, `not_for`, `examples`,
`signals`) — none reserved; the model sees names and values.

The API reference marks `instructions` required. Python still types it as optional, and JS 0.7.0 accepts a Noul without
it when `criteria` describes an outcome, which the API reference doesn't mention. Always send it. The docs don't say
whether absent optional fields should be omitted or sent as `null`; omitting is the safe choice **(inferred)**. The
SDKs can forward extra keys (Python `extra_body`, raw question dicts); whether the server accepts unknown fields is
undocumented.

The score page now says a Score "should have at least two levels; the API accepts up to 10", and JS 0.7.0 still rejects
fewer than two, so keep 2–10.

### Request rules

| Rule      | Limit                                                                                        | Otherwise |
|-----------|----------------------------------------------------------------------------------------------|-----------|
| Body size | ≤ 32 MiB, headers excluded; applies to both the compressed and the decompressed body         | 413       |
| Encoding  | UTF-8 JSON without a byte order mark                                                         | 400       |
| Nesting   | ≤ 64 levels of objects and arrays in `state`, and in each `instructions` or criteria value   | 400       |
| Numbers   | Finite only: `NaN`, `Infinity` and unrepresentable values such as `1e400` are rejected       | 400       |

- **Nesting, probed 2026-10-15.** The outermost object or array is level 1, so 64 nested arrays pass and 65 fail. Each
  criteria value is checked on its own: a Choice option, a Noul `true`/`false` or a Score level can nest 64 deep inside
  its map or array. The 400 body is
  `{"detail":{"error_type":"api_usage_error","message":"Request JSON nesting exceeds the maximum depth of 64."}}`.
  jev4k checks this before sending (`MAX_SEND_DEPTH` in `internal/JsonDepth.kt`).
- **Compression.** `Content-Encoding: gzip`, `x-gzip` or `zstd`, one layer only, zstd window ≤ 8 MiB. An unsupported
  encoding or more than one layer → 415; a malformed compressed body → 400. Both SDKs now compress bodies of 1 KiB or
  more with zstd by default; an uncompressed body is still valid.
- No limit on the number of questions; only the context limits (§3) bound a request.
- Send identifiers and exact large quantities as strings.

### Images and Box (experimental)

Both are labeled "Dreamer-ready": the request format, answers and limits can change between releases
(`images.md`, `primitives/box.md`).

- **Off by default.** Every request with an image or a Box question needs `TypeSafe-Danger-Zone-Images: true`, or it
  gets a 400. Jev 1.13 and 1.14 accept images; Box needs Jev 1.14 (`jev-preview` or `jev-1.14.0`).
- **`assets`** maps each reference string to an Asset: `{"type": "image", "format": "png" | "jpg" | "jpeg",
  "data_base64": "…"}`, or `{"type": "image", "part": "<multipart part name>"}`. `type` is optional; `part` wins if
  both are set. Base64 uses the standard alphabet with `=` padding, no whitespace and no `data:` prefix.
- **References** are matched as plain text in every string in `state`, `instructions` and `criteria`: a whole string
  value first, then left to right without overlap. Object keys (option names, question ids) are never replaced.
  Every asset must be referenced (else 400). Each use sends the image again and counts against the limits again. A
  reference is 1..1,024 UTF-8 bytes and not blank; all references in a request total ≤ 64 KiB. Use a fresh UUID per
  image. Answers echo the reference, not the image (e.g. in a Score `legend`).
- **Multipart** is preferred, since base64 makes each image a third larger. The first part, named `request`
  (`application/json`), holds the body. Each image part has content type `image/png`, `image/jpeg`, `image/jpg`,
  `application/octet-stream` or none. Part names are unique and non-empty, and each part belongs to exactly one asset.
  Both SDKs send multipart whenever a request has assets.
- **Limits** (inclusive; an image outside them is rejected, never resized, and fails the whole request): PNG or JPEG
  stills only (no animated PNG); ≤ 16 MiB per file, after base64 decoding; each side 256..1568 px; 78,400..2,458,624
  pixels in total; all images ≤ 32 MiB after processing (re-encoding can grow them); the whole request ≤ 32 MiB once
  every reference is expanded. Size limits → 413; a bad image, asset or multipart part → 400; too many tokens → 400
  `max_tokens_exceeded`.
- Metadata (EXIF, color profiles) is stripped and the EXIF orientation isn't applied, so rotate the pixels first. An
  8-bit baseline JPEG is the safest JPEG.
- Images are billed as input tokens at the text price, and count toward the context length.
- **Box question**: ``{"type": "box", "instructions": "the coffee mug in `photo`"}``, with no `criteria`. Answer:
  `{"type": "box", "box": {"top", "bottom", "left", "right"}, "confidence"}`, each edge a fraction 0..1 of the image's
  height or width from the top-left corner, with `left < right` and `top < bottom`. It always returns a box, even for an
  absent target, so pair it with a Noul ("Is there a mug in `photo`?"). One target per question. Both SDKs type Box
  `confidence` as nullable.

### Response

```json
{
  "model": "jev-1.13.0",
  "answers": {
    "is_urgent":  { "type": "noul",   "noul": 0.92 },
    "department": { "type": "choice", "choice": "technical",
                    "probabilities": { "billing": 0.08, "technical": 0.85, "sales": 0.07 },
                    "confidence": 0.78 },
    "frustration":{ "type": "score",  "score": 1.6,
                    "legend": { "0": "Calm", "1": "Frustrated", "2": "Very angry" },
                    "probabilities": { "0": 0.05, "1": 0.3, "2": 0.65 },
                    "confidence": 0.4 }
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
- Box (experimental): see above.
- `confidence` comes with Choice, Score and Box answers. The formulas are now documented (§6); the example values above
  follow them.
- `usage` counts may be `null` when the API didn't report them (JS 0.7.0 types; Python always had `int | None`).
- Key order inside `probabilities`/`answers` is not the request order in the examples — don't rely on it.
- `model`: the **versioned ID** that answered (examples now show `jev-1.13.0` for a `jev-latest` request). Treat it as a
  string and log it.

### Models endpoint

`GET https://api.typesafe.ai/v1/models` (Bearer auth) → `{ "models": [ { "name", "description", "release_date" } ] }`.
Currently lists aliases only; versioned IDs such as `jev-1.14.0` are accepted in `model` even if unlisted.

### Errors

JSON body + standard status. Error bodies have no documented schema; keep them raw. Observed shape for a 400:
`{"detail": {"error_type": "api_usage_error", "message": "…"}}`.

| Status | Meaning                                                                                                    |
|--------|------------------------------------------------------------------------------------------------------------|
| `400`  | A request rule broken (encoding, nesting, a non-finite number, a malformed compressed body); images not turned on; a bad image, asset or multipart part; `max_tokens_exceeded` (over the context length) |
| `401`  | Missing or invalid key                                                                                     |
| `413`  | Body over 32 MiB, or an image or reference size limit                                                                   |
| `415`  | Unsupported `Content-Encoding`, or more than one compression layer                                         |
| `422`  | Validation failure; the body names the offending field (e.g. sending legacy `document`)                   |
| `429`  | Rate limit                                                                                                 |
| `529`  | Overloaded                                                                                                 |

The SDKs also map 403/404/5xx to error classes. 413 and 415 get no class of their own (the base API error) and aren't
retried.

Retry behavior both official SDKs share (details and error class trees in `sdks.md`):

| Setting      | Default                                                                                      |
|--------------|----------------------------------------------------------------------------------------------|
| Max retries  | 2 (after the first attempt)                                                                  |
| Retried      | HTTP 408, 429, 500–599 (includes 529), connection errors, timeouts; other 4xx never          |
| Backoff      | 0.5 s doubling, capped at 5 s; jitter subtracts up to 25% of each delay                      |
| Server hints | Honor `Retry-After` and `retry-after-ms` (JS ignores hints > 60 s and falls back to backoff) |
| Timeout      | 10 s per attempt; Python also caps the whole call (attempts + delays) at 30 s                |

## 3. Models, limits, pricing

|          | Jev 1.14 (`jev-1.14.0`)                                         | Jev 1.13 (`jev-1.13.0`) |
|----------|-----------------------------------------------------------------|-------------------------|
| Released | 2026-10-14                                                      | —                       |
| Price    | $0.042 per million **input** tokens ($42/Btok); **output free** | same                    |
| Context  | 64k per request, 32k per question (below)                       | same                    |
| Input    | Text; images experimentally                                     | same                    |
| Box      | yes                                                             | no                      |

- **Context**: `per_request_context` (64k) covers the `state` plus all questions; `per_question_context` (32k) covers
  the `state` plus any one question. The models table still says "Text" for input; `images.md` says both versions
  accept images.
- **Long context**: `TypeSafe-Danger-Zone-Long-Context: true` on a request raises both limits to 262k, on 1.13 and
  1.14. Same price per token, but slower and less accurate.
- **Rate limits**: set per organization, in tokens per second and requests per second, and shown in the console
  (console.typesafe.ai/rate-limits). Over either → 429. The September figures (250,000 tokens/s, 1,200 requests/min)
  are gone. Cookbooks report ~8 concurrent requests on a shared key already hitting limits. Higher limits on custom and
  enterprise plans.

Aliases (2026-10-15): `jev-latest` → `jev-1.13.0` (latest stable; SDK default) · `jev-preview` → `jev-1.14.0`
(newest release, official or not). Aliases move on release; **pin the versioned ID if you tuned thresholds**. The
jaggedness page uses the short form `jev-1.13` in an SDK call.

From the Jev 1.14 announcement (blog, 2026-10-14):

- **`jev-latest` moves to `jev-1.14.0` "in a week"**, so around 2026-10-21. `jev-1.13.0` "will be available for a
  while".
- **Deprecation notice**: 60 days before `jev-1.13.0` is formally deprecated, 30 days for `jev-1.14.0`. A deprecation
  policy is promised but not yet published.
- 1.14 is "small, subtle improvements to everything", with the largest gain expected in multilingual support.
- `jev-1.13.0` "was multimodal all along": images work on it too. Box is 1.14-only.
- The "danger zone" headers mark features TypeSafe says are here to stay but still too jagged to vouch for in
  production.
- A new opt-in lets TypeSafe use your queries to find model jaggedness (still no training on them). Neither the post
  nor the docs say how to turn it on, so it is presumably an account setting **(inferred)**.

## 4. Choosing and writing questions

- **Choice**: one of an unordered known set → maps to code branches. Give the *full* option list (options are cheap),
  add `other` / `none of the above` when coverage is uncertain. `null` descriptions are fine when names are
  self-explanatory. Option names and descriptions are both sent to the model. jev-1.13 can lean toward the first
  option (§8).
- **Score**: position on a spectrum you can describe in steps → maps to thresholds/ranking. Levels must **describe
  situations, not degrees** ("Broken, but workaround exists", not "moderately severe"). Each level is judged on its own;
  the model doesn't see level numbers or neighbors, so "worse than previous" and numeric-only levels fail (numeric
  levels `["0","1","2"]` gave 0.55 @ conf 0.33 vs 0.0 @ 1.0 with descriptions). One dimension per Score. Give a rare
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
- Structured instructions can also carry data the question refers to, named in backticks like state paths:
  ``{"potential_duplicate": {"name": …, "location": …}, "question": "Is the resume for the same person as
  `potential_duplicate`?"}``. Code can build one such question per database record and ask them all in one request.
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

`confidence.md` now gives the exact formulas. Each is 1 when all probability is on one outcome and 0 when it is spread
evenly.

- **Choice** (n options, p_max the selected option's probability): `(p_max − 1/n) / (1 − 1/n)`. Only the top
  probability counts, so (0.6, 0.3, 0.1) and (0.6, 0.2, 0.2) both give 0.4: confidence doesn't separate "0.45 vs a
  runner-up at 0.44" from "0.45 with the rest scattered". The docs suggest two alternatives computed from
  `probabilities`: the top probability itself, and the top-to-second ratio for decisions that come down to two
  candidates.
- **Score** (n levels, p_i per level, m the most likely level): `max(0, 1 − Σ p_i·|i − m| / MAD_unif)`, where
  `MAD_unif = (1/n)·Σ |i − (n−1)/2|`. Probability on a neighboring level costs less than on a distant one: with three
  levels, (0, 0.5, 0.5) gives 0.25 and (0.5, 0, 0.5) gives 0.
- **Noul**: none; `noul` already is the distribution. `|2p − 1|` is the Choice formula for a yes/no pair, if one number
  is needed for both kinds.
- **Box**: reported, with no recommended threshold yet.
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

## 8. Known weaknesses of jev-1.13 (`model-jaggedness/jev-1.13.md`, reviewed 2026-10-02)

1. **Literal reading** — answers the words, not the intent; state exact conditions, put boundary cases in criteria,
   split interpretations into literal questions.
2. **Math/numbers** — no arithmetic, **no counting** (count in code: one Noul per item, sum thresholded answers). Worse
   on numeric representations (hex colors, RGB, assembly) than semantic ones — convert/bucket in code. Don't interpolate
   exact magnitudes from Score expectations; thresholds on them are OK.
3. **Dates** — can't order/diff dates or check windows; extract components via Choices (month, day, year, with a "not
   stated" option), do date math in code.
4. **Indirection** — double negatives and multi-hop "property of a property" hurt; name the state part directly.
5. **Large irrelevant state** — accuracy falls with distractors; filter in code first or pre-filter with Nouls. The
   state counts once against each context limit.
6. **Adversarial content** — state isn't treated as hostile; injected instructions can move answers. Be explicit in
   criteria; test.
7. **Contradictory instructions vs criteria** — e.g. Noul with `true` meaning "no" performs worse; keep aligned.
8. **Choice option order** — the answer can depend on option order, leaning toward the first option. Reorder the
   options and check that the answer holds. (Until October this item was "structural invariants": a Noul and a yes/no
   Choice on the same question differed, 0.22 vs 0.01, and P (q) + P (not q) ≠ 1, 0.72 + 0.47. The page dropped those
   numbers.)
9. **Generation** — not a generator; extract candidates with regex/an LLM and let Jev choose.

## 9. Legacy preview API — do not use

Old `POST /preview/evaluation` is gone. v1 renames: `document`→`state` (sending `document` now fails 422), `prompts[]`
with `key` → `questions{}` map, Choice `options[]` → `criteria{}`, Score `levels[{level,description}]` → `criteria[]`
(no skipped levels), `responses[]` → `answers{}`, answer fields `probability`→`noul`, `chosen`→`choice`, `expectation`→
`score`, Choice `probabilities` array → map, Score gained `probabilities`, `usage.billing_units` → `input_tokens`/
`output_tokens`, confidence recomputed. Python package `typesafe-client` is dead → `typesafe-sdk`. The site's migration
guide (`migrating-to-v1.md`), the source of this list, was removed in October 2026, so this is now the only record.

## 10. Implications for a Kotlin client (inferred)

- Model `EntryType` (instructions, option descriptions, levels, Noul true/false) as arbitrary JSON (e.g.
  kotlinx.serialization `JsonElement`) with string convenience builders.
- Question/Answer are sealed hierarchies discriminated by `"type"` (`noul`/`choice`/`score`, and now `box`). Choice
  option keys and Score level keys are strings on the wire; expose Score levels as `Int` and `legend` values as JSON.
  Decode an unknown answer type to a raw fallback rather than failing; jev4k decodes `box` that way (`UnknownAnswer`).
- Validate client-side against the documented bounds: Choice ≤ 255 options, Score 2–10 levels (the docs give no minimum
  option count for Choice; 11 Score levels returns a server error), nesting ≤ 64 per value, finite numbers only. Both
  SDKs also reject an empty `questions` map.
- Mirror the SDK retry table in §2 and the env-var precedence; expose `x-typesafe-request-id` on responses and errors.
  The JS SDK's generics (criteria keys → typed `choice`, score tuple → typed `legend`) are the closest model for an
  idiomatic typed Kotlin API; see `sdks.md` §4.
- Images and Box aren't modeled by jev4k. A base64 asset can be sent today through `JevCallOptions.extraBody["assets"]`
  plus the opt-in header in `headers`; multipart isn't possible. Compression is optional.
- Keep the API key out of source; read `TYPESAFE_API_KEY`.
