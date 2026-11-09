// Süre, tarih ve numara biçimleme. Intl'e bağımlı değil; çıktı her cihazda aynı.

const MONTHS = [
  'Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
  'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık',
];

const pad = (n: number) => n.toString().padStart(2, '0');

/** 65_000 → "1:05", 3_725_000 → "1:02:05" */
export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

/** Saat:dakika, ör. "14:05" */
export function formatTime(epochMs: number): string {
  const d = new Date(epochMs);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function startOfDay(epochMs: number): number {
  const d = new Date(epochMs);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Aynı güne ait kayıtları gruplamak için anahtar. */
export function dayKey(epochMs: number): string {
  return String(startOfDay(epochMs));
}

/** "Bugün", "Dün" veya "7 Kasım 2026" */
export function formatDayTitle(epochMs: number, now: number = Date.now()): string {
  const day = startOfDay(epochMs);
  const today = startOfDay(now);
  if (day === today) return 'Bugün';
  // Yaz saati geçişlerinde 24 saatlik fark kaymasın diye takvim üzerinden hesaplanır
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  if (day === yesterday.getTime()) return 'Dün';
  const d = new Date(epochMs);
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}

export function formatName(name: string | null): string {
  return name && name.trim() ? name : 'Bilinmeyen kişi';
}

export function formatNumber(number: string | null): string {
  return number && number.trim() ? number : 'Bilinmeyen numara';
}

/** Numara/isim olmayan sürümde satır/başlık etiketi: aramanın yönü. */
export function directionLabel(direction: string): string {
  return direction === 'incoming' ? 'Gelen arama' : direction === 'outgoing' ? 'Giden arama' : 'Arama';
}

/** 1_536_000 → "1,5 MB" */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(1).replace('.', ',')} ${units[unit]}`;
}