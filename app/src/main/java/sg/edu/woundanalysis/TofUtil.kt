package sg.edu.woundanalysis

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.media.Image
import android.view.TextureView
import java.nio.ShortBuffer

internal const val WIDTH = 1080
internal const val HEIGHT = 1920
private var RANGE_MAX = Int.MAX_VALUE
private var RANGE_MIN = Int.MIN_VALUE

/**
 * Generates a depth array from DEPTH16 image.
 */
fun getDepthMask(image: Image): Array<Int> {
    val shortDepthBuffer: ShortBuffer =
            image.planes[0].buffer.asShortBuffer()

    val mask: Array<Int> = Array(WIDTH * HEIGHT, {it} )
    for (y in 0..HEIGHT) {
        for (x in 0..WIDTH) {
            val index: Int = y * WIDTH + x
            val depthSample: Short = shortDepthBuffer.get(index)
            val newValue = extractRange(depthSample, 0.1f)
            mask[index] = newValue
        }
    }
    return mask
}

/**
 * Extracts the value encoded in each of the data point in DEPTH16 image.
 */
fun extractRange(sample: Short, confidenceFilter: Float): Int {
    val depthRange = sample.toInt() and 0x1FFF
    val depthConfidence: Short = ((sample.toInt() shr 13) and 0x7).toShort()
    val depthPercentage: Float = if (depthConfidence.equals(0)) 1.toFloat()
    else (depthConfidence - 1) / 7.toFloat()

    RANGE_MAX = Math.max(depthRange, RANGE_MAX)
    RANGE_MIN = Math.max(depthRange, RANGE_MIN)

    return if (depthPercentage > confidenceFilter) depthRange else 0
}

/**
 * Normalizes a value to be within 8bit.
 */
fun normalizeRange(range: Int): Int {
    var normalized: Float = range.toFloat() - RANGE_MIN
    normalized = Math.max(RANGE_MIN.toFloat(), normalized)
    normalized = Math.min(RANGE_MAX.toFloat(), normalized)
    normalized = normalized - RANGE_MIN
    normalized = normalized / (RANGE_MAX - RANGE_MIN) * 255
    return normalized.toInt()
}

/**
 * Converts an array of depth values to a bitmap.
 */
fun convertToRGBBitmap(mask: Array<Int>): Bitmap {
    val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    for (y in 0..HEIGHT) {
        for (x in 0..WIDTH) {
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
    matrix.postRotate(270.toFloat(), centerX.toFloat(), centerY.toFloat())

    return matrix
}

