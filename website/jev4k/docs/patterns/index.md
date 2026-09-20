---
icon: lucide/layers
---

# Patterns

Jev is designed to sit inside a larger program. Code owns the control flow, the arithmetic and the side
effects; Jev supplies narrow judgments where the program needs to understand language. These patterns show
how those pieces fit. They're adapted from TypeSafe's documentation and cookbooks, written with jev4k.

| Pattern                                   | What it does                                                                                 |
|-------------------------------------------|----------------------------------------------------------------------------------------------|
| [Speculative Fan-Out](fan-out.md)         | Ask every question a code path *might* need in one request; code picks the relevant answers  |
| [Routing](routing.md)                     | Classify a request and send it to the right handler: deterministic code, an LLM, or a person |
| [Composite Scoring](composite-scoring.md) | Score dimensions separately and combine them with weights you control                        |
| [Verification](verification.md)           | Check citations, extractions and tool calls, and escalate only what fails                    |
| [Extraction](extraction.md)               | Recover exact values: code finds candidates, Jev picks; code does the arithmetic             |
| [Search & Ranking](search.md)             | Re-rank retrieved passages, find the line that answers a question, filter RAG context        |
| [Guardrails & Moderation](guardrails.md)  | Screen LLM inputs and outputs, and moderate content, under named policies                    |
| [Classification](classification.md)       | Walk deep taxonomies and pick from large option sets in stages                               |

## Principles they share

1. **Keep deterministic work in code.** String matching, schema validation, counting, and date math are cheaper
   and more reliable in code.
2. **Ask narrow questions.** One snap judgment per question; combine answers in code.
3. **One request per decision.** Put the whole unit (a pair, a query and a passage, a claim and its source) in
   the state, and ask all of its questions together.
4. **Keep policy in code.** Thresholds, weights and precedence are constants you can review and change without
   new requests.
5. **Always keep an "unsure" path.** A confidence gate, a middle Score level, or a Noul band sends the ambiguous
   cases to a person or a stronger model.
