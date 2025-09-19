package com.mycamera

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import androidx.core.graphics.createBitmap

class ObjectDetectorHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTDETV0,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    var objectDetectorListener: DetectorListener? = null
) {
    private var objectDetector: ObjectDetector? = null
    private var imageRotation = 0
    private lateinit var imageProcessingOptions: ImageProcessingOptions

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector?.close()
        objectDetector = null
    }

    fun setupObjectDetector() {
        val baseOptionsBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName = when (currentModel) {
            MODEL_EFFICIENTDETV0 -> "efficientdet_lite0.tflite"
            MODEL_EFFICIENTDETV2 -> "efficientdet_lite2.tflite"
            else -> "efficientdet_lite0.tflite"
        }

        baseOptionsBuilder.setModelAssetPath(modelName)

        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (objectDetectorListener == null) {
                    throw IllegalStateException(
                        "objectDetectorListener must be set when runningmode is LIVE_STREAM."
                    )
                }
            }

            RunningMode.IMAGE, RunningMode.VIDEO -> {
                // no-op
            }
        }

        try {
            val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setScoreThreshold(threshold).setRunningMode(runningMode)
                .setMaxResults(maxResults)

            imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageRotation).build()

            when (runningMode) {
                RunningMode.IMAGE, RunningMode.VIDEO -> optionsBuilder.setRunningMode(
                    runningMode
                )

                RunningMode.LIVE_STREAM -> optionsBuilder.setRunningMode(
                    runningMode
                ).setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e(TAG, "Tflite failed to load model with error:" + e.message)
        } catch (e: RuntimeException) {
            objectDetectorListener?.onError(
                "object detector failer to initialize. see  error logs for details",
                GPU_ERROR
            )
            Log.e(
                TAG,
                "Object detector failed  to load model with error: " + e.message
            )
        }
    }

    fun isClosed(): Boolean {
        return objectDetector == null
    }

    fun detectVideoFile(
        videoUri: Uri, inferenceIntervalMs: Long
    ): ResultBundle? {

        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
            )
        }

        if (objectDetector == null) return null

        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()

        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        val resultList = mutableListOf<ObjectDetectorResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever.getFrameAtTime(
                timestampMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST
            )?.let { frame ->
                val argb8888Frame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)

                val mpImage = BitmapImageBuilder(argb8888Frame).build()
                objectDetector?.detectForVideo(mpImage, timestampMs)
                    ?.let { detectionResult ->
                        resultList.add(detectionResult)
                    } ?: run {
                    didErrorOccurred = true
                    objectDetectorListener?.onError(
                        "ResultBundle could not be returned in detectVideoFile"
                    )
                }
            } ?: run {
                didErrorOccurred = true
                objectDetectorListener?.onError(
                    "Frame at specified time could not be retrieved when detecting in video."
                )
            }
        }
        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    fun detectLivestreamFrame(imageProxy: ImageProxy) {

        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLivestreamFrame while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        if (imageProxy.imageInfo.rotationDegrees != imageRotation) {
            imageRotation = imageProxy.imageInfo.rotationDegrees
            clearObjectDetector()
            setupObjectDetector()
            return
        }

        val mpImage = BitmapImageBuilder(bitmapBuffer).build()

        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        objectDetector?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    private fun returnLivestreamResult(
        result: ObjectDetectorResult, input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        objectDetectorListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width,
                imageRotation,
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        objectDetectorListener?.onError(
            error.message ?: "An Unknown error has  occurred"
        )
    }

    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attemptimg to call detectImage while not using RunningMode.IMAGE"
            )
        }

        if (objectDetector == null) return null
        val startTime = SystemClock.uptimeMillis()

        val mpImage = BitmapImageBuilder(image).build()
        objectDetector?.detect(mpImage)?.also { detectionResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                listOf(detectionResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        return null
    }

    data class ResultBundle(
        val results: List<ObjectDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inputImageRotation: Int = 0
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTDETV0 = 0
        const val MODEL_EFFICIENTDETV2 = 1
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5f
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val TAG = "ObjectDetectorHelper"
    }

    interface DetectorListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
