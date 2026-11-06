import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { CallRecorder } from '../native/CallRecorder';

export default function StatusScreen() {
  const [bridge, setBridge] = useState<string>('kontrol ediliyor…');

  useEffect(() => {
    CallRecorder.ping()
      .then(result => setBridge(result))
      .catch((e: unknown) => setBridge(`HATA: ${e instanceof Error ? e.message : String(e)}`));
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Native köprü</Text>
      <Text style={[styles.value, bridge === 'pong' ? styles.ok : styles.fail]}>{bridge}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24 },
  label: { fontSize: 14, opacity: 0.7 },
  value: { fontSize: 20, fontWeight: '600', marginTop: 4 },
  ok: { color: '#2e7d32' },
  fail: { color: '#c62828' },
});