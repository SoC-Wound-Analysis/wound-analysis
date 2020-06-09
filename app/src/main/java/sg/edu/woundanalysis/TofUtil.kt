package sg.edu.woundanalysis

import android.content.ContentValues.TAG
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
 * Conversion of the distance encoded in a depth16 image from raw units to millimeter.
 */
internal var RAW_DISTANCE_TO_MM : Double = 1.0

/**
 * The dimensions of the ToF Camera input.
 */
internal var WIDTH : Int = 320
internal var HEIGHT : Int = 240

private var RANGE_MAX = Int.MAX_VALUE
private var RANGE_MIN = Int.MIN_VALUE


/**
 * Generates a depth array from DEPTH16 image.
 */
fun getDepthArray(image: Image): Array<Int> {
    Log.d(TAG, "DEPTH width: ${image.width}, height: ${image.height}")
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
 */
fun normalizeRange(range: Int): Int {
    var normalized: Float = range.toFloat() - RANGE_MIN
    normalized = Math.max(RANGE_MIN.toFloat(), normalized)
    normalized = Math.min(RANGE_MAX.toFloat(), normalized)
    normalized -= RANGE_MIN
    normalized = normalized / (RANGE_MAX - RANGE_MIN) * 255
    return normalized.toInt()
}

/**
 * Converts an array of depth values to a bitmap.
 */
fun convertToRGBBitmap(mask: Array<Int>): Bitmap {
    val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    for (y in 0 until HEIGHT) {
        for (x in 0 until WIDTH) {
            val index = y * WIDTH + x
            bitmap.setPixel(x, y, Color.argb(255, 0, mask[index], 0))
        }
    }
    return bitmap
}

fun defaultBitMapTransform(view : TextureView) : Matrix {
    val matrix : Matrix = Matrix()
    val centerX : Int = view.width / 2
    val centerY : Int = view.height / 2

    val bufferRect : RectF = RectF(0.toFloat(), 0.toFloat(), WIDTH.toFloat(), HEIGHT.toFloat())
    val viewRect : RectF = RectF(0.toFloat(), 0.toFloat(), view.width.toFloat(), view.height.toFloat())
    matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER)
    matrix.postRotate(90.toFloat(), centerX.toFloat(), centerY.toFloat())

    return matrix
}

/**
 * Calculates the average distance(per unit of DEPTH16's bit value) of the center of the image.
 */
fun getCenterDistance(depthArray : Array<Int>) : Double {
    val startingWidth = WIDTH / 2 - 1
    val startingHeight = HEIGHT / 2 - 1
    var distanceSum = 0;
    var count = 0
    for (y in startingHeight..startingHeight + 1)
        for (x in startingWidth..startingWidth + 1)
            distanceSum += depthArray[y * WIDTH + x]
            count += 1

    Log.d(TAG, "$count")
    return distanceSum.toDouble() / count

}

fun getFov(depthArray: Array<Int>, focalLen : Int) : Double {
    val leftPixelHeight = HEIGHT / 2
    val leftPixelWidth = 0
    val rightPixelHeight = HEIGHT / 2
    val rightPixelWidth = WIDTH - 1
    val leftDist = convertToMm(depthArray[leftPixelHeight * WIDTH + leftPixelWidth])
    val rightDist = convertToMm(depthArray[rightPixelHeight * WIDTH + rightPixelHeight])

    // Cosine rule to find the angle
    val nominator = focalLen.toDouble().pow(2)
    - leftDist.toDouble().pow(2)
    - rightDist.toDouble().pow(2)
    val denominator = 2 * leftDist * rightDist
    return acos(nominator / denominator)
}

fun convertToMm(rawValue : Int) : Double {
    return rawValue * RAW_DISTANCE_TO_MM;
}

