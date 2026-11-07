import { DeviceEventEmitter, NativeModules } from 'react-native';
import type { PlaybackProgress, Recording, RecorderSettings, ServiceStatus } from '../types';

// Uygulamada NativeModules'a erişilen tek yer burasıdır (CLAUDE.md §5).

interface CallRecorderNative {
  ping(): Promise<string>;
  startMonitoring(): Promise<void>;
  stopMonitoring(): Promise<void>;
  getStatus(): Promise<ServiceStatus>;
  openAccessibilitySettings(): Promise<void>;
  openAppSettings(): Promise<void>;
  requestIgnoreBatteryOptimizations(): Promise<void>;
  getSettings(): Promise<RecorderSettings>;
  setSettings(settings: Partial<RecorderSettings>): Promise<RecorderSettings>;
  listRecordings(): Promise<Recording[]>;
  deleteRecording(id: string): Promise<void>;
  shareRecording(id: string): Promise<void>;
  play(id: string): Promise<void>;
  pause(): Promise<void>;
  seekTo(positionMs: number): Promise<void>;
  stop(): Promise<void>;
}

/** Native'den gelen olaylar ve taşıdıkları veri. */
export interface CallRecorderEvents {
  recordingSaved: Recording;
  playbackProgress: PlaybackProgress;
  playbackEnded: { id: string };
}

const native = NativeModules.CallRecorder as CallRecorderNative | undefined;

// Modül yoksa import anında değil, çağrı anında hata ver: böylece ekran açılır
// ve hata okunabilir şekilde gösterilir.
function call<T>(fn: (n: CallRecorderNative) => Promise<T>): Promise<T> {
  if (!native) {
    return Promise.reject(
      new Error(
        'CallRecorder native modülü bulunamadı. MainApplication.kt içinde CallRecorderPackage eklendi mi, uygulama yeniden build edildi mi?',
      ),
    );
  }
  return fn(native);
}

export const CallRecorder = {
  ping: () => call(n => n.ping()),
  startMonitoring: () => call(n => n.startMonitoring()),
  stopMonitoring: () => call(n => n.stopMonitoring()),
  getStatus: () => call(n => n.getStatus()),
  openAccessibilitySettings: () => call(n => n.openAccessibilitySettings()),
  openAppSettings: () => call(n => n.openAppSettings()),
  requestIgnoreBatteryOptimizations: () => call(n => n.requestIgnoreBatteryOptimizations()),
  getSettings: () => call(n => n.getSettings()),
  setSettings: (settings: Partial<RecorderSettings>) => call(n => n.setSettings(settings)),
  listRecordings: () => call(n => n.listRecordings()),
  deleteRecording: (id: string) => call(n => n.deleteRecording(id)),
  shareRecording: (id: string) => call(n => n.shareRecording(id)),
  play: (id: string) => call(n => n.play(id)),
  pause: () => call(n => n.pause()),
  seekTo: (positionMs: number) => call(n => n.seekTo(positionMs)),
  stop: () => call(n => n.stop()),
};

/**
 * Native olaylarına abone olur. Kotlin tarafı olayları emitDeviceEvent ile gönderdiği için
 * DeviceEventEmitter yeterli; NativeEventEmitter'ın gerektirdiği ek metotlara gerek yok.
 */
export function addCallRecorderListener<K extends keyof CallRecorderEvents>(
  event: K,
  handler: (payload: CallRecorderEvents[K]) => void,
): { remove(): void } {
  return DeviceEventEmitter.addListener(event, handler);
}