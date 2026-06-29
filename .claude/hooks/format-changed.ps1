# PostToolUse formatter: after an Edit/Write to a .scala/.sbt file, run scalafmt on just that file.
# Never blocks (always exits 0); a formatting failure is surfaced as stderr only, never reverts the edit.
#
# NOTE: scalafmt is not configured in this repo yet (no .scalafmt.conf, no scalafmt CLI on PATH), so
# this hook is currently a safe no-op. It "switches on" automatically once a .scalafmt.conf is added
# and the standalone `scalafmt` CLI is installed (e.g. via coursier).
$raw = [Console]::In.ReadToEnd()
try { $fp = ($raw | ConvertFrom-Json).tool_input.file_path } catch { exit 0 }
if ([string]::IsNullOrWhiteSpace($fp)) { exit 0 }
if ($fp -notmatch '\.(scala|sbt)$') { exit 0 }

$root = $env:CLAUDE_PROJECT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { exit 0 }

# scalafmt marker: without a config there is nothing to enforce.
$cfg = Join-Path $root '.scalafmt.conf'
if (-not (Test-Path $cfg)) { exit 0 }

# Need the standalone scalafmt CLI; `sbt scalafmt` is far too slow to run per edit.
$scalafmt = Get-Command scalafmt -ErrorAction SilentlyContinue
if (-not $scalafmt) { exit 0 }

try {
    & $scalafmt.Source --config "$cfg" "$fp" *> $null
} catch {
    [Console]::Error.WriteLine("format-changed: scalafmt failed for $fp")
}
exit 0
