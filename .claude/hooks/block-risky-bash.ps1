# Session-wide risky-command gate (PreToolUse on Bash/PowerShell): escalate destructive commands to a user confirmation.
# Emits permissionDecision=ask JSON for matches; otherwise stays silent so normal flow proceeds.
$raw = [Console]::In.ReadToEnd()
try { $cmd = ($raw | ConvertFrom-Json).tool_input.command } catch { exit 0 }
if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

$rules = @(
    @{ p = 'git\s+push\s+.*(--force|-f)\b';   why = 'force push' },
    @{ p = 'git\s+reset\s+--hard';            why = 'hard reset (discards commits/changes)' },
    @{ p = 'git\s+clean\s+-[A-Za-z]*f';       why = 'git clean -f (deletes untracked files)' },
    @{ p = 'git\s+checkout\s+--\s';           why = 'checkout -- (discards file changes)' },
    @{ p = 'git\s+restore(\s|$)';             why = 'git restore (discards changes)' },
    @{ p = '\brm\s+-[A-Za-z]*r[A-Za-z]*f';    why = 'rm -rf (recursive force delete)' },
    @{ p = 'git\s+commit\s+.*--no-verify';    why = 'commit --no-verify (skips hooks)' },
    @{ p = 'migrate_drp_down';                why = 'DRP down migration (drops all DRP tables)' },
    @{ p = 'docker(\s+compose|-compose)\s+down\b[^\n]*(\s-v\b|--volumes)'; why = 'docker compose down -v (wipes the Postgres data volume)' },
    @{ p = '\bdrop\s+(database|table|schema)\b'; why = 'SQL DROP (database/table/schema)' },
    @{ p = '\btruncate\s+table\b';            why = 'SQL TRUNCATE' }
)
foreach ($r in $rules) {
    if ($cmd -match $r.p) {
        $reason = "Risky command ($($r.why)). Confirm you intend to run this."
        $out = @{ hookSpecificOutput = @{ hookEventName = 'PreToolUse'; permissionDecision = 'ask'; permissionDecisionReason = $reason } }
        [Console]::Out.WriteLine(($out | ConvertTo-Json -Depth 5 -Compress))
        exit 0
    }
}
exit 0
