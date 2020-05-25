package sg.edu.woundanalysis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    //Useless CameraX variables
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    //Creates Texture View for Camera Preview
    private var mTextureView: TextureView? = null
    private var camera: Camera? = null

    val surfaceReadyCallback = object: SurfaceHolder.Callback {

        // Unused listeners
        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder?) {}

        override fun surfaceCreated(holder: SurfaceHolder?) {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appbar.setTitle(R.string.app_name)

        // Request camera permission
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_PERMISSIONS)

       // Set surfaceView's callback
        surfaceView.holder.addCallback(surfaceReadyCallback)

        // Listener for take photo button
        btn_capture.setOnClickListener(capturePhoto())

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    /**
     * Handles Permission results.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this,
                        "Permission not granted by user",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun capturePhoto(): View.OnClickListener? {
        return null;
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun allPermissionsGranted() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Initializes the camera device with CameraX API.
     */
    private fun startCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val tofCamera = cameraManager.cameraIdList[0]

        Log.d(TAG, "Hello World!")
        Log.d(TAG, cameraManager.cameraIdList[4])

        if (ActivityCompat.checkSelfPermission(baseContext,
                        android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        cameraManager.openCamera(tofCamera, object:
                CameraDevice.StateCallback() {

            // Unused listeners
            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, error: Int) {}

            override fun onOpened(cameraDevice: CameraDevice) {
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)

                cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->

                    streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)?.let { yuvSizes ->
                        val previewSize = yuvSizes.last()

                        val displayRotation = windowManager.defaultDisplay.rotation
                        val swappedDimensions = areDimensionsSwapped(displayRotation, cameraCharacteristics)

                        // Choose the correct width and height
                        val rotatedPreviewHeight = if (swappedDimensions) previewSize.width else previewSize.height
                        val rotatedPreviewWidth = if (swappedDimensions) previewSize.height else previewSize.width
                    }
                }

                // I dunno where to put this part
                val previewSurface = surfaceView.holder.surface

                val captureCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigureFailed(session: CameraCaptureSession) {}

                    override fun onConfigured(session: CameraCaptureSession) {
                        val previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                        }

                        session.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                object: CameraCaptureSession.CaptureCallback() {},
                                android.os.Handler {true}
                        )
                    }
                }

                cameraDevice.createCaptureSession(mutableListOf(previewSurface),
                captureCallback, android.os.Handler {true})

            }

        }, android.os.Handler {true})
    }

    /**
     * Helper function to determine and adjust the preview according to the orientation of the camera.
     */
    private fun areDimensionsSwapped(displayRotation: Int, cameraCharacteristics: CameraCharacteristics): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 90 || cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 0 || cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                // Impossible to reach this part of code
            }
        }
        return swappedDimensions
    }

    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TAG = R.string.app_name.toString()
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val TOF_CAMERA_ID = 4

    }

}