import React from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

interface Props {
  visible: boolean;
  onAccept: () => void;
}

/** İlk açılışta bir kez gösterilen yasal bilgilendirme (CLAUDE.md §13). Geri tuşuyla kapanmaz. */
export default function DisclaimerModal({ visible, onAccept }: Props) {
  return (
    <Modal visible={visible} animationType="fade" onRequestClose={() => {}}>
      <View style={styles.container}>
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.title}>Başlamadan önce</Text>
          <Text style={styles.body}>
            Bu uygulama telefon görüşmelerinizi yalnızca bu telefona kaydeder. Kayıtlar hiçbir yere
            gönderilmez; uygulama internete bağlanmaz.
          </Text>
          <Text style={styles.body}>
            Türkiye'de görüşmenin karşı tarafın bilgisi olmadan kaydedilmesi KVKK ve Türk Ceza Kanunu
            kapsamında suç oluşturabilir. Kayıtları yalnızca kişisel amaçla kullanın ve görüşme
            yaptığınız kişiyi kayıt yapıldığı konusunda bilgilendirin. Bu sorumluluk size aittir.
          </Text>
          <Text style={styles.body}>
            Android, uygulamaların hat sesini doğrudan kaydetmesine izin vermez; kayıt mikrofondan
            alınır. Karşı tarafın sesi en iyi hoparlör açıkken kaydedilir.
          </Text>
        </ScrollView>
        <Pressable style={styles.button} onPress={onAccept}>
          <Text style={styles.buttonText}>Anladım</Text>
        </Pressable>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', padding: 24 },
  content: { paddingTop: 24 },
  title: { fontSize: 24, fontWeight: '700', color: '#111', marginBottom: 16 },
  body: { fontSize: 16, lineHeight: 24, color: '#333', marginBottom: 16 },
  button: { backgroundColor: '#1976d2', borderRadius: 8, paddingVertical: 14, alignItems: 'center' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
});