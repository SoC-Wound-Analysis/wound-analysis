package sg.edu.woundanalysis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.hardware.camera2.*
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private lateinit var outputDirectory : File
    private lateinit var cameraExecutor : ExecutorService
    private lateinit var tofImageReader : ImageReader
    private lateinit var rgbImageReader : ImageReader

    private lateinit var rgbSession : CameraCaptureSession
    private lateinit var tofSession : CameraCaptureSession

    private val tofCameraId : String by lazy { getTofCamera() }
    private val rgbCameraID : String by lazy { getRgbCamera() }

    private val tofCharacteristics : CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(tofCameraId)
    }
    private val rgbCharacteristics : CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(rgbCameraID)
    }

    // Threads
    private val tofThread = HandlerThread("TOFThread").apply { start() }
    private val tofThreadHandler = Handler(tofThread.looper)
    private val rgbThread = HandlerThread("RGBThread").apply { start() }
    private val rgbThreadHandler = Handler(rgbThread.looper)
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    // Persistent MediaRecorder surface
    private val recorderSurface : Surface by lazy {
        val surface = MediaCodec.createPersistentInputSurface()

        //Create buffer for the real mediaRecorder
        createRecorder(surface).apply {
            prepare()
            release()
        }

        surface
    }

    private val rgbMediaRecorder by lazy { createRecorder(recorderSurface) }
    private val tofMediaRecorder by lazy { MediaRecorder() }

    // Requests for video capturing sessions
    private val recordRequest: CaptureRequest by lazy {
        rgbSession.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(Surface(textureView2.surfaceTexture))
            addTarget(recorderSurface)
            // Can set user requested fps
        }.build()
    }

    // Indicates if camera is recording
    private var isRecording = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appbar.setTitle(R.string.app_name)

        // Request camera permission
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_PERMISSIONS)

        // Listener for take photo button
        btn_capture.setOnClickListener {

            captureTofBitmap()
            //captureRgbPhoto()

            if (isRecording) {
                stopRgbVideo()
            } else {
                captureRgbVideo()
            }

            it.post {it.isEnabled = true}
        }

        textureView2.surfaceTextureListener = mSurfaceTextureListener

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun createRecorder(surface : Surface) : MediaRecorder {

        val profile : CamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P)
        val output = File(outputDirectory, "VID_${SDF.format(Date())}.mpg")

        return MediaRecorder().apply {
            reset()

            // Setting the parameters must be in a certain order
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(output)
            setVideoEncodingBitRate(profile.videoBitRate);
            setVideoFrameRate(profile.videoFrameRate)
            setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            setInputSurface(surface)
        }

    }


    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(p0: SurfaceTexture?, p1: Int, p2: Int) {
            startCamera()
        }

        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) {
            configureRGBOutputSize(textureView2.width, textureView2.height)
        }

        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean { return true }
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) {}

    }

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
        // For unknown reasons, permission checks are required within the function despite being handled in onCreate()
        if (ActivityCompat.checkSelfPermission(baseContext,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        cameraManager.openCamera(tofCameraId, openTofCameraCallback, tofThreadHandler)
        cameraManager.openCamera(rgbCameraID, openRgbCameraCallback, rgbThreadHandler)
    }

    /**
     * Helper function to get ID of TOF Camera.
     * @return a string consisting of a positive integer if found, -1 otherwise.
     **/
    private fun getTofCamera(): String {
        for (camera in cameraManager.cameraIdList) {
            val chars: CameraCharacteristics = cameraManager.getCameraCharacteristics(camera)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            var isTofCamera = false
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

    /**
     * Helper function to get ID of RGB Camera.
     * @return a string consisting of a positive integer if found, -1 otherwise.
     **/
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

    //********* Callbacks *********//
    private val openRgbCameraCallback = object : CameraDevice.StateCallback() {

        // Unused states
        override fun onDisconnected(camera: CameraDevice) {}
        override fun onError(camera: CameraDevice, error: Int) {}

        override fun onOpened(cameraDevice: CameraDevice) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)
            val streamConfigMap = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!

            // Logs the rgb camera input size
            /*
            streamConfigMap.getOutputSizes(ImageFormat.RAW_SENSOR)!!.forEach {
                Log.d(TAG, "RGB camera size : ${it.width} * ${it.height}")
            }
             */

            // Targets for the CaptureSession
            rgbImageReader = ImageReader.newInstance(RGB_WIDTH, RGB_HEIGHT, ImageFormat.RAW_SENSOR, 2)
            rgbImageReader.setOnImageAvailableListener(null, null)

            val surface = Surface(textureView2.surfaceTexture)
            val targets = listOf(surface, rgbImageReader.surface, recorderSurface)

            val rgbCaptureCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "RGB Camera Configured failed")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "RGB Configured successfully")
                    val previewRequestBuilder = cameraDevice
                            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            .apply {addTarget(surface)}

                    session.setRepeatingRequest(
                            previewRequestBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {},
                            rgbThreadHandler
                    )

                    rgbSession = session
                }
            }

            cameraDevice.createCaptureSession(targets, rgbCaptureCallback, rgbThreadHandler)
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

            tofImageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.DEPTH16, 2)

            tofImageReader.setOnImageAvailableListener({ reader ->
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
            val targets = listOf(tofImageReader.surface)
            val tofCaptureCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d(TAG, "Camera Configured failed")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Configured successfully")
                    tofSession = session
                    val previewRequestBuilder = cameraDevice
                            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            .apply {addTarget(tofImageReader.surface)}

                    session.setRepeatingRequest(
                            previewRequestBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {},
                            tofThreadHandler
                    )

                }

            }

            cameraDevice.createCaptureSession(targets, tofCaptureCallback, tofThreadHandler)
        }

    }

    private fun configureRGBOutputSize(viewWidth : Int, viewHeight : Int) {
        val matrix = Matrix()
        //val viewRect = RectF(0.toFloat(), 0.toFloat(), viewWidth.toFloat(), viewHeight.toFloat());
        val viewRect = RectF(0.toFloat(), 0.toFloat(), 50.toFloat(), 50.toFloat());
        val bufferRect = RectF(0.toFloat(), 0.toFloat(), 4032.toFloat(), 3024.toFloat());
        val centerX = viewRect.centerX();
        val centerY = viewRect.centerY();

        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        matrix.postScale(4.toFloat(), 1.toFloat(), centerX, centerY);

        textureView2.setTransform(matrix);
    }

    private fun captureTofBitmap() {
        val tofFile = File(outputDirectory, "BITMAP_${SDF.format(Date())}.bmp")
        val outputStream = FileOutputStream(tofFile)
        textureView.bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    }

    private fun captureRgbPhoto() {
        //Flush images
        @Suppress("ControlFlowWIthEmptyBody")
        while (rgbImageReader.acquireNextImage() != null) {
            Log.d(TAG, "Inside the flush images loop")
        }

        // Start a new queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        rgbImageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image added to new queue")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = rgbSession
                .device
                .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                .apply { addTarget(rgbImageReader.surface)}

        rgbSession.capture(captureRequest.build(), object: CameraCaptureSession.CaptureCallback() {

            override fun onCaptureCompleted(session: CameraCaptureSession,
                                            request: CaptureRequest,
                                            result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                while (true) {
                    val image = imageQueue.take()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp) continue
                    Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                    // Unset the image reader listener
                    rgbImageReader.setOnImageAvailableListener(null, null)

                    // Clear the queue if there's any left
                    while (imageQueue.size > 0) {
                        imageQueue.take().close()
                    }

                    saveRgbImage(result, image)

                    break

                }
            }
        }, rgbThreadHandler)

    }

    private fun captureRgbVideo() {
        /*
        val recorderSurface : Surface = rgbMediaRecorder.surface
        val surfaces = mutableListOf<Surface>(recorderSurface)
         */

        Log.d(TAG, "Entered capture video funtion")
        rgbSession.setRepeatingRequest(recordRequest, null, rgbThreadHandler)

        rgbMediaRecorder.apply {
            prepare()
            start()
        }

        Log.d(TAG, "Exiting capture video funtion")

    }

    fun stopRgbVideo() {
        Log.d(TAG, "Entering stop video function")

        isRecording = false

        try {
            rgbSession.stopRepeating()
            rgbSession.abortCaptures()
        } catch (e : Exception) {
            e.printStackTrace()
        }

        rgbMediaRecorder.stop()
        rgbMediaRecorder.reset()
    }

    private fun saveRgbImage(result : CaptureResult, image: Image) {
        val dngCreator = DngCreator(rgbCharacteristics, result)
        val output = File(outputDirectory, "IMG_${SDF.format(Date())}.dng")
        FileOutputStream(output).use { dngCreator.writeImage(it, image)}
    }

    //************ Constants *********//
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val IMAGE_BUFFER_SIZE = 2
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val SDF = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        private const val RECORDER_VIDEO_BITRATE : Int = 10_000_000
    }

}