# SakarC40Agent — Security Audit (Phase 0)

**Status:** documents CURRENT findings only. **No security hardening was implemented in this phase** — per the task instructions, only genuine compilation problems would have been fixed, and none existed (the build succeeded unmodified). Every item below is a finding to carry into a future implementation phase, cross-referenced against `docs/security/SAKAR_SECURITY_REQUIREMENTS.md`.

**Scope:** `robot/SakarC40Agent` source only (10 Gradle modules, ~19 Java files). The external Peanut SDK (`peanut-sdk-v1.3.0`, `peanut-sdk-release.aar`) was not modified and was treated as a read-only vendor dependency, exactly as in the earlier `PEANUT_SDK_C40_TECHNICAL_STUDY.md`.

---

## Positive findings (existing controls already in place)

| Area | Finding |
|---|---|
| Hardcoded credentials | **None found.** Exhaustive grep for `appId=`, `appSecret=`, `password=`, `apikey=` literal assignments and for any AppId/Secret value found zero matches. All credentials are sourced from `secrets.properties` (git-ignored) via generated `BuildConfig` fields, with safe empty defaults when absent — `SdkConnectionConfig.fromBuildConfig()` is documented as the only supported way to obtain one. |
| Secrets in Git | `secrets.properties` and `local.properties` are both git-ignored by the project's own `.gitignore`; only `secrets.properties.example` (placeholder values only, `APP_ID=`, `APP_SECRET=` empty) exists in the repository. |
| Secrets in logs | `SdkCallLogger`/`MainActivity`'s raw on-screen log never includes `appId`/`appSecret` — the logged "request" string for `PeanutSDK.init` is explicitly built as `"linkType=" + ... + " host=" + ...`, omitting the credential fields entirely. Verified by direct source read of every call site that logs a request string. |
| Cleartext HTTP / TLS | **Not applicable yet** — no networking client of any kind exists in this codebase (confirmed by grep: no `OkHttp`, `Retrofit`, `HttpURLConnection`, raw `Socket`, or `http://` literal anywhere). Nothing to misconfigure today; becomes a real requirement (`docs/security/SAKAR_SECURITY_REQUIREMENTS.md` Section 8, TLS/no-cleartext-traffic) the moment cloud connectivity is added. |
| Exported Android components | Only one exported component exists in the entire app: `MainActivity`, which **must** be exported to serve as the `LAUNCHER` activity. No `Service`, `Receiver`, or `Provider` is declared anywhere, exported or not — minimal attack surface by omission, not by explicit hardening. |
| Insecure local hardware access | `SerialPortInspector` only calls `File.exists()` on three candidate serial device paths — never opens, reads, or writes them. `DeviceEnvironmentInspector` only reads `android.os.Build` fields and enumerates already-bound network interfaces via standard `java.net` APIs. Neither claims a resource that could conflict with the stock Keenon software. |
| Command handling / actuation gating | Every action that could move the robot or touch charging (`goToPoint`, `pause/resume/stopNavigation`, `start/stopCharging`) is blocked by `C40RobotController.guard()` unless `operatingMode == HARDWARE_TEST`, which nothing in this codebase ever sets. `MotorComponent.enable()` (motor lock/unlock) is **not called anywhere in this codebase at all** — there is currently no lock/unlock actuation code path to audit for bypass risk, because it does not exist yet. |
| `android:allowBackup` | Set to `false` in `app/src/main/AndroidManifest.xml` — prevents Android's automatic backup mechanism from exporting app data (including any future cached credentials) off-device. |

## Findings requiring attention in a future phase (not fixed now)

| # | Finding | Severity (per `SAKAR_SECURITY_RISK_REGISTER.md` scale) | Detail |
|---|---|---|---|
| F1 | Unused `READ_PHONE_STATE` permission | Low | Declared in `app/src/main/AndroidManifest.xml`; exhaustive grep for `TelephonyManager`, `getDeviceId`, `getImei` found **zero usages anywhere in this codebase**. This violates the minimal-permissions principle (`docs/security/SAKAR_SECURITY_REQUIREMENTS.md` Section 8). Recommendation for a future phase: either remove the permission, or if it is needed for a not-yet-written device-identity feature, add that code and document the justification at the same time it's declared. |
| F2 | No device/agent identity mechanism | Medium (becomes higher once cloud connectivity exists) | The master security requirements (Section 8 / Part 21) require each agent install to have its own registered device identity, distinct from the robot's identity. No such mechanism exists in this codebase — there is no unique agent ID generated, stored, or exposed anywhere. Expected gap at this phase; flagged so it is not forgotten. |
| F3 | No Android Keystore usage | Medium (becomes higher once real credentials are provisioned) | `secrets.properties` values flow into plain `BuildConfig` string constants, compiled into the APK. This is acceptable only because no *real* production credential exists in this codebase today (only empty placeholders). Once a real `APP_ID`/`APP_SECRET` is provisioned for an actual deployment, storing it as a compile-time `BuildConfig` string (readable by decompiling the APK) is insufficient — the master requirements' Android Keystore control (Section 8) is not implemented. |
| F4 | No release-build hardening | Low at this phase | `release` build type has `minifyEnabled false` (no code shrinking/obfuscation) and no explicit `debuggable false`/ProGuard rule set beyond Android defaults. Acceptable for a diagnostic proof-of-concept; must be revisited before any real-world deployment build. |
| F5 | No app-integrity / tamper-detection mechanism | Low at this phase | No signature-verification or repackaging-detection logic exists. Expected gap — not required for a codebase with no security-sensitive actuation wired in yet, but listed for Section 8's "app integrity" control. |
| F6 | No TLS/certificate-pinning configuration | N/A now, becomes required later | There is no network security config (`res/xml/network_security_config.xml`) anywhere in the project, because there is no network traffic to configure yet. Must be added together with the first real network client, not after. |
| F7 | No kiosk/device-owner enforcement | Expected at this phase | The master requirements (Part 11/21/38) explicitly identify OS-level device management as likely necessary to make any future remote-lock feature meaningful against the stock Keenon app. Nothing in this codebase implements or configures this yet — consistent with the fact that no lock/unlock actuation code exists yet either. |
| F8 | `AppId`/`AppSecret` scope is SDK-wide, not per-Sakar-user | Inherited vendor limitation, not a code defect | As already documented in `PEANUT_SDK_C40_TECHNICAL_STUDY.md`: the Peanut SDK itself has no concept of per-user authorization — this is a vendor SDK characteristic this codebase cannot fix, only work around at the Sakar Backend layer (not yet built). Restated here for completeness, not a new finding. |

## Robot identity / device identity / token handling / authentication / authorization — current state

| Concept | Current state |
|---|---|
| Robot identity | Not established by this codebase. `RuntimeSnapshot.getRobotIp()`/`getRobotArmInfo()`/`getRobotStm32Info()` exist as read fields but nothing in this project treats any of them as a stable identity anchor yet. |
| Device (agent) identity | Does not exist (F2 above). |
| Token handling | N/A — no backend exists to issue or validate a token against; the only "credential" concept is the SDK's own `AppId`/`AppSecret`, handled as described above. |
| Authentication | N/A — the agent has no user-facing login of its own; it is a headless-from-a-user's-perspective, single-tenant on-robot process. |
| Authorization | The only authorization concept present is `OperatingMode` (`DIAGNOSTIC_ONLY` vs `HARDWARE_TEST`) — a local, unauthenticated, hardcoded-default gate. It is **not** a substitute for the RBAC/command-authorization model specified in `docs/security/SAKAR_SECURITY_REQUIREMENTS.md` Sections 4–5, which requires a Sakar Backend this codebase does not yet talk to. |

## Explicit non-action taken

Per the task's instruction to document issues first and not implement hardening except where required for a genuine compilation problem: **the build succeeded without any source modification**, so none of F1–F8 above were acted upon. `local.properties` was created (git-ignored, machine-specific, contains no secret — only a local Android SDK path) solely to make the Phase 0 build attempt possible; this is not a security-relevant change.
