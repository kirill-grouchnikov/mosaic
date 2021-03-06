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

import androidx.compose.animation.core.*
import androidx.compose.desktop.AppManager
import androidx.compose.desktop.ComposePanel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.pushingpixels.aurora.*
import org.pushingpixels.aurora.component.*
import org.pushingpixels.aurora.component.utils.*
import org.pushingpixels.aurora.icon.AuroraIcon
import org.pushingpixels.aurora.icon.AuroraThemedIcon
import java.awt.*
import javax.swing.JWindow
import kotlin.math.roundToInt

interface CommandActionPreview {
    /**
     * Invoked when a command preview has been activated.
     *
     * @param command Command for which the preview has been activated.
     */
    // TODO - remove nullability when buttons are only created from commands
    fun onCommandPreviewActivated(command: Command?)

    /**
     * Invoked when a command preview has been canceled.
     *
     * @param command Command for which the preview has been canceled.
     */
    // TODO - remove nullability when buttons are only created from commands
    fun onCommandPreviewCanceled(command: Command?)
}

data class Command(
    val text: String,
    val extraText: String? = null,
    val iconFactory: AuroraIcon.Factory?,
    val disabledIconFactory: AuroraIcon.Factory? = null,
    val action: (() -> Unit)? = null,
    val actionPreview: CommandActionPreview? = null,
    var isActionEnabled: State<Boolean>? = null,
    var isActionToggle: Boolean = false,
    var isActionToggleSelected: Boolean = false,
    val secondaryContentModel: CommandMenuContentModel? = null,
    var isSecondaryEnabled: State<Boolean>? = null
)

data class CommandGroup(
    val title: String? = null,
    val command: List<Command>
)

data class CommandMenuContentModel(
    val commands: List<CommandGroup>
) {
    constructor(group: CommandGroup) : this(listOf(group))
}

enum class TextClick {
    ACTION, POPUP
}

data class CommandPresentationModel(
    val popupPlacementStrategy: PopupPlacementStrategy = PopupPlacementStrategy.DOWNWARD,
    val textClick: TextClick = TextClick.ACTION,
    val horizontalGapScaleFactor: Float = 1.0f,
    val verticalGapScaleFactor: Float = 1.0f
)

/**
 * Enumerates the available command button kinds.
 */
enum class CommandButtonKind(val hasAction: Boolean, val hasPopup: Boolean) {
    /**
     * Command button that has only action area.
     */
    ACTION_ONLY(true, false),

    /**
     * Command button that has only popup area.
     */
    POPUP_ONLY(false, true),

    /**
     * Command button that has both action and popup areas, with the main
     * text click activating the action.
     */
    ACTION_AND_POPUP_MAIN_ACTION(true, true),

    /**
     * Command button that has both action and popup areas, with the main
     * text click activating the popup.
     */
    ACTION_AND_POPUP_MAIN_POPUP(true, true);
}

@Immutable
private class SplitButtonDrawingCache(
    val colorScheme: MutableColorScheme = MutableColorScheme(
        displayName = "Internal mutable",
        isDark = false,
        ultraLight = Color.White,
        extraLight = Color.White,
        light = Color.White,
        mid = Color.White,
        dark = Color.White,
        ultraDark = Color.White,
        foreground = Color.Black
    )
)

@Composable
fun AuroraSplitButton(
    command: Command,
    presentationModel: CommandPresentationModel
) {
    val drawingCache = remember { SplitButtonDrawingCache() }

    val actionInteractionSource = remember { MutableInteractionSource() }
    val popupInteractionSource = remember { MutableInteractionSource() }

    var actionRollover by remember { mutableStateOf(false) }
    var popupRollover by remember { mutableStateOf(false) }
    var combinedRollover = derivedStateOf { actionRollover and popupRollover }

    val isActionPressed by actionInteractionSource.collectIsPressedAsState()
    val isPopupPressed by popupInteractionSource.collectIsPressedAsState()

    val currentActionState = remember {
        mutableStateOf(
            ComponentState.getState(
                isEnabled = command.isActionEnabled?.value ?: false,
                isRollover = actionRollover,
                isSelected = command.isActionToggle and command.isActionToggleSelected,
                isPressed = isActionPressed
            )
        )
    }
    val currentPopupState = remember {
        mutableStateOf(
            ComponentState.getState(
                isEnabled = command.isSecondaryEnabled?.value ?: false,
                isRollover = popupRollover,
                isSelected = false,
                isPressed = isPopupPressed
            )
        )
    }

    val decorationAreaType = AuroraSkin.decorationAreaType
    val skinColors = AuroraSkin.colors
    val buttonShaper = AuroraSkin.buttonShaper
    val painters = AuroraSkin.painters

    val auroraTopLeftOffset = remember { AuroraOffset(0.0f, 0.0f) }
    val auroraSize = AuroraSize(0, 0)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textStyle = LocalTextStyle.current
    val resourceLoader = LocalFontLoader.current

    // Transition for the action selection state
    val actionSelectionTransition =
        updateTransition(command.isActionToggle and command.isActionToggleSelected)
    val actionSelectedFraction by actionSelectionTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the action rollover state
    val actionRolloverTransition = updateTransition(actionRollover)
    val actionRolloverFraction by actionRolloverTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the action pressed state
    val actionPressedTransition = updateTransition(isActionPressed)
    val actionPressedFraction by actionPressedTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the action enabled state
    val actionEnabledTransition = updateTransition(command.isActionEnabled?.value ?: false)
    val actionEnabledFraction by actionEnabledTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // TODO - figure out why the animations are not running without looking
    //  at the result (and how it looks like in the new animation APIs)
    val actionTotalFraction =
        actionSelectedFraction + actionRolloverFraction + actionPressedFraction + actionEnabledFraction

    val actionModelStateInfo = remember { ModelStateInfo(currentActionState.value) }
    val actionTransitionInfo = remember { mutableStateOf<TransitionInfo?>(null) }

    StateTransitionTracker(
        modelStateInfo = actionModelStateInfo,
        currentState = currentActionState,
        transitionInfo = actionTransitionInfo,
        enabled = command.isActionEnabled?.value ?: false,
        selected = command.isActionToggle and command.isActionToggleSelected,
        rollover = actionRollover,
        pressed = isActionPressed,
        duration = AuroraSkin.animationConfig.regular
    )

    if (actionTransitionInfo.value != null) {
        LaunchedEffect(currentActionState.value) {
            val transitionFloat = Animatable(actionTransitionInfo.value!!.from)
            val result = transitionFloat.animateTo(
                targetValue = actionTransitionInfo.value!!.to,
                animationSpec = tween(durationMillis = actionTransitionInfo.value!!.duration)
            ) {
                actionModelStateInfo.updateActiveStates(value)
            }

            if (result.endReason == AnimationEndReason.Finished) {
                actionModelStateInfo.updateActiveStates(1.0f)
                actionModelStateInfo.clear(currentActionState.value)
            }
        }
    }

    // Transition for the popup selection state
    val popupSelectionTransition = updateTransition(false)
    val popupSelectedFraction by popupSelectionTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the popup rollover state
    val popupRolloverTransition = updateTransition(popupRollover)
    val popupRolloverFraction by popupRolloverTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the popup pressed state
    val popupPressedTransition = updateTransition(isPopupPressed)
    val popupPressedFraction by popupPressedTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the popup enabled state
    val popupEnabledTransition = updateTransition(command.isSecondaryEnabled?.value ?: false)
    val popupEnabledFraction by popupEnabledTransition.animateFloat(transitionSpec = {
        tween(durationMillis = AuroraSkin.animationConfig.regular)
    }) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // TODO - figure out why the animations are not running without looking
    //  at the result (and how it looks like in the new animation APIs)
    val totalPopupFraction =
        popupSelectedFraction + popupRolloverFraction + popupPressedFraction + popupEnabledFraction

    val popupModelStateInfo = remember { ModelStateInfo(currentPopupState.value) }
    val popupTransitionInfo = remember { mutableStateOf<TransitionInfo?>(null) }

    StateTransitionTracker(
        modelStateInfo = popupModelStateInfo,
        currentState = currentPopupState,
        transitionInfo = popupTransitionInfo,
        enabled = command.isSecondaryEnabled?.value ?: false,
        selected = false,
        rollover = popupRollover,
        pressed = isPopupPressed,
        duration = AuroraSkin.animationConfig.regular
    )

    if (popupTransitionInfo.value != null) {
        LaunchedEffect(currentPopupState.value) {
            val transitionFloat = Animatable(popupTransitionInfo.value!!.from)
            val result = transitionFloat.animateTo(
                targetValue = popupTransitionInfo.value!!.to,
                animationSpec = tween(durationMillis = popupTransitionInfo.value!!.duration)
            ) {
                popupModelStateInfo.updateActiveStates(value)
            }

            if (result.endReason == AnimationEndReason.Finished) {
                popupModelStateInfo.updateActiveStates(1.0f)
                popupModelStateInfo.clear(currentPopupState.value)
            }
        }
    }

    val layoutManager = CommandButtonLayoutManagerMedium(
        layoutDirection = layoutDirection,
        _density = density,
        textStyle = textStyle,
        resourceLoader = resourceLoader
    )
    // TODO - cache layout info if command info, presentation info or incoming
    //  constraints haven't changed
    val layoutInfo = layoutManager.getLayoutInfo(
        command = command,
        presentationModel = presentationModel,
        paddingValues = ButtonSizingConstants.DefaultButtonContentPadding
    )

    Layout(
        modifier = Modifier.splitButtonLocator(auroraTopLeftOffset, auroraSize),
        content = {
            Box(
                modifier = Modifier
                    // TODO - this needs to be toggleable for toggleable action
                    // TODO - handle commands with no action (only secondary / popup content)
                    .clickable(
                        enabled = command.isActionEnabled?.value ?: false,
                        onClick = command.action ?: {},
                        interactionSource = actionInteractionSource,
                        indication = null
                    ).pointerMoveFilter(onEnter = {
                        actionRollover = true
                        false
                    }, onExit = {
                        actionRollover = false
                        false
                    }, onMove = {
                        false
                    })
            ) {
                // Populate the cached color scheme for filling the button container
                // based on the current model state info
                populateColorScheme(
                    drawingCache.colorScheme,
                    actionModelStateInfo,
                    currentActionState.value,
                    decorationAreaType,
                    ColorSchemeAssociationKind.FILL
                )
                // And retrieve the container fill colors
                val fillUltraLight = drawingCache.colorScheme.ultraLightColor
                val fillExtraLight = drawingCache.colorScheme.extraLightColor
                val fillLight = drawingCache.colorScheme.lightColor
                val fillMid = drawingCache.colorScheme.midColor
                val fillDark = drawingCache.colorScheme.darkColor
                val fillUltraDark = drawingCache.colorScheme.ultraDarkColor
                val fillIsDark = drawingCache.colorScheme.isDark

                // Populate the cached color scheme for drawing the button border
                // based on the current model state info
                populateColorScheme(
                    drawingCache.colorScheme,
                    actionModelStateInfo,
                    currentActionState.value,
                    decorationAreaType,
                    ColorSchemeAssociationKind.BORDER
                )
                // And retrieve the border colors
                val borderUltraLight = drawingCache.colorScheme.ultraLightColor
                val borderExtraLight = drawingCache.colorScheme.extraLightColor
                val borderLight = drawingCache.colorScheme.lightColor
                val borderMid = drawingCache.colorScheme.midColor
                val borderDark = drawingCache.colorScheme.darkColor
                val borderUltraDark = drawingCache.colorScheme.ultraDarkColor
                val borderIsDark = drawingCache.colorScheme.isDark

                val fillPainter = painters.fillPainter
                val borderPainter = painters.borderPainter

                // TODO: handle action alpha
                val alpha: Float = 1.0f

                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = this.size.width
                    val height = this.size.height
                    // TODO - revisit this
                    val sides = if (layoutInfo.popupClickArea.width == 0.0f) ButtonSides()
                    else ButtonSides(openSides = setOf(Side.END), straightSides = setOf(Side.END))

                    val openDelta = 3
                    // TODO - add RTL support
                    val deltaLeft = if (sides.openSides.contains(Side.START)) openDelta else 0
                    val deltaRight = if (sides.openSides.contains(Side.END)) openDelta else 0
                    val deltaTop = if (sides.openSides.contains(Side.TOP)) openDelta else 0
                    val deltaBottom = if (sides.openSides.contains(Side.BOTTOM)) openDelta else 0

                    withTransform({
                        clipRect(
                            left = 0.0f,
                            top = 0.0f,
                            right = width,
                            bottom = height,
                            clipOp = ClipOp.Intersect
                        )
                        translate(left = -deltaLeft.toFloat(), top = -deltaTop.toFloat())
                    }) {
                        val outline = buttonShaper.getButtonOutline(
                            width = width + deltaLeft + deltaRight,
                            height = height + deltaTop + deltaBottom,
                            extraInsets = 0.5f,
                            isInner = false,
                            sides = sides,
                            drawScope = this
                        )

                        val outlineBoundingRect = outline.bounds
                        if (outlineBoundingRect.isEmpty) {
                            return@withTransform
                        }

                        // Populate the cached color scheme for filling the button container
                        drawingCache.colorScheme.ultraLight = fillUltraLight
                        drawingCache.colorScheme.extraLight = fillExtraLight
                        drawingCache.colorScheme.light = fillLight
                        drawingCache.colorScheme.mid = fillMid
                        drawingCache.colorScheme.dark = fillDark
                        drawingCache.colorScheme.ultraDark = fillUltraDark
                        drawingCache.colorScheme.isDark = fillIsDark
                        drawingCache.colorScheme.foreground = Color.Black
                        fillPainter.paintContourBackground(
                            this, this.size, outline, drawingCache.colorScheme, alpha
                        )

                        // Populate the cached color scheme for drawing the button border
                        drawingCache.colorScheme.ultraLight = borderUltraLight
                        drawingCache.colorScheme.extraLight = borderExtraLight
                        drawingCache.colorScheme.light = borderLight
                        drawingCache.colorScheme.mid = borderMid
                        drawingCache.colorScheme.dark = borderDark
                        drawingCache.colorScheme.ultraDark = borderUltraDark
                        drawingCache.colorScheme.isDark = borderIsDark
                        drawingCache.colorScheme.foreground = Color.Black

                        val innerOutline =
                            if (borderPainter.isPaintingInnerOutline) buttonShaper.getButtonOutline(
                                width = width + deltaLeft + deltaRight,
                                height = height + deltaTop + deltaBottom,
                                extraInsets = 1.0f,
                                isInner = true,
                                sides = sides,
                                drawScope = this
                            ) else null

                        borderPainter.paintBorder(
                            this, this.size, outline, innerOutline, drawingCache.colorScheme, alpha
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.clickable(
                    enabled = command.isSecondaryEnabled?.value ?: false, onClick = {
                        // TODO - move off of JWindow when https://github.com/JetBrains/compose-jb/issues/195
                        //  is addressed
                        val jwindow = AuroraPopupWindow()
                        jwindow.focusableWindowState = false
                        jwindow.type = Window.Type.POPUP
                        jwindow.isAlwaysOnTop = true

                        // TODO - hopefully temporary. Mark the popup window as fully transparent
                        //  so that when it is globally positioned, we can size it to the actual
                        //  content and make it fully opaque
                        jwindow.opacity = 0.0f

                        val auroraWindow = AppManager.focusedWindow!!.window
                        val locationOnScreen = auroraWindow.locationOnScreen

                        // anchor the popup window to the bottom left corner of the component
                        // in screen coordinates
                        // TODO - figure out the sizing (see above)
                        jwindow.setBounds(
                            (locationOnScreen.x + auroraTopLeftOffset.x / density.density).toInt(),
                            (locationOnScreen.y + auroraTopLeftOffset.y / density.density).toInt(),
                            1000,
                            1000
                        )

                        val popupContent = ComposePanel()
                        popupContent.setContent {
                            CompositionLocalProvider(
                                LocalDecorationAreaType provides decorationAreaType,
                                LocalSkinColors provides skinColors,
                                LocalButtonShaper provides buttonShaper,
                                LocalPainters provides painters,
                                LocalAnimationConfig provides AuroraSkin.animationConfig
                            ) {
                                SplitButtonPopupContent(
                                    window = jwindow,
                                    anchorSize = auroraSize,
                                    command = command,
                                    presentationModel = presentationModel
                                )
                            }
                        }
                        jwindow.contentPane.add(popupContent, BorderLayout.CENTER)
                        jwindow.invalidate()
                        jwindow.validate()
                        jwindow.isVisible = true
                        jwindow.pack()
                    }, interactionSource = popupInteractionSource, indication = null
                ).pointerMoveFilter(onEnter = {
                    popupRollover = true
                    false
                }, onExit = {
                    popupRollover = false
                    false
                }, onMove = {
                    false
                })
            ) {
                // Populate the cached color scheme for filling the button container
                // based on the current model state info
                populateColorScheme(
                    drawingCache.colorScheme,
                    popupModelStateInfo,
                    currentPopupState.value,
                    decorationAreaType,
                    ColorSchemeAssociationKind.FILL
                )
                // And retrieve the container fill colors
                val fillUltraLight = drawingCache.colorScheme.ultraLightColor
                val fillExtraLight = drawingCache.colorScheme.extraLightColor
                val fillLight = drawingCache.colorScheme.lightColor
                val fillMid = drawingCache.colorScheme.midColor
                val fillDark = drawingCache.colorScheme.darkColor
                val fillUltraDark = drawingCache.colorScheme.ultraDarkColor
                val fillIsDark = drawingCache.colorScheme.isDark

                // Populate the cached color scheme for drawing the button border
                // based on the current model state info
                populateColorScheme(
                    drawingCache.colorScheme,
                    popupModelStateInfo,
                    currentPopupState.value,
                    decorationAreaType,
                    ColorSchemeAssociationKind.BORDER
                )
                // And retrieve the border colors
                val borderUltraLight = drawingCache.colorScheme.ultraLightColor
                val borderExtraLight = drawingCache.colorScheme.extraLightColor
                val borderLight = drawingCache.colorScheme.lightColor
                val borderMid = drawingCache.colorScheme.midColor
                val borderDark = drawingCache.colorScheme.darkColor
                val borderUltraDark = drawingCache.colorScheme.ultraDarkColor
                val borderIsDark = drawingCache.colorScheme.isDark

                val fillPainter = painters.fillPainter
                val borderPainter = painters.borderPainter

                // TODO: handle popup alpha
                val alpha: Float = 1.0f

                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = this.size.width
                    val height = this.size.height
                    // TODO - revisit this
                    val sides = if (layoutInfo.actionClickArea.width == 0.0f) ButtonSides()
                    else ButtonSides(
                        openSides = setOf(Side.START),
                        straightSides = setOf(Side.START)
                    )

                    val openDelta = 3
                    // TODO - add RTL support
                    val deltaLeft = if (sides.openSides.contains(Side.START)) openDelta else 0
                    val deltaRight = if (sides.openSides.contains(Side.END)) openDelta else 0
                    val deltaTop = if (sides.openSides.contains(Side.TOP)) openDelta else 0
                    val deltaBottom = if (sides.openSides.contains(Side.BOTTOM)) openDelta else 0

                    withTransform({
                        clipRect(
                            left = 0.0f,
                            top = 0.0f,
                            right = width,
                            bottom = height,
                            clipOp = ClipOp.Intersect
                        )
                        translate(left = -deltaLeft.toFloat(), top = -deltaTop.toFloat())
                    }) {
                        val outline = buttonShaper.getButtonOutline(
                            width = width + deltaLeft + deltaRight,
                            height = height + deltaTop + deltaBottom,
                            extraInsets = 0.5f,
                            isInner = false,
                            sides = sides,
                            drawScope = this
                        )

                        val outlineBoundingRect = outline.bounds
                        if (outlineBoundingRect.isEmpty) {
                            return@withTransform
                        }

                        // Populate the cached color scheme for filling the button container
                        drawingCache.colorScheme.ultraLight = fillUltraLight
                        drawingCache.colorScheme.extraLight = fillExtraLight
                        drawingCache.colorScheme.light = fillLight
                        drawingCache.colorScheme.mid = fillMid
                        drawingCache.colorScheme.dark = fillDark
                        drawingCache.colorScheme.ultraDark = fillUltraDark
                        drawingCache.colorScheme.isDark = fillIsDark
                        drawingCache.colorScheme.foreground = Color.Black
                        fillPainter.paintContourBackground(
                            this, this.size, outline, drawingCache.colorScheme, alpha
                        )

                        // Populate the cached color scheme for drawing the button border
                        drawingCache.colorScheme.ultraLight = borderUltraLight
                        drawingCache.colorScheme.extraLight = borderExtraLight
                        drawingCache.colorScheme.light = borderLight
                        drawingCache.colorScheme.mid = borderMid
                        drawingCache.colorScheme.dark = borderDark
                        drawingCache.colorScheme.ultraDark = borderUltraDark
                        drawingCache.colorScheme.isDark = borderIsDark
                        drawingCache.colorScheme.foreground = Color.Black

                        val innerOutline =
                            if (borderPainter.isPaintingInnerOutline) buttonShaper.getButtonOutline(
                                width = width + deltaLeft + deltaRight,
                                height = height + deltaTop + deltaBottom,
                                extraInsets = 1.0f,
                                isInner = true,
                                sides = sides,
                                drawScope = this
                            ) else null

                        borderPainter.paintBorder(
                            this, this.size, outline, innerOutline, drawingCache.colorScheme, alpha
                        )
                    }
                }
            }
            // Text content can be in action or popup area
            val modelStateInfoForText =
                if (layoutInfo.isTextInActionArea) actionModelStateInfo else popupModelStateInfo
            val currStateForText =
                if (layoutInfo.isTextInActionArea) currentActionState.value else currentPopupState.value
            SplitButtonIconContent(
                command,
                layoutManager.getPreferredIconSize(),
                modelStateInfoForText,
                currStateForText
            )
            SplitButtonTextContent(command, modelStateInfoForText, currStateForText)
            if (layoutInfo.popupActionRect.width > 0) {
                SplitButtonPopupIconContent(popupModelStateInfo, currentPopupState.value)
            }
        }) { measurables, _ ->

        // Measure the action and popup boxes
        val actionMeasurable = measurables[0]
        val actionPlaceable = actionMeasurable.measure(
            Constraints.fixed(
                width = layoutInfo.actionClickArea.width.roundToInt(),
                height = layoutInfo.actionClickArea.height.roundToInt()
            )
        )
        val popupMeasurable = measurables[1]
        val popupPlaceable = popupMeasurable.measure(
            Constraints.fixed(
                width = layoutInfo.popupClickArea.width.roundToInt(),
                height = layoutInfo.popupClickArea.height.roundToInt()
            )
        )
        val iconMeasurable = measurables[2]
        val iconPlaceable = iconMeasurable.measure(
            Constraints.fixed(
                width = layoutInfo.iconRect.width.roundToInt(),
                height = layoutInfo.iconRect.height.roundToInt()
            )
        )
        val textMeasurable = measurables[3]
        val textPlaceable = textMeasurable.measure(
            Constraints.fixed(
                width = layoutInfo.textLayoutInfoList[0].textRect.width.roundToInt(),
                height = layoutInfo.textLayoutInfoList[0].textRect.height.roundToInt()
            )
        )
        var popupIconPlaceable: Placeable? = null
        if (layoutInfo.popupActionRect.width > 0) {
            val popupIconMeasurable = measurables[4]
            popupIconPlaceable = popupIconMeasurable.measure(
                Constraints.fixed(
                    width = layoutInfo.popupActionRect.width.roundToInt(),
                    height = layoutInfo.popupActionRect.height.roundToInt()
                )
            )
        }

        layout(
            width = layoutInfo.fullSize.width.toInt(), height = layoutInfo.fullSize.height.toInt()
        ) {
            actionPlaceable.placeRelative(
                x = layoutInfo.actionClickArea.left.roundToInt(),
                y = layoutInfo.actionClickArea.top.roundToInt()
            )
            popupPlaceable.placeRelative(
                x = layoutInfo.popupClickArea.left.roundToInt(),
                y = layoutInfo.popupClickArea.top.roundToInt()
            )
            iconPlaceable.placeRelative(
                x = layoutInfo.iconRect.left.roundToInt(),
                y = layoutInfo.iconRect.top.roundToInt()
            )
            textPlaceable?.placeRelative(
                x = layoutInfo.textLayoutInfoList[0].textRect.left.roundToInt(),
                y = layoutInfo.textLayoutInfoList[0].textRect.top.roundToInt()
            )
            popupIconPlaceable?.placeRelative(
                x = layoutInfo.popupActionRect.left.roundToInt(),
                y = layoutInfo.popupActionRect.top.roundToInt()
            )
        }
    }
}

@Composable
private fun SplitButtonTextContent(
    command: Command, modelStateInfo: ModelStateInfo, currState: ComponentState
) {
    val decorationAreaType = AuroraSkin.decorationAreaType
    val skinColors = AuroraSkin.colors

    // Compute the text color based on the passed model state (which can be action
    // or popup)
    val textColor = getTextColor(
        modelStateInfo = modelStateInfo,
        currState = currState,
        skinColors = skinColors,
        decorationAreaType = decorationAreaType,
        isTextInFilledArea = true
    )

    // Pass our text color and model state snapshot to the children
    // TODO - pass the right model state info snapshot
    CompositionLocalProvider(
        LocalTextColor provides textColor,
        LocalModelStateInfoSnapshot provides ModelStateInfoSnapshot(
            currModelState = ComponentState.ENABLED,
            stateContributionMap = emptyMap(),
            activeStrength = 1.0f
        )
    ) {
        AuroraText(command.text)
    }
}

@Composable
private fun SplitButtonIconContent(
    command: Command, iconSize: Dp, modelStateInfo: ModelStateInfo, currState: ComponentState
) {
    if (command.iconFactory != null) {
        // TODO - remember based on the icon size
        val icon = remember { command.iconFactory.createNewIcon() }
        // TODO - why does this need to be divided by density?
        icon.setSize(
            iconSize / LocalDensity.current.density,
            iconSize / LocalDensity.current.density
        )
        val decorationAreaType = AuroraSkin.decorationAreaType
        val skinColors = AuroraSkin.colors

        // Compute the text color based on the passed model state (which can be action
        // or popup)
        val textColor = getTextColor(
            modelStateInfo = modelStateInfo,
            currState = currState,
            skinColors = skinColors,
            decorationAreaType = decorationAreaType,
            isTextInFilledArea = true
        )

        // Pass our text color and model state snapshot to the children
        // TODO - pass the right model state info snapshot
        CompositionLocalProvider(
            LocalTextColor provides textColor,
            LocalModelStateInfoSnapshot provides ModelStateInfoSnapshot(
                currModelState = ComponentState.ENABLED,
                stateContributionMap = emptyMap(),
                activeStrength = 1.0f
            )
        ) {
            AuroraThemedIcon(icon = icon)
        }
    }
}

@Composable
private fun SplitButtonPopupIconContent(
    modelStateInfo: ModelStateInfo, currState: ComponentState
) {
    val decorationAreaType = AuroraSkin.decorationAreaType

    val arrowColor = getStateAwareColor(
        modelStateInfo,
        currState,
        decorationAreaType,
        ColorSchemeAssociationKind.MARK
    ) { it.markColor }

    Box {
        Canvas(modifier = Modifier.matchParentSize()) {
            val arrowWidth = ComboBoxSizingConstants.DefaultComboBoxArrowWidth.toPx()
            val arrowHeight = ComboBoxSizingConstants.DefaultComboBoxArrowHeight.toPx()
            translate(
                left = (size.width - arrowWidth) / 2.0f,
                top = (size.height - arrowHeight) / 2.0f
            ) {
                drawArrow(
                    drawScope = this,
                    width = arrowWidth,
                    height = arrowHeight,
                    strokeWidth = 2.0.dp.toPx(),
                    direction = PopupPlacementStrategy.DOWNWARD,
                    layoutDirection = layoutDirection,
                    color = arrowColor
                )
            }
        }
    }
}

@Composable
private fun SplitButtonPopupContent(
    window: JWindow,
    anchorSize: AuroraSize,
    command: Command,
    presentationModel: CommandPresentationModel
) {
    val borderScheme = AuroraSkin.colors.getColorScheme(
        decorationAreaType = DecorationAreaType.NONE,
        associationKind = ColorSchemeAssociationKind.BORDER,
        componentState = ComponentState.ENABLED
    )
    val popupBorderColor = AuroraSkin.painters.borderPainter.getRepresentativeColor(borderScheme)
    val density = LocalDensity.current.density
    val contentSize = AuroraSize(0, 0)
    Box(modifier = Modifier.auroraBackground(window = window).onGloballyPositioned {
        // Get the size of the content and update the popup window bounds
        val popupWidth = (contentSize.width / density).toInt()
        val popupHeight = (contentSize.height / density).toInt()

        val popupRect = Rectangle(
            window.x,
            window.y + (anchorSize.height / (2 * density)).toInt(),
            popupWidth,
            popupHeight
        )

        // Make sure the popup stays in screen bounds
        val screenBounds = window.graphicsConfiguration.bounds
        if (popupRect.x < 0) {
            popupRect.translate(-popupRect.x, 0)
        }
        if ((popupRect.x + popupRect.width) > screenBounds.width) {
            popupRect.translate(screenBounds.width - popupRect.x - popupRect.width, 0)
        }
        if (popupRect.y < 0) {
            popupRect.translate(0, -popupRect.y)
        }
        if ((popupRect.y + popupRect.height) > screenBounds.height) {
            popupRect.translate(0, screenBounds.height - popupRect.y - popupRect.height)
        }

        window.bounds = popupRect
        window.opacity = 1.0f
        window.preferredSize = Dimension(popupRect.width, popupRect.height)
        window.size = Dimension(popupRect.width, popupRect.height)
        window.invalidate()
        window.validate()
    }) {
        Canvas(Modifier.matchParentSize()) {
            val outline = Outline.Rectangle(
                rect = Rect(
                    left = 0.5f, top = 0.5f, right = size.width - 0.5f, bottom = size.height - 0.5f
                )
            )
            drawOutline(
                outline = outline, color = popupBorderColor, style = Stroke(width = 1.0f)
            )
        }
        SplitButtonPopupColumn(contentSize = contentSize) {
            for (item in command.secondaryContentModel!!.commands[0].command) {
                AuroraMenuButton(
                    enabled = true,
                    onClick = {
                        // TODO - support nested secondary content
                        window.dispose()
                        item.action?.invoke()
                    },
                    sides = ButtonSides(straightSides = Side.values().toSet()),
                    backgroundAppearanceStrategy = BackgroundAppearanceStrategy.FLAT,
                    sizingStrategy = ButtonSizingStrategy.EXTENDED,
                ) {
                    AuroraText(text = item.text, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SplitButtonPopupColumn(contentSize: AuroraSize, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        // The column width is determined by the widest child
        val contentTotalWidth = measurables.maxOf { it.maxIntrinsicWidth(Int.MAX_VALUE) }

        val placeables = measurables.map { measurable ->
            // Measure each child with fixed (widest) width
            measurable.measure(Constraints.fixedWidth(contentTotalWidth))
        }

        // The children are laid out in a column
        val contentMaxHeight = placeables.sumBy { it.height }
        contentSize.width = contentTotalWidth
        contentSize.height = contentMaxHeight

        layout(width = contentTotalWidth, height = contentMaxHeight) {
            var yPosition = 0

            // TODO - support RTL
            placeables.forEach { placeable ->
                placeable.placeRelative(
                    x = 0, y = yPosition
                )
                yPosition += placeable.height
            }
        }
    }
}

private class SplitButtonBoxLocator(val topLeftOffset: AuroraOffset, val size: AuroraSize) :
    OnGloballyPositionedModifier {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // Convert the top left corner of the component to the root coordinates
        val converted = coordinates.localToRoot(Offset.Zero)
        topLeftOffset.x = converted.x
        topLeftOffset.y = converted.y

        // And store the component size
        size.width = coordinates.size.width
        size.height = coordinates.size.height
    }
}

@Composable
private fun Modifier.splitButtonLocator(topLeftOffset: AuroraOffset, size: AuroraSize) = this.then(
    SplitButtonBoxLocator(topLeftOffset, size)
)


