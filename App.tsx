import React from 'react';
import { Button } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import RecordingsScreen from './src/screens/RecordingsScreen';
import StatusScreen from './src/screens/StatusScreen';

export type RootStackParamList = {
  Recordings: undefined;
  Status: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
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
  );
}