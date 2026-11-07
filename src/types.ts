// Native tarafla paylaşılan tipler (CLAUDE.md §5).

export type Direction = 'incoming' | 'outgoing' | 'unknown';
export type AudioSourceName = 'VOICE_CALL' | 'VOICE_COMMUNICATION' | 'VOICE_RECOGNITION' | 'MIC';
export type AudioRoute = 'earpiece' | 'speaker' | 'wired' | 'bluetooth' | 'unknown';

export interface Recording {
  id: string;
  filePath: string;
  number: string | null;
  direction: Direction;
  startedAt: number; // epoch ms
  durationMs: number;
  sizeBytes: number;
  audioSource: AudioSourceName;
  audioRoute: AudioRoute;
}

export interface PlaybackProgress {
  id: string;
  positionMs: number;
  durationMs: number;
}

/** Durum ekranında gösterilen çalışma zamanı izinleri (PermissionsAndroid anahtarlarıyla aynı). */
export type PermissionKey = 'RECORD_AUDIO' | 'READ_PHONE_STATE' | 'READ_CALL_LOG' | 'POST_NOTIFICATIONS';

export interface ServiceStatus {
  monitoring: boolean;
  accessibilityEnabled: boolean;
  ignoringBatteryOptimizations: boolean;
  permissions: Record<PermissionKey, 'granted' | 'denied'>;
}

export type SampleRate = 16000 | 44100;

export interface RecorderSettings {
  autoRecord: boolean;
  preferredSource: AudioSourceName | 'AUTO';
  sampleRate: SampleRate;
}