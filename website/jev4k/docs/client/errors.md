---
icon: lucide/triangle-alert
---

# Retries & Errors

## Retries

Failed requests are retried automatically. `RetryPolicy`'s defaults match TypeSafe's official Python and JS
SDKs:

| Setting                  | Default           | Meaning                                                              |
|--------------------------|-------------------|----------------------------------------------------------------------|
| `maxRetries`             | 2                 | retries after the first attempt; 0 disables retries                  |
| `retryStatuses`          | 408, 429, 500–599 | HTTP statuses worth retrying, including 529 Overloaded               |
| `retryOnConnectionError` | `true`            | retry when no response arrives (DNS, refused or dropped connections) |
| `retryOnTimeout`         | `true`            | retry an attempt that exceeded `timeout`                             |
| `initialBackoff`         | 0.5 s             | first delay, doubling on each retry                                  |
| `maxBackoff`             | 5 s               | cap on the delay                                                     |
| `jitter`                 | 0.25              | subtract up to this fraction of each delay at random                 |
| `respectRetryAfter`      | `true`            | honor the server's `retry-after-ms` / `Retry-After` header           |
| `maxRetryAfter`          | 60 s              | ignore server hints longer than this and use backoff instead         |

`RetryPolicy.NONE` turns retries off. The [Configuration](configuration.md#retry-policies) page shows common
variations.

Other 4xx errors, such as a bad key or an invalid request, are never retried: retrying can't fix them.

## Errors

Every failure of a request or a response is a `JevException`:

| Exception                           | When                                                                                                           |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `JevConfigException`                | the client can't be configured, e.g. no API key                                                                |
| `JevValidationException`            | the request broke a local rule; `problems` lists every issue, and nothing was sent                             |
| `JevApiException`                   | a non-2xx response after retries, carrying `status`, `body`, `bodyJson`, `headers`, `requestId` and `endpoint` |
| ↳ `JevBadRequestException`          | 400                                                                                                            |
| ↳ `JevAuthenticationException`      | 401: missing or invalid API key                                                                                |
| ↳ `JevPermissionDeniedException`    | 403                                                                                                            |
| ↳ `JevNotFoundException`            | 404                                                                                                            |
| ↳ `JevUnprocessableEntityException` | 422: the server rejected the request; `body` names the field                                                   |
| ↳ `JevRateLimitException`           | 429; `retryAfter` is the server's hint, if it sent one                                                         |
| ↳ `JevInternalServerException`      | 5xx                                                                                                            |
| ↳ ↳ `JevOverloadedException`        | 529: TypeSafe is temporarily overloaded                                                                        |
| ↳ `JevResponseValidationException`  | a 2xx response that was malformed or didn't match the questions; `fieldPath` locates it                        |
| `JevConnectionException`            | no response at all: DNS, TLS, or a refused or dropped connection                                               |
| ↳ `JevTimeoutException`             | an attempt exceeded `timeout`                                                                                  |

Messages include the status and request id, and never the API key.

Misusing a result is a programming error, not a `JevException`: asking for an id or handle that wasn't in the
request, or reading a Noul as a Choice, throws `IllegalArgumentException`.

## Handling errors

```kotlin
--8<-- "ErrorExamples.kt:handling"
```

Or map any failure to a description with a `when`:

```kotlin
--8<-- "ErrorExamples.kt:when"
```

## Validation errors

Local validation reports every problem at once, before anything is sent:

```kotlin
--8<-- "ErrorExamples.kt:validation"
```
