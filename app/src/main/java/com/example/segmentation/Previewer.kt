package com.example.segmentation

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.util.Log
import android.view.TextureView
import com.example.segmentation.views.PreviewerView
import android.hardware.camera2.CameraAccessException
import android.media.ImageReader
import android.os.HandlerThread
import android.media.Image.Plane
import android.widget.ImageView
import android.graphics.*
import android.graphics.Bitmap
import com.example.segmentation.utils.convertYUV420ToARGB8888


class Previewer(private val context: Context, private val model: Model,
                private val view: PreviewerView):
    TextureView.SurfaceTextureListener {

    companion object {
        const val TAG = "Previewer"

        const val MODE = CameraCharacteristics.LENS_FACING_BACK
    }

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var reader: ImageReader

    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    private var data: Array<ByteArray?> = Array(3) { null }

    private var camera: CameraDevice? = null
    private var previewSession: CameraCaptureSession? = null

    init {
        view.surfaceTextureListener = this
    }

    fun clear() {
        camera?.close()
        previewSession?.close()
    }

    fun onPreviewerStart() {
        handlerThread = HandlerThread(MainActivity.THREAD_PROCESSING, HandlerThread.MAX_PRIORITY).also {
            it.start()
        }
        handler = Handler(handlerThread.looper)
        handler.post {
            onPreviewProcess()

            handler.post(previewProcessRunnable)
        }
    }

    fun onPreviewerStop() {
        clear()
        if (handlerThread.quitSafely()) {
            handlerThread.join()

            handlerThread.quit()
        }
    }

    fun onCameraInit() {
        if (view.surfaceTexture != null) {
            Camera(context).init(handler, stateCallback, MODE)
        }
    }

    private val captureCallbacks = object: CameraCaptureSession.CaptureCallback() {}

    private fun onPreviewStart() {
        val builder = camera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: return

        reader = ImageReader.newInstance(480, 480, ImageFormat.YUV_420_888, 1).apply {
            setOnImageAvailableListener({
                val image = it.acquireLatestImage()

                val planes = image.planes
                onFillBytes(planes)

                val yRowStride = planes[0].rowStride
                val uvRowStride = planes[1].rowStride
                val uvPixelStride = planes[1].pixelStride

                val output = IntArray(image.width * image.height)
                convertYUV420ToARGB8888(data, image.width, image.height, yRowStride, uvRowStride, uvPixelStride, output)

                val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(output, 0, image.width, 0, 0, image.width, image.height)

                val result = model.inferModel(bitmap)
                (context as MainActivity).runOnUiThread {
                    context.findViewById<ImageView>(R.id.image_view).setImageBitmap(result)
                }

                image.close()
            }, handler)
        }
        builder.addTarget(reader.surface)

        camera?.createCaptureSession(mutableListOf(reader.surface), object: CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {
                if (camera == null) {
                    return
                }

                previewSession = session
                try {
                    // Continuous Auto focus adaptable for content stream.
                    builder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )

                    // Start displaying the camera preview.
                    val request = builder.build()
                    session.setRepeatingRequest(request, captureCallbacks, handler)
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "Failed to set up config to capture Camera", e)
                }
            }

            override fun onConfigureFailed(p0: CameraCaptureSession) {}
        }, null)
    }

    private fun onFillBytes(planes: Array<Plane>) {
        for (i in planes.indices) {
            if (data[i] == null) {
                data[i] = ByteArray(planes[i].buffer.capacity())
            }

            planes[i].buffer.get(data[i]!!)
        }
    }

    private fun onPreviewProcess() {
        // Do something if preview is active
    }

    private val previewProcessRunnable: Runnable = object: Runnable {
        override fun run() {
            onPreviewProcess()

            handler.post(this)
        }
    }

    private val stateCallback = object: CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            camera = cameraDevice

            onPreviewStart()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
            camera = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraDevice.close()
            camera = null
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height

        onCameraInit()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        // Ignored, Camera does all the work for us
        viewWidth = width
        viewHeight = height
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {
        // Invoked every time there's a new Camera preview frame
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
        camera?.close()

        return true
    }
}
