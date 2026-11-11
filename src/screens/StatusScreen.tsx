import React, { useState } from 'react';
import {
  PermissionsAndroid,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import { useRecordings } from '../hooks/useRecordings';
import { useServiceStatus } from '../hooks/useServiceStatus';
import { CallRecorder } from '../native/CallRecorder';
import type { PermissionKey, RecorderSettings, SampleRate } from '../types';
import { formatBytes } from '../utils/format';

/** İzin isteme sırası CLAUDE.md §7'deki sırayla aynıdır. */
const PERMISSION_ROWS: { key: PermissionKey; title: string; why: string }[] = [
  { key: 'RECORD_AUDIO', title: 'Mikrofon', why: 'Arama sesini kaydetmek için.' },
  { key: 'READ_PHONE_STATE', title: 'Telefon durumu', why: 'Aramanın ne zaman başlayıp bittiğini anlamak için.' },
  { key: 'POST_NOTIFICATIONS', title: 'Bildirim', why: 'Kaydın çalıştığını ve sorunları göstermek için.' },
];

const SOURCE_OPTIONS: { value: RecorderSettings['preferredSource']; label: string }[] = [
  { value: 'AUTO', label: 'Otomatik' },
  { value: 'VOICE_CALL', label: 'Hat sesi' },
  { value: 'VOICE_COMMUNICATION', label: 'İletişim' },
  { value: 'VOICE_RECOGNITION', label: 'Tanıma' },
  { value: 'MIC', label: 'Mikrofon' },
];

const RATE_OPTIONS: { value: SampleRate; label: string }[] = [
  { value: 16000, label: 'Standart (16 kHz)' },
  { value: 44100, label: 'Yüksek (44,1 kHz)' },
];

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function CheckRow(props: {
  ok: boolean;
  title: string;
  why: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={styles.checkRow}>
      <Text style={[styles.checkMark, props.ok ? styles.ok : styles.fail]}>{props.ok ? '✓' : '✗'}</Text>
      <View style={styles.flex}>
        <Text style={styles.checkTitle}>{props.title}</Text>
        <Text style={styles.hint}>{props.why}</Text>
      </View>
      {!props.ok && props.actionLabel ? (
        <Pressable style={styles.action} onPress={props.onAction}>
          <Text style={styles.actionText}>{props.actionLabel}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function Chips<T extends string | number>(props: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <View style={styles.chips}>
      {props.options.map(o => (
        <Pressable
          key={String(o.value)}
          style={[styles.chip, o.value === props.value && styles.chipActive]}
          onPress={() => props.onChange(o.value)}>
          <Text style={[styles.chipText, o.value === props.value && styles.chipTextActive]}>{o.label}</Text>
        </Pressable>
      ))}
    </View>
  );
}

export default function StatusScreen() {
  const { status, settings, error, setError, refresh, updateSettings, setMonitoring } = useServiceStatus();
  const { recordings } = useRecordings();
  // Kalıcı reddedilen izinler: Android bir daha sormaz, kullanıcı uygulama ayarlarından açar
  const [blocked, setBlocked] = useState<Partial<Record<PermissionKey, boolean>>>({});

  const run = (fn: () => Promise<unknown>) => {
    fn().catch(e => setError(errorMessage(e)));
  };

  const requestPermission = async (key: PermissionKey) => {
    if (blocked[key]) {
      await CallRecorder.openAppSettings();
      return;
    }
    const result = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS[key]);
    if (result === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN) {
      setBlocked(b => ({ ...b, [key]: true }));
    }
    await refresh();
  };

  if (!status || !settings) {
    return (
      <View style={styles.container}>
        <Text style={styles.hint}>{error ?? 'Yükleniyor…'}</Text>
      </View>
    );
  }

  const canMonitor =
    status.permissions.RECORD_AUDIO === 'granted' && status.permissions.READ_PHONE_STATE === 'granted';
  const totalBytes = recordings.reduce((sum, r) => sum + r.sizeBytes, 0);

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.section}>Gerekenler</Text>
      {PERMISSION_ROWS.map(p => (
        <CheckRow
          key={p.key}
          ok={status.permissions[p.key] === 'granted'}
          title={p.title}
          why={p.why}
          actionLabel={blocked[p.key] ? 'Ayarları aç' : 'İzin ver'}
          onAction={() => run(() => requestPermission(p.key))}
        />
      ))}
      <CheckRow
        ok={status.ignoringBatteryOptimizations}
        title="Pil optimizasyonu kapalı"
        why="Sistemin arka planda servisi kapatmaması için."
        actionLabel="Düzelt"
        onAction={() => run(CallRecorder.requestIgnoreBatteryOptimizations)}
      />
      <CheckRow
        ok={status.accessibilityEnabled}
        title="Erişilebilirlik servisi"
        why="Android 10+'da arama sırasında mikrofonun kapatılmaması için. Ekranı okumaz."
        actionLabel="Aç"
        onAction={() => run(CallRecorder.openAccessibilitySettings)}
      />
      {!status.accessibilityEnabled ? (
        <Text style={styles.note}>
          Anahtar gri ve açılamıyorsa: Ayarlar › Uygulamalar › CallRecorder › sağ üstteki ⋮ › "Kısıtlı
          ayarlara izin ver" seçeneğini açıp tekrar deneyin.
        </Text>
      ) : null}

      <Text style={styles.section}>Kayıt</Text>
      <View style={styles.switchRow}>
        <View style={styles.flex}>
          <Text style={styles.checkTitle}>Arama izleme</Text>
          <Text style={[styles.hint, status.monitoring ? styles.ok : styles.fail]}>
            {status.monitoring ? 'Çalışıyor' : 'Kapalı'}
          </Text>
        </View>
        <Switch
          value={status.monitoring}
          disabled={!canMonitor && !status.monitoring}
          onValueChange={v => run(() => setMonitoring(v))}
        />
      </View>
      <View style={styles.switchRow}>
        <View style={styles.flex}>
          <Text style={styles.checkTitle}>Otomatik kayıt</Text>
          <Text style={styles.hint}>Kapalıyken izleme sürer ama aramalar kaydedilmez.</Text>
        </View>
        <Switch value={settings.autoRecord} onValueChange={v => run(() => updateSettings({ autoRecord: v }))} />
      </View>
      <View style={styles.switchRow}>
        <View style={styles.flex}>
          <Text style={styles.checkTitle}>Kayıt sırasında hoparlörü aç</Text>
          <Text style={styles.hint}>
            Karşı tarafın sesinin de kaydedilmesine yardımcı olur. Aramayı hoparlöre alır (herkes
            duyabilir) ve her cihazda çalışmayabilir.
          </Text>
        </View>
        <Switch value={settings.autoSpeaker} onValueChange={v => run(() => updateSettings({ autoSpeaker: v }))} />
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}

      <Text style={styles.label}>Ses kaynağı</Text>
      <Chips
        options={SOURCE_OPTIONS}
        value={settings.preferredSource}
        onChange={v => run(() => updateSettings({ preferredSource: v }))}
      />
      <Text style={styles.label}>Kalite</Text>
      <Chips
        options={RATE_OPTIONS}
        value={settings.sampleRate}
        onChange={v => run(() => updateSettings({ sampleRate: v }))}
      />
      <Text style={styles.hint}>Ayar değişiklikleri bir sonraki aramadan itibaren geçerlidir.</Text>

      <Text style={styles.section}>Kayıtlar</Text>
      <Text style={styles.body}>
        {recordings.length} kayıt · {formatBytes(totalBytes)}. Kayıtlar yalnızca bu telefonda tutulur;
        uygulama silinirse kayıtlar da silinir. Bu sürümde kayıtlarda numara ve kişi adı gösterilmez,
        yalnızca aramanın yönü ve saati tutulur.
      </Text>

      <Text style={styles.section}>Bilinmesi gerekenler</Text>
      <Text style={styles.body}>
        Android, uygulamaların hat sesini doğrudan kaydetmesine izin vermez; kayıt telefonun mikrofonundan
        alınır. Kendi sesiniz her zaman net kaydedilir, karşı tarafın sesi en iyi hoparlör açıkken duyulur.
        Kulaklık veya Bluetooth kullanılırken karşı tarafın sesi kayda girmeyebilir. En iyi sonuç için
        yukarıdaki "Kayıt sırasında hoparlörü aç" ayarını kullanın veya aramayı elle hoparlöre alın.
        Görüşme kaydı yalnızca kişisel kullanım içindir; karşı tarafı bilgilendirmek sizin
        sorumluluğunuzdadır.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, paddingBottom: 40 },
  flex: { flex: 1 },
  section: { fontSize: 13, fontWeight: '700', color: '#666', marginTop: 24, marginBottom: 8 },
  checkRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8 },
  checkMark: { fontSize: 18, width: 28, fontWeight: '700' },
  checkTitle: { fontSize: 16, color: '#111' },
  hint: { fontSize: 13, color: '#666', marginTop: 2 },
  note: { fontSize: 13, color: '#8a5a00', marginLeft: 28, marginBottom: 4 },
  action: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6, backgroundColor: '#1976d2' },
  actionText: { color: '#fff', fontWeight: '600' },
  switchRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8 },
  ok: { color: '#2e7d32' },
  fail: { color: '#c62828' },
  error: { color: '#c62828', marginTop: 4 },
  label: { fontSize: 14, color: '#111', marginTop: 16, marginBottom: 6 },
  chips: { flexDirection: 'row', flexWrap: 'wrap' },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#ccc',
    marginRight: 8,
    marginBottom: 8,
  },
  chipActive: { backgroundColor: '#1976d2', borderColor: '#1976d2' },
  chipText: { fontSize: 14, color: '#111' },
  chipTextActive: { color: '#fff' },
  body: { fontSize: 14, color: '#333', lineHeight: 20 },
});