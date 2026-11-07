import React, { useEffect, useState } from 'react';
import { Button } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import DisclaimerModal from './src/components/DisclaimerModal';
import { CallRecorder } from './src/native/CallRecorder';
import RecordingsScreen from './src/screens/RecordingsScreen';
import StatusScreen from './src/screens/StatusScreen';

export type RootStackParamList = {
  Recordings: undefined;
  Status: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  // null: henüz okunmadı. Okuma hata verirse uygulamayı kilitlememek için bilgilendirme gösterilmez.
  const [disclaimerAccepted, setDisclaimerAccepted] = useState<boolean | null>(null);

  useEffect(() => {
    CallRecorder.getSettings()
      .then(s => setDisclaimerAccepted(s.disclaimerAccepted))
      .catch(() => setDisclaimerAccepted(true));
  }, []);

  const acceptDisclaimer = () => {
    setDisclaimerAccepted(true);
    CallRecorder.setSettings({ disclaimerAccepted: true }).catch(() => {});
  };

  return (
    <>
      <NavigationContainer>
        <Stack.Navigator initialRouteName="Recordings">
          <Stack.Screen
            name="Recordings"
            component={RecordingsScreen}
            options={({ navigation }) => ({
              title: 'Kayıtlar',
              headerRight: () => (
                <Button title="Durum" onPress={() => navigation.navigate('Status')} />
              ),
            })}
          />
          <Stack.Screen name="Status" component={StatusScreen} options={{ title: 'Durum' }} />
        </Stack.Navigator>
      </NavigationContainer>
      <DisclaimerModal visible={disclaimerAccepted === false} onAccept={acceptDisclaimer} />
    </>
  );
}