package com.example.segmentation.utils

/**
 * As in:
 * https://github.com/tensorflow/examples/blob/master/lite/examples/image_classification/android/app/src/main/java/org/tensorflow/lite/examples/classification/env/ImageUtils.java
 */
fun convertYUV420ToARGB8888(data: Array<ByteArray?>, width: Int, height: Int, yRowStride: Int,
                            uvRowStride: Int, uvPixelStride: Int, out: IntArray) {
    var yp = 0
    for (j in 0 until height) {
        val pY = yRowStride * j
        val pUV = uvRowStride * (j shr 1)

        for (i in 0 until width) {
            val uv_offset = pUV + (i shr 1) * uvPixelStride

            out[yp++] = YUV2RGB(
                0xff and data[0]!![pY + i].toInt(),
                0xff and data[1]!![uv_offset].toInt(),
                0xff and data[2]!![uv_offset].toInt()
            )
        }
    }
}

/**
 * As in:
 * https://github.com/tensorflow/examples/blob/master/lite/examples/image_classification/android/app/src/main/java/org/tensorflow/lite/examples/classification/env/ImageUtils.java
 */
private fun YUV2RGB(y: Int, u: Int, v: Int): Int {
    var y = y
    var u = u
    var v = v

    // Adjust and check YUV values
    y = if (y - 16 < 0) 0 else y - 16
    u -= 128
    v -= 128

    // This is the floating point equivalent. We do the conversion in integer
    // because some Android devices do not have floating point in hardware.
    // nR = (int)(1.164 * nY + 2.018 * nU);
    // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
    // nB = (int)(1.164 * nY + 1.596 * nV);
    val y1192 = 1192 * y
    var r = y1192 + 1634 * v
    var g = y1192 - 833 * v - 400 * u
    var b = y1192 + 2066 * u

    // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
    r = if (r > 262143) 262143 else if (r < 0) 0 else r
    g = if (g > 262143) 262143 else if (g < 0) 0 else g
    b = if (b > 262143) 262143 else if (b < 0) 0 else b

    return -0x1000000 or (r shl 6 and 0xff0000) or (g shr 2 and 0xff00) or (b shr 10 and 0xff)
}