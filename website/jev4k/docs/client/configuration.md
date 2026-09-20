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
Blank environment variables are ignored.

| Setting        | Environment variable     | Default                                                                        |
|----------------|--------------------------|--------------------------------------------------------------------------------|
| `apiKey`       | `TYPESAFE_API_KEY`       | none; required                                                                 |
| `baseUrl`      | `TYPESAFE_BASE_URL`      | `https://api.typesafe.ai`                                                      |
| `defaultModel` | `TYPESAFE_DEFAULT_MODEL` | `jev-latest`                                                                   |
| `timeout`      |                          | 10 seconds per HTTP attempt                                                    |
| `retry`        |                          | `RetryPolicy()`, matching the official SDKs; see [Retries & Errors](errors.md) |
| `engine`       |                          | a CIO engine created by the client                                             |
| `headers`      |                          | none; extra headers sent with every request                                    |

A missing API key throws a `JevConfigException` that names `TYPESAFE_API_KEY`. `JevConfig.toString()` redacts
the key, so it's safe to log.

## Retry policies

```kotlin
--8<-- "ClientExamples.kt:retry"
```

## A custom HTTP engine

To tune connection pooling, TLS, or proxies, pass your own Ktor engine:

```kotlin
--8<-- "ClientExamples.kt:engine"
```

The client doesn't close an engine you pass in, so one engine can be shared by several clients. Close it
yourself when you're done with it. `timeout` still bounds each whole attempt, but the connect and socket
timeouts are left to an engine you supply, so settings like `endpoint.connectTimeout` above take effect.

`baseUrl` must carry a scheme, and `timeout` must be at least a millisecond; anything else is a
`JevConfigException` from the builder, with every problem it found listed at once.

## Security

- Keep API keys on the server. Never ship them in a browser or mobile client.
- The key is sent as `Authorization: Bearer ...` on every request, and redacted from `JevConfig.toString()`.
- Every request carries a `User-Agent` of `jev4k/<version>`. A `headers` entry of the same name replaces the
  built-in one rather than adding a second value, so a gateway that needs its own `Authorization` can have it.
