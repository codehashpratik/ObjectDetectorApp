package com.camera

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.CustomCameraManagerDelegate
import com.facebook.react.viewmanagers.CustomCameraManagerInterface

@ReactModule(name = ReactCameraManager.REACT_CLASS)
class ReactCameraManager(context: ReactApplicationContext) : SimpleViewManager<ReactMyCamera>(),
  CustomCameraManagerInterface<ReactMyCamera> {

  private val delegate: CustomCameraManagerDelegate<ReactMyCamera, ReactCameraManager> =
    CustomCameraManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<ReactMyCamera> = delegate

  override fun getName(): String = REACT_CLASS

  override fun createViewInstance(context: ThemedReactContext): ReactMyCamera = ReactMyCamera(context)

  companion object {
    const val REACT_CLASS = "CustomMyCamera"
  }
}