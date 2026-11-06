import { NativeModules } from 'react-native';

// Uygulamada NativeModules'a erişilen tek yer burasıdır (CLAUDE.md §5).

interface CallRecorderNative {
  ping(): Promise<string>;
}

const native = NativeModules.CallRecorder as CallRecorderNative | undefined;

// Modül yoksa import anında değil, çağrı anında hata ver: böylece ekran açılır
// ve hata Durum ekranında okunabilir şekilde gösterilir.
function getNative(): CallRecorderNative {
  if (!native) {
    throw new Error(
      'CallRecorder native modülü bulunamadı. MainApplication.kt içinde CallRecorderPackage eklendi mi, uygulama yeniden build edildi mi?',
    );
  }
  return native;
}

export const CallRecorder = {
  ping: (): Promise<string> => {
    try {
      return getNative().ping();
    } catch (e) {
      return Promise.reject(e);
    }
  },
};