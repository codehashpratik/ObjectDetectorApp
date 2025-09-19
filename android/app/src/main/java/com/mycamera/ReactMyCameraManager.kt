package com.mycamera

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.CustomMyCameraManagerDelegate
import com.facebook.react.viewmanagers.CustomMyCameraManagerInterface

@ReactModule(name = ReactMyCameraManager.REACT_CLASS)
class ReactMyCameraManager(context: ReactApplicationContext) : SimpleViewManager<ReactMyCamera>(),
  CustomMyCameraManagerInterface<ReactMyCamera> {

  private val delegate: CustomMyCameraManagerDelegate<ReactMyCamera, ReactMyCameraManager> =
    CustomMyCameraManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<ReactMyCamera> = delegate

  override fun getName(): String = REACT_CLASS

  override fun createViewInstance(context: ThemedReactContext): ReactMyCamera = ReactMyCamera(context)

  companion object {
    const val REACT_CLASS = "CustomMyCamera"
  }
}