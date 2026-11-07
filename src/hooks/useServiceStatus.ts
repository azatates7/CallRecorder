import { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { CallRecorder } from '../native/CallRecorder';
import type { RecorderSettings, ServiceStatus } from '../types';

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/**
 * Servis durumu, izinler ve ayarlar. Kullanıcı Android ayarlarından geri dönünce
 * (uygulama tekrar ön plana gelince) durum yeniden okunur.
 */
export function useServiceStatus() {
  const [status, setStatus] = useState<ServiceStatus | null>(null);
  const [settings, setSettings] = useState<RecorderSettings | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [s, st] = await Promise.all([CallRecorder.getStatus(), CallRecorder.getSettings()]);
      setStatus(s);
      setSettings(st);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') refresh();
    });
    return () => sub.remove();
  }, [refresh]);

  const updateSettings = useCallback(async (patch: Partial<RecorderSettings>) => {
    try {
      setSettings(await CallRecorder.setSettings(patch));
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  const setMonitoring = useCallback(
    async (enabled: boolean) => {
      setError(null);
      try {
        await (enabled ? CallRecorder.startMonitoring() : CallRecorder.stopMonitoring());
      } catch (e) {
        setError(errorMessage(e));
      }
      // Servis kendi içinde başlatılamazsa (ör. Android kısıtı) gerçek durumu yansıt
      setTimeout(refresh, 500);
    },
    [refresh],
  );

  return { status, settings, error, setError, refresh, updateSettings, setMonitoring };
}