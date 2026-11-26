package com.readyroom.app.ui.scan

/**
 * Maps frame-pixel coordinates onto the preview's on-screen box.
 *
 * `PreviewView` defaults to `FILL_CENTER`: it scales the camera image until it
 * covers the view, then crops the overflow. So a box drawn directly at
 * detection coordinates would sit in the wrong place — it has to be scaled by
 * the same factor and shifted by the same crop offset the preview applied.
 *
 * Pure arithmetic so it can be tested without a device.
 */
data class OverlayMapping(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun mapX(frameX: Float): Float = frameX * scale + offsetX

    fun mapY(frameY: Float): Float = frameY * scale + offsetY

    companion object {
        /**
         * Computes the mapping for a frame shown in a view of [viewWidth] x
         * [viewHeight] under FILL_CENTER.
         *
         * @param frameWidth width of the analysed frame in pixels
         * @param frameHeight height of the analysed frame in pixels
         */
        fun fillCenter(
            frameWidth: Int,
            frameHeight: Int,
            viewWidth: Float,
            viewHeight: Float,
        ): OverlayMapping {
            if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
                return OverlayMapping(1f, 0f, 0f)
            }

            // Scale until the frame covers the view; the larger ratio wins.
            val scale = maxOf(
                viewWidth / frameWidth,
                viewHeight / frameHeight,
            )

            // The overflow is centred, so half of it hangs off each edge.
            val offsetX = (viewWidth - frameWidth * scale) / 2f
            val offsetY = (viewHeight - frameHeight * scale) / 2f

            return OverlayMapping(scale, offsetX, offsetY)
        }
    }
}
