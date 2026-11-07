import React, { useMemo, useState } from 'react';
import { Alert, Pressable, SectionList, StyleSheet, Text, TextInput, View } from 'react-native';
import { useRecordings } from '../hooks/useRecordings';
import { CallRecorder } from '../native/CallRecorder';
import type { Direction, Recording } from '../types';
import { dayKey, formatDayTitle, formatDuration, formatNumber, formatTime } from '../utils/format';
import PlayerSheet from './PlayerSheet';

type Filter = 'all' | Exclude<Direction, 'unknown'>;

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'all', label: 'Tümü' },
  { value: 'incoming', label: 'Gelen' },
  { value: 'outgoing', label: 'Giden' },
];

const digitsOf = (s: string) => s.replace(/\D/g, '');

/** Yön filtresi + numara araması. Rakamlar boşluk/tire/+ farkı gözetmeden karşılaştırılır. */
function matches(r: Recording, filter: Filter, query: string): boolean {
  if (filter !== 'all' && r.direction !== filter) return false;
  const q = query.trim();
  if (!q) return true;
  const qDigits = digitsOf(q);
  if (qDigits) return digitsOf(r.number ?? '').includes(qDigits);
  return formatNumber(r.number).toLocaleLowerCase('tr').includes(q.toLocaleLowerCase('tr'));
}

interface Section {
  key: string;
  title: string;
  data: Recording[];
}

/** Liste zaten en yeni önce sıralı geldiği için tek geçişte günlere bölünür. */
function groupByDay(recordings: Recording[]): Section[] {
  const sections: Section[] = [];
  for (const r of recordings) {
    const key = dayKey(r.startedAt);
    const last = sections[sections.length - 1];
    if (last && last.key === key) {
      last.data.push(r);
    } else {
      sections.push({ key, title: formatDayTitle(r.startedAt), data: [r] });
    }
  }
  return sections;
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function RecordingsScreen() {
  const { recordings, loading, error, reload, remove } = useRecordings();
  const [selected, setSelected] = useState<Recording | null>(null);
  const [filter, setFilter] = useState<Filter>('all');
  const [query, setQuery] = useState('');
  const sections = useMemo(
    () => groupByDay(recordings.filter(r => matches(r, filter, query))),
    [recordings, filter, query],
  );
  const filtering = filter !== 'all' || query.trim() !== '';

  const confirmDelete = (r: Recording) => {
    Alert.alert('Kaydı sil', `${formatNumber(r.number)} · ${formatTime(r.startedAt)} silinsin mi?`, [
      { text: 'Vazgeç', style: 'cancel' },
      {
        text: 'Sil',
        style: 'destructive',
        onPress: () => {
          remove(r.id).catch(e => Alert.alert('Silinemedi', errorMessage(e)));
        },
      },
    ]);
  };

  const showActions = (r: Recording) => {
    Alert.alert(formatNumber(r.number), `${formatDayTitle(r.startedAt)} · ${formatTime(r.startedAt)}`, [
      {
        text: 'Paylaş',
        onPress: () => {
          CallRecorder.shareRecording(r.id).catch(e => Alert.alert('Paylaşılamadı', errorMessage(e)));
        },
      },
      { text: 'Sil', style: 'destructive', onPress: () => confirmDelete(r) },
      { text: 'Vazgeç', style: 'cancel' },
    ]);
  };

  return (
    <View style={styles.container}>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <View style={styles.toolbar}>
        <TextInput
          style={styles.search}
          value={query}
          onChangeText={setQuery}
          placeholder="Numara ara"
          placeholderTextColor="#999"
          keyboardType="phone-pad"
          clearButtonMode="while-editing"
        />
        <View style={styles.filters}>
          {FILTERS.map(f => (
            <Pressable
              key={f.value}
              style={[styles.filter, filter === f.value && styles.filterActive]}
              onPress={() => setFilter(f.value)}>
              <Text style={[styles.filterText, filter === f.value && styles.filterTextActive]}>{f.label}</Text>
            </Pressable>
          ))}
        </View>
      </View>
      <SectionList
        sections={sections}
        keyExtractor={item => item.id}
        refreshing={loading}
        onRefresh={reload}
        renderSectionHeader={({ section }) => <Text style={styles.header}>{section.title}</Text>}
        renderItem={({ item }) => (
          <Pressable
            style={styles.row}
            onPress={() => setSelected(item)}
            onLongPress={() => showActions(item)}>
            <Text
              style={[styles.icon, item.direction === 'incoming' ? styles.incoming : styles.outgoing]}>
              {item.direction === 'incoming' ? '↙' : item.direction === 'outgoing' ? '↗' : '•'}
            </Text>
            <View style={styles.rowText}>
              <Text style={styles.number}>{formatNumber(item.number)}</Text>
              <Text style={styles.meta}>{formatTime(item.startedAt)}</Text>
            </View>
            <Text style={styles.duration}>{formatDuration(item.durationMs)}</Text>
          </Pressable>
        )}
        ListEmptyComponent={
          loading ? undefined : (
            <Text style={styles.empty}>
              {filtering ? 'Eşleşen kayıt yok.' : 'Henüz kayıt yok. Durum ekranındaki adımları tamamlayın.'}
            </Text>
          )
        }
        contentContainerStyle={sections.length === 0 ? styles.emptyContainer : undefined}
      />
      <PlayerSheet recording={selected} onClose={() => setSelected(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  error: { color: '#c62828', padding: 16 },
  toolbar: { padding: 12, backgroundColor: '#fff', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#ddd' },
  search: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 16,
    color: '#111',
  },
  filters: { flexDirection: 'row', marginTop: 8 },
  filter: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#ccc',
    marginRight: 8,
  },
  filterActive: { backgroundColor: '#1976d2', borderColor: '#1976d2' },
  filterText: { fontSize: 14, color: '#111' },
  filterTextActive: { color: '#fff' },
  header: {
    fontSize: 13,
    fontWeight: '600',
    color: '#666',
    backgroundColor: '#f2f2f2',
    paddingHorizontal: 16,
    paddingVertical: 6,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#fff',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#ddd',
  },
  icon: { fontSize: 20, width: 28 },
  incoming: { color: '#2e7d32' },
  outgoing: { color: '#1976d2' },
  rowText: { flex: 1 },
  number: { fontSize: 16, color: '#111' },
  meta: { fontSize: 13, color: '#666', marginTop: 2 },
  duration: { fontSize: 14, color: '#444', fontVariant: ['tabular-nums'] },
  emptyContainer: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  empty: { fontSize: 16, textAlign: 'center', opacity: 0.7 },
});