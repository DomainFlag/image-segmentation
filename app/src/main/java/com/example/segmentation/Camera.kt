package com.example.segmentation

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.util.Log

class Camera(context: Context) {

    private val manager = context.getSystemService(Service.CAMERA_SERVICE) as CameraManager

    @SuppressLint("MissingPermission")
    fun init(handler: Handler, callback: CameraDevice.StateCallback, mode: Int) {
        val id = getCameraId(mode)
        if (id != null) {
            manager.openCamera(id, callback, handler)
        }
    }

    private fun getCameraId(mode: Int): String? {
        for(id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == mode) {
                return id
            }
        }

        return null
    }

    private fun getCamerasInfo() {
        for(id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!.toTypedArray()
            val cameraType = when(characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                else -> "External"
            }

            Log.v(
                MainActivity.TAG, String.format("%s Camera id: %s, focal lengths: %s", cameraType, id,
                    focalLengths.joinToString("mm")))
        }
    }
}