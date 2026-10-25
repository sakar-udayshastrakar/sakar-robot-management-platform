import { useEffect, useMemo, useState } from 'react';
import { PageHeader } from '../../components/ui/PageHeader';
import { SimulatedDataBanner } from '../../components/ui/SimulatedDataBanner';
import { VoiceSettingsPanel } from './VoiceSettingsPanel';
import { VoiceAnnouncementPanel } from './VoiceAnnouncementPanel';
import { DEFAULT_VOICE_SETTINGS, getVoiceService, type VoiceSettings } from './VoiceService';

const STORAGE_KEY = 'sakar.voice.settings';

function loadSettings(): VoiceSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return { ...DEFAULT_VOICE_SETTINGS, ...(JSON.parse(raw) as Partial<VoiceSettings>) };
  } catch {
    // ignore unreadable/blocked storage — fall back to defaults
  }
  return DEFAULT_VOICE_SETTINGS;
}

// Voice announcement demo. The real C40 speaks via an on-device engine over
// Android AIDL (see research/.../ui/VOICE_MODULE_ANALYSIS.md); there is no
// Sakar backend voice endpoint, so this screen is a clearly-labeled demo that
// plays audio in the operator's own browser via the Web Speech API. Settings
// persist per-browser only.
export function VoicePage() {
  const service = useMemo(() => getVoiceService(), []);
  const [settings, setSettings] = useState<VoiceSettings>(() => loadSettings());

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // per-viewer convenience only; safe to ignore if storage is unavailable
    }
  }, [settings]);

  // Stop any in-progress speech when leaving the page.
  useEffect(() => () => service.stop(), [service]);

  return (
    <div>
      <PageHeader
        title="Voice Announcements"
        subtitle="Configure and preview robot voice announcements (demo)."
      />
      <SimulatedDataBanner label="Demo voice playback (browser only)" />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <VoiceSettingsPanel settings={settings} onChange={setSettings} service={service} />
        <VoiceAnnouncementPanel settings={settings} service={service} />
      </div>
    </div>
  );
}
