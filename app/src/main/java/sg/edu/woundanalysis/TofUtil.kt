package sg.edu.woundanalysis

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.media.Image
import android.util.Log
import android.view.TextureView
import java.nio.ShortBuffer
import kotlin.math.*

/**
 * The dimensions of the ToF Camera input.
 */
internal var WIDTH : Int = 640
internal var HEIGHT : Int = 480
internal const val TAG = "Wound_Analysis"

private var RANGE_MAX = Int.MAX_VALUE
private var RANGE_MIN = Int.MIN_VALUE
private const val MAX_DIST = 8192

/**
 * Generates a depth array from DEPTH16 image.
 *
 * @return a 1D array conraining values in terms of millimeters.
 */
fun getDepthArray(image: Image): Array<Int> {
    val shortDepthBuffer: ShortBuffer =
            image.planes[0].buffer.asShortBuffer()

    val depthArray : Array<Int> = Array(WIDTH * HEIGHT, {it} )
    for (y in 0 until HEIGHT) {
        for (x in 0 until WIDTH) {
            val index: Int = y * WIDTH + x
            val depthSample: Short = shortDepthBuffer.get(index)
            val newValue = extractDepth(depthSample, 0.1f)
            depthArray[index] = newValue
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

    RANGE_MAX = max(depthRange, RANGE_MAX)
    RANGE_MIN = min(depthRange, RANGE_MIN)

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
        return 255

    return ((originalDist / MAX_DIST.toFloat()) * 255).toInt()
}

/**
 * Converts an array of depth values to a bitmap.
 */
fun convertToRGBBitmap(mask: Array<Int>): Bitmap {
    val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    for (y in 0 until HEIGHT) {
        for (x in 0 until WIDTH) {
            val index = y * WIDTH + x
            //bitmap.setPixel(x, y, Color.argb(255, 0, 255 - normalizedDist, 0))
            bitmap.setPixel(x, y,
                    Color.argb(255, 0, (mask[index]), 0))
        }
    }
    return bitmap
}

fun defaultBitMapTransform(view : TextureView) : Matrix {
    val matrix : Matrix = Matrix()
    val centerX : Int = view.width / 2
    val centerY : Int = view.height / 2

    val bufferRect : RectF = RectF(0.toFloat(), 0.toFloat(), WIDTH.toFloat(), HEIGHT.toFloat())
    Log.d(TAG, "defaultBitMapTransform(): view width: ${view.width}, view height: ${view.height}")
    val viewRect : RectF = RectF(0.toFloat(), 0.toFloat(), view.width.toFloat(), view.height.toFloat())
    matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER)
    matrix.postRotate(90.toFloat(), centerX.toFloat(), centerY.toFloat())

    return matrix
}

/**
 * Calculates the average distance(per unit of DEPTH16's bit value) of the center of the image.
 */
fun getCenterDistance(depthArray : Array<Int>) : Double {
    val startingWidth = WIDTH / 2
    val startingHeight = HEIGHT / 2
    val distanceSum = depthArray[startingHeight * WIDTH + startingWidth]
    return distanceSum.toDouble()

}

/**
 * Calculates the horizontal field of view of the camera.
 */
fun getHorFov(depthArray: Array<Int>, objWidth : Int) : Double {
    val leftPixelHeight = HEIGHT / 2
    val leftPixelWidth = 0
    val rightPixelHeight = HEIGHT / 2
    val rightPixelWidth = WIDTH - 1
    val leftDist = depthArray[leftPixelHeight * WIDTH + leftPixelWidth]
    val rightDist = depthArray[rightPixelHeight * WIDTH + rightPixelWidth]
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
