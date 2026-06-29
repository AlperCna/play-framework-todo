---
description: On-demand scope review — bind the diff to the right feature, run the scope-review chain, and report. Opt-in; never blocks a commit.
---

# /review-gate — on-demand scope review

Run this whenever you want a scope & constitution check of the current changes (typically after
`/speckit-implement`, before committing). It is **opt-in** and **advisory** — nothing is enforced,
nothing blocks a commit. You decide what to do with the report.

## Preflight (scope binding — do this FIRST, before invoking any reviewer)

Branch convention: each feature lives on its own branch `feature/<num>-<slug>` cut from `main`, and
only that feature's code is written on it. Bind the review to that feature:

1. `branch = git rev-parse --abbrev-ref HEAD`. Expected feature segment = branch with the
   `feature/` prefix stripped (e.g. `feature/643-abc-def` → `643-abc-def`).
2. `feature_dir` = `feature_directory` from `.specify/feature.json`. Its basename SHOULD equal the
   expected segment from step 1.
3. **Mismatch check:** if the branch is not `feature/*`, or `feature_dir`'s basename ≠ the branch
   segment, **STOP and warn** the user:
   `⚠ Branch <branch> ile feature.json (<feature_dir>) uyuşmuyor — yanlış US'i review edebilirsin.`
   Ask whether to proceed anyway or fix the binding first. Do NOT silently review.
4. `base = git merge-base main HEAD` — the branch's divergence point. The review diff is
   `git diff <base>` (covers all commits on the branch since it left main, **plus** any uncommitted
   working-tree changes; immune to `main` advancing afterward).
5. Announce the plan in one line before proceeding, e.g.:
   `Review: <feature_dir> spec  vs  git diff <base>  (branch <branch>)`.

## Review chain (ordered — this is the ONLY place to extend it)

1. `scope-reviewer` — scope & constitution conformance of the diff.

<!-- To add another reviewer: append it as step 2 with the same APPROVE / NEEDS FIX contract. -->

## Steps

1. For each reviewer in the chain above, **in order**, invoke it with the Agent tool
   (`subagent_type` = the reviewer's name). Pass the bound context from Preflight explicitly:
   the feature directory (`<feature_dir>`) and the exact diff range (`git diff <base>`), and tell it
   to use that range rather than re-deriving its own. (Independent reviewers may be launched in
   parallel; keep report order stable.)
2. Collect each reviewer's verdict (`APPROVE` / `NEEDS FIX`) and findings.
3. Present a combined report: one section per reviewer (findings table + its verdict), then a single
   overall verdict line.

That's the whole job — the report is informational. Fixing issues or committing is your call.
