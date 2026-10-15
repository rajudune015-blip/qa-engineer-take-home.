# Supplied Sample Survey Schema — QA Review

## Valid skip-logic example used by the Playwright test

In `Student Interests & Hobbies`, `q_3` asks “Do you have a hobby?” and `q_4` asks “What is your hobby? (If Yes)”. The supplied `q_4.visibilityLogic` is enabled, targets `q_3`, uses `equals`, and expects `Yes`.

Expected behavior: q_4 starts hidden; selecting Yes shows it; selecting No hides it. The demo clears a previous q_4 answer when the field becomes hidden. Product owners should confirm whether hidden answers must be cleared or retained for audit purposes.

## Defects to raise against the supplied JSON

1. **Invalid JSON syntax:** the `hfc_sleep_plausible` `logic` array contains two adjacent objects without a comma, so standard JSON parsers cannot load the file as supplied.
2. **Unexpected condition property:** the Survey Builder specification documents `logic[].attr`, but the sample uses `questioID`. Confirm the canonical field name and align producer/consumer contracts.
3. **Contradictory logic:** the HFC rule checks `q_2 = Yes` AND `q_3 < 6`, while `q_3` is visible only when `q_2 = No`. Under the documented visibility rule, the combined condition cannot normally be satisfied. Confirm the intended condition/action.
4. **Schema contract mismatch:** the Android specification uses `validationRules` (object or empty array in examples), while the Builder capabilities document describes a `validations` array of `{type,value,message}`. Define a canonical schema version or explicit adapter.
5. **Coverage gap:** the sample primarily exercises Text, Dropdown, and Number; it does not validate all 20 input types described in the Builder capabilities document.

## Recommended schema publication gate

Parse JSON and validate it against a versioned schema before publication. Check unique question IDs, supported input types/operators, valid visibility/HFC references, valid rule properties, and satisfiable logic. Reject or clearly flag invalid schemas instead of letting the mobile renderer silently skip rules.
