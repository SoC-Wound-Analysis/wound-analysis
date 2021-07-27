package sg.edu.woundanalysis

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.media.Image
import android.view.TextureView
import java.nio.ShortBuffer
import kotlin.math.*

/**
 * The dimensions of the ToF Camera input.
 */
internal var TOF_WIDTH : Int = 640
internal var TOF_HEIGHT : Int = 480
internal var RGB_WIDTH : Int = 4032
internal var RGB_HEIGHT : Int = 3024
internal const val TAG = "Wound_Analysis"
private const val MAX_DIST = 8192
private const val RGB_MAX_VALUE = 256 * 256

/**
 * Generates a depth array from DEPTH16 image.
 *
 * @return a 1D array conraining values in terms of millimeters.
 */
fun getDepthArray(image: Image): Array<Int> {
    val shortDepthBuffer: ShortBuffer =
            image.planes[0].buffer.asShortBuffer()

    val depthArray : Array<Int> = Array(TOF_WIDTH * TOF_HEIGHT, {it} )
    for (y in 0 until TOF_HEIGHT) {
        for (x in 0 until TOF_WIDTH) {
            val index: Int = y * TOF_WIDTH + x
            depthArray[index] = extractDepth(shortDepthBuffer.get(index), 0.1f)
        }
    }
    return depthArray
}

/**
 * Extracts the value encoded in each of the data point in DEPTH16 image.
 *
 * @return a value between 0 and 2^13 millimeters.
 */
fun extractDepth(sample: Short, confidenceFilter: Float): Int {
    val depthRange = sample.toInt() and 0x1FFF
    val depthConfidence: Short = ((sample.toInt() shr 13) and 0x7).toShort()
    val depthPercentage: Float = if (depthConfidence.equals(0)) 1.toFloat()
    else (depthConfidence - 1) / 7.toFloat()

    //return if (depthPercentage > confidenceFilter) depthRange else 0
    return depthRange
}

/**
 * Normalizes a value to be within 8bit.
 *
 * @return a value between 0 and 255(inclusive).
 */
fun normalizeRange(originalDist: Int): Int {

    // Filter out data points beyond the magnitude of our interest
    if (originalDist > MAX_DIST)
        return RGB_MAX_VALUE

    return ((originalDist.toDouble() / MAX_DIST) * RGB_MAX_VALUE).toInt()
}

/**
 * Converts an array of depth values to a bitmap.
 */
fun convertToRGBBitmap(mask: Array<Int>): Bitmap {
    val bitmap: Bitmap = Bitmap.createBitmap(TOF_WIDTH, TOF_HEIGHT, Bitmap.Config.ARGB_8888)
    for (y in 0 until TOF_HEIGHT) {
        for (x in 0 until TOF_WIDTH) {
            val index = y * TOF_WIDTH + x
            //bitmap.setPixel(x, y, Color.argb(255, 0, 255 - normalizedDist, 0))
            bitmap.setPixel(x, y,
                    Color.argb(255, 0, mask[index], 0))
        }
    }
    return bitmap
}

fun defaultBitMapTransform(view : TextureView) : Matrix {
    val matrix : Matrix = Matrix()
    val centerX : Int = view.width / 2
    val centerY : Int = view.height / 2

    //val bufferRect : RectF = RectF(0.toFloat(), 0.toFloat(), TOF_WIDTH.toFloat(), TOF_HEIGHT.toFloat())
    //val viewRect : RectF = RectF(0.toFloat(), 0.toFloat(), view.width.toFloat(), view.height.toFloat())
    val bufferRect : RectF = RectF(0.toFloat(), 0.toFloat(), TOF_HEIGHT.toFloat(), TOF_WIDTH.toFloat())
    val viewRect : RectF = RectF(0.toFloat(), 0.toFloat(), view.width.toFloat(), view.height.toFloat())
    matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER)
    matrix.postRotate(90.toFloat(), centerY.toFloat(), centerX.toFloat())

    return matrix
}

/**
 * Calculates the average distance(per unit of DEPTH16's bit value) of the center of the image.
 */
fun getCenterDistance(depthArray : Array<Int>) : Double {
    val startingWidth = TOF_WIDTH / 2
    val startingHeight = TOF_HEIGHT / 2
    val distanceSum = depthArray[startingHeight * TOF_WIDTH + startingWidth]
    return distanceSum.toDouble()

}

/**
 * Calculates the horizontal field of view of the camera.
 */
fun getHorFov(depthArray: Array<Int>, objWidth : Int) : Double {
    val leftPixelHeight = TOF_HEIGHT / 2
    val leftPixelWidth = 0
    val rightPixelHeight = TOF_HEIGHT / 2
    val rightPixelWidth = TOF_WIDTH - 1
    val leftDist = depthArray[leftPixelHeight * TOF_WIDTH + leftPixelWidth]
    val rightDist = depthArray[rightPixelHeight * TOF_WIDTH + rightPixelWidth]
   // Log.d(TAG, "Left Distance: ${leftDist}mm")
    //Log.d(TAG, "Right distance: ${rightDist}mm")

    // Cosine rule to find the angle
    // l^2 + r^2 - 2lrcos(x) = h^2
    val nominator = leftDist.toDouble().pow(2)
    + rightDist.toDouble().pow(2)
    - objWidth.toDouble().pow(2)
    val denominator = 2 * leftDist * rightDist
    val fovInRadian = acos(nominator / denominator)

    return fovInRadian * 180 / Math.PI
}

/**
 * Calculates the vertical field of view of the camera.
 */
fun getVerFov(depthArray: Array<Int>, objWidth : Int) : Double {
    val leftPixelHeight = TOF_HEIGHT / 2
    val leftPixelWidth = 0
    val rightPixelHeight = TOF_HEIGHT / 2
    val rightPixelWidth = TOF_WIDTH - 1
    val leftDist = depthArray[leftPixelHeight * TOF_WIDTH + leftPixelWidth]
    val rightDist = depthArray[rightPixelHeight * TOF_WIDTH + rightPixelWidth]
    // Log.d(TAG, "Left Distance: ${leftDist}mm")
    //Log.d(TAG, "Right distance: ${rightDist}mm")

    // Cosine rule to find the angle
    // l^2 + r^2 - 2lrcos(x) = h^2
    val nominator = leftDist.toDouble().pow(2)
    + rightDist.toDouble().pow(2)
    - objWidth.toDouble().pow(2)
    val denominator = 2 * leftDist * rightDist
    val fovInRadian = acos(nominator / denominator)

    return fovInRadian * 180 / Math.PI
}
