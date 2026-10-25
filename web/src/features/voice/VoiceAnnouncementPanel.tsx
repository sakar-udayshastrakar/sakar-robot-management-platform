import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { ANNOUNCEMENTS, CATEGORY_LABELS, type AnnouncementCategory } from './announcementCatalog';
import type { VoiceService, VoiceSettings } from './VoiceService';

interface VoiceAnnouncementPanelProps {
  settings: VoiceSettings;
  service: VoiceService;
}

const CATEGORY_ORDER: AnnouncementCategory[] = ['cleaning', 'charging', 'error', 'maintenance', 'system'];

// The announcement catalog. Each row plays a Sakar-authored message through the
// demo VoiceService in the operator's browser — it does NOT command a robot to
// speak (no backend voice endpoint exists). Text is Sakar's own wording, not
// recovered vendor audio (see announcementCatalog.ts).
export function VoiceAnnouncementPanel({ settings, service }: VoiceAnnouncementPanelProps) {
  const supported = service.isSupported();
  return (
    <Card title="Announcement catalog">
      <p className="sakar-page-subtitle" style={{ marginBottom: 12 }}>
        Sakar-authored event announcements. Press play to preview one in your browser (demo only — no robot is
        contacted). Language and voice follow the Voice settings above.
      </p>
      {CATEGORY_ORDER.map((category) => {
        const items = ANNOUNCEMENTS.filter((a) => a.category === category);
        if (items.length === 0) return null;
        return (
          <div key={category} style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8 }}>
              <Badge tone="neutral">{CATEGORY_LABELS[category]}</Badge>
            </div>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
              {items.map((a) => (
                <li key={a.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                  <span>{a.text[settings.language]}</span>
                  <button
                    type="button"
                    className="sakar-btn sakar-btn--secondary"
                    onClick={() => void service.speak(a.text[settings.language], settings)}
                    disabled={!supported || settings.muted}
                    aria-label={`Play announcement: ${a.text.en}`}
                  >
                    Play
                  </button>
                </li>
              ))}
            </ul>
          </div>
        );
      })}
    </Card>
  );
}
