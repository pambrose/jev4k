---
icon: lucide/database
---

# State

The **state** is what Jev reads: the material you'd hand to an expert before asking them to decide. `query` and
`ask` accept it in three forms.

## Text

```kotlin
--8<-- "StateExamples.kt:string"
```

A string suits a single message, document or transcript.

## JSON

For anything with parts, prefer a JSON object, so each part has a name and the relationships stay clear:

```kotlin
--8<-- "StateExamples.kt:json"
```

This is one state, even though it holds a conversation, an order and a policy. Put information together when
the decision needs to compare the parts.

## @Serializable values

Any `@Serializable` value works as state. Fields that equal their defaults are still sent, so the model sees
them:

```kotlin
--8<-- "StateExamples.kt:serializable"
```

`Order("A-104")` is sent as `{"id":"A-104","status":"open","items":[]}`. A value that isn't `@Serializable`
is rejected with a `JevValidationException` that says so.

## Pointing questions at fields

Name the part of the state a question is about with a backticked dot-and-index path, such as
`` `ticket.messages[0].text` `` or `` `order.charges` ``, as in the JSON example above. Explicit paths tell the
model which part to judge.

## Send only what's needed

Unrelated detail acts as a distractor: accuracy falls as the state fills with content the question doesn't
need. Filter in code first. Given a ticket type like this:

```kotlin
--8<-- "Shared.kt:support-ticket"
```

send only the parts the question reads:

```kotlin
--8<-- "StateExamples.kt:focused"
```

- **Text only.** Jev reads text: strings, JSON objects, and arrays of text. Convert images, audio and binary
  data to text or fields first.
- **English works best.** Other languages, including CJK scripts, work but less well; test on your own data.
- **Context limits.** A request's state plus all its questions must fit in 64k tokens, and the state plus the
  longest single question in 32k.
