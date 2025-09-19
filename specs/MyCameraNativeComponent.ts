import {
 
  type HostComponent,
  type ViewProps,
  codegenNativeComponent,
} from 'react-native';


export interface NativeProps extends ViewProps {}

export default codegenNativeComponent<NativeProps>(
  'CustomMyCamera',
) as HostComponent<NativeProps>;