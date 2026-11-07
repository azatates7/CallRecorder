import { useCallback, useEffect, useState } from 'react';
import { CallRecorder, addCallRecorderListener } from '../native/CallRecorder';

export interface PlayerState {
  id: string | null;
  playing: boolean;
  positionMs: number;
  durationMs: number;
}

const IDLE: PlayerState = { id: null, playing: false, positionMs: 0, durationMs: 0 };

/** Native oynatıcının durumunu olaylardan takip eder. */
export function usePlayer() {
  const [state, setState] = useState<PlayerState>(IDLE);

  useEffect(() => {
    const progress = addCallRecorderListener('playbackProgress', e => {
      setState(s => (s.id === e.id ? { ...s, positionMs: e.positionMs, durationMs: e.durationMs } : s));
    });
    const ended = addCallRecorderListener('playbackEnded', e => {
      setState(s => (s.id === e.id ? { ...s, playing: false, positionMs: s.durationMs } : s));
    });
    return () => {
      progress.remove();
      ended.remove();
    };
  }, []);

  const play = useCallback(async (id: string) => {
    await CallRecorder.play(id);
    setState(s => (s.id === id ? { ...s, playing: true } : { ...IDLE, id, playing: true }));
  }, []);

  const pause = useCallback(async () => {
    await CallRecorder.pause();
    setState(s => ({ ...s, playing: false }));
  }, []);

  const seekTo = useCallback(async (positionMs: number) => {
    await CallRecorder.seekTo(positionMs);
  }, []);

  const stop = useCallback(async () => {
    await CallRecorder.stop();
    setState(IDLE);
  }, []);

  return { state, play, pause, seekTo, stop };
}