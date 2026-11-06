import { NativeModules } from 'react-native';

// Uygulamada NativeModules'a erişilen tek yer burasıdır (CLAUDE.md §5).

interface CallRecorderNative {
  ping(): Promise<string>;
  startMonitoring(): Promise<void>;
  stopMonitoring(): Promise<void>;
  isMonitoring(): Promise<boolean>; // geçici, Faz 3'te getStatus() olacak
}

const native = NativeModules.CallRecorder as CallRecorderNative | undefined;

// Modül yoksa import anında değil, çağrı anında hata ver: böylece ekran açılır
// ve hata Durum ekranında okunabilir şekilde gösterilir.
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
  isMonitoring: () => call(n => n.isMonitoring()),
};