import type { VoiceLanguage } from './VoiceService';

// Sakar-authored announcement catalog (DEMO / inferred content).
//
// The original C40 ships 79 numbered MP3 prompts (en + zh) played by
// peanut-clean's SoundPlayerLifecycle, plus an on-device TTS engine. Their
// actual spoken text is NOT present in the static reverse-engineering evidence
// (see research/c40-reverse-engineering/ui/VOICE_ASSET_INVENTORY.csv —
// announcement_text = UNKNOWN). The strings below are therefore SAKAR's OWN
// wording, grouped by the announcement categories the vendor task logic is
// known to trigger (recharge, self-clean, faults — STRONGLY_SUPPORTED in the
// findings). They are NOT a reproduction of vendor audio or text. No vendor
// MP3 is bundled or shipped.

export type AnnouncementCategory = 'cleaning' | 'charging' | 'error' | 'maintenance' | 'system';

export interface Announcement {
  id: string;
  category: AnnouncementCategory;
  text: Record<VoiceLanguage, string>;
}

export const CATEGORY_LABELS: Record<AnnouncementCategory, string> = {
  cleaning: 'Cleaning',
  charging: 'Charging & dock',
  error: 'Errors & warnings',
  maintenance: 'Maintenance',
  system: 'System',
};

export const ANNOUNCEMENTS: Announcement[] = [
  { id: 'clean_start', category: 'cleaning', text: { en: 'Starting cleaning task.', zh: '开始清洁任务。' } },
  { id: 'clean_pause', category: 'cleaning', text: { en: 'Cleaning paused.', zh: '清洁已暂停。' } },
  { id: 'clean_resume', category: 'cleaning', text: { en: 'Resuming cleaning.', zh: '继续清洁。' } },
  { id: 'clean_complete', category: 'cleaning', text: { en: 'Cleaning complete.', zh: '清洁完成。' } },
  { id: 'clean_area_skipped', category: 'cleaning', text: { en: 'Area skipped.', zh: '已跳过该区域。' } },
  { id: 'charge_returning', category: 'charging', text: { en: 'Returning to the charging dock.', zh: '正在返回充电桩。' } },
  { id: 'charge_charging', category: 'charging', text: { en: 'Charging.', zh: '正在充电。' } },
  { id: 'charge_low_battery', category: 'charging', text: { en: 'Battery low. Please let me charge.', zh: '电量低，请让我充电。' } },
  { id: 'charge_full', category: 'charging', text: { en: 'Charging complete.', zh: '充电完成。' } },
  { id: 'error_obstacle', category: 'error', text: { en: 'Obstacle detected. Please clear my path.', zh: '检测到障碍物，请让一让。' } },
  { id: 'error_stuck', category: 'error', text: { en: 'I am stuck and need assistance.', zh: '我被困住了，需要帮助。' } },
  { id: 'error_fault', category: 'error', text: { en: 'A fault has occurred. Please check the robot.', zh: '发生故障，请检查机器人。' } },
  { id: 'maint_consumable', category: 'maintenance', text: { en: 'A consumable part needs replacing soon.', zh: '耗材即将需要更换。' } },
  { id: 'maint_water_tank', category: 'maintenance', text: { en: 'Please check the water tank.', zh: '请检查水箱。' } },
  { id: 'maint_dustbin', category: 'maintenance', text: { en: 'Please empty the dust bin.', zh: '请清空集尘盒。' } },
  { id: 'system_startup', category: 'system', text: { en: 'System ready.', zh: '系统就绪。' } },
  { id: 'system_moving', category: 'system', text: { en: 'Please mind the robot.', zh: '机器人移动中，请注意。' } },
];
