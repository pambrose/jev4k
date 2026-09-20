---
icon: lucide/scan-text
---

# Extraction

Jev chooses; it doesn't generate. That's an advantage for extraction: when the model picks from candidates your
code found, it can't invent a value or transpose a digit.

## Pick from candidate spans

Find candidates with a regex (or a parser, a roster, or an NER model), offer them as Choice options, and copy the
chosen span verbatim:

```kotlin
--8<-- "ExtractionExamples.kt:candidate-spans"
```

- **Finding candidates is the hard part.** Regexes should over-find: Jev handles the disambiguation. For
  example, the sender wants the receipt at their *personal* address, not the one in the `From:` line.
- **Always include a `none` option,** and check for it before using the value.
- **Stay under 255 candidates.** For more, narrow in two stages: pick a section first, then a span inside it.

## Dates: read the parts, compute in code

Jev reads dates as text, not as ordered quantities, so comparing dates or counting days in the model is
unreliable. Instead, ask which date *parts* the text states, each from a closed set, and do the calendar math
in code:

```kotlin
--8<-- "ExtractionExamples.kt:date-query"
```

```kotlin
--8<-- "ExtractionExamples.kt:date-assemble"
```

Validate in code, too. In TypeSafe's example, a document containing a *different* date made the mode come back
as "absolute" with no month. Only the incomplete parts and the low minimum confidence (0.46) caught it.

## Function calling

Map a natural-language command onto an ordinary typed function:

- a Choice picks the function
- a Choice per closed-set argument fills in its value
- a "stated" Noul per optional argument decides whether the user specified it at all

```kotlin
--8<-- "ExtractionExamples.kt:function-calling"
```

Without the "stated" Nouls, a Choice would confidently fill in a window the user never mentioned.

Some tips for argument questions:

- Write each question about the idea, not the user's likely words.
- Don't name a question after its parameter.
- When two arguments share options, spell out each one's role: "the stock being measured, named first"
  versus "the one it's compared against".

## Keep arithmetic in code

The same rule applies to amounts: let Jev identify *which* number is the total, then compute with it in code:

```kotlin
--8<-- "BestPracticeExamples.kt:math-in-code"
```
