---
icon: lucide/triangle-alert
---

# Retries & Errors

## Retries

Failed requests are retried automatically. `RetryPolicy`'s defaults match TypeSafe's official JS SDK:

| Setting                  | Default           | Meaning                                                              |
|--------------------------|-------------------|----------------------------------------------------------------------|
| `maxRetries`             | 2                 | retries after the first attempt; 0 disables retries                  |
| `retryStatuses`          | 408, 429, 500–599 | HTTP statuses worth retrying, including 529 Overloaded               |
| `retryOnConnectionError` | `true`            | retry when no response arrives (DNS, refused or dropped connections) |
| `retryOnTimeout`         | `true`            | retry an attempt that exceeded `timeout`                             |
| `initialBackoff`         | 0.5 s             | first delay, doubling on each retry                                  |
| `maxBackoff`             | 5 s               | cap on the delay                                                     |
| `jitter`                 | 0.25              | subtract up to this fraction of each delay at random                 |
| `respectRetryAfter`      | `true`            | honor `retry-after-ms` / `Retry-After` (seconds or an HTTP date)     |
| `maxRetryAfter`          | 60 s              | ignore server hints longer than this and use backoff instead         |

The Python SDK shares these retries, backoff, jitter and statuses, but doesn't cap server hints, and it also gives
each call a 30 s budget in total. jev4k has no total budget: `timeout` applies to each attempt, so a call that
retries on long server hints can take a couple of minutes.

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
| ↳ `JevRateLimitException`           | 429; `retryAfter` (`retryAfterMillis` from Java) is the server's hint, if it sent one                          |
| ↳ `JevInternalServerException`      | 5xx                                                                                                            |
| ↳ ↳ `JevOverloadedException`        | 529: TypeSafe is temporarily overloaded                                                                        |
| ↳ `JevResponseValidationException`  | a 2xx response that was malformed or didn't match the questions; `fieldPath` locates it                        |
| `JevConnectionException`            | no complete response: DNS, TLS, a refused or dropped connection, or a body cut short                           |
| ↳ `JevTimeoutException`             | an attempt exceeded `timeout`, or a supplied engine's own connect or socket timeout (the message names which)  |

Messages include the status and request id, and never the API key. Header names in `headers` are lowercased, so
`e.headers["retry-after"]` finds the header however the server spelled it and whichever engine read it.

A redirect isn't followed, so a 3xx arrives as a plain `JevApiException`. A response declaring a body over 16 MiB is
refused unread: a 2xx as a `JevResponseValidationException`, anything else as its status's exception with a null
`body`, the message saying why.

On Linux and Windows, any bare `IllegalStateException` raised during a call is also a `JevConnectionException`,
because that is how the Curl and WinHttp engines report a failed connection; the original is kept as its cause.

Misusing a result is a programming error, not a `JevException`: asking for an id or handle that wasn't in the
request, reading a Noul as a Choice, or reading a Choice with an enum that lacks one of its options throws
`IllegalArgumentException`.

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
