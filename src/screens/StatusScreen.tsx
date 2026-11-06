import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Switch, Text, View } from 'react-native';
import { CallRecorder } from '../native/CallRecorder';

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function StatusScreen() {
  const [bridge, setBridge] = useState<string>('kontrol ediliyor…');
  const [monitoring, setMonitoring] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshMonitoring = useCallback(() => {
    CallRecorder.isMonitoring()
      .then(setMonitoring)
      .catch((e: unknown) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    CallRecorder.ping()
      .then(setBridge)
      .catch((e: unknown) => setBridge(`HATA: ${errorMessage(e)}`));
    refreshMonitoring();
  }, [refreshMonitoring]);

  const toggleMonitoring = async (value: boolean) => {
    setError(null);
    try {
      if (value) {
        await CallRecorder.startMonitoring();
      } else {
        await CallRecorder.stopMonitoring();
      }
    } catch (e) {
      setError(errorMessage(e));
    }
    // Servis kendi içinde başlatılamazsa (ör. Android kısıtı) gerçek durumu yansıt.
    setTimeout(refreshMonitoring, 500);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Native köprü</Text>
      <Text style={[styles.value, bridge === 'pong' ? styles.ok : styles.fail]}>{bridge}</Text>

      <View style={styles.row}>
        <View style={styles.rowText}>
          <Text style={styles.label}>Arama izleme servisi</Text>
          <Text style={[styles.value, monitoring ? styles.ok : styles.fail]}>
            {monitoring ? 'Çalışıyor' : 'Kapalı'}
          </Text>
        </View>
        <Switch value={monitoring} onValueChange={toggleMonitoring} />
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24 },
  label: { fontSize: 14, opacity: 0.7 },
  value: { fontSize: 20, fontWeight: '600', marginTop: 4 },
  ok: { color: '#2e7d32' },
  fail: { color: '#c62828' },
  row: { flexDirection: 'row', alignItems: 'center', marginTop: 24 },
  rowText: { flex: 1 },
  error: { color: '#c62828', marginTop: 8 },
});