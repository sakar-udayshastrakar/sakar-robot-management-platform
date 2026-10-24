# C40 Reverse-Engineering — Research Archive

A self-contained, read-only **archive copy** of the static reverse-engineering investigation of the
Keenon C40 (`S-LS-M014C00-RW-F00-V246`) cleaning-robot software release. Everything here was **copied**
(never moved) from the live investigation workspace and verified by SHA-256.

- **Archived:** 2026-10-25
- **Files:** 169 (+ this README + `MANIFEST.csv`)
- **Copy verification:** 0 conflicts, 0 hash mismatches, 29/29 JSON parse OK, all CSV registers intact.

## Source workspace (do NOT move or delete)

Primary source — the authoritative deliverable set:

```
D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS
```

Analysis/validation scripts and a few run logs were copied from the investigation's **ephemeral
session scratchpad** (temp directory, subject to cleanup) so the methodology is preserved here.

> The original workspace is intentionally left intact. REA scripts, workers, and investigation state
> reference its current path; this archive is a copy only. Source files were read, never modified
> (verified: 61 source files, 0 modified during archival).

## Provenance

The investigation ran as parallel Claude Code sessions. The consolidated deliverables
(`MASTER_REVERSE_ENGINEERING_REPORT.md`, `REVERSE_ENGINEERING_SUMMARY.md`, per-domain analyses,
registers) were produced by the primary finalization session; a second session independently verified
the registers and authored `REVERSE_ENGINEERING_MASTER_REPORT.md` and `UNRESOLVED_GAPS.md`.

**Two near-duplicate master reports are preserved on purpose** (authoritative status not yet decided):
- `reports/MASTER_REVERSE_ENGINEERING_REPORT.md` — primary session (fuller; includes exec summary, inventory integrity vs `upgradeConfig.json`, methodology).
- `reports/REVERSE_ENGINEERING_MASTER_REPORT.md` — second session (index + consistency-gate results).

## Structure

| Folder | Count | Contents |
|---|---|---|
| `reports/` | 15 | Narrative master & per-domain analysis documents (both master reports, executive summary, security audit, ROS/robotics/network/Android/SDK/native/database analyses, unresolved gaps) |
| `registers/` | 5 | Tabular registers (CSV): findings, network endpoints, ROS static references, artifact inventory, embedded-artifact inventory |
| `findings/` | 2 | Structured JSON: `ROS_IP_TOPICS_FINDINGS.json` (RIP-001…015), `inventory_base.json` |
| `state/` | 1 | `INVESTIGATION_STATE.md` (phase tracking, consistency gate) |
| `workers/` | 23 | Raw per-domain worker outputs (`*.md` + `*_findings/_endpoints/_ros_refs/_artifacts.json`) and `orchestrator_validation.json` (quality check) for all 5 workers |
| `evidence/` | 15 | REA supporting evidence: APK-signature analysis, `rea_inspect_*`, decompilation-validation `rea_val_*`, worker `.err` logs, `uncovered_artifacts.txt` |
| `scripts/` | 104 | Analysis & validation scripts (Python/shell), incl. `pcl/`, `ria/`, `sys/` worker subfolders, `assemble.py`, `merge_ep.py`, `qa_gate.py`, `qa_check_fp.py` |
| `logs/` | 4 | Build/job logs (`npm_ci.log`, `jl1/2/3.txt`) |
| `MANIFEST.csv` | — | Every archived file: category, filename, source path, dest path, size, SHA-256, copy status |

### What is intentionally NOT archived here
The session scratchpad also held large, **regenerable** derivatives that do not belong in a source
repository: extracted/decompiled third-party APK and `.deb` trees, firmware extractions, and multi-
hundred-MB JADX SQLite caches (`code.db`, `code_prev.db`). These are reproducible from the original
vendor binaries (kept in the source workspace alongside `REVERSE_ENGINEERING_RESULTS`) and are
excluded to keep the archive self-contained, licensable, and repo-appropriate.

## Evidence classifications

| Label | Meaning |
|---|---|
| `CONFIRMED_STATIC` | Directly present in shipped artifacts (config literal, launch XML, decompiled constant, string table). |
| `CONFIRMED_LIVE` | **None** — no runtime/live access was used. |
| `STRONGLY_SUPPORTED` | Multiple independent static indicators agree; one link inferential. |
| `INFERRED` | Reasoned from convention/adjacent evidence (e.g. an upstream default port). |
| `HISTORICAL` | Legacy/abandoned material (e.g. `to_abandon/` scripts). |
| `UNVERIFIED_RUNTIME` | Statically determined; needs read-only live inspection to confirm. |
| `UNKNOWN` / `NOT_ANALYZED` / `BLOCKED` | Not determined; see `reports/UNRESOLVED_GAPS.md`. |

## Headline facts (all CONFIRMED_STATIC)

- ROS computer `192.168.64.20`; ROS Master `http://192.168.64.20:11311`; Android tablet `192.168.64.10` (also the ROS PC's Internet gateway). Flat bridged LAN `192.168.64.0/24`.
- Tablet drives the robot directly via rosbridge WS 9090/9091 and the Peanut SDK (CoAP 5683 / HTTP 54322 / TFTP 9527 / serial) — none authenticated/TLS inside the robot network.
- 127 artifacts analyzed; **226 findings** (1 Critical, 24 High, 52 Medium, 27 Low, 122 Info); 394 network endpoints; 1,115 static ROS references. No duplicate finding IDs; every finding carries evidence.

## Known limitations

Static-only engagement: **no** live/dynamic testing, network probing, robot control, firmware
flashing, credential use, or secret extraction. No native disassembler / binwalk / tshark was
available (native `.so` and firmware analyzed by symbols/strings/headers only); BKS keystore and
`resources.arsc` were not parsed. See `reports/UNRESOLVED_GAPS.md` for the complete list and the
read-only next steps (only if authorized access is later granted).

## Redaction

Secrets are referenced by **type and location only** throughout (never values). The archive was
scanned for raw credential literals before copying; none were present. Do not add extracted secret
values to any file here.

## Verifying this archive

```bash
# Re-verify a file's SHA-256 against MANIFEST.csv
sha256sum research/c40-reverse-engineering/reports/MASTER_REVERSE_ENGINEERING_REPORT.md
```

Each row of `MANIFEST.csv` records the original source path and the SHA-256 that was confirmed equal
on the destination copy at archival time.

## Git note

This repo's `.gitignore` excludes `*.log` and any `logs/` directory, so `research/.../logs/` persists
on disk but is **not** tracked by git unless force-added. All other archive folders are trackable.
Nothing here is committed automatically.
