---
name: privacy-check
description: Data-Safety + disclosure gate for AI4Kids Android changes that ship data off-device, change a permission, add an SDK, or alter network config — the cases that have NO web counterpart so the web-parity skill won't catch them. Use when editing ai/ (Gemini/Cloudflare), AndroidManifest permissions, res/xml/network_security_config.xml, or adding a dependency in app/build.gradle.kts. It only runs the privacy/compliance checklist; for porting web features (including "don't drag web tracking into the offline core"), use the web-parity skill instead.
---

# Privacy Check — off-device / permission / SDK changes

Narrow gate for the posture-relevant changes that aren't parity work (see [CLAUDE.md](../../../CLAUDE.md) → Privacy posture). The recurring, easy-to-forget step is: when something new collects or transmits, **keep the Play Data Safety form and the in-app/permission disclosures in sync.** This skill exists to force that step.

> Scope: this is the *compliance checklist* only. Parity-driven privacy (not replicating the web's tracking/leaderboard/registration in the offline core) belongs to [web-parity](../web-parity/SKILL.md). Don't duplicate it here.

## When to run

Trigger on a change that touches any of:

- **`ai/`** — [GeminiClient.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ai/GeminiClient.kt), [CloudflareClient.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ai/CloudflareClient.kt), [ArtEngine.kt](../../../app/src/main/java/sg/com/tertiarycourses/ai4kids/ai/ArtEngine.kt) — anything that changes *what* is sent off-device (new model, richer prompt, new payload, new fallback host).
- **Permissions** in [AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml) — adding/removing a `uses-permission`. Current set: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO` (sensitive).
- **[res/xml/network_security_config.xml](../../../app/src/main/res/xml/network_security_config.xml)** — cleartext or trusted-host changes (prod is HTTPS-only; cleartext is dev-hosts only).
- **A new dependency / SDK** in [app/build.gradle.kts](../../../app/build.gradle.kts) — especially anything that could phone home, track, or collect.

## Checklist

1. **What data now leaves the device, and to whom?** Name the field(s) and the destination host (Google Gemini, Cloudflare Workers AI, the ai4kids backend, or a new one). If nothing new leaves the device, note that and stop — no Data Safety change needed.
2. **Does it fit the posture?** No ads, no third-party analytics/tracking SDKs, offline core collects nothing. A new tracking/analytics SDK is a **hard stop** — reject it. Keyed AI features must stay **dormant with a blank key** (`GeminiClient.isConfigured()` pattern) and fully offline.
3. **New permission?** Justify it against a concrete feature, add the user-facing rationale, and confirm it's the narrowest permission that works. `RECORD_AUDIO` is disclosed for on-device `SpeechRecognizer` (note: may send audio to Google; we pass `EXTRA_PREFER_OFFLINE`).
4. **Secrets stay off the client** — keys live in git-ignored `local.properties` → `BuildConfig`, never in source or shown in UI.
5. **Network config** — production paths stay HTTPS-only; cleartext only for local dev hosts.
6. **➜ Update the Play Data Safety form** to match the new collection/transmission, and update any in-app disclosure. State explicitly in your summary whether the form needs a change and what.

## Output

A short report: *what now leaves the device* → *destination* → *posture verdict (fits / hard-stop)* → *Data Safety form: no change | update needed (what)* → *permission/disclosure actions*. Flag hard stops (tracking SDK, secret on client, silent new collection) loudly.