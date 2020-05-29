package sg.edu.woundanalysis

import android.media.Image
import java.nio.ShortBuffer
import kotlin.experimental.and

class TofUtil(image : Image) {

    init {

    }

    companion object {
        const val WIDTH : Int = 200
        const val HEIGHT : Int = 180

        fun getDepthMask(image : Image) : Array<Int> {
            val shortDepthBuffer : ShortBuffer =
            image.getPlanes()[0].getBuffer().asShortBuffer()

            val mask : Array<Int> = Array(WIDTH * HEIGHT)
            for (y in 0..HEIGHT) {
                for (x in 0..WIDTH) {
                    val index : Int = y * WIDTH + x
                    val depthSample : Short = shortDepthBuffer.get(index)
                    val newValue = extractRange(depthSample, 0.1f)
                    mask[index] = newValue
                }
            }

            return mask
        }

        fun extractRange(sample : Short, confidenceFilter : Float) : Int {
            val depthRange = sample and 0x1FFF
            return depthRange.toInt()
        }


    }
}