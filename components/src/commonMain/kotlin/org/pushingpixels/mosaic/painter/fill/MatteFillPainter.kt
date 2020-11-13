/*
 * Copyright (c) 2020 Mosaic, Kirill Grouchnikov. All Rights Reserved.
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
package org.pushingpixels.mosaic.painter.fill

import androidx.compose.ui.graphics.Color
import org.pushingpixels.mosaic.colorscheme.MosaicColorScheme
import org.pushingpixels.mosaic.utils.getInterpolatedColor

/**
 * Fill painter that returns images with matte appearance. This class is part of
 * officially supported API.
 *
 * @author Kirill Grouchnikov
 */
class MatteFillPainter : ClassicFillPainter() {
    override val displayName: String
        get() = "Matte"

    override fun getTopFillColor(fillScheme: MosaicColorScheme): Color {
        return getInterpolatedColor(
            super.getBottomFillColor(fillScheme),
            super.getMidFillColorTop(fillScheme), 0.5f
        )
    }

    override fun getMidFillColorTop(fillScheme: MosaicColorScheme): Color {
        return getInterpolatedColor(
            super.getMidFillColorTop(fillScheme),
            super.getBottomFillColor(fillScheme), 0.7f
        )
    }

    override fun getBottomFillColor(fillScheme: MosaicColorScheme): Color {
        return super.getMidFillColorTop(fillScheme)
    }

    companion object {
        /**
         * Reusable instance of this painter.
         */
        val INSTANCE = MatteFillPainter()
    }
}