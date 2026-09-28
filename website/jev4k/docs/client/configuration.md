---
icon: lucide/settings
---

# Configuration

## Defaults

With `TYPESAFE_API_KEY` set, a no-argument `JevClient()` is ready to use:

```kotlin
--8<-- "ClientExamples.kt:default"
```

`JevClient` holds a Ktor HTTP client, so close it when you're done. `use { }` does that for you.

## The configuration builder

```kotlin
--8<-- "ClientExamples.kt:builder"
```

Each setting resolves in this order: the explicit value, then the environment variable, then the default.
String settings are trimmed, so the trailing newline of a value read from a file is harmless, and a value that is
blank after trimming counts as unset.

| Setting             | Environment variable     | Default                                                                        |
|---------------------|--------------------------|--------------------------------------------------------------------------------|
| `apiKey`            | `TYPESAFE_API_KEY`       | none; required                                                                 |
| `baseUrl`           | `TYPESAFE_BASE_URL`      | `https://api.typesafe.ai`                                                      |
| `defaultModel`      | `TYPESAFE_DEFAULT_MODEL` | `jev-latest`                                                                   |
| `timeout`           |                          | 10 seconds per HTTP attempt                                                    |
| `retry`             |                          | `RetryPolicy()`, matching the official SDKs; see [Retries & Errors](errors.md) |
| `engine`            |                          | the platform's default engine, created by the client (CIO on the JVM)          |
| `headers`           |                          | none; extra headers sent with every request                                    |
| `allowInsecureHttp` |                          | `false`; allows a plain `http://` `baseUrl` on a host other than this machine  |

A missing API key throws a `JevConfigException` that names `TYPESAFE_API_KEY`. `JevConfig.toString()` redacts
the key, so it's safe to log.

## Running with Ollaya

jev4k works with [Ollaya](https://ollaya.dev), which serves the same API from your own machine. Ollaya runs
Laya, a different model from Jev, so answers can differ from TypeSafe's. No code changes are needed; point the
client at it with three environment variables:

```bash
--8<-- "ClientConfig.txt:ollaya"
```

For an Ollaya server on another host, such as `http://ollaya:11435` in Docker, also set `allowInsecureHttp = true`:
plain `http://` is accepted without it only for this machine (`localhost`, `127.x.x.x`, `::1`).

## Retry policies

```kotlin
--8<-- "ClientExamples.kt:retry"
```

To use a different timeout, retry policy or headers for some calls only, pass
[per-call options](calls.md#per-call-options) rather than building a second client.

## A custom HTTP engine

The client creates its own engine unless you pass one: CIO on the JVM, Darwin on Apple platforms, Curl on Linux,
WinHttp on Windows, and the `fetch`-based Js engine on Node.js. To tune connection pooling, TLS, or proxies, pass
your own Ktor engine:

```kotlin
--8<-- "ClientExamples.kt:engine"
```

The client doesn't close an engine you pass in, so one engine can be shared by several clients. Close it
yourself when you're done with it. `timeout` still bounds each whole attempt, but the connect and socket
timeouts are left to an engine you supply, so settings like `endpoint.connectTimeout` above take effect.

The builder checks the settings Ktor would otherwise reject on every request, and throws a `JevConfigException`
listing every problem it found at once:

- `baseUrl` must be an absolute `https://` URL with a host and, optionally, a path. Plain `http://` is accepted
  for this machine, or anywhere with `allowInsecureHttp = true`. Credentials, a query or a fragment are refused;
  send what a gateway needs in `headers` instead.
- `apiKey` must not contain control characters, and `headers` must be valid HTTP names and values.
- `timeout` must be at least a millisecond.

The messages never quote the key, a header value, or a URL that could hold credentials.

## Security

- Keep API keys on the server. Never ship them in a browser or mobile client; an Apple-platform app should reach
  Jev through a backend of your own (`baseUrl`) that adds the real key.
- The key is sent as `Authorization: Bearer ...` on every request, and redacted from `JevConfig.toString()`.
- Every request carries a `User-Agent` of `jev4k/<version>`. A `headers` entry of the same name replaces the
  built-in one rather than adding a second value, so a gateway that needs its own `Authorization` or `Accept` can
  have it.
- Redirects are never followed. A 3xx response is reported as a `JevApiException` with its status, because following
  one would send your headers, and on Node.js the request body, to whatever host it names.
- A response whose declared `Content-Length` is over 16 MiB is refused before its body is read into memory. A body
  sent without a length isn't checked; the per-attempt `timeout` bounds how long it can stream.
