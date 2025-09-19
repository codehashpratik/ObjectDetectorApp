package com.mycamera

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

class ReactMyCameraPackage  : BaseReactPackage() {

  override fun createViewManagers(
    reactContext: ReactApplicationContext
  ): List<ViewManager<*, *>> {
    return listOf(ReactMyCameraManager(reactContext))
  }

  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return when (name) {
      ReactMyCameraManager.REACT_CLASS -> ReactMyCameraManager(reactContext)
      else -> null
    }
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider{
    mapOf(ReactMyCameraManager.REACT_CLASS to ReactModuleInfo(
        ReactMyCameraManager.REACT_CLASS,
        ReactMyCameraManager.REACT_CLASS,
        canOverrideExistingModule = false,
        needsEagerInit = false,
        isCxxModule= false,
        isTurboModule = true,

    )
    )
  }
}
