package sg.edu.woundanalysis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.lang.Exception
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageReader: ImageReader
    private lateinit var camera : CameraDevice

    // Camera thread
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraThreadHandler = Handler(cameraThread.looper)

    // Image Reader thread
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)


    //***************** Permissions ************//
    /**
     * Handles Permission results.
     * pen/open
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

    private fun allPermissionsGranted() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
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


    /**
     * Initializes the camera device with camera2 API.
     */
    private fun startCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // For unknown reasons, permission checks are required within the function despite being handled in onCreate()
        if (ActivityCompat.checkSelfPermission(baseContext,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val tofCameraId: String = getTofCamera()
        // Hardcoded RGC camera ID
        val rbgCameraID = "0"

        cameraManager.openCamera(tofCameraId, openCameraCallback, cameraThreadHandler)


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

    //helper function to get ID of TOF Camera
    private fun getTofCamera(): String {
        for (camera in cameraManager.cameraIdList) {
            val chars: CameraCharacteristics = cameraManager.getCameraCharacteristics(camera)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            var isTofCamera: Boolean = false
            if (capabilities != null) {
                for (capability in capabilities) {
                    val capable: Boolean = capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT
                    isTofCamera = isTofCamera || capable
                }
            }

            // Blocker statement if camera is not TOF
            if (!isTofCamera) {
                continue
            }

            // Logs TOF camera's characteristics
            val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            Log.d(TAG, "Sensor size: " + sensorSize)
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            if (focalLengths!!.size > 0) {
                val focalLength = focalLengths[0];
                val fov = 2 * Math.atan((sensorSize!!.width / (2 * focalLength).toDouble()))
                Log.d(TAG, "Calculated FoC: " + fov)
            }
            Log.d(TAG, "TOF Camera ID: " + camera)
            return camera
        }

        Log.d(TAG, "no camera found")
        return "-1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appbar.setTitle(R.string.app_name)

        // Request camera permission
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_PERMISSIONS)

        // Listener for take photo button
        btn_capture.setOnClickListener(capturePhoto())

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (e: Throwable) {
            Log.d(TAG, "Error while closing camera")
        }
    }

    //********* Callbacks *********//
    val openCameraCallback = object : CameraDevice.StateCallback() {

        // Unused states
        override fun onDisconnected(camera: CameraDevice) {}
        override fun onError(camera: CameraDevice, error: Int) {}

        override fun onOpened(cameraDevice: CameraDevice) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)
            val streamConfigMap = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!

            val previewSize = streamConfigMap
                    .getOutputSizes(ImageFormat.DEPTH16)!!
                    .filter {
                        it.width < Resources.getSystem().displayMetrics.widthPixels &&
                                it.height < Resources.getSystem().displayMetrics.heightPixels
                    }
                    .maxBy { it.height * it.width }!!

            imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.DEPTH16, 1)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                Log.d(TAG, "Image available in queue: ${image.timestamp}")
                val depthMask = getDepthMask(image)
                val bitmap = convertToRGBBitmap(depthMask)
                val canvas: Canvas = textureView.lockCanvas()
                canvas.drawBitmap(bitmap, defaultBitMapTransform(textureView), null)
                textureView.unlockCanvasAndPost(canvas)

                image.close()
            }, cameraThreadHandler)

            // Targets for the CaptureSession
            //val targets = listOf(surfaceView.holder.surface, imageReader.surface)
            val targets = listOf(imageReader.surface)

            val captureCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {}

                override fun onConfigured(session: CameraCaptureSession) {
                    val previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    }

                    session.setRepeatingRequest(
                            previewRequestBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {},
                            Handler { true }
                    )
                }
            }

            cameraDevice.createCaptureSession(targets,
                    captureCallback, Handler { true })
        }

    }

    //************ Constants *********//
    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val TAG = "Wound_Analysis"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

}