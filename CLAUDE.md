# CLAUDE.md — CallRecorder (Android, React Native)

Bu dosya, projeyi sıfırdan kuracak ve geliştirecek Claude Code oturumları için tek referanstır. Kararlar burada; bir kararı değiştirmeden önce gerekçesini yaz ve bu dosyayı güncelle.

> **DİKKAT — mevcut durum:** Uygulama şu an **Play uyumlu, sade sürüm**. Erişilebilirlik servisi, `READ_CALL_LOG` ve kişi adları (`READ_CONTACTS`) **çıkarıldı**. §2.3, §2.6, §6, §7, §8, §9-Faz3/4 bu özellikleri anlatır ama **artık kodda yoklar**. Neyin neden çıkarıldığı ve nasıl geri getirileceği **§14'te**. Sideload (kişisel kullanım) için bu üçünü geri eklemek gerekebilir.

## 1. Amaç ve kapsam

Telefonda **gelen ve giden aramaları otomatik olarak** cihazın yerel depolamasına ses dosyası olarak kaydeden, kayıtları daha sonra listeleyip dinleyebileceğim, paylaşabileceğim ve silebileceğim **basit, tamamen çevrimdışı** bir Android uygulaması.

**Kapsam içi (MVP):**
- Arama başladığında otomatik kayıt, bittiğinde otomatik durdurma
- Kayıt meta verisi: yön (gelen/giden), başlangıç zamanı, süre (numara ve kişi adı bu sürümde yok — §14)
- Kayıt listesi (tarihe göre gruplu, gelen/giden filtresi, numaraya göre arama)
- Oynatma (oynat/duraklat/ileri-geri sarma), paylaşma, silme
- Durum/Ayarlar ekranı: izin kontrol listesi, servis durumu, otomatik kayıt aç/kapa, ses kaynağı seçimi

**Kapsam dışı (bilinçli olarak):**
- Backend, bulut yedekleme, hesap/giriş, analytics — **uygulama internete çıkmaz**, `INTERNET` izni eklenmez
- iOS (iOS arama kaydına izin vermez) — `ios/`, `Gemfile`, `.bundle/` projeden kaldırıldı
- Play Store dağıtımı (bkz. §2) — APK yan yükleme (sideload) ile kişisel kullanım

## 2. Android kısıtları — mimarinin nedeni (önce bunu oku)

Bu bölüm tasarımın tamamını belirler; "neden böyle yaptık" sorusunun cevabı buradadır.

1. **`AudioSource.VOICE_CALL` üçüncü taraf uygulamalarda çalışmaz.** Android 9+ ile hat sesini (karşı tarafın sesini doğrudan) yakalamak `CAPTURE_AUDIO_OUTPUT` gerektirir; bu izin yalnızca sistem uygulamalarına verilir. Bazı üretici ROM'larında / root'lu cihazlarda istisna olabilir, o yüzden **yalnızca fallback zincirinin ilk denemesi** olarak kalır.
2. **Pratikte kayıt mikrofondan yapılır.** Kullanılabilir kaynaklar: `VOICE_COMMUNICATION`, `VOICE_RECOGNITION`, `MIC`. Sonuç:
   - Kendi sesim her zaman net kaydedilir.
   - Karşı tarafın sesi, ahize/hoparlörden mikrofona sızdığı ölçüde kaydedilir; **hoparlör modunda** en iyi sonuç alınır. Kalite cihaza göre değişir.
   - **Kulaklık (kablolu/Bluetooth) takılıyken karşı tarafın sesi fiziksel olarak mikrofona ulaşmaz.** "Kulaklıktan bağımsız tam iki taraflı kayıt" root/sistem uygulaması olmadan **garanti edilemez**. Uygulama yine de kayıt alır (kendi sesim + varsa sızan ses) ve bu durumu kayıt meta verisinde `audioRoute` ile işaretler. Bunu kullanıcıdan saklama; Durum ekranında kısa bir açıklama göster.
3. **Android 10+ eşzamanlı mikrofon erişimi:** _(Bu sürümde erişilebilirlik servisi Play uyumu için çıkarıldı — §14. Aşağıdaki, sideload sürümünde geçerli olan tasarımdır.)_ Arama sırasında arka plandaki bir uygulama mikrofonu normalde alamaz. Çalışan bir **AccessibilityService**'i olan uygulamalar bu kuralda istisnadır (Cube ACR vb. bu yolu kullanır). Bu yüzden projede **olay işlemeyen, minimal bir AccessibilityService** bulunur; tek görevi süreci "kullanıcıya yardımcı servis" statüsünde tutmaktır. Kullanıcı bunu Ayarlar > Erişilebilirlik'ten elle açar. Bu servis Play Store politikalarıyla çelişir → sideload dağıtımın bir nedeni de budur.
4. **Ön plan servisi (Foreground Service) zorunludur.** Arka planda mikrofon erişimi için `foregroundServiceType="microphone"`. Android 11+ "while-in-use" kuralı gereği mikrofon tipli FGS **uygulama ön plandayken başlatılmalıdır** → servis kullanıcı uygulamayı açıp "Otomatik kayıt"ı etkinleştirdiğinde başlar ve kalıcı bildirimle sürekli çalışır (arama başına servis başlatılmaz).
5. **Yeniden başlatma sonrası:** `BOOT_COMPLETED` üzerinden mikrofon tipli FGS başlatmak yeni Android sürümlerinde kısıtlıdır. Strateji: boot'ta servisi başlatmayı dene; başarısız olursa "Kayıt için uygulamayı bir kez açın" bildirimi göster. Erişilebilirlik servisi aktifken davranışı **gerçek cihazda doğrula** (§10).
6. **Giden arama numarası:** _(Bu sürümde `READ_CALL_LOG` çıkarıldı — §14; numara hiç alınmıyor, meta.number = null.)_ Android 9+ ile numara `READ_CALL_LOG` olmadan gelmez. Yaklaşım: arama bitince (`IDLE`) `CallLog`'dan son kaydı oku, zaman damgasıyla eşleştir; numara/yön/süre buradan alınır. Kayıt sırasında numara bilinmiyorsa geçici dosya adı kullanılır, sonra meta veri güncellenir.
7. **Pil optimizasyonu:** Xiaomi/Samsung/Huawei gibi üreticiler servisi öldürebilir. Durum ekranında `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` için yönlendirme olur.

## 3. Teknoloji kararları

| Konu | Karar | Gerekçe |
|---|---|---|
| Framework | React Native CLI (bare), **TypeScript**, New Architecture açık | Native Kotlin modülü zorunlu; Expo Go'da çalışmaz, Expo eklemek ek katman |
| RN sürümü | Kurulum anındaki **güncel stabil** sürüm (`npx @react-native-community/cli@latest init`) | Sürümü bu dosyaya sabitleme; kurulumda kontrol et ve §11'e yaz |
| Native taraf | **Kotlin**, tek native modül: `CallRecorderModule` | Kayıt, oynatma, dosya işlemleri tek yerde; JS tarafı ince kalır |
| Kayıt | `MediaRecorder`, MPEG_4 / AAC, mono, 16 kHz (varsayılan; ayarlanabilir 44.1 kHz), 64 kbps → `.m4a` | Her cihazda var, ek bağımlılık yok, ses için yeterli ve küçük dosya |
| Arama durumu | API 31+: `TelephonyCallback.CallStateListener`; altı: `PhoneStateListener` | Resmi API'ler |
| Meta veri | Her kayıt için yan dosya: `<id>.json` (kayıt `<id>.m4a` yanında) | Tek kullanıcı, yüzlerce kayıt — veritabanı gereksiz. Liste yavaşlarsa Room'a geç (§9) |
| Oynatma | Native `MediaPlayer` (aynı modülde) | Ağır oynatıcı kütüphanelerine gerek yok |
| Paylaşma | `FileProvider` + `Intent.ACTION_SEND` (native) | Ek paket gerektirmez |
| Navigasyon | `@react-navigation/native` + `native-stack` | Standart, hafif |
| State | React state + basit hook'lar (`useRecordings`, `useServiceStatus`) | Redux/Zustand gereksiz |
| Depolama konumu | `context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)` (API 31+), altında `Music` fallback | Ek depolama izni gerekmez; uygulama silinince kayıtlar da silinir (Durum ekranında belirt) |

**Bağımlılık kuralı:** Yeni npm/Gradle paketi eklemeden önce neden gerekli olduğunu ve native ile neden çözülmediğini yaz. Varsayılan cevap "ekleme".

## 4. Proje yapısı

```
CallRecorder/
├── CLAUDE.md
├── App.tsx
├── src/
│   ├── native/
│   │   └── CallRecorder.ts        # NativeModules sarmalayıcı + tipler (tek giriş noktası)
│   ├── screens/
│   │   ├── RecordingsScreen.tsx   # liste, filtre, arama
│   │   ├── PlayerSheet.tsx        # oynatıcı (alt panel/modal)
│   │   └── StatusScreen.tsx       # izinler, servis, ayarlar
│   ├── hooks/
│   │   ├── useRecordings.ts
│   │   ├── usePlayer.ts
│   │   └── useServiceStatus.ts
│   ├── components/
│   │   └── DisclaimerModal.tsx    # ilk açılış yasal bilgilendirmesi (§13)
│   ├── utils/format.ts            # süre, tarih, numara biçimleme
│   └── types.ts
└── android/app/src/main/
    ├── AndroidManifest.xml
    ├── res/xml/
    │   ├── file_paths.xml               # FileProvider
    │   └── accessibility_service.xml
    └── java/com/callrecorder/recorder/   # paket: com.callrecorder.recorder
        ├── CallRecorderModule.kt        # RN köprüsü (@ReactMethod'lar + event emitter)
        ├── CallRecorderPackage.kt
        ├── CallMonitorService.kt        # Foreground service, arama durumu dinleyici
        ├── CallRecorder.kt              # MediaRecorder sarmalayıcı + audio source fallback
        ├── RecordingRepository.kt       # .m4a + .json okuma/yazma/silme
        ├── CallLogResolver.kt           # arama sonrası numara/yön/süre eşleştirme
        ├── ContactNameResolver.kt       # numara → rehberdeki ad (PhoneLookup)
        ├── RecordingPlayer.kt           # MediaPlayer sarmalayıcı (oynat/duraklat/atla, ilerleme olayları)
        ├── KeepAliveAccessibilityService.kt
        ├── BootReceiver.kt
        └── Prefs.kt                     # SharedPreferences (ayarlar)
```

## 5. Native modül sözleşmesi (JS ↔ Kotlin)

`src/native/CallRecorder.ts` dışında hiçbir yerde `NativeModules` doğrudan kullanılmaz.

```ts
type Direction = 'incoming' | 'outgoing' | 'unknown';
type AudioSourceName = 'VOICE_CALL' | 'VOICE_COMMUNICATION' | 'VOICE_RECOGNITION' | 'MIC';

interface Recording {
  id: string;               // yyyyMMdd_HHmmss_<rastgele4>
  filePath: string;
  number: string | null;
  name: string | null;      // rehberden, her listelemede okunur; JSON'a yazılmaz
  direction: Direction;
  startedAt: number;        // epoch ms
  durationMs: number;
  sizeBytes: number;
  audioSource: AudioSourceName;   // fiilen kullanılan kaynak
  audioRoute: 'earpiece' | 'speaker' | 'wired' | 'bluetooth' | 'unknown';
}

// Kayıtlar
listRecordings(): Promise<Recording[]>          // startedAt azalan
deleteRecording(id: string): Promise<void>
shareRecording(id: string): Promise<void>

// Servis
startMonitoring(): Promise<void>                 // uygulama ön plandayken çağrılır
stopMonitoring(): Promise<void>
getStatus(): Promise<{
  monitoring: boolean;
  accessibilityEnabled: boolean;
  ignoringBatteryOptimizations: boolean;
  permissions: Record<'RECORD_AUDIO' | 'READ_PHONE_STATE' | 'READ_CALL_LOG' | 'READ_CONTACTS' | 'POST_NOTIFICATIONS', 'granted' | 'denied'>;
}>
openAccessibilitySettings(): Promise<void>
openAppSettings(): Promise<void>                 // kalıcı reddedilen izinler, kısıtlı ayarlar
requestIgnoreBatteryOptimizations(): Promise<void>

// Ayarlar
getSettings(): Promise<{ autoRecord: boolean; preferredSource: AudioSourceName | 'AUTO'; sampleRate: 16000 | 44100; disclaimerAccepted: boolean }>
setSettings(s: Partial<...>): Promise<Settings>   // güncel ayarları döner

// Oynatma
play(id: string): Promise<void>; pause(): Promise<void>; seekTo(ms: number): Promise<void>; stop(): Promise<void>

// Event'ler (Kotlin: emitDeviceEvent, JS: DeviceEventEmitter — addCallRecorderListener)
'recordingSaved' (payload: Recording)
'playbackProgress' ({ id, positionMs, durationMs }) | 'playbackEnded' ({ id })
```

## 6. Kayıt akışı

1. `CallMonitorService` arama durumunu dinler.
2. `RINGING` → yön adayını `incoming` olarak işaretle (kayıt başlamaz).
3. `OFFHOOK` → `autoRecord` açıksa kaydı başlat. Önceki durum `RINGING` değilse yön `outgoing`.
4. Ses kaynağı **fallback zinciri** (`preferredSource = AUTO` ise): `VOICE_CALL → VOICE_COMMUNICATION → VOICE_RECOGNITION → MIC`. Her biri için `prepare()` + `start()` dene; exception'da sonrakine geç. Başarılı kaynağı meta veriye yaz. Not: bazı cihazlarda `VOICE_CALL` exception atmadan **sessiz dosya** üretir — ilk 2 sn `getMaxAmplitude()` sürekli 0 ise durdur ve sonraki kaynağa geç.
5. `IDLE` → kaydı durdur, `CallLogResolver` ile numara/yön/süreyi doldur (CallLog gecikmeli yazabilir → kısa retry: 3 deneme, 1 sn arayla), `.json` yaz, `recordingSaved` event'i gönder.
6. Hata durumları: yarım dosya kalırsa sil; süre < 1 sn ise kaydetme; hatayı logla.
7. Bekleme/çift arama: MVP'de aktif arama sürdüğü müddetçe **tek dosya** (ikinci arama ayrı kaydedilmez). Bunu bilinen sınırlama olarak not et.

## 7. İzinler (AndroidManifest)

- `RECORD_AUDIO`, `READ_PHONE_STATE`
- ~~`READ_CALL_LOG`~~, ~~`READ_CONTACTS`~~ → Play uyumu için çıkarıldı (§14)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` (API 34+)
- `POST_NOTIFICATIONS` (API 33+)
- `RECEIVE_BOOT_COMPLETED`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `<service android:foregroundServiceType="microphone">`
- ~~AccessibilityService~~ → Play uyumu için çıkarıldı (§14).
- **`INTERNET` ana manifest'te yok.** Yalnızca debug'da Metro bağlantısı için `android/app/src/debug/AndroidManifest.xml`'de tanımlı; release APK'da bulunmaz.

İzin isteme sırası (Durum ekranında adım adım): Mikrofon → Telefon durumu → Bildirim → Pil optimizasyonu. (Arama kaydı, Kişiler ve Erişilebilirlik bu sürümde yok — §14.)

## 8. UI

- Sade, tek tema (sistem açık/koyu uyumlu), Türkçe metinler (`src/strings.ts` içinde tek yerde).
- **Kayıtlar ekranı:** Bugün / Dün / tarih başlıkları; satırda yön ikonu, numara, saat, süre. Üstte filtre (Tümü / Gelen / Giden) ve numara arama kutusu. Satıra dokun → oynatıcı paneli; uzun bas → paylaş/sil (silmede onay).
- **Durum ekranı:** Yeşil/kırmızı kontrol listesi (izinler, servis, erişilebilirlik, pil), "Otomatik kayıt" anahtarı, ses kaynağı seçimi, kalite seçimi, toplam kayıt sayısı/boyutu, §2 kısıtlarının 3-4 cümlelik özeti.
- Boş durum: "Henüz kayıt yok. Durum ekranındaki adımları tamamlayın."

## 9. Fazlar

1. **Faz 0 — İskelet:** RN projesi (TS), navigasyon, boş ekranlar, native modül iskeleti ve `ping()` ile köprü testi. Debug APK cihazda açılıyor. ✅
2. **Faz 1 — Kayıt çekirdeği:**
   - **1a:** Manifest izinleri, `CallMonitorService` (ön plan servisi + arama durumu logları), `startMonitoring`/`stopMonitoring`/`isMonitoring` (geçici) ve Durum ekranında geçici aç/kapa anahtarı. Kayıt yok. ✅
   - **1b:** `CallRecorder.kt` (fallback zinciri), `RecordingRepository.kt`, `CallLogResolver.kt`; dosya + JSON yazma. Logcat ve `adb shell ls` ile doğrulama. ✅
     - JSON'da `filePath` yerine `fileName` tutulur; tam yol listelemede (Faz 2) `RecordingRepository.dir` ile üretilir.
     - `audioRoute` kayıt başladığı anki değerdir; arama içinde değişirse yansımaz.
     - Servis çalışırken her arama kaydedilir; `autoRecord` ayarı Faz 3'te bağlanır.
   - Faz 1'de izinler `adb shell pm grant` ile verilir; izin ekranı Faz 3'te.
3. **Faz 2 — Liste & oynatma:** `listRecordings`, oynatıcı, sil, paylaş. ✅
   - Oynatıcı `RecordingPlayer.kt`'de (MediaPlayer, işlemler ana thread'de). Kaydırma çubuğu için paket eklenmedi: dokunarak konuma atlama + ±10 sn düğmeleri.
   - Olaylar Kotlin'de `emitDeviceEvent`, JS'te `DeviceEventEmitter` ile (`addCallRecorderListener`). Servis → modül aktarımı `RecordingEvents` nesnesiyle.
   - Filtre (gelen/giden) ve numara arama Faz 4'te.
4. **Faz 3 — İzinler & Durum ekranı:** izin akışı, erişilebilirlik, pil, boot davranışı. ✅
   - Çalışma zamanı izinleri RN'in yerleşik `PermissionsAndroid` ile istenir (paket yok). Native `getStatus` yalnızca granted/denied döner; "kalıcı reddedildi" JS'te istek sonucundan anlaşılır ve `openAppSettings` ile uygulama ayarlarına yönlendirilir.
   - `isMonitoring` kaldırıldı, yerini `getStatus().monitoring` aldı. `Prefs.monitoringEnabled` kullanıcının izlemeyi açık bırakıp bırakmadığını tutar.
   - Açılışta servis iki yoldan geri getirilmeye çalışılır: `BootReceiver` ve erişilebilirlik servisinin `onServiceConnected`'ı. İkisi de başarısızsa `StartupNotice` bildirimi gösterilir; servis başlayınca bildirim kaldırılır.
   - `preferredSource` AUTO değilse yalnızca o kaynak denenir (sessiz de olsa). Ayarlar bir sonraki aramadan itibaren geçerlidir.
   - Android 13+'da yan yüklenen uygulamalarda erişilebilirlik anahtarı "Kısıtlı ayarlar" yüzünden gri olabilir; Durum ekranı nasıl açılacağını söyler.
   - Ertelenenler: `src/strings.ts` ve ilk açılış yasal bilgilendirmesi Faz 4'e alındı (bkz. Faz 4).
5. **Faz 4 — Cila:**
   - **4a:** Kayıtlar ekranında yön filtresi (Tümü/Gelen/Giden) + numara araması (rakamlar boşluk/+/tire farkı gözetmeden eşleşir); ilk açılış yasal bilgilendirmesi (`DisclaimerModal`, onay `Prefs.disclaimerAccepted`'da); `INTERNET` izni `src/debug/AndroidManifest.xml`'e taşındı; release APK kendi anahtarıyla imzalanır.
   - `recordingStarted`/`recordingFailed` olayları sözleşmeden çıkarıldı: arayüz kullanmıyor, hatalar logcat'te.
   - **4b:** Kişi adları. `ContactNameResolver` her listelemede `PhoneLookup` ile adı bulur (ad JSON'a yazılmaz; rehber değişiklikleri eski kayıtlara yansır). Satırda üstte ad (yoksa "Bilinmeyen kişi", italik), altta numara · saat. Arama isimde de çalışır; klavye artık yalnızca rakam değil.
   - Açık (karar bekliyor): otomatik silme (X günden eski), metinlerin `src/strings.ts`'e toplanması.
6. **Faz 5 — Play uyumu (sade sürüm):** Erişilebilirlik servisi, `READ_CALL_LOG`, `READ_CONTACTS`/kişi adları çıkarıldı. Ayrıntı ve geri getirme §14. ✅

Her faz sonunda: gerçek cihazda §10 kontrol listesinin ilgili maddeleri.

## 10. Test (gerçek cihaz zorunlu)

Emülatör arama sesini gerçekçi simüle etmez; kayıt testleri **fiziksel cihazda** yapılır.

Kontrol listesi (her cihaz/Android sürümü için `docs/device-matrix.md`'ye yaz):
- [ ] Gelen arama — ahize / hoparlör / kablolu kulaklık / Bluetooth → dosya oluştu mu, iki taraf duyuluyor mu, hangi `audioSource` seçildi
- [ ] Giden arama — aynı 4 senaryo
- [ ] Cevapsız/reddedilen arama → kayıt oluşmamalı
- [ ] Erişilebilirlik kapalıyken davranış (Android 10+'da sessiz/boş kayıt beklenir — doğrula)
- [ ] Uygulama kapatıldıktan (recent'tan kaydırma) sonra kayıt devam ediyor mu
- [ ] Yeniden başlatma sonrası
- [ ] 30+ dk uzun arama, düşük depolama alanı

Birim testleri: JS tarafında `utils/` ve hook'lar için Jest; Kotlin tarafında `RecordingRepository` ve `CallLogResolver` eşleştirme mantığı için JUnit. UI testleri MVP'de yok.

## 11. Komutlar

> Geliştirme ortamı: **Windows + PowerShell**. Gradle komutları `.\gradlew` ile çalıştırılır.
> **`npx expo ...` komutu kesinlikle çalıştırılmaz ve `expo` paketi eklenmez** — bare RN projesinde autolinking `:expo` projesini build'e ekler ve build kırılır.

```bash
npm install
npx react-native start                 # Metro
npx react-native run-android           # debug, bağlı cihaza
cd android && ./gradlew assembleRelease -PreactNativeArchitectures=arm64-v8a   # telefona kurulacak release APK (yalnızca arm64) → android/app/build/outputs/apk/release/
npx react-native run-android --active-arch-only   # debug: yalnızca bağlı cihazın/emülatörün ABI'si derlenir
adb logcat -s CallRecorder:*           # native loglar (TAG = "CallRecorder")
npm test
npx tsc --noEmit && npx eslint .
```

İmzalama: release anahtarı `android/app/callrecorder-release.keystore` (git'e girmez, `.gitignore`'daki `*.keystore` kuralı). Şifreler repo'da değil `%USERPROFILE%\.gradle\gradle.properties` içinde (`CALLRECORDER_STORE_PASSWORD`, `CALLRECORDER_KEY_PASSWORD`). Anahtar kaybolursa güncelleme yüklenemez; uygulama silinip yeniden kurulmalı (kayıtlar silinir) → anahtarı yedekle.

Boyut: release'te R8 (`enableProguardInReleaseBuilds = true`) + `shrinkResources` açık; `gradle.properties` içinde `reactNativeArchitectures=arm64-v8a,x86_64` (telefon + emülatör). Debug APK boyutu önemsizdir, boyut her zaman release APK üzerinden ölçülür.

Sürümler (kurulumdaki varsayılanlar): RN `____` (package.json'dan doldur), minSdk `24`, compileSdk `37`, targetSdk `36`, Kotlin `2.2.0`, Gradle `9.4.1`.

## 12. Kodlama kuralları

- TypeScript `strict`. `any` yok; native dönüşleri `src/types.ts`'teki tiplere map'le.
- Kotlin: her public işlem `try/catch` ile sarılır, hata `Promise.reject(code, message)` ile JS'e döner; hata kodları sabit (`E_PERMISSION`, `E_SERVICE`, `E_RECORDER`, `E_IO`, `E_NOT_FOUND`, `E_INVALID`, `E_PLAYER`).
- Log TAG'i her yerde `CallRecorder`. Kişisel veri (numara) yalnızca debug build'de loglanır.
- Değişiklikler **küçük ve hedefli**: istenmeyen refactor, yeniden adlandırma, format değişikliği yapma. Bir dosyaya dokunuyorsan nedenini commit mesajında yaz.
- Yeni bir mimari karar alındığında bu dosyanın ilgili bölümünü güncelle.

## 13. Yasal not

Türkiye'de görüşme kaydı KVKK ve TCK kapsamında değerlendirilebilir; kayıtlar yalnızca kişisel kullanım içindir ve karşı tarafı bilgilendirmek kullanıcının sorumluluğundadır. Uygulama ilk açılışta bunu tek ekranlık bir bilgilendirme ile gösterir (bir kez, onaylanınca tekrar gösterilmez).

## 14. Play uyumu için çıkarılanlar (go-live notu)

Bu bölüm, uygulamanın Google Play politikalarına uyması için tek sürümden **tamamen çıkarılan** üç özelliği ve bunların nasıl geri getirileceğini anlatır. Üçü de Play tarafından reddedilme nedeniydi (bkz. aşağıdaki gerekçeler).

**Çıkarılanlar ve neden:**
1. **Erişilebilirlik servisi** (`KeepAliveAccessibilityService.kt`, manifest `<service>`, `res/xml/accessibility_service.xml`, `strings.xml`'deki `accessibility_label`/`accessibility_description`, Durum ekranındaki satır). → Play, erişilebilirlik API'sinin arama kaydı için kullanılmasını açıkça yasaklıyor.
2. **`READ_CALL_LOG`** (`CallLogResolver.kt`, manifest izni, servis içinde numara/yön eşleştirme). → Kısıtlı izin; yalnızca varsayılan arayıcı uygulamalara verilir.
3. **Kişi adları / `READ_CONTACTS`** (`ContactNameResolver.kt`, manifest izni). → 2 çıkınca numara hep null olduğundan isim de bulunamaz; kullanılmayan hassas izin reviewer için risk. Bu yüzden 2 ile birlikte çıkarıldı.

**Davranışa etkisi (sade sürüm):**
- Android 10+ cihazlarda arama sırasında mikrofon susturulabilir → **kayıt sessiz çıkabilir**. Root dışı çözüm yok; bu, erişilebilirlik servisinin çıkarılmasının bedeli.
- `meta.number` her zaman `null`, `name` her zaman `null`. Yön (`direction`) hâlâ RINGING/OFFHOOK sırasından belirlenir. Liste/oynatıcıda "Bilinmeyen kişi" / "Bilinmeyen numara" görünür.
- `Recording` sözleşmesindeki `number`/`name` alanları **korundu** (hep null); geri getirme kolay olsun diye UI'a dokunulmadı.

**Geri getirme (sideload / kişisel kullanım için):** En temizi git geçmişinden "Faz 4b" commit'ini referans almak. Elle yapılacaksa üçü birbirine bağlı, şu sırayla:
1. `READ_CALL_LOG`: `CallLogResolver.kt`'yi geri ekle, manifest'e izni ekle, `CallMonitorService.stopRecording`'de `callLogResolver.resolve(...)` ile numara/yönü doldur (git'teki eski sürüm), `STATUS_PERMISSIONS` ve `PERMISSION_ROWS`'a `READ_CALL_LOG` ekle, `PermissionKey`'e geri koy.
2. `READ_CONTACTS` + isim: `ContactNameResolver.kt`'yi geri ekle, manifest izni, `toMap`'te `names.nameFor(...)` ile `name` doldur, listRecordings/savedListener'da `ContactNameResolver` örneğini geç, izin satırı + `PermissionKey`.
3. Erişilebilirlik: `KeepAliveAccessibilityService.kt` + `res/xml/accessibility_service.xml` + `strings.xml` metinleri + manifest `<service>` geri; `getStatus`'a `accessibilityEnabled`, modüle `openAccessibilitySettings`, `ServiceStatus`'a alan, Durum ekranına CheckRow + "Kısıtlı ayarlar" notu geri.

**Play yayını için kalan adımlar (sade sürümle):** `assembleRelease` yerine **AAB**: `.\gradlew bundleRelease` → `android/app/build/outputs/bundle/release/app-release.aab`. Play Console (25 USD, tek sefer), Play App Signing, store listesi, veri güvenliği formu, içerik derecelendirmesi, mikrofon ön plan servisi türü için beyan+gerekçe. `RECORD_AUDIO` + mikrofon FGS gerekçeli olarak kabul edilir.