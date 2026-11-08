import React, { useEffect, useState } from 'react';
import { Alert, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { usePlayer } from '../hooks/usePlayer';
import type { Recording } from '../types';
import { formatDayTitle, formatDuration, formatName, formatNumber, formatTime } from '../utils/format';

const SKIP_MS = 10_000;

interface Props {
  recording: Recording | null;
  onClose: () => void;
}

function showError(e: unknown) {
  Alert.alert('Oynatma hatası', e instanceof Error ? e.message : String(e));
}

/** Alt panel oynatıcı. Açılınca kaydı çalmaya başlar, kapanınca durdurur. */
export default function PlayerSheet({ recording, onClose }: Props) {
  const { state, play, pause, seekTo, stop } = usePlayer();
  const [barWidth, setBarWidth] = useState(0);

  const recordingId = recording?.id;
  useEffect(() => {
    if (!recordingId) return;
    play(recordingId).catch(showError);
    return () => {
      stop().catch(() => {});
    };
  }, [recordingId, play, stop]);

  if (!recording) return null;

  // Native süre gelene kadar meta verideki süre gösterilir
  const duration = state.durationMs || recording.durationMs;
  const position = Math.min(state.positionMs, duration);
  const ratio = duration > 0 ? position / duration : 0;

  const seekBy = (deltaMs: number) => {
    seekTo(Math.max(0, Math.min(duration, position + deltaMs))).catch(showError);
  };

  const togglePlay = () => {
    (state.playing ? pause() : play(recording.id)).catch(showError);
  };

  return (
    <Modal visible transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} />
      <View style={styles.sheet}>
        <Text style={styles.title}>{formatName(recording.name)}</Text>
        <Text style={styles.subtitle}>
          {formatNumber(recording.number)} · {formatDayTitle(recording.startedAt)} ·{' '}
          {formatTime(recording.startedAt)}
        </Text>

        <Pressable
          style={styles.barTrack}
          onLayout={e => setBarWidth(e.nativeEvent.layout.width)}
          onPress={e => {
            if (barWidth > 0) seekTo((e.nativeEvent.locationX / barWidth) * duration).catch(showError);
          }}>
          <View style={[styles.barFill, { width: `${ratio * 100}%` }]} />
        </Pressable>
        <View style={styles.times}>
          <Text style={styles.time}>{formatDuration(position)}</Text>
          <Text style={styles.time}>{formatDuration(duration)}</Text>
        </View>

        <View style={styles.controls}>
          <Pressable style={styles.button} onPress={() => seekBy(-SKIP_MS)}>
            <Text style={styles.buttonText}>−10 sn</Text>
          </Pressable>
          <Pressable style={[styles.button, styles.primary]} onPress={togglePlay}>
            <Text style={[styles.buttonText, styles.primaryText]}>
              {state.playing ? 'Duraklat' : 'Oynat'}
            </Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => seekBy(SKIP_MS)}>
            <Text style={styles.buttonText}>+10 sn</Text>
          </Pressable>
        </View>

        <Pressable style={styles.close} onPress={onClose}>
          <Text style={styles.closeText}>Kapat</Text>
        </Pressable>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)' },
  sheet: {
    backgroundColor: '#fff',
    padding: 24,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
  },
  title: { fontSize: 20, fontWeight: '600', color: '#111' },
  subtitle: { fontSize: 14, color: '#666', marginTop: 4 },
  barTrack: {
    height: 8,
    borderRadius: 4,
    backgroundColor: '#e0e0e0',
    marginTop: 24,
    overflow: 'hidden',
  },
  barFill: { height: '100%', backgroundColor: '#1976d2' },
  times: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 6 },
  time: { fontSize: 12, color: '#666', fontVariant: ['tabular-nums'] },
  controls: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 20 },
  button: {
    flex: 1,
    paddingVertical: 12,
    marginHorizontal: 4,
    borderRadius: 8,
    backgroundColor: '#f0f0f0',
    alignItems: 'center',
  },
  buttonText: { fontSize: 16, color: '#111' },
  primary: { backgroundColor: '#1976d2' },
  primaryText: { color: '#fff', fontWeight: '600' },
  close: { alignItems: 'center', marginTop: 16, paddingVertical: 8 },
  closeText: { fontSize: 15, color: '#1976d2' },
});