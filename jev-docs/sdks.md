# TypeSafe SDKs (Python + JS): notes for jev4k

- Sources: `pages/sdk.md`, `sdk/python*`, `sdk/javascript*`; cross-checked with `api.md` and `models.md`.
- Versions: Python `typesafe-sdk` **0.7.0** (built on `httpx2` + `pydantic`). JS `@typesafe-ai/sdk` **0.6.0**
  (`VERSION = "0.6.0"`; `fetch`; Node.js 20+; ESM + CJS + .d.ts).
- Repos: github.com/typesafe-ai/typesafe-sdk-python, …/typesafe-sdk-js (`src/client.ts`, `src/types.ts` @ v0.6.0).

## 1. Wire contract as the SDKs show it

**Base URL**: `https://api.typesafe.ai`, overridable with env `TYPESAFE_BASE_URL`. JS strips trailing slashes.

**Endpoints**

- `POST /v1/systemone`: api.md, and JS `SystemOneRequestPayload` ("Request body for `POST /v1/systemone`").
- `GET /v1/models`: **not in the SDK files**; from `models.md` ("currently lists the aliases"; versioned IDs like
  `jev-1.13.0` accepted even if unlisted).

**Headers**

- `Authorization: Bearer <API_KEY>`; `Content-Type: application/json`.
- `Accept` is set by the SDK. Python treats it as "protected" from `extra_headers`. **Value not specified.**
- An "SDK identification" header is also protected (Python). **Name and value (User-Agent format) not specified.**
- Response header `x-typesafe-request-id` is exposed by both SDKs, on success and on error.
- Retry hints read from responses: `Retry-After` and `retry-after-ms`. **Not specified**: which takes precedence, and
  whether Retry-After is seconds or an HTTP-date.
- **Not mentioned anywhere**: idempotency keys, `x-should-retry`, client-sent request IDs.
- Custom headers: Python client `headers=` + per-call `extra_headers=`; JS `defaultHeaders` + per-call `headers` (merged
  over).

**Request**

```json
{"state": "text" | {..} | [..],
 "model": "jev-latest",
 "questions": {
   "<id>": {"type":"noul",  "instructions":"...", "criteria":{"true":"...","false":"..."}},
   "<id>": {"type":"choice","instructions":"...", "criteria":{"billing":"desc","other":null}},
   "<id>": {"type":"score", "instructions":"...", "criteria":["Calm","Frustrated","Very angry"]}}}
```

- Question id is caller-chosen; the answer returns under the same id. Per api.md the key "is not sent to the underlying
  model".
- `criteria`: noul optional/`null` (optional `true`/`false` keys); choice required (label → description or `null`);
  score required **ordered array** (index i = level i, from 0).
- The SDKs always send a resolved `model` (default `jev-latest`). JS `SystemOneRequestPayload.model: string` is
  non-optional.
- Extra top-level fields: Python `extra_body` (e.g. `{"beam_width": 4}`) is shallow-merged last-write-wins, even over
  `state`/`model`/`questions`, nested objects replaced. JS: "Additional properties on a request variable are forwarded,
  including `null` values".
- Raw question dicts may carry unknown keys, e.g. `"weight": 2`, as a forward-compatibility escape hatch.
- **Not specified**: whether null optional fields (`instructions`, `criteria`) are omitted or sent as `null`. Python
  schemas say `"default": null`, and JS `noul()` defaults `instructions` to `null`.

**Response**

```json
{"model":"jev-latest",
 "answers":{
   "u":{"type":"noul","noul":0.92},
   "d":{"type":"choice","choice":"technical","probabilities":{"billing":0.08,"technical":0.85,"sales":0.07},"confidence":0.82},
   "f":{"type":"score","score":1.6,"legend":{"0":"Calm","1":"Frustrated","2":"Very angry"},
        "probabilities":{"0":0.05,"1":0.3,"2":0.65},"confidence":0.78}},
 "usage":{"input_tokens":312,"output_tokens":48}}
```

- `noul` = P (yes) in [0,1]; `choice` = argmax label (`probabilities` sum ≈1); `score` = probability-weighted mean of
  levels (can be fractional).
- Score `legend`/`probabilities` use **string** keys `"0".."n"` on the wire (Python parses as `int`; JS types
  `number | \`${number}\``). Legend value = original criteria entry.
- `confidence` (0..1): choice and score only.

**Models list**: `{"models":[{"name","description","release_date"}]}`, all strings; `release_date` is `YYYY-MM-DD`.
Python returns `ListModelsResponse`; JS unwraps to `ModelCard[]`.

**Where the SDKs and api.md disagree, or add detail**

| Topic                        | api.md                                                                      | Python                                                             | JS                                                                                 |
|------------------------------|-----------------------------------------------------------------------------|--------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `model`                      | required                                                                    | optional; env or `jev-latest`, always sent                         | same                                                                               |
| `state`                      | required; str, obj or arr                                                   | `JSONContent`, **not None** (nested None OK)                       | `EntryType`, **null allowed**                                                      |
| `instructions`               | required; str, obj or arr                                                   | optional, `JSONContent\|None=None`                                 | optional `EntryType` (incl. null)                                                  |
| noul criteria `true`/`false` | string                                                                      | `JSONContent\|None`                                                | `EntryType`                                                                        |
| choice criteria values       | `string\|null`                                                              | `JSONContent\|None`                                                | `EntryType`                                                                        |
| score criteria               | array, ≥2 levels                                                            | `Sequence[JSONContent]`, **nonempty only**, items non-null         | `[EntryType, EntryType, ...EntryType[]]`, **≥2 checked at runtime**, null items OK |
| score `legend` values        | string                                                                      | `str\|dict\|list`                                                  | `T[score]` (the criteria entry)                                                    |
| answer `type`                | required                                                                    | defaulted, not in `required`                                       | required literal                                                                   |
| `usage.*`                    | integer, required                                                           | `int\|None=None` ("when reported")                                 | `number`, required                                                                 |
| `answers`                    | required                                                                    | not in the schema's `required` list (only `model` and `usage` are) | required                                                                           |
| statuses                     | 401, 422, 429, 529                                                          | classes for 400/401/403/404/422/429/5xx; retries 408 too           | same                                                                               |
| error body                   | JSON "describing what went wrong"; a 422 body "details the offending field" | kept raw (JSON, text or None)                                      | JSON, text or undefined                                                            |

## 2. Client defaults to replicate

**Config precedence**: explicit option, then env var, then SDK default. Empty or whitespace-only env values are ignored.

| Env                      | Meaning                                                   | Default                                            |
|--------------------------|-----------------------------------------------------------|----------------------------------------------------|
| `TYPESAFE_API_KEY`       | API key, **required**. Missing key throws at construction | none                                               |
| `TYPESAFE_BASE_URL`      | API root                                                  | `https://api.typesafe.ai`                          |
| `TYPESAFE_DEFAULT_MODEL` | model                                                     | `jev-latest`                                       |
| `TYPESAFE_LOG_LEVEL`     | log level                                                 | Python: unset (applied once at import). JS: `warn` |

- Python constants: `API_KEY_ENV`, `BASE_URL_ENV`, `DEFAULT_MODEL_ENV`, `LOG_LEVEL_ENV`, `DEFAULT_BASE_URL`,
  `DEFAULT_MODEL`, `DEFAULT_TIMEOUT = 10.0`. JS: `ENV = {apiKey, baseURL, defaultModel, logLevel}` (var names);
  `EnvVar` = their union.

**Timeouts**

- Python: 10.0 s "for each HTTP operation" (`float` or `httpx2.Timeout`); inherits `http_client.timeout` if supplied;
  per-call override; invalid value raises `TypeSafeError`.
- JS: 10000 ms **per attempt**, covering the full body (buffered under the timeout); per-call override.
- Total budget: Python `RetryPolicy.timeout = 30.0` s per call (first attempt + delays; `None` = unlimited); it stops
  before a retry whose delay would reach or exceed the budget and re-raises the last error. JS: **none**.

**RetryPolicy** (Python dataclass in seconds; JS interface in ms)

| Python                  | JS                   | Default                                                                              |
|-------------------------|----------------------|--------------------------------------------------------------------------------------|
| `max_retries`           | `maxRetries`         | 2 retries after the first attempt; 0 disables                                        |
| `backoff_initial`       | `backoffInitialMs`   | 0.5 s / 500 ms, doubled each attempt                                                 |
| `backoff_max`           | `backoffMaxMs`       | 5.0 s / 5000 ms                                                                      |
| `backoff_jitter`        | `backoffJitter`      | 0.25: a random fraction of the delay (0..1) is *subtracted*                          |
| `http_statuses`         | `httpStatuses`       | {408, 429, 500–599}, which includes 529                                              |
| `respect_retry_after`   | `respectRetryAfter`  | true: honor `Retry-After` / `retry-after-ms`                                         |
| (none)                  | `maxRetryAfterMs`    | JS 60000; a longer server delay falls back to backoff. **Python: no cap documented** |
| `api_connection_error`  | `apiConnectionError` | true. JS includes interrupted response bodies                                        |
| `api_timeout_error`     | `apiTimeoutError`    | true                                                                                 |
| `exceptions: set[type]` | (none)               | Python only: extra exception types to retry                                          |
| `predicate(exc)->bool`  | (none)               | Python only: True triggers a retry                                                   |
| `timeout`               | (none)               | Python only: 30.0 s total budget                                                     |

- Backoff: `delay_n = min(initial·2^(n-1), max)`, minus up to `jitter·delay`. The random distribution is not specified.
- Python: `backoff_initial` or `backoff_max` = 0 disables backoff. Invalid values are rejected (0.6.0).
- Overrides: Python `retry=` on client or per call (per-call replaces client's). JS `retry: Partial<RetryPolicy>` on
  both; omitted fields inherit.
- JS `RequestOptions.signal: AbortSignal` cancels the request **and pending retries** (throws `APIUserAbortError`).
- Errors surface only "after any retries". Other 4xx (400/401/403/404/409/422) are not retried by default.

**Logging**

- Python: logger `typesafe_sdk`. Levels: `debug`, `info`, `warning`, `error`, `off`.
- JS: `LogLevel = "debug"|"info"|"warn"|"error"|"off"` (default `warn`; `LOG_LEVELS` most→least verbose). `Logger` =
  `debug/info/warn/error(message, ...args)`; default is a prefixed `console`.
- `info` logs one summary line per request. `debug` adds headers and bodies.
- Redaction: Python redacts authorization, API keys, cookies, and headers whose name contains `token`/`secret`; JS
  "known credential headers". **Bodies never redacted.**
- Python skips unknown answer kinds with a warning; `raw_http_response.json()["answers"]` still shows them. Unknown
  response fields are ignored (pydantic `extra="ignore"`, `frozen`, `strict`).

**Other behavior**

- Python: `TypeSafeClient` / `AsyncTypeSafeClient` are context managers; `close()` / `await aclose()` also closes a
  supplied client. `transport=` and `http_client=` are mutually exclusive (`ValueError`); either is closed with the SDK
  client.
- JS: injectable `fetch`; `dangerouslyAllowBrowser` (default false) allows browser use, exposing the key. Constructor
  throws if "the API key is missing, configuration is invalid, or the runtime is unsupported".
- Client-side validation: empty `questions` fails in both; Python `TypeSafeError` on empty score criteria; JS throws
  on <2 score criteria.

## 3. Error model

**Python** (all picklable since 0.6.0; messages include HTTP details and metadata)

```
TypeSafeError(Exception)             # base; also: missing key, bad timeout, empty questions/score criteria
+- TypeSafeAPIError                  # non-2xx after retries: status, body, headers, endpoint, request_id(prop, str|None)
|  +- TypeSafeBadRequestError 400 · TypeSafeAuthenticationError 401 · TypeSafePermissionDeniedError 403
|  +- TypeSafeNotFoundError 404 · TypeSafeUnprocessableEntityError 422 · TypeSafeInternalServerError 5xx
|  +- TypeSafeRateLimitError 429     # + retry_after_ms = parse_retry_after(headers); ms or None
|  `- TypeSafeAPIResponseValidationError  # 2xx body missing/invalid required data; + field_path e.g. "answers.tone.confidence"
|                                         #   args = (status, body, headers, field_path, endpoint)
`- TypeSafeAPIConnectionError(TypeSafeError, ConnectionError)   # no HTTP response
   `- TypeSafeAPITimeoutError(..., TimeoutError)                # + timeout (seconds or httpx2.Timeout)
```

- `body`: JSON, text, or None if empty. `endpoint`: method + URL without credentials/query/fragment ("when available").

**JS**

```
TypeSafeError(message, options?) extends Error
+- APIError(status, body, headers: Headers, message?)  # status, body (JSON|text|undefined), headers, requestId
|  |  static fromResponse(status, body, headers): APIError  -> subclass by status
|  +- BadRequestError 400 · AuthenticationError 401 · PermissionDeniedError 403 · NotFoundError 404
|  +- UnprocessableEntityError 422 · InternalServerError 5xx
|  `- RateLimitError 429  + retryAfterMs: number|undefined (undefined when absent/invalid)
+- APIConnectionError(message="Connection error.")   # DNS, TLS, closed conn, interrupted body
|  `- APITimeoutError(timeoutMs)                     # + timeoutMs
`- APIUserAbortError(message="Request was aborted.")
```

- JS has **no** response-validation error class.
- **Not specified**: which class other statuses (e.g. 408, 409) map to. Presumably the base API error.
- 529 "Overloaded" (api.md) is a 5xx, so it maps to `InternalServerError` and is retried by default.

## 4. Typed question/answer design

**Value types**

- Python: `JSONValue = str|int|float|bool|Sequence[JSONValue|None]|Mapping[str,JSONValue|None]`;
  `JSONContent = str|Mapping[str,JSONValue|None]|Sequence[JSONValue|None]`.
- JS: `JsonValue = string|number|boolean|null|JsonValue[]|{[k]:JsonValue}`;
  `EntryType = string|{[k:string]:JsonValue}|JsonValue[]|null` (state, instructions, criteria);
  `Description = EntryType` (null = undescribed).

**Python questions**: objects and dicts can be mixed in one request.

- Pydantic objects (`additionalProperties:false`; `type` is a defaulted Literal):
    - `Noul(instructions: JSONContent|None=None, criteria: NoulCriteria|None=None)`. `NoulCriteria` is a TypedDict
      `{true, false: JSONContent|None}`.
    - `Choice(criteria: Mapping[str, JSONContent|None], instructions=None)`
    - `Score(criteria: Sequence[JSONContent], instructions=None)`: nonempty and ordered; index = score.
- TypedDicts `NoulModel`/`ChoiceModel`/`ScoreModel`: required `type` Literal; `instructions` NotRequired; `criteria`
  NotRequired only for noul.
- Aliases: `QuestionModel = NoulModel|ChoiceModel|ScoreModel`, `Question = Noul|Choice|Score|QuestionModel`,
  `Questions = Mapping[str, Question]`.
-
`system_one(state, questions, *, model=None, retry=None, timeout=None, extra_headers=None, extra_body=None, response_model=None)` →
`SystemOneResponse`, or `ResponseT` when `response_model=type[ResponseT]`.

**Python answers** (frozen, strict, extra ignored)

- `NoulAnswer{type:'noul', noul: float}`
- `ChoiceAnswer{type:'choice', choice: str, confidence: float, probabilities: dict[str,float]}`
-
`ScoreAnswer{type:'score', score: float, confidence: float, legend: dict[int, str|dict|list], probabilities: dict[int,float]}`
- `Answer = Annotated[NoulAnswer|ChoiceAnswer|ScoreAnswer, Field(discriminator="type")]`
- `Usage{input_tokens: int|None=None, output_tokens: int|None=None}`
- `SystemOneResponse{model: str, usage: Usage, answers: dict[str, Answer]}` + cached views `.nouls`/`.choices`/`.scores`
  (keyed by question id), `.request_id: str`, `.raw_http_response: httpx2.Response`.
- `response_model` (0.7.0): subclass `SystemOneResponse` with fields named after question ids (e.g.
  `billing: NoulAnswer`, then `result.billing == result.nouls["billing"]`), or any `BaseModel` mirroring the raw body
  (e.g. `answers: BillingAnswers`). Mismatch raises `TypeSafeAPIResponseValidationError`.
- `client.models` (`Models` / `AsyncModels`)`.list(*, retry, timeout, extra_headers)` →
  `ListModelsResponse{models: tuple[ModelMetadata,...]}` (+ `request_id`, `raw_http_response`).
  `ModelMetadata{name, description, release_date}`.

**JS**: generics carry the criteria keys through to the answers.

```ts
function noul(instructions?: EntryType /*=null*/, criteria?: {true?: EntryType; false?: EntryType} | null): NoulQuestion;
function choice<T extends ChoiceCriteria>(instructions: EntryType, criteria: T): ChoiceQuestion<T>;
function score<T extends ScoreCriteria>(instructions: EntryType, criteria: T): ScoreQuestion<T>;
type ChoiceCriteria = { [label: string]: EntryType };
type ScoreCriteria  = readonly [EntryType, EntryType, ...EntryType[]];          // ≥2; index = score
interface NoulQuestion      { type: "noul";   instructions?: EntryType; criteria?: {true?; false?} | null }
interface ChoiceQuestion<T> { type: "choice"; instructions?: EntryType; criteria: T }
interface ScoreQuestion<T>  { type: "score";  instructions?: EntryType; criteria: T }
type Question = NoulQuestion | ScoreQuestion | ChoiceQuestion;  interface Questions { [name: string]: Question }

interface NoulResponse      { readonly type: "noul"; readonly noul: number }
interface ChoiceResponse<T> { readonly type: "choice"; readonly choice: keyof T & string; readonly confidence: number;
                              readonly probabilities: { readonly [label: string]: number } }
interface ScoreResponse<T>  { readonly type: "score"; readonly score: number; readonly confidence: number;
                              readonly legend: ScoreLegend<T>; readonly probabilities: { readonly [s in number | `${number}`]: number } }
type ScoreOf<T>     = number extends T["length"] ? number : Extract<keyof T, `${number}`>;  // tuple -> its indices
type ScoreLegend<T> = { readonly [score in ScoreOf<T>]: T[score] };
type ResultFor<T> = T extends NoulQuestion ? NoulResponse : T extends ScoreQuestion<infer S> ? ScoreResponse<S>
                  : T extends ChoiceQuestion<infer E> ? ChoiceResponse<E> : never;
interface SystemOneResult<Q> { readonly answers: { readonly [K in keyof Q]: ResultFor<Q[K]> }; // docs render K as string|number|symbol
                               readonly model: string; readonly usage: Usage }
// Usage {input_tokens, output_tokens: number}; ModelCard {name, description, release_date: string} (all readonly)
```

-
`systemOne<Q extends Questions>(request: {state: EntryType; questions: Q; model?: string}, options?: RequestOptions): APIPromise<SystemOneResult<Q>>`
- `RequestOptions = {headers?, retry?: Partial<RetryPolicy>, signal?: AbortSignal, timeout?: number}`
- `models.list(options?): APIPromise<ModelCard[]>`
- `APIPromise<T> extends Promise<T>`: `asResponse()` (raw `Response`, caller owns body), `withResponse()` →
  `{data, response, requestId}`, `map(fn)` (shares one parse). Non-2xx rejects with `APIError`, even via `asResponse()`.
- JS has no nouls/choices/scores grouping: `answers.<id>`, with `choice` typed as the union of criteria keys. Client
  readonly props: `baseURL`, `defaultHeaders`, `defaultModel`, `fetch`, `logger`, `logLevel`, `models`, `retry`,
  `timeout`.

## 5. Examples

**Python**

```python
from typesafe_sdk import Choice, Noul, Score, TypeSafeClient, RetryPolicy, TypeSafeAPIError
with TypeSafeClient(model="jev-latest", retry=RetryPolicy(max_retries=3)) as client:  # key from TYPESAFE_API_KEY
    try:
        r = client.system_one(
            state={"document": "I was charged twice. Please fix this ASAP."},
            questions={
                "billing": Noul(instructions="Is this ticket about billing?"),
                "tone": Choice(instructions="What is the customer's tone?",
                               criteria={"calm": None, "frustrated": None, "angry": None}),
                "urgency": Score(instructions="How urgent is this ticket?",
                                 criteria=["can wait", "this week", "today"]),
            })
        print(r.nouls["billing"].noul, r.choices["tone"].choice, r.choices["tone"].confidence,
              r.scores["urgency"].score, r.usage.input_tokens, r.request_id)
    except TypeSafeAPIError as e:
        print(e.status, e.request_id)
    print(client.models.list().models)   # async: AsyncTypeSafeClient + await
```

**JS/TS**

```ts
import { TypeSafeClient, noul, choice, score, APIError } from "@typesafe-ai/sdk";
const client = new TypeSafeClient({ retry: { maxRetries: 3 }, timeout: 10_000 }); // key from TYPESAFE_API_KEY
try {
  const { data, requestId } = await client.systemOne({
    state: { document: "I was charged twice. Please fix this ASAP." },
    questions: {
      billing: noul("Is this ticket about billing?"),
      tone: choice("What is the customer's tone?", { calm: null, frustrated: null, angry: null }),
      urgency: score("How urgent is this ticket?", ["can wait", "this week", "today"]),
    },
  }).withResponse();
  data.answers.tone.choice;  // "calm" | "frustrated" | "angry"
  data.answers.billing.noul; data.answers.urgency.score; data.answers.urgency.legend; data.usage.input_tokens;
} catch (e) { if (e instanceof APIError) console.log(e.status, e.requestId); }
const models = await client.models.list(); // ModelCard[]
```

Unspecified: whether `as const` is needed for `ScoreOf` to infer tuple indices (else `number`).

## 6. Changelog highlights

- **Python 0.7.0 (2026-09-18)**: BREAKING serialization `msgspec` → `pydantic`; fix: `str` subclasses were serialized as
  char lists (emit JSON strings); new `response_model`.
- **Python 0.6.0 (2026-09-15)**: BREAKING **`Score.criteria` is an ordered sequence, no longer a dict keyed by
  integers**; inputs accept `Mapping`/`Sequence`; richer error messages; `RetryPolicy` validates values; exceptions and
  responses picklable.
- **JS 0.6.0 (2026-09-15)**: BREAKING: same `Score.criteria` change. First public releases: Python 0.5.7 (2026-09-14),
  JS 0.5.7 (2026-09-11).
- For the v1 wire format: score criteria are sent as a JSON array (matching api.md). Score answers still use
  string-keyed `legend` and `probabilities`. The changelogs mention no other wire changes.
