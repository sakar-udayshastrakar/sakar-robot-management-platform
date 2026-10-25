import { useEffect, useState } from 'react';
import { Card } from '../../components/ui/Card';
import type { VoiceService, VoiceSettings, VoiceOption, VoiceLanguage } from './VoiceService';

interface VoiceSettingsPanelProps {
  settings: VoiceSettings;
  onChange: (next: VoiceSettings) => void;
  service: VoiceService;
}

// Voice announcement settings — language, volume, mute, voice/timbre, and a
// test announcement. All settings are local to this browser (a demo control
// surface); nothing is sent to a robot. See VoiceService.ts.
export function VoiceSettingsPanel({ settings, onChange, service }: VoiceSettingsPanelProps) {
  const [voices, setVoices] = useState<VoiceOption[]>([]);
  const supported = service.isSupported();

  useEffect(() => {
    if (!supported) return;
    const load = () => setVoices(service.listVoices());
    load();
    // Browser voice lists populate asynchronously on first use.
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.addEventListener('voiceschanged', load);
      return () => window.speechSynthesis.removeEventListener('voiceschanged', load);
    }
    return undefined;
  }, [service, supported]);

  const langVoices = voices.filter((v) => v.lang.toLowerCase().startsWith(settings.language));

  function test() {
    void service.speak(settings.language === 'zh' ? '这是一次测试播报。' : 'This is a test announcement.', settings);
  }

  return (
    <Card
      title="Voice settings"
      actions={
        <button type="button" className="sakar-btn sakar-btn--secondary" onClick={test} disabled={!supported || settings.muted}>
          Test announcement
        </button>
      }
    >
      {!supported && (
        <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
          This browser has no Web Speech API, so the demo cannot play audio here. Settings below are still saved.
        </p>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
        <div className="sakar-field" style={{ marginBottom: 0 }}>
          <label htmlFor="voice-language">Language</label>
          <select
            id="voice-language"
            value={settings.language}
            onChange={(e) => onChange({ ...settings, language: e.target.value as VoiceLanguage })}
          >
            <option value="en">English</option>
            <option value="zh">Chinese (中文)</option>
          </select>
        </div>

        <div className="sakar-field" style={{ marginBottom: 0 }}>
          <label htmlFor="voice-select">Voice / timbre</label>
          <select
            id="voice-select"
            value={settings.voiceURI ?? ''}
            onChange={(e) => onChange({ ...settings, voiceURI: e.target.value || null })}
            disabled={!supported}
          >
            <option value="">Browser default</option>
            {langVoices.map((v) => (
              <option key={v.uri} value={v.uri}>{v.name} ({v.lang})</option>
            ))}
          </select>
        </div>

        <div className="sakar-field" style={{ marginBottom: 0 }}>
          <label htmlFor="voice-volume">Volume ({Math.round(settings.volume * 100)}%)</label>
          <input
            id="voice-volume"
            type="range"
            min={0}
            max={100}
            value={Math.round(settings.volume * 100)}
            onChange={(e) => onChange({ ...settings, volume: Number(e.target.value) / 100 })}
          />
        </div>

        <div className="sakar-field" style={{ marginBottom: 0 }}>
          <label htmlFor="voice-rate">Speed ({settings.rate.toFixed(1)}×)</label>
          <input
            id="voice-rate"
            type="range"
            min={5}
            max={20}
            value={Math.round(settings.rate * 10)}
            onChange={(e) => onChange({ ...settings, rate: Number(e.target.value) / 10 })}
          />
        </div>

        <div className="sakar-field" style={{ marginBottom: 0, alignSelf: 'end' }}>
          <label htmlFor="voice-mute" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input
              id="voice-mute"
              type="checkbox"
              checked={settings.muted}
              onChange={(e) => onChange({ ...settings, muted: e.target.checked })}
            />
            Mute announcements
          </label>
        </div>
      </div>
    </Card>
  );
}
