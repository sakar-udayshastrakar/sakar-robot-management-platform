# C40 Voice / Audio Module Analysis

**Source:** `../workers/system_apps.md` §2.3–2.6/§2.11, `../workers/installation_assistant_sdk.md` §8/B.3, `../workers/peanut_clean_app.md` §2/§10, `../workers/peanut_clean_app_findings.json`, and the extracted `peanut-clean/assets/voice|bg|voiceConfig`. Companion data: `VOICE_ASSET_INVENTORY.csv`.

Confidence: **C-S** CONFIRMED_STATIC · **S-S** STRONGLY_SUPPORTED · **INF** INFERRED · **UNK** UNKNOWN. Nothing is runtime-verified.

## There are TWO distinct voice mechanisms — do not conflate them

### A. System speech engine — `ServiceSystem` (`com.keenon.systemservice`)
The UI apps do **not** run speech engines; they call `ServiceSystem` over AIDL.

- **Exported AIDL services (C-S, no permission required — security finding SYS-004):**
  - `tts.TtsApiService` → `ITtsAidlApi`: `startTts(String tts, String provider, boolean hold, OnTtsAidlListener)`, `execCmdAsync`.
  - `voice.VoiceApiService` → `IVoiceAidlApi` (impl `VoiceAidlApiImpl`): `execCmd`, `setDynamicHotwords(List)`, `startTts`, `addOnVoiceListener`, v3 `startTtsOnline`; callbacks `onVoiceEvent`, `onNlpSkill`.
- **Client SDK both apps embed (C-S):** `serviceSdk.tts.TtsClient`/`ITtsApis`, `serviceSdk.voice.VoiceClient`/`IVoiceApis`.
  - `ITtsApis`: `startTts(String)`, `startTts(String,String,boolean,OnTtsListener)`, `stopTts()`, `isTtsPlaying()`, `setTtsTimbre(int)`, `setTtsAudioStreamType(int)`.
  - `IVoiceApis`: `activeVoice()`, `sleep()`, `startNlp(String)`, `setVoiceInteractionEnable(bool)`, `setDynamicHotWords(List)`, `setRobotState(int)`, listeners `onAsrResult/onAwake/onNlpAnswer/onNlpSkill`.
- **Engines / providers (C-S, from `assets/voiceConfig/voice_config.json` + `voice_config_oversea.json`):**
  - **Domestic:** audio `rokid` (mic array), **wake word "你好花生"**, ASR `iflytek_robot`, NLP `llm_cn` (local), **TTS `iflytek_offline`** (local MSC `libmsc.so`; voices `assets/tts/*.jet`: xiaoyan/xiaofeng/xiaofang).
  - **Overseas:** ASR/TTS `micro` = **Microsoft Azure Speech SDK** + Azure OpenAI; NLP `llm_cn`.
  - LLM providers: Moonshot/Kimi, DeepSeek via DashScope, Azure OpenAI (8 regions). *(Hard-coded keys exist — findings SYS-008/009; values NOT reproduced here.)*
- **Voice command surface (C-S strings, `VoiceCmdV1/V2Executor`):** `cmd_set_voice_mode`, `cmd_set_awakeword`, `cmd_set_tts_timbre`, `cmd_set_audio_switch`, `cmd_stop_tts`, `cmd_wakeup`, `cmd_sleep`, `cmd_start_nlp`/`cmd_stop_nlp`, `cmd_set_dynamic_hot_words`, `cmd_gpt_*`. Local ASR matching (`assets/asr/asr_local_functions.json`) is **chat/entertainment only** (close_chat, talent_show/dance).
- **Robot-motion by voice: NONE FOUND.** No voice command maps to drive/clean/dock. Any robot effect would be executed by a listening app via `onNlpSkill` — **UNK/INF**.
- **Config:** timbre `setTtsTimbre(int)`; audio stream/mute `setTtsAudioStreamType(int)` / `cmd_set_audio_switch`; wake word `cmd_set_awakeword`; `actuators.json` → `VoiceActuatorFactory` provides **volume + language-switch** actuators.

### B. Bundled audio prompts — `peanut-clean`
- **`SoundPlayerLifecycle`** (C-S): an in-app sound player, **separate** from ServiceSystem TTS.
- **79 `.mp3` announcements** under `assets/voice/` — `en` (38) + `zh` (41), named `<event>_<variant>.mp3` (see `VOICE_ASSET_INVENTORY.csv`). **4 background-music tracks** under `assets/bg/` (deep, pianoboy, spring, summer).
- Cruise feature has a **selectable voice list**: route `cruise_voice_list` (`CruiseVoiceListActivity`).
- Announcements are fired from task logic (S-S, `_findings.json`): recharge flow "speaks the recharge voice"; self-clean "speaks SELF_CLEAN voice". Which specific prompt uses bundled mp3 vs ServiceSystem TTS is **INF** (both mechanisms present).
- Audio-settings UI: **`Audio` fragment** in `SettingActivity` (C-S name; `MODIFY_AUDIO_SETTINGS` permission). Volume/mute controls INF.
- Mapping-tool SPA also ships tutorial audio (139 prompts + subtitles) played in-browser — tutorial narration, not robot TTS (C-S).

## Languages
- Bundled announcements: **English + Chinese** (C-S, from the `voice/en` and `voice/zh` folders).
- Speech engine: domestic (zh) vs oversea (Azure) configs (C-S).
- Mapping-tool SPA i18n: en/zh/zht/ja/ko/th (C-S). Full peanut-clean language-pack list is **UNK** (cloud resource packs, not enumerated in evidence).

## The number→text mapping is UNKNOWN
The mp3 event codes (1, 2, 3 … 40) are **not** mapped to their spoken text in the static evidence. We do **not** fabricate the text; `VOICE_ASSET_INVENTORY.csv` marks `announcement_text = UNKNOWN`.

## Sakar-compatible voice design (recommendation)
Because (a) the real engine is on-robot (iflytek/Azure over AIDL, not reachable from a cloud web app), (b) there is no Sakar backend voice endpoint, and (c) no voice→motion capability exists, the Sakar web app should implement voice as a **clearly-labeled demo/mock** behind a `VoiceService` abstraction that a real backend can later implement:
- **Announcement settings:** language (en/zh to mirror evidence), volume, mute, timbre (int, mirroring `setTtsTimbre`).
- **Test announcement:** play a browser `SpeechSynthesis` (Web Speech API) or a Sakar-supplied clip — never a vendor mp3 in production.
- **Event→announcement catalog:** editable list keyed by the evidence event codes, with Sakar-authored text (since original text is UNK).
- **No voice-command (ASR) UI** unless/until a verified capability exists — the evidence shows chat-only ASR and **no** robot-motion voice control.
- Mark everything `dataMode: 'simulated'` per the project's data-honesty convention.
