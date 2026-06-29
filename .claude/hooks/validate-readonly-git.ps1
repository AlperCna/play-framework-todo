# scope-reviewer Bash gate (PreToolUse): allow ONLY read-only git inspection; block everything else.
# Reads the hook JSON from stdin, extracts .tool_input.command, and exits 2 to block (Claude Code hook contract).
$raw = [Console]::In.ReadToEnd()
try {
    $cmd = ($raw | ConvertFrom-Json).tool_input.command
} catch {
    [Console]::Error.WriteLine("scope-reviewer gate: unparseable hook input")
    exit 2
}

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }
$c = $cmd.Trim()

# Block shell chaining / redirection that could smuggle a second command past the allowlist.
if ($c -match '[;&|`<>]' -or $c -match '\$\(') {
    [Console]::Error.WriteLine("scope-reviewer is read-only: shell chaining/redirection not allowed -> $c")
    exit 2
}

# Allowlist: read-only git inspection subcommands only.
if ($c -match '^git\s+(diff|status|log|show|merge-base)(\s|$)') {
    exit 0
}

[Console]::Error.WriteLine("scope-reviewer is read-only: only 'git diff|status|log|show|merge-base' allowed -> $c")
exit 2
