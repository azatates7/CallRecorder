import React, { useMemo, useState } from 'react';
import { Alert, Pressable, SectionList, StyleSheet, Text, View } from 'react-native';
import { useRecordings } from '../hooks/useRecordings';
import type { Direction, Recording } from '../types';
import { dayKey, directionLabel, formatDayTitle, formatDuration, formatTime } from '../utils/format';
import PlayerSheet from './PlayerSheet';

type Filter = 'all' | Exclude<Direction, 'unknown'>;

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'all', label: 'Tümü' },
  { value: 'incoming', label: 'Gelen' },
  { value: 'outgoing', label: 'Giden' },
];

// Bu sürümde numara/isim yok (Play uyumu, §14), bu yüzden yalnızca yön filtresi var.
function matchesFilter(r: Recording, filter: Filter): boolean {
  return filter === 'all' || r.direction === filter;
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
  const { recordings, loading, error, reload, remove, removeMany } = useRecordings();
  const [selected, setSelected] = useState<Recording | null>(null);
  const [filter, setFilter] = useState<Filter>('all');
  // Toplu seçim modu: boş Set = mod kapalı
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const selecting = selectedIds.size > 0;

  const visible = useMemo(
    () => recordings.filter(r => matchesFilter(r, filter)),
    [recordings, filter],
  );
  const sections = useMemo(() => groupByDay(visible), [visible]);
  const filtering = filter !== 'all';

  const toggle = (id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectAll = () => setSelectedIds(new Set(visible.map(r => r.id)));
  const clearSelection = () => setSelectedIds(new Set());

  const deleteSelected = () => {
    const ids = [...selectedIds];
    Alert.alert('Kayıtları sil', `${ids.length} kayıt silinsin mi?`, [
      { text: 'Vazgeç', style: 'cancel' },
      {
        text: 'Sil',
        style: 'destructive',
        onPress: () => {
          removeMany(ids)
            .then(clearSelection)
            .catch(e => Alert.alert('Silinemedi', errorMessage(e)));
        },
      },
    ]);
  };

  return (
    <View style={styles.container}>
      {error ? <Text style={styles.error}>{error}</Text> : null}

      {selecting ? (
        <View style={styles.selbar}>
          <Pressable style={styles.selIcon} onPress={clearSelection}>
            <Text style={styles.selIconText}>✕</Text>
          </Pressable>
          <Text style={styles.selCount}>{selectedIds.size} seçildi</Text>
          <Pressable style={styles.selAction} onPress={selectAll}>
            <Text style={styles.selActionText}>Tümünü seç</Text>
          </Pressable>
          <Pressable style={styles.selAction} onPress={deleteSelected}>
            <Text style={[styles.selActionText, styles.selDelete]}>Sil</Text>
          </Pressable>
        </View>
      ) : (
        <View style={styles.toolbar}>
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
      )}

      <SectionList
        sections={sections}
        keyExtractor={item => item.id}
        refreshing={loading}
        onRefresh={selecting ? undefined : reload}
        renderSectionHeader={({ section }) => <Text style={styles.header}>{section.title}</Text>}
        renderItem={({ item }) => {
          const checked = selectedIds.has(item.id);
          return (
            <Pressable
              style={[styles.row, checked && styles.rowChecked]}
              onPress={() => (selecting ? toggle(item.id) : setSelected(item))}
              onLongPress={() => toggle(item.id)}>
              {selecting ? (
                <Text style={[styles.icon, styles.check]}>{checked ? '☑' : '☐'}</Text>
              ) : (
                <Text
                  style={[styles.icon, item.direction === 'incoming' ? styles.incoming : styles.outgoing]}>
                  {item.direction === 'incoming' ? '↙' : item.direction === 'outgoing' ? '↗' : '•'}
                </Text>
              )}
              <View style={styles.rowText}>
                <Text style={styles.name}>{directionLabel(item.direction)}</Text>
                <Text style={styles.meta}>{formatTime(item.startedAt)}</Text>
              </View>
              <Text style={styles.duration}>{formatDuration(item.durationMs)}</Text>
            </Pressable>
          );
        }}
        ListEmptyComponent={
          loading ? undefined : (
            <Text style={styles.empty}>
              {filtering ? 'Bu yönde kayıt yok.' : 'Henüz kayıt yok. Durum ekranındaki adımları tamamlayın.'}
            </Text>
          )
        }
        contentContainerStyle={sections.length === 0 ? styles.emptyContainer : undefined}
      />

      <PlayerSheet
        recording={selecting ? null : selected}
        onClose={() => setSelected(null)}
        onDelete={id => remove(id).catch(e => Alert.alert('Silinemedi', errorMessage(e)))}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  error: { color: '#c62828', padding: 16 },
  toolbar: { padding: 12, backgroundColor: '#fff', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#ddd' },
  filters: { flexDirection: 'row' },
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
  selbar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#1976d2',
  },
  selIcon: { paddingHorizontal: 8, paddingVertical: 4 },
  selIconText: { color: '#fff', fontSize: 18 },
  selCount: { flex: 1, color: '#fff', fontSize: 16, fontWeight: '600', marginLeft: 8 },
  selAction: { paddingHorizontal: 10, paddingVertical: 4 },
  selActionText: { color: '#fff', fontSize: 15 },
  selDelete: { fontWeight: '700' },
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
  rowChecked: { backgroundColor: '#e3f2fd' },
  icon: { fontSize: 20, width: 28 },
  check: { color: '#1976d2' },
  incoming: { color: '#2e7d32' },
  outgoing: { color: '#1976d2' },
  rowText: { flex: 1 },
  name: { fontSize: 16, color: '#111' },
  meta: { fontSize: 13, color: '#666', marginTop: 2 },
  duration: { fontSize: 14, color: '#444', fontVariant: ['tabular-nums'] },
  emptyContainer: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  empty: { fontSize: 16, textAlign: 'center', opacity: 0.7 },
});