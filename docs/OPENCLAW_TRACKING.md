# OpenClaw Upstream Tracking

## Architecture: What PocketClaw tracks vs. ignores

PocketClaw is a native Android app (Kotlin).
OpenClaw is a Node.js/PC tool.
No shared code. We track concepts and security knowledge only.

### Why Termux is irrelevant to PocketClaw
PocketClaw does NOT use Termux. It is a native Android app.
Termux-based OpenClaw cannot use AccessibilityService (Android limitation).
PocketClaw's native approach supports full UI automation that
Termux-based OpenClaw cannot achieve.

## Integration Points (where upstream changes may affect PocketClaw)

| OpenClaw/NemoClaw Change | PocketClaw File to Update | Effort |
|---|---|---|
| New prompt injection pattern | SuspicionScorer.kt keyword lists | Low |
| New LLM provider popular | New LlmProvider implementation | Low |
| New HITL channel (new SNS) | New RemoteApprovalProvider impl | Low |
| New security attack vector | ActionValidatorImpl.kt deny lists | Low |
| NemoClaw policy format update | New SecurityPolicy implementation | Medium |
| NemoClaw privacy router update | New PrivacyRouter implementation | Medium |

## Rollback Strategy
- Single file change: git checkout HEAD~1 -- <file>
- Entire PR rollback: GitHub "Revert" button on the PR
- Provider swap: change 1 line in AgentModule.kt Hilt binding
- Policy swap: change 1 line in AppModule.kt Hilt binding

## Monthly Review Checklist
[ ] Check OpenClaw releases: github.com/openclaw/openclaw/releases
[ ] Check NemoClaw releases: github.com/NVIDIA/NemoClaw/releases
[ ] Keywords to watch: "security", "injection", "vulnerability", "schema"
[ ] Update this table if action needed:

| Date | Upstream Change | PocketClaw Action | Status |
|---|---|---|---|

## Security Advisories Incorporated
| Advisory | Upstream Fix | PocketClaw Countermeasure | Date |
|---|---|---|---|
| Prompt injection via notifications | SuspicionScorer | SuspicionScorer.kt | 2026-03-24 |
| Unconstrained file access | Landlock sandbox | WorkspaceBoundaryEnforcer.kt | 2026-03-24 |
| auth:none IPC exposure | Auth required | signature permission + exported=false | 2026-03-24 |
