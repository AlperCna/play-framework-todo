# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`

**Created**: [DATE]

**Type**: [Feature Implementation | Feature Enhancement | Bug Fixing | Refactoring | Performance Optimization | Code Understanding]

**Status**: Draft

**Input**: User description: "$ARGUMENTS"

<!--
============================================================================
TEMPLATE ROUTER — READ FIRST (delete this entire block from the final spec)

This template covers THREE task-type families. The **Type** in the header above
selects which body to keep. Map Type -> family:

  - Behavior-producing   -> Feature Implementation, Feature Enhancement, Bug Fixing
  - Behavior-preserving  -> Refactoring, Performance Optimization
  - Non-code             -> Code Understanding

STEPS:
  1. Confirm the **Type** field is set. If it is unknown, ASK the user — do not guess.
  2. Keep ONLY the one "FAMILY" section below that matches that family.
  3. DELETE the other two FAMILY sections, their banner comments, and this router block.
  4. The header above is universal — it stays for every family.

HARD RULE (all families): the spec MAY name boundaries and externally-fixed decisions —
in/out-of-scope modules (Scope), the target structure/technology of a migration/refactor
(Structural Goal, Old -> New Mapping), and externally imposed technology choices (Constraints).
The spec MUST NOT contain implementation HOW — algorithms, internal class/method design, control
flow; that is plan.md's job. Test: a decision GIVEN to the work belongs in the spec; a decision
MADE while implementing belongs in plan.
Mark genuine unknowns with [NEEDS CLARIFICATION: ...] (max 3).
============================================================================
-->

<!-- ===================== FAMILY: BEHAVIOR-PRODUCING =====================
     KEEP for: Feature Implementation, Feature Enhancement, Bug Fixing.
     Center of gravity: the new or changed behavior.
     Delete this whole section (banner to /banner) if the Type is not in this family. -->

## User Scenarios & Testing *(mandatory)*

<!--
  Prioritized user journeys (P1, P2, ...), each INDEPENDENTLY TESTABLE so that
  implementing just one still yields a viable MVP slice.
-->

### User Story 1 - [Brief Title] (Priority: P1)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it ranks here]

**Independent Test**: [How this story can be fully verified on its own]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]
2. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it ranks here]

**Independent Test**: [How this story can be fully verified on its own]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

[Add more user stories as needed, each with an assigned priority]

### Bug context *(Bug Fixing only — delete if not a bug fix)*

- **Reproduction**: [exact steps / conditions that trigger the bug]
- **Expected vs Actual**: Expected — [intended behavior]; Actual — [faulty behavior]
- **Root cause** *(if known / to be investigated)*: [...]

### Edge Cases

- What happens when [boundary condition]?
- How does the system handle [error scenario]?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST [specific capability, e.g., "allow users to filter the report by name"]
- **FR-002**: Users MUST be able to [key interaction]
- **FR-003**: System MUST [behavior / data requirement]

*Mark unclear requirements:* **FR-00X**: System MUST [...] [NEEDS CLARIFICATION: ...]

### Preserved Behaviors *(MUST stay unchanged)*

<!-- Neighboring behaviors this change must NOT alter. Essential for Feature
     Enhancement and Bug Fixing; the scope-reviewer checks the diff against this list. -->

- [Existing behavior that must remain exactly as-is]

### Key Entities *(include if the feature involves data)*

- **[Entity]**: [what it represents, key attributes, relationships — no implementation detail]

## Scope *(mandatory)*

- **In scope**: [files / modules / behaviors that MAY be touched]
- **Out of scope**: [explicitly off-limits — MUST NOT be modified]

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: [Measurable, technology-agnostic outcome]
- **SC-002**: [Zero-regression statement — behavior not covered by this change stays identical]

## Constraints / Imposed Decisions *(if any)*

<!-- Non-negotiable choices imposed from OUTSIDE the agent: stakeholder/business mandates,
     fixed technology or integration points, compliance/security rules. These are explicit
     DECISIONS, not silent defaults (Assumptions is for defaults). Keep the behavioral
     requirements above free of HOW; record the imposed HOW here so plan.md's Technical
     Context picks it up. Flag any that conflict with the existing architecture
     (Constitution I — no parallel/duplicate mechanisms) so analyze and the scope-reviewer
     can catch it. -->

- [e.g., "Periodic email delivery is mandated to use Akka (stakeholder decision). NOTE: conflicts with the existing Hangfire background-job mechanism — raise before planning."]

## Assumptions

- [Reasonable defaults chosen where the input was silent]

<!-- ===================== /FAMILY: BEHAVIOR-PRODUCING ===================== -->


<!-- ===================== FAMILY: BEHAVIOR-PRESERVING =====================
     KEEP for: Refactoring, Performance Optimization.
     Center of gravity: the INVARIANT (what must NOT change). New functional behavior
     is forbidden. The "Functional Requirements" notion is deliberately absent here.
     Delete this whole section if the Type is not in this family. -->

## Invariant — Observable External Behavior *(mandatory, MUST NOT change)*

<!-- The behavioral contract that must remain byte-for-byte identical. This is the
     HEART of a behavior-preserving spec; the scope-reviewer flags any diff that
     changes it. Be concrete. -->

- **Inputs / outputs**: [what callers send and receive — unchanged]
- **Public API / contract**: [signatures, routes, response shapes — unchanged]
- **Side effects**: [writes, events, emails, logs that must stay the same]
- **Data shapes**: [persisted / returned structures — unchanged]

## Structural Goal

- [What improves internally and why — e.g., library X -> Y, layer separation, hot-path rework]

### Old -> New Mapping *(only when one structure/technology is being replaced by another — delete otherwise)*

| Old structure / technology | New equivalent |
|---|---|
| [...] | [...] |

### Baseline & Target *(Performance Optimization only — delete if not perf)*

- **Metric**: [e.g., p95 latency, query count]
- **Baseline (today)**: [measured value]
- **Target**: [numeric goal for the same metric]

## Forbidden *(MUST NOT)*

- New functional requirements; opportunistic improvements; any observable behavior change.

## Scope *(mandatory)*

- **In scope**: [files / modules that MAY be touched]
- **Out of scope**: [explicitly off-limits — MUST NOT be modified]

## Success Criteria *(mandatory)*

- **SC-001**: External behavior is identical before and after (zero observable change).
- **SC-002**: [Refactor] the intended internal structure/technology change is achieved. / [Perf] the target metric is met.

## Constraints / Imposed Decisions *(if any)*

<!-- External mandates BEYOND the structural goal above (e.g., fixed runtime, no new
     dependencies, compliance rules). The main tech direction belongs in Structural Goal /
     Old -> New Mapping; record ADDITIONAL imposed constraints here. Explicit decisions,
     not silent defaults (that is Assumptions). -->

- [e.g., "Must remain on .NET 8; no new third-party dependencies introduced."]

## Assumptions

- [Reasonable defaults chosen where the input was silent]

<!-- ===================== /FAMILY: BEHAVIOR-PRESERVING ===================== -->


<!-- ===================== FAMILY: NON-CODE (Code Understanding) =====================
     KEEP for: Code Understanding. No code is produced; implement is NOT run.
     This is a lightweight stub by design. Delete this whole section if the Type
     is not Code Understanding. -->

## Questions to Answer *(mandatory)*

- **Q1**: [the precise question this investigation must answer]
- **Q2**: [...]

## Investigation Scope

- [Modules / files / flows to examine]

## Output Format

- [Deliverable: analysis / flow summary / risk list / diagram]. NOTE: implement is NOT run; the output is understanding, not code.

## Done

- [ ] Each question answered to sufficient depth, with references to the relevant code locations.

<!-- ===================== /FAMILY: NON-CODE ===================== -->
