import { useEffect, useState } from 'react';
import {
  PermissionsAndroid,
  Platform,
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';
import MyCamera from './specs/MyCameraNativeComponent';

function App() {
  const isDarkMode = useColorScheme() === 'dark';
  const [isCameraPermissionGranted, setIsCameraPermissionGranted] =
    useState(false);

  async function requestCameraAndStoragePermissions() {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.CAMERA,
        ]);
        const cameraGranted =
          granted[PermissionsAndroid.PERMISSIONS.CAMERA] ===
          PermissionsAndroid.RESULTS.GRANTED;
        if (cameraGranted) {
          setIsCameraPermissionGranted(true);
        } else {
          console.log('One or more permissions denied');
          return false;
        }
      } catch (err) {
        console.warn(err);
        return false;
      }
    }
  }
  useEffect(() => {
    requestCameraAndStoragePermissions();
  }, []);

  return (
    <View style={styles.container}>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      {isCameraPermissionGranted ? (
        <MyCamera style={styles.cameraView} />
      ) : (
        <Text>Camera Permission not granted</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    alignContent: 'center',
  },
  cameraView: {
    width: '100%',
    height: '100%',
  },
});
export default App;
