// VoiceService — abstraction over "announce a message in the operator's language".
//
// DATA-HONESTY NOTE: this is a DEMO implementation. The real C40 speaks through
// an on-device engine (iflytek offline / Azure Speech) reached over Android
// AIDL from the ServiceSystem app — see
// research/c40-reverse-engineering/ui/VOICE_MODULE_ANALYSIS.md. There is NO
// Sakar Cloud Backend voice endpoint, and the web app never talks to a robot
// directly, so this service cannot make a robot speak. The default
// implementation plays audio in the operator's OWN browser via the Web Speech
// API, purely so the Voice UI is usable and testable. A real
// `BackendVoiceService` (calling a future `/robots/{id}/voice` endpoint) can be
// dropped in behind this same interface without touching the UI.

export type VoiceLanguage = 'en' | 'zh';

export interface VoiceSettings {
  language: VoiceLanguage;
  /** 0..1 */
  volume: number;
  muted: boolean;
  /** chosen SpeechSynthesis voiceURI, or null for the browser default */
  voiceURI: string | null;
  /** 0.5..2 — conceptually mirrors the vendor timbre/speed knob (setTtsTimbre) */
  rate: number;
}

export const DEFAULT_VOICE_SETTINGS: VoiceSettings = {
  language: 'en',
  volume: 1,
  muted: false,
  voiceURI: null,
  rate: 1,
};

export interface VoiceOption {
  uri: string;
  name: string;
  lang: string;
}

export interface VoiceService {
  /** 'demo' = browser-only playback; 'backend' = a real robot-facing impl. */
  readonly kind: 'demo' | 'backend';
  isSupported(): boolean;
  listVoices(): VoiceOption[];
  speak(text: string, settings: VoiceSettings): Promise<void>;
  stop(): void;
}

class BrowserVoiceService implements VoiceService {
  readonly kind = 'demo' as const;

  isSupported(): boolean {
    return (
      typeof window !== 'undefined' &&
      'speechSynthesis' in window &&
      typeof window.SpeechSynthesisUtterance !== 'undefined'
    );
  }

  listVoices(): VoiceOption[] {
    if (!this.isSupported()) return [];
    try {
      return window.speechSynthesis.getVoices().map((v) => ({ uri: v.voiceURI, name: v.name, lang: v.lang }));
    } catch {
      return [];
    }
  }

  speak(text: string, settings: VoiceSettings): Promise<void> {
    if (!this.isSupported() || settings.muted || !text.trim()) return Promise.resolve();
    return new Promise<void>((resolve) => {
      try {
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.volume = Math.max(0, Math.min(1, settings.volume));
        utterance.rate = Math.max(0.5, Math.min(2, settings.rate));
        utterance.lang = settings.language === 'zh' ? 'zh-CN' : 'en-US';
        if (settings.voiceURI) {
          const match = window.speechSynthesis.getVoices().find((v) => v.voiceURI === settings.voiceURI);
          if (match) utterance.voice = match;
        }
        utterance.onend = () => resolve();
        utterance.onerror = () => resolve();
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(utterance);
      } catch {
        resolve();
      }
    });
  }

  stop(): void {
    if (!this.isSupported()) return;
    try {
      window.speechSynthesis.cancel();
    } catch {
      // nothing actionable if the browser refuses to cancel
    }
  }
}

let singleton: VoiceService | null = null;

/** Returns the process-wide VoiceService (browser demo today). */
export function getVoiceService(): VoiceService {
  if (!singleton) singleton = new BrowserVoiceService();
  return singleton;
}
