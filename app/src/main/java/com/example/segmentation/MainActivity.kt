package com.example.segmentation

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"

        const val THREAD_PROCESSING = "Processing Thread"
        const val REQUEST_PERMISSION_CODE = 241
        const val FRAGMENT_DIALOG = "Permission Dialog"
    }

    private lateinit var previewer: Previewer
    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkCameraHardware(this) && getCameraPermissions()) {
            startCameraPreview()
        }
    }

    private fun startCameraPreview() {
        // Create model for inference
        model = Model(this)
        model.createModel("model.tflite", Model.Companion.Device.GPU)

        // Create a previewer and attach the model to it
        previewer = Previewer(this, model, findViewById(R.id.previewer_view))
    }

    override fun onResume() {
        // Resume active thread
        previewer.onPreviewerStart()
        previewer.onCameraInit()

        super.onResume()
    }

    override fun onPause() {
        // Pause active thread
        previewer.onPreviewerStop()
        previewer.clear()


        super.onPause()
    }

    override fun onDestroy() {
        model.clear()

        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val index = permissions.indexOf(Manifest.permission.CAMERA)
        if(requestCode == REQUEST_PERMISSION_CODE && index != -1
            && grantResults[index] == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        }
    }

    private fun getCameraPermissions(): Boolean {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            return true
        } else {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    PermissionDialog().show(supportFragmentManager, FRAGMENT_DIALOG)
                } else {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION_CODE)
                }
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION_CODE)
            }
        }

        return false
    }

    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    private class PermissionDialog: DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(context)
                .setMessage("To perform segmentation, permission for an active camera is required")
                .setPositiveButton("Permit") { _, _ ->
                    parentFragment?.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION_CODE)
                }
                .setNegativeButton("Close") { _, _ ->
                    // Do something
                }
                .create()
        }
    }
}
