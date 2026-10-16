---
icon: lucide/image
---

# Images & Box

Jev reads PNG and JPEG images as well as text. An image can go anywhere text can: in the state, in a question's
instructions, and in its criteria. The Box question type finds where something is in an image.

!!! warning "Experimental"

    TypeSafe calls images and Box "Dreamer-ready": the request format, the answers and the limits can change between
    its releases, and so can this part of jev4k. Use them for prototypes, and see TypeSafe's
    [Images](https://docs.typesafe.ai/images) and [Box](https://docs.typesafe.ai/primitives/box) pages.

## Turning images on

Everything here is marked `@ExperimentalJevImages`. Opt in with `@OptIn(ExperimentalJevImages::class)` on the code
that uses it, or for a whole module with `optIn.add("com.pambrose.jev4k.ExperimentalJevImages")` in Gradle's
`compilerOptions`. jev4k then sends TypeSafe's `TypeSafe-Danger-Zone-Images: true` header with every request that
carries an image or a Box question. A header of the same name in the client's or the call's headers replaces it.

## Placing and attaching

A `JevImage` comes from bytes (`JevImage.of`), base64 text (`JevImage.fromBase64`), or a file (`JevImage.read`, with a
kotlinx-io `Path` anywhere, or a `java.nio.file.Path` or `File` on the JVM). Each one has a reference, a string that
stands for the image wherever it is placed. Its `toString()` is that reference, so string templates, `jsonOf`, `entry`
and `@Serializable` fields all place it. The API replaces every occurrence of a reference with the image.

Placing an image doesn't send it. `attach` it in the query block, or in a `JevQuery`, so its bytes go with the
questions. jev4k refuses to send a request that attaches an image nothing references, or that places a generated
reference without attaching its image.

```kotlin
--8<-- "ImageExamples.kt:state"
```

## Images in instructions

An image only one question needs can go straight into its instructions. An image several questions use belongs in the
state, where it is sent once; every place a reference appears adds another copy of the image to what the model reads.

```kotlin
--8<-- "ImageExamples.kt:instructions"
```

## Images in criteria

Choice options, Score levels and Noul definitions can be pictures, or descriptions with example pictures. Option names
are never replaced, so the picture goes in the option's description. A `JevQuery` attaches its own pictures, which
travel with every request that asks it; `ask(query, state, images...)` adds the call's own images after them.

```kotlin
--8<-- "ImageExamples.kt:criteria"
```

`JevImage.read` in an object's initializer, as above, reads the file there, and a missing file fails the object's
first use with an `ExceptionInInitializerError`. When that matters, read the bytes elsewhere and use `JevImage.of`.

A `@Serializable` field of type `JevImage` is written as its reference:

```kotlin
--8<-- "ImageExamples.kt:serializable"
```

## Your own references

A generated reference is `jev4k-image-` followed by a UUID. You can choose your own, for prompts kept in
configuration, but the API matches references as plain text anywhere inside a string: a reference `cat` also matches
inside `category`. Use delimiters that can't occur in your text.

```kotlin
--8<-- "ImageExamples.kt:references"
```

## Box

A Box question finds one target and answers with the box around it. Each edge is a fraction, from 0 to 1, of the
image's height (`top`, `bottom`) or width (`left`, `right`), measured from the top-left corner. A Box always returns a
box, even when the target isn't there, so ask a Noul in the same request. Box needs Jev 1.14 (`jev-preview` or
`jev-1.14.0`).

```kotlin
--8<-- "ImageExamples.kt:box"
```

To crop to the box, multiply each edge by the image's size in pixels:

```kotlin
--8<-- "ImageExamples.kt:crop"
```

## What jev4k checks

Before sending, jev4k reports every problem at once, as a `JevValidationException`:

- an image that is empty, isn't valid base64, or is neither a PNG nor a JPEG
- a reference that is blank or longer than 1,024 bytes, two images sharing a reference, or references over 64 KiB in
  total
- an attached image that no string references, or a generated reference placed without its image attached
- a Box question with no image attached, or `extraBody["assets"]` on a request that attaches images

The API enforces its own size and pixel limits (each side 256 to 1568 pixels, 16 MiB per file, 32 MiB per request),
answering with a 400 or a 413. Resize and rotate images before sending them; the API strips EXIF data and doesn't apply
its orientation.

A request with images is sent as `multipart/form-data`, the JSON body first and then one part per image, as the
official SDKs send it. Images count as input tokens at the text price.

If you cache results on the model, the state and the questions, include each attached image's bytes in the key too:
`QuestionSet.toJson()` holds only the references.

## From Java

Java ignores the opt-in annotation. Build an image with `JevImage.of(Files.readAllBytes(path))`:

```java
--8<-- "JavaInterop.java:images"
```
