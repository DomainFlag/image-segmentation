package com.example.segmentation

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.graphics.Bitmap
import kotlin.math.roundToLong


class Model(private val context: Context) {

    companion object {
        const val TAG = "Model"

        enum class Device {
            CPU, GPU, NNAPI
        }

        val DIMENSIONS = intArrayOf(224, 224)

        val IMAGE_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val IMAGE_SD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var interpreter: Interpreter? = null

    private val buffer = ByteBuffer.allocateDirect(1 * 3 * DIMENSIONS[0] * DIMENSIONS[1] * 4)
    private val values = IntArray(DIMENSIONS[0] * DIMENSIONS[1])

    init {
        buffer.order(ByteOrder.nativeOrder())
    }

    fun createModel(name: String, mode: Device) {
        val options = Interpreter.Options()
        options.setNumThreads(1)

        val mappedFile = FileUtil.loadMappedFile(context, name)
        val delegate: Delegate? = when(mode) {
            Device.GPU -> GpuDelegate()
            Device.NNAPI -> NnApiDelegate()
            else -> null
        }

        if(delegate != null) {
            options.addDelegate(delegate)
        }

        interpreter = Interpreter(mappedFile, options)

        // Reads type and shape of input and output tensors, respectively.
        val inputShape = interpreter!!.getInputTensor(0).shape()
        if (inputShape!!.contentEquals(DIMENSIONS)) {
            Log.e(TAG, "Input shape not supported: " + inputShape.contentToString())
        }

        Log.v(TAG, "Input counts is: " + interpreter!!.inputTensorCount)
        Log.v(TAG, "Using input shape: " + inputShape.contentToString() +
                " and input shape: " + interpreter!!.getInputTensor(0).dataType())

        val outputShape = interpreter!!.getOutputTensor(0).shape()
        if (outputShape!!.contentEquals(DIMENSIONS)) {
            Log.e(TAG, "Output shape not supported: " + outputShape.contentToString())
        }

        Log.v(TAG, "Output counts is: " + interpreter!!.outputTensorCount)
        Log.v(TAG, "Using output shape: " + outputShape.contentToString() +
                " and output shape: " + interpreter!!.getOutputTensor(0).dataType())
    }

    fun inferModel(bitmap: Bitmap): Bitmap {
        val inputBitmap = Bitmap.createScaledBitmap(bitmap, DIMENSIONS[0], DIMENSIONS[1], false)

        // Copy bitmap pixels
        inputBitmap.getPixels(values, 0, DIMENSIONS[0], 0, 0, DIMENSIONS[0], DIMENSIONS[1])

        // Set position to 0 for reading
        buffer.rewind()
        for (i in 0 until DIMENSIONS[0]) {
            for (j in 0 until DIMENSIONS[1]) {
                val pixelValue = values[i * DIMENSIONS[0] + j]

                buffer.putFloat(((pixelValue shr 16 and 0xFF) / 255f - IMAGE_MEAN[0]) / IMAGE_SD[0])
                buffer.putFloat(((pixelValue shr 8 and 0xFF) / 255f - IMAGE_MEAN[1]) / IMAGE_SD[1])
                buffer.putFloat(((pixelValue and 0xFF) / 255f - IMAGE_MEAN[2]) / IMAGE_SD[2])
            }
        }

        // Copy the output data into TensorFlow.
        val outputBuffer = ByteBuffer.allocateDirect(3 * DIMENSIONS[0] * DIMENSIONS[1] * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        outputBuffer.rewind()

        val startTime = System.currentTimeMillis()

        // Inference to be done
        interpreter!!.run(buffer, outputBuffer)

        val endTime = System.currentTimeMillis()

        outputBuffer.rewind()
        val transform = IntArray(DIMENSIONS[0] * DIMENSIONS[1])
        for (i in 0 until DIMENSIONS[0]) {
            for (j in 0 until DIMENSIONS[1]) {
                val p = i * DIMENSIONS[0] + j

                var color = 0xFF000000
                for (t in 0 until 3) {
                    val value = outputBuffer.float

                    color = color or ((value * 255f).roundToLong() shl (t * 8))
                }

                transform[p] = color.toInt()
            }
        }

        Log.v(TAG, (endTime - startTime).toString())

        val finalResult = Bitmap.createBitmap(DIMENSIONS[0], DIMENSIONS[1], Bitmap.Config.ARGB_8888)
        finalResult.setPixels(transform, 0, DIMENSIONS[0], 0, 0, DIMENSIONS[0], DIMENSIONS[0]);

        outputBuffer.clear()

        return finalResult
    }

    fun clear() {
        interpreter?.close()
    }
}