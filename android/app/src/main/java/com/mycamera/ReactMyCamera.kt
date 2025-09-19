package com.mycamera

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.uimanager.ThemedReactContext
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import androidx.camera.core.ImageProxy

class ReactMyCamera(context:ThemedReactContext) :ConstraintLayout(context),
   ObjectDetectorHelper.DetectorListener,LifecycleEventListener{
    private val  currentContext: ThemedReactContext = context

    private var viewFinder = PreviewView(context)
    private var overlayView : OverlayView = OverlayView(context, null)

    private var backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var imageAnalyzer:ImageAnalysis? = null
    private var preview:Preview? = null
    private var camera :Camera? = null
    private var cameraProvider:ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK

    private lateinit var objectDetectorHelper:ObjectDetectorHelper

    private var delegate: Int = ObjectDetectorHelper.DELEGATE_CPU
    private var threshold:Float =
         ObjectDetectorHelper.THRESHOLD_DEFAULT
    private var maxResults :Int =   
         ObjectDetectorHelper.MAX_RESULTS_DEFAULT
    private var model: Int  = ObjectDetectorHelper.MODEL_EFFICIENTDETV0    


    private fun getActivity():Activity{
        return currentContext.currentActivity!!
    }  


    init {
        context.addLifecycleEventListener(this)
        var constraintSet = ConstraintSet()
        constraintSet.clone(this)

        viewFinder.id = View.generateViewId()
        installHierarchyFitter(viewFinder)
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        addView(viewFinder)
        constraintSet.constraintWidth(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constraintHeight(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
        )
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
        )
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
        )
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
        )

        overlayView.id = View.generateViewId()
        addView(overlayView)
        constraintSet.constraintWidth(overlayView.id,ConstraintLayout.MATCH_CONSTRAINT)
        constraintSet.constraintHeight(overlayView.id,ConstraintLayout.MATCH_CONSTRAINT)
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
        )
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
        )
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
        )
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
        )
        bringChildToFront(overlayView)

        constraintSet.applyTo(this)
        overlayView.setRunningMode(RunningMode.LIVE_STREAM)

        backgroundExecutor.execute{
            objectDetectorHelper =
                ObjectDetectorHelper(
                    context = context,
                    threshold = threshold,
                    currentDelegate = delegate,
                    currentModel = model,
                    maxResults = maxResults,
                    objectDetectorListener = this,
                    runningMode = RunningMode.LIVE_STREAM
            
            )

            viewFinder.post{
                setUpCamera()
            }


            
        }

    }

    private fun installHierarchyFitter(view:ViewGroup){
        if (context is ThemedReactContext){
            view.setOnHierarchyChangeListener(object:OnHierarchyChangeListener{
                override fun onChildViewRemoved(parent: View?,child:View?)= Unit
                override fun onChildViewAdded(parent:View?, child:View?){
                    parent?.measure(
                        MeasureSpec.makeMeasureSpec(measuredWidth,MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(measuredHeight,MeasureSpec.EXACTLY)
                    )
                    parent?.layout(0,0, parent.measuredWidth, parent.measuredHeight)
                }
            })
        }
    }

    private fun  setUpCamera(){
        val cameraProviderFuture =
        ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()

                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun bindCameraUseCases(){
        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_NONE
        )
        val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(aspectRatioStrategy)
        .build()

        val cameraProvider =
        cameraProvider?:throw IllegalStateException("camera Initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        preview = 
        Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .setTargetRotation(viewFinder.display.rotation)
        .build()
        

        imageAnalyzer = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setTargetRotation(viewFinder.display.rotation)
        .setBackPressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also{
            it.setAnalyzer(
                backgroundExecutor,
                objectDetectorHelper::detectLivestreamFrame
            )
        }

        cameraProvider.unbindAll()

        try{
            camera = cameraProvider.bindToLifecycle(
                getActivity() as AppCompatActivity,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.surfaceProvider = viewFinder.surfaceProvider
        } catch(exc:Exception){
            Log.e("TAG" , "Use case binding failed" , exc)
        }

    }
    override fun onError(error:String , errorCode: Int){
        Log.i("TAG",error)
    }

    override fun  onResults(resultBundle:ObjectDetectorHelper.ResultBundle){
        val detectionResult = resultBundle.results[0]
        overlayView.setResults(
            detectionResult,
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth,
            resultBundle.inputImageRotation
        )
        overlayView.invalidate()
    }

    override fun onHostResume(){
        backgroundExecutor.execute{
            if(objectDetectorHelper.isClosed()){
                objectDetectorHelper.setObjectDetector()
            }
        }
    }

    override fun onHostPause(){
        if(this::objectDetectorHelper.isInitialized){
            backgroundExecutor.execute{
                objectDetectorHelper.clearObjectDetector()}
        }
    }
    
    override fun onHostDestroy(){
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE,
            TimeUnit.NANOSECONDS
        )
    }

   }
