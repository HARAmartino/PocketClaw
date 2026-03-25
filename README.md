# PocketClaw

> **An open-source autonomous AI agent platform for native Android — zero-trust, fully offline UI automation.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-29%2B-brightgreen.svg)](https://android-arsenal.com/api?level=29)
[![Version](https://img.shields.io/badge/version-0.1.0--alpha-orange.svg)](app/build.gradle.kts)

---

## What is PocketClaw?

PocketClaw turns a low-spec Android device into a fully autonomous AI agent.  
It offloads LLM inference to the cloud (OpenAI-compatible, Anthropic) while using Android's **AccessibilityService API** for local UI automation — tapping, typing, scrolling, and navigating apps exactly as a human would.

Key characteristics:

- **Native Android app** — not a Termux script, not a server process.
- **Zero-trust security model** — 6 layered defences, hardware kill switch, human-in-the-loop (HITL) escalation.
- **Extensible skill system** — third-party `AgentSkill` APKs discovered at runtime via signed manifests.
- **Privacy-first** — `PrivacyRouter` redacts PII before it leaves the device; secrets stored in `EncryptedSharedPreferences`.

---

## Why NOT Termux?

| | Termux / PC-based agent | PocketClaw (native Android) |
|---|---|---|
| **AccessibilityService** | ❌ Not available | ✅ Full support |
| **UI automation** | Screenshot + OCR hacks | ✅ Direct node interaction |
| **Background execution** | Killed by Android | ✅ Foreground service + AlarmManager |
| **Install required** | Developer mode / ADB | ✅ Standard APK |
| **Battery / thermal** | N/A | ✅ Built-in thermal throttling |

Android's security model blocks any non-system process running in Termux from accessing `AccessibilityService`. PocketClaw is a proper Android app and is the only open-source autonomous agent with **full AccessibilityService support**.

---

## Requirements

| Requirement | Details |
|---|---|
| **Android version** | Android 10 (API 29) or higher |
| **RAM** | 4 GB minimum (6 GB recommended) |
| **Storage** | 200 MB free |
| **Network** | Internet access (LLM API calls) |
| **Permissions** | See [Quick Start](#quick-start) |
| **Dedicated device** | Strongly recommended — the agent will autonomously interact with all installed apps |

> ⚠️ **Security notice**: PocketClaw requires extensive permissions (Accessibility, Notification Listener, etc.). Run it on a dedicated device that does not hold sensitive personal data.

---

## Quick Start

### Build from Source

```bash
# Clone the repository
git clone https://github.com/HARAmartino/PocketClaw.git
cd PocketClaw

# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Required Permissions

Grant the following permissions via the in-app onboarding screen or system settings:

| Permission | Purpose |
|---|---|
| **Accessibility Service** | UI automation (tap, type, scroll) |
| **Notification Listener** | Trigger agent on incoming notifications |
| **Overlay (Draw on top)** | Kill-switch overlay button |
| **Battery Optimization** | Exclude PocketClaw for reliable background execution |

### First-time Setup

1. Open PocketClaw → tap **Settings**.
2. Enter your LLM API key (OpenAI-compatible or Anthropic).
3. (Optional) Configure HITL provider (Telegram bot or Discord webhook).
4. Enable the **Accessibility Service** when prompted.
5. Return to the **Dashboard** and tap **Start Agent**.

---

## Architecture Overview

See [`docs/PRD.md`](docs/PRD.md) for the full architecture diagram and interface definitions.

High-level layers:

```
TRIGGER LAYER      →  NotificationListener / AlarmManager / TaskQueueContentProvider
ORCHESTRATION      →  AgentOrchestrator (ActionValidator → CapabilityEnforcer chain)
LLM LAYER          →  LlmProvider (OpenAI-compat / Anthropic) + PrivacyRouter
ACCESSIBILITY      →  AgentAccessibilityService (AccessibilityTree → LlmAction)
SKILL LAYER        →  AgentSkill APKs discovered via SkillLoader (signed manifests)
PERSISTENCE        →  Room DB + DataStore + EncryptedSharedPreferences
```

---

## Security Model

PocketClaw implements **6 layers of defence**:

| Layer | Component | Description |
|---|---|---|
| 1 | `SuspicionScorer` | Prompt-injection keyword detection |
| 2 | `TrustedInputBoundary` | Sanitises all LLM-supplied strings |
| 3 | `ActionValidator` | Deny-list for destructive accessibility actions |
| 4 | `CapabilityEnforcer` | Permission-scoped capability checks |
| 5 | `WorkspaceBoundaryEnforcer` | Landlock-style file path sandbox |
| 6 | `SecurityPolicy` | Pluggable policy engine (NemoClaw-compatible) |

Additional safeguards:

- **Kill switch**: A persistent overlay button that immediately stops all agent activity.
- **Zero-trust networking**: `NetworkGateway` validates all outbound endpoints against an allowlist.
- **HITL escalation**: The LLM self-escalates ambiguous or unsafe actions to a human operator via Telegram or Discord.
- **Daily token budget**: Hard limit on LLM API spend, configurable in Settings.

---

## OEM Battery Setup

Android OEMs apply aggressive battery optimisation that can kill background services. Configure the following for reliable agent execution:

### Xiaomi (MIUI / HyperOS)
1. **Settings → Apps → Manage apps → PocketClaw → Battery saver** → set to **No restrictions**.
2. **Security app → Battery → Power-intensive prompts** → disable for PocketClaw.
3. **Settings → Additional settings → Developer options → Background process limit** → Standard limit.

### Huawei (EMUI / HarmonyOS)
1. **Settings → Apps → PocketClaw → Battery** → enable **Run in background**.
2. **Settings → Battery → App launch** → set PocketClaw to **Manage manually**, enable all three toggles.

### OPPO / OnePlus (ColorOS / OxygenOS)
1. **Settings → Battery → Battery optimisation → PocketClaw** → **Don't optimise**.
2. **Settings → Apps → PocketClaw → Battery usage** → **Allow background activity**.

### Samsung (One UI)
1. **Settings → Apps → PocketClaw → Battery** → **Unrestricted**.
2. **Settings → Battery → Background usage limits** → remove PocketClaw from sleeping apps.

### Vivo (OriginOS / FuntouchOS)
1. **i Manager → App Manager → PocketClaw** → enable **Allow background run**.
2. **Settings → Battery → High background power consumption** → allow PocketClaw.

---

## Contributing

Contributions are welcome! Please read the guidelines below before opening a pull request.

### Extension Points

PocketClaw is designed for extensibility. The four primary extension interfaces are:

| Interface | File | Purpose |
|---|---|---|
| `AgentSkill` | `agent/skill/AgentSkill.kt` | Add new automation capabilities as separate APKs |
| `RemoteApprovalProvider` | `agent/hitl/RemoteApprovalProvider.kt` | Add new HITL channels (e.g., SMS, email) |
| `SecurityPolicy` | `agent/security/SecurityPolicy.kt` | Swap or extend the action-level policy engine |
| `PrivacyRouter` | `agent/llm/PrivacyRouter.kt` | Customise PII redaction before LLM calls |

### Adding an AgentSkill

1. Create a new Android library/app module that implements `AgentSkill`.
2. Declare a `<meta-data android:name="com.pocketclaw.skill.SKILL_MANIFEST">` entry in its `AndroidManifest.xml`.
3. Sign the APK with a certificate whose SHA-256 fingerprint is registered in `SkillTrustStore`.
4. Install on the same device — `SkillLoader` discovers it automatically at startup.

### Pull Request Checklist

- [ ] Unit tests added for new logic (see `app/src/test/`).
- [ ] ProGuard rules updated if new serializable classes are added (`app/proguard-rules.pro`).
- [ ] No new permissions added without a PRD update (`docs/PRD.md`).
- [ ] `CHANGELOG` entry added (if applicable).

---

## OpenClaw / NemoClaw Upstream Tracking

PocketClaw monitors upstream projects for security patterns and new LLM provider support.

See [`docs/OPENCLAW_TRACKING.md`](docs/OPENCLAW_TRACKING.md) for:
- Which upstream changes affect which PocketClaw files.
- The monthly review checklist.
- Security advisories incorporated from upstream.

---

## License

```
Copyright 2026 PocketClaw Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

See the full license text in [`LICENSE`](LICENSE).
