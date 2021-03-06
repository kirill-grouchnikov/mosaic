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
package org.pushingpixels.aurora.component.model

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Definition of a layout manager for command buttons.
 *
 * @author Kirill Grouchnikov
 */
interface CommandButtonLayoutManager: MeasureScope {
    /**
     * Enumerates the available values for separator orientations.
     */
    enum class CommandButtonSeparatorOrientation {
        /**
         * Vertical separator orientation.
         */
        VERTICAL,

        /**
         * Horizontal separator orientation.
         */
        HORIZONTAL
    }

    /**
     * Layout information on a single line of text.
     *
     * @param text Text itself
     * @param textRect The text rectangle.
     */
    data class TextLayoutInfo(val text: String, var textRect: Rect)

    /**
     * Layout information on different visual parts of a single command button.
     *
     * @param actionClickArea The action area. A mouse click in this area will trigger the action
     * lambda set as [Command.action].
     * @param popupClickArea The popup area. A mouse click in this area will show the popup content
     * associated with the command button.
     * @param separatorArea The separator area. If it's not empty, the command button will show a
     * separator between [.actionClickArea] and
     * [.popupClickArea] on mouse rollover.
     * @param iconRect Rectangle for the command button icon.
     * @param textLayoutInfoList Layout information for the command button text (that can span
     * multiple lines).
     * @param extraTextLayoutInfoList Layout information for the command button extra text
     * (that can span multiple lines).
     * @param popupActionRect Rectangle for the icon associated with the [.popupClickArea].
     * This icon is an arrow indicating that the command button has a popup area.
     * @param isTextInActionArea Indication whether the command button text (rectangles in
     * [.textLayoutInfoList]) belongs in the action area.
     */
    data class CommandButtonLayoutInfo(
        val fullSize: Size,
        val actionClickArea: Rect,
        val popupClickArea: Rect,
        val separatorArea: Rect?,
        val separatorOrientation: CommandButtonSeparatorOrientation?,
        val iconRect: Rect,
        val textLayoutInfoList: List<TextLayoutInfo>,
        val extraTextLayoutInfoList: List<TextLayoutInfo>?,
        val popupActionRect: Rect,
        val isTextInActionArea: Boolean
    )

    /**
     * Returns the preferred icon size of the specified command button when it uses
     * this layout manager.
     *
     * @param commandButton Command button.
     * @return The preferred icon size of the specified command button when it uses
     * this layout manager.
     */
    fun getPreferredIconSize(): Dp

    /**
     * Returns the layout information for the specified command button.
     *
     * @param commandButton Command button.
     * @return The layout information for the specified command button.
     */
    fun getLayoutInfo(
        command: Command,
        presentationModel: CommandPresentationModel,
        paddingValues: PaddingValues
    ): CommandButtonLayoutInfo
}