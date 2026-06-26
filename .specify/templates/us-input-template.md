# User Story: [TITLE]

**US ID**: [DevOps work item no.]
**Type**: [Feature Implementation | Feature Enhancement | Bug Fixing | Refactoring | Performance Optimization | Code Understanding]
**Status**: [Draft | Ready]
**Summary (one sentence)**: [What this US enables / changes — in one breath]

<!--
============================================================================
HOW TO FILL — READ FIRST (you may delete this block from the final US)

• This US is the pipeline's ONLY input. The reader is not a human but an agent;
  it cannot ask you anything in the hallway. EXTERNALIZE everything in your head.

• Write WHAT and WHY, not HOW. The only thing forbidden is *implementation HOW*:
  how the internals are built — algorithms, internal class/method design, control
  flow (the agent's + plan's job). EXCEPTION — you MAY name these three:
    (a) scope BOUNDARY modules (§2 In/Out of scope),
    (b) the structural/migration TARGET of a refactor/performance task (§3, e.g., X→Y),
    (c) an externally IMPOSED technology/decision (§6).
  These are not "how it's coded"; they are boundary, target, and external decision
  — same rule as the spec.

• Type MUST be ONE of the 6 supported values. Test Generation is OUT OF SCOPE for
  this MVP; do not write an unrecognized/unsupported type — if you do, do not submit.

• In §3, fill ONLY the sub-block matching your Type; delete the others / leave `N/A`.

• *(mandatory)* fields are non-negotiable. In *(if any)* fields, if there is no
  information do not leave it blank — write "None" explicitly. "Considered, none"
  differs from "skipped".
============================================================================
-->

---

## 1. Problem / Motivation (WHY) *(mandatory)*

<!-- Who experiences which problem/pain; what changes once it is solved. The agent
     breaks every ambiguity tie BASED ON THIS. A US with a weak "why" forces the
     agent to guess blindly at every gap. -->

- [State the problem and its value in plain language]

## 2. Scope *(mandatory)*

- **In scope**: [modules / behaviors that MAY be touched]
- **Out of scope**: [strictly off-limits — MUST NOT be touched]

<!-- "Out of scope" matters more than "In scope": it is the only thing stopping the
     agent's gold-plating and scope creep, and the scope-reviewer checks the diff
     against this list. Writing down what you DO NOT want is a separate discipline;
     do not skip it. -->

## 3. Type-Specific Core *(mandatory — fill ONLY the block matching your Type)*

**▸ Type ∈ {Feature Implementation, Feature Enhancement, Bug Fixing}**
- **Expected behavior**: [what will happen — for Enhancement, write only the CHANGED part (before→after)]
- **Beyond the happy path**: [known edge / error / boundary cases]
- **To preserve**: [neighboring behaviors this change MUST NOT break — especially Enhancement/Bug]
- *(Bug Fixing only)* **Repro**: [steps/conditions that trigger the bug] · **Expected vs Actual**: [what should happen] / [what happens]

**▸ Type ∈ {Refactoring, Performance Optimization}**
- **Must not change (invariant)**: [external behavior contract: inputs/outputs, public API, side effects — stays identical]
- **Structural goal**: [what improves internally — e.g., X → Y]
- *(Performance only)* **Baseline + Target**: [metric + today's measured value] → [target numeric value]

**▸ Type = Code Understanding**
- **Question(s) to answer**: [precise, one by one]
- **Expected output form**: [analysis / flow summary / risk list — NO code is produced]

## 4. Acceptance Criteria / Done *(mandatory)*

<!-- Measurable, technology-agnostic, and in v1 VISUALLY/MANUALLY verifiable.
     Not "it works"; rather "with input X, Y appears; on invalid input, error Z shows".
     For behavior-preserving types, also state "external behavior stays identical at
     these points". -->

- [ ] [Criterion 1 — testable]
- [ ] [Criterion 2 — testable]

---

## 5. Assumptions & Preconditions *(if any; else "None")*

<!-- What is taken as "already present and correct". If left unwritten, the agent
     makes silent assumptions. -->

- [None / ...]

## 6. Constraints / Externally Imposed Decisions *(if any; else "None")*

<!-- Non-negotiable technology/integration/compliance choices. These are DECISIONS
     (not assumptions). If an imposed choice conflicts with the existing architecture,
     FLAG THE CONFLICT — do not bury it. E.g., "Periodic email via Akka (stakeholder
     decision). WARNING: conflicts with the existing Hangfire background-job mechanism
     — must be discussed before planning." -->

- [None / ...]

## 7. Open Questions / Decisions Pending *(if any; else "None")*

<!-- Points you deliberately leave open, with no answer yet. Do not bury them; make
     them VISIBLE — /clarify targets exactly these. -->

- [None / ...]
