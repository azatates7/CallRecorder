import { NativeModules, PermissionsAndroid, Platform } from 'react-native';

const { CallRecorderModule } = NativeModules;

export type Recording = {
    uri: string;
    name: string;
    dateAdded: number; // unix seconds
    size: number; // bytes
};

/**
 * Maps our required Android permission strings to PermissionsAndroid's constant equivalents so
 * we get the OS permission dialog rather than just checking status.
 */
const PERMISSION_MAP: Record<string, string> = {
    'android.permission.RECORD_AUDIO': PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
    'android.permission.READ_PHONE_STATE': PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
    'android.permission.WRITE_EXTERNAL_STORAGE':
        PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
    'android.permission.POST_NOTIFICATIONS':
        (PermissionsAndroid.PERMISSIONS as any).POST_NOTIFICATIONS,
};

async function requestMissingPermissions(): Promise<boolean> {
    if (Platform.OS !== 'android') return false;

    const missing: string[] = await CallRecorderModule.getMissingPermissions();
    if (missing.length === 0) return true;

    const mapped = missing.map(p => PERMISSION_MAP[p]).filter(Boolean);
    const results = await PermissionsAndroid.requestMultiple(mapped as any);
    return Object.values(results).every(
        status => status === PermissionsAndroid.RESULTS.GRANTED,
    );
}

async function setEnabled(enabled: boolean): Promise<boolean> {
    if (enabled) {
        const granted = await requestMissingPermissions();
        if (!granted) {
            throw new Error(
                'Gerekli izinler verilmeden çağrı kaydı başlatılamaz (mikrofon, telefon durumu, bildirim).',
            );
        }
    }
    return CallRecorderModule.setEnabled(enabled);
}

async function isEnabled(): Promise<boolean> {
    return CallRecorderModule.isEnabled();
}

async function listRecordings(): Promise<Recording[]> {
    return CallRecorderModule.listRecordings();
}

export const CallRecorder = {
    setEnabled,
    isEnabled,
    listRecordings,
    requestMissingPermissions,
};