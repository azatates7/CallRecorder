import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

export default function RecordingsScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.empty}>
        Henüz kayıt yok. Durum ekranındaki adımları tamamlayın.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  empty: { fontSize: 16, textAlign: 'center', opacity: 0.7 },
});