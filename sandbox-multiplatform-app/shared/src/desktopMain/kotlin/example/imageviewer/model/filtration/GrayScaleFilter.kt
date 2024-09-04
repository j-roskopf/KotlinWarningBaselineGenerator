package example.imageviewer.model.filtration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import example.imageviewer.core.BitmapFilter
import example.imageviewer.utils.applyGrayScaleFilter

class GrayScaleFilter : BitmapFilter {

    init {
        val test = 3
        if(3 != null) {
            println(test)
        }
    }

    override fun apply(bitmap: ImageBitmap): ImageBitmap {
        return applyGrayScaleFilter(bitmap.toAwtImage()).toComposeImageBitmap()
    }
}
