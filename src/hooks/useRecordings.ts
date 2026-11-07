import { useCallback, useEffect, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { CallRecorder, addCallRecorderListener } from '../native/CallRecorder';
import type { Recording } from '../types';

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/** Kayıt listesi: ekran odaklanınca ve yeni kayıt kaydedilince yenilenir. */
export function useRecordings() {
  const [recordings, setRecordings] = useState<Recording[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setRecordings(await CallRecorder.listRecordings());
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      reload();
    }, [reload]),
  );

  useEffect(() => {
    const sub = addCallRecorderListener('recordingSaved', () => {
      reload();
    });
    return () => sub.remove();
  }, [reload]);

  const remove = useCallback(async (id: string) => {
    await CallRecorder.deleteRecording(id);
    setRecordings(list => list.filter(r => r.id !== id));
  }, []);

  return { recordings, loading, error, reload, remove };
}