/*
 * Copyright (c) 2020-2021 Aurora, Kirill Grouchnikov. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  o Neither the name of the copyright holder nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.pushingpixels.aurora.painter.border

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import org.pushingpixels.aurora.colorscheme.AuroraColorScheme
import org.pushingpixels.aurora.common.HashMapKey
import org.pushingpixels.aurora.common.interpolateTowards

/**
 * Delegate border painter that allows tweaking the visual appearance of borders.
 *
 * @author Kirill Grouchnikov
 */
class DelegateFractionBasedBorderPainter(
    override val displayName: String,
    val delegate: FractionBasedBorderPainter,
    val masks: IntArray,
    val transform: (AuroraColorScheme) -> AuroraColorScheme
) : AuroraBorderPainter {
    override val isPaintingInnerOutline = false

    override fun paintBorder(
        drawScope: DrawScope,
        size: Size,
        outline: Outline,
        outlineInner: Outline?,
        borderScheme: AuroraColorScheme,
        alpha: Float
    ) {
        // shift the scheme
        val scheme = getShiftScheme(borderScheme)
        val fractions = delegate.getFractions()
        val colorQueries = delegate.getColorQueries()
        val borderColors = mutableListOf<Color>()
        for (i in fractions.indices) {
            // Get the matching color
            val transformed = colorQueries[i].invoke(scheme)
            // Transform to an ARGB integer
            val transformedArgb = (
                    ((transformed.alpha * 255.0f + 0.5f).toInt() shl 24) or
                            ((transformed.red * 255.0f + 0.5f).toInt() shl 16) or
                            ((transformed.green * 255.0f + 0.5f).toInt() shl 8) or
                            (transformed.blue * 255.0f + 0.5f).toInt()
                    )
            // And apply the mask
            borderColors.add(Color(value = (transformedArgb.toULong() and masks[i].toULong()) shl 32))
        }

        with(drawScope) {
            drawOutline(
                outline = outline,
                style = Stroke(width = 1.0f),
                brush = ShaderBrush(
                    LinearGradientShader(
                        from = Offset.Zero,
                        to = Offset(0.0f, size.height),
                        colors = borderColors,
                        colorStops = fractions.toList(),
                        tileMode = TileMode.Repeated
                    )
                ),
                alpha = alpha
            )
        }
    }

    /**
     * Retrieves a transformed color scheme.
     *
     * @param orig Original color scheme.
     * @return Transformed color scheme.
     */
    private fun getShiftScheme(orig: AuroraColorScheme): AuroraColorScheme {
        val key = HashMapKey(orig.displayName, displayName, transform)
        var result = transformMap[key]
        if (result == null) {
            result = transform.invoke(orig)
            transformMap[key] = result
        }
        return result
    }

    override fun getRepresentativeColor(borderScheme: AuroraColorScheme): Color {
        val fractions = delegate.getFractions()
        val colorQueries = delegate.getColorQueries()

        for (i in 0 until fractions.size - 1) {
            val fractionLow = fractions[i]
            val fractionHigh = fractions[i + 1]
            if (fractionLow == 0.5f) {
                // Get the matching color
                val transformed = colorQueries[i].invoke(borderScheme)
                // Transform to an ARGB integer
                val transformedArgb = (
                        ((transformed.alpha * 255.0f + 0.5f).toInt() shl 24) or
                                ((transformed.red * 255.0f + 0.5f).toInt() shl 16) or
                                ((transformed.green * 255.0f + 0.5f).toInt() shl 8) or
                                (transformed.blue * 255.0f + 0.5f).toInt()
                        )
                // And apply the mask
                return Color(value = (transformedArgb.toULong() and masks[i].toULong()) shl 32)
            }
            if (fractionHigh == 0.5f) {
                // Get the matching color
                val transformed = colorQueries[i + 1].invoke(borderScheme)
                // Transform to an ARGB integer
                val transformedArgb = (
                        ((transformed.alpha * 255.0f + 0.5f).toInt() shl 24) or
                                ((transformed.red * 255.0f + 0.5f).toInt() shl 16) or
                                ((transformed.green * 255.0f + 0.5f).toInt() shl 8) or
                                (transformed.blue * 255.0f + 0.5f).toInt()
                        )
                // And apply the mask
                return Color(value = (transformedArgb.toULong() and masks[i + 1].toULong()) shl 32)
            }
            if (fractionLow < 0.5f || fractionHigh > 0.5f) {
                continue
            }
            // current range contains 0.5f

            // Get the matching low color
            val transformedLow = colorQueries[i].invoke(borderScheme)
            // Transform to an ARGB integer
            val transformedLowArgb = (
                    ((transformedLow.alpha * 255.0f + 0.5f).toInt() shl 24) or
                            ((transformedLow.red * 255.0f + 0.5f).toInt() shl 16) or
                            ((transformedLow.green * 255.0f + 0.5f).toInt() shl 8) or
                            (transformedLow.blue * 255.0f + 0.5f).toInt()
                    )
            // And apply the mask
            val colorLow = Color(value = (transformedLowArgb.toULong() and masks[i].toULong()) shl 32)

            // Get the matching high color
            val transformedHigh = colorQueries[i + 1].invoke(borderScheme)
            // Transform to an ARGB integer
            val transformedHighArgb = (
                    ((transformedHigh.alpha * 255.0f + 0.5f).toInt() shl 24) or
                            ((transformedHigh.red * 255.0f + 0.5f).toInt() shl 16) or
                            ((transformedHigh.green * 255.0f + 0.5f).toInt() shl 8) or
                            (transformedHigh.blue * 255.0f + 0.5f).toInt()
                    )
            // And apply the mask
            val colorHigh = Color(value = (transformedHighArgb.toULong() and masks[i + 1].toULong()) shl 32)

            val colorLowLikeness = (0.5f - fractionLow) / (fractionHigh - fractionLow)
            return colorLow.interpolateTowards(colorHigh, colorLowLikeness)
        }
        throw IllegalStateException("Could not find representative color")

    }

    companion object {
        /**
         * Map of transformed color schemes (to speed up the subsequent lookups).
         */
        private val transformMap = hashMapOf<HashMapKey, AuroraColorScheme>()
    }
}
