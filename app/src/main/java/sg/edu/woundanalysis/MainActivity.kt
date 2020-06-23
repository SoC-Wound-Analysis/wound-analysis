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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageReader: ImageReader
    private lateinit var camera : CameraDevice

    // Threads
    private val tofThread = HandlerThread("TOFThread").apply { start() }
    private val tofThreadHandler = Handler(tofThread.looper)
    private val rgbThread = HandlerThread("RGBThread").apply { start() }
    private val rgbThreadHandler = Handler(rgbThread.looper)
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
        Log.d(TAG, "Entering startCamera()")

        // For unknown reasons, permission checks are required within the function despite being handled in onCreate()
        if (ActivityCompat.checkSelfPermission(baseContext,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val tofCameraId: String = getTofCamera()
        // Hardcoded RGC camera ID
        val rgbCameraID = getRgbCamera()

        cameraManager.openCamera(tofCameraId, openTofCameraCallback, tofThreadHandler)
        cameraManager.openCamera(rgbCameraID, openRgbCameraCallback, rgbThreadHandler)
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
            //Log.d(TAG, "Sensor size: " + sensorSize)
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            //Log.d(TAG, "TOF Camera ID: " + camera)
            return camera
        }

        Log.d(TAG, "no camera found")
        return "-1"
    }

    //helper function to get ID of TOF Camera
    private fun getRgbCamera(): String {
        for (camera in cameraManager.cameraIdList) {
            val chars: CameraCharacteristics = cameraManager.getCameraCharacteristics(camera)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
            var supportsRaw: Boolean = false
            supportsRaw = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities

            // Blocker statement if camera is not TOF
            if (!supportsRaw) {
                continue
            }

            // Logs TOF camera's characteristics
            Log.d(TAG, "RGB Camera ID: " + camera)
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

        surfaceView3!!.holder!!.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
            }
            override fun surfaceDestroyed(p0: SurfaceHolder?) {
            }

            override fun surfaceCreated(p0: SurfaceHolder?) {
                startCamera()
            }
        })

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    //********* Callbacks *********//
    private val openRgbCameraCallback = object : CameraDevice.StateCallback() {
        // Unused states
        override fun onDisconnected(camera: CameraDevice) {}
        override fun onError(camera: CameraDevice, error: Int) {}

        override fun onOpened(cameraDevice: CameraDevice) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)
            val streamConfigMap = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!

            streamConfigMap.getOutputSizes(ImageFormat.RAW_SENSOR)!!.forEach {
                Log.d(TAG, "RGB camera size : ${it.width} * ${it.height}")
            }

            // Targets for the CaptureSession
            val targets = listOf(surfaceView3.holder.surface)
            val captureCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "RGB Camera Configured failed")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "RGB Configured successfully")
                    val previewRequestBuilder = cameraDevice
                            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            .apply {addTarget(surfaceView3.holder.surface)}

                    session.setRepeatingRequest(
                            previewRequestBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {},
                            rgbThreadHandler
                    )

                }

            }

            cameraDevice.createCaptureSession(targets,
                    captureCallback, rgbThreadHandler)
        }

    }

    private val openTofCameraCallback = object : CameraDevice.StateCallback() {

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
            //Log.d(TAG, "Height : ${previewSize.height}, Width: ${previewSize.width}")

            imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.DEPTH16, 2)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                val depthMask = getDepthArray(image)

                // All the testing logs
                val testDist = 763
                //Log.d(TAG, "Image available in queue: ${image.timestamp}, " +
                //       "Distance: ${getCenterDistance(depthMask)}mm")
                //Log.d(TAG, "FOV for object of width ${testDist}mm: ${getFov(depthMask, testDist)}")

                val bitmap = convertToRGBBitmap(depthMask)
                val canvas: Canvas = textureView.lockCanvas()
                canvas.drawBitmap(bitmap, defaultBitMapTransform(textureView), null)
                textureView.unlockCanvasAndPost(canvas)
                image.close()
            }, imageReaderHandler)

            // Targets for the CaptureSession
            val targets = listOf(imageReader.surface)
            val captureCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "Camera Configured failed")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Configured successfully")
                    val previewRequestBuilder = cameraDevice
                            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            .apply {addTarget(imageReader.surface)}

                    session.setRepeatingRequest(
                            previewRequestBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {},
                            tofThreadHandler
                    )

                }

            }

            cameraDevice.createCaptureSession(targets,
                    captureCallback, tofThreadHandler)
        }

    }

    //************ Constants *********//
    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

}