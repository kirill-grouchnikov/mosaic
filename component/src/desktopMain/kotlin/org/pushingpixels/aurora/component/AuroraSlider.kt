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
package org.pushingpixels.aurora.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.unit.dp
import org.pushingpixels.aurora.AuroraSkin
import org.pushingpixels.aurora.ColorSchemeAssociationKind
import org.pushingpixels.aurora.ComponentState
import org.pushingpixels.aurora.Side
import org.pushingpixels.aurora.common.withAlpha
import org.pushingpixels.aurora.component.utils.*
import org.pushingpixels.aurora.painter.fill.ClassicFillPainter
import org.pushingpixels.aurora.utils.getBaseOutline
import kotlin.math.roundToInt

object SliderConstants {
    val DefaultSliderContentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 8.dp)
    val DefaultWidth = 240.dp
    val ThumbFullSize = 18.dp
    val TrackHeight = 6.dp
    val TrackTickGap = 4.dp
    val TickHeight = 8.dp
}

@Composable
fun AuroraSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onTriggerValueChange: (Float) -> Unit,
    onValueChangeEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tickSteps: Int = 0, // Zero means continuous slider value range
    snapToTicks: Boolean = false,
    drawTicks: Boolean = false
) {
    require((value >= valueRange.start) && (value <= valueRange.endInclusive)) {
        "Value $value not in range ${valueRange.start}..${valueRange.endInclusive}"
    }
    require(tickSteps >= 0) {
        "Cannot have negative tick steps"
    }

    AuroraSlider(
        sliderValue = value,
        sliderValueRange = valueRange,
        onTriggerValueChange = onTriggerValueChange,
        onValueChangeEnd = onValueChangeEnd,
        modifier = modifier,
        enabled = enabled,
        tickSteps = tickSteps,
        snapToTicks = snapToTicks,
        drawTicks = drawTicks,
        interactionSource = remember { MutableInteractionSource() }
    )
}

@Immutable
private class SliderDrawingCache(
    val trackRect: AuroraRect = AuroraRect(0.0f, 0.0f, 0.0f, 0.0f),
    val thumbRect: AuroraRect = AuroraRect(0.0f, 0.0f, 0.0f, 0.0f),
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
private fun AuroraSlider(
    sliderValue: Float,
    sliderValueRange: ClosedFloatingPointRange<Float>,
    onTriggerValueChange: (Float) -> Unit,
    onValueChangeEnd: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    tickSteps: Int,
    snapToTicks: Boolean,
    drawTicks: Boolean,
    interactionSource: MutableInteractionSource
) {
    val drawingCache = remember { SliderDrawingCache() }
    var rollover by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentState = remember {
        mutableStateOf(
            ComponentState.getState(
                isEnabled = enabled,
                isRollover = rollover,
                isSelected = false,
                isPressed = isPressed
            )
        )
    }


    val trackFillState = if (enabled) ComponentState.ENABLED else ComponentState.DISABLED_UNSELECTED
    val trackSelectedState = if (enabled) ComponentState.SELECTED else ComponentState.DISABLED_SELECTED

    val skinColors = AuroraSkin.colors
    val decorationAreaType = AuroraSkin.decorationAreaType
    val painters = AuroraSkin.painters

    // install state-aware alpha channel (support for skins
    // that use translucency on disabled states).
    val stateAlpha = skinColors.getAlpha(
        decorationAreaType = decorationAreaType,
        componentState = trackFillState
    )
    val fillScheme = skinColors.getColorScheme(
        decorationAreaType = decorationAreaType,
        componentState = trackFillState
    )
    val selectionColorScheme = skinColors.getColorScheme(
        decorationAreaType = decorationAreaType,
        componentState = trackSelectedState
    )
    val borderFillColorScheme = skinColors.getColorScheme(
        decorationAreaType = decorationAreaType,
        associationKind = ColorSchemeAssociationKind.BORDER,
        componentState = trackFillState
    )
    val borderSelectionColorScheme = skinColors.getColorScheme(
        decorationAreaType = decorationAreaType,
        associationKind = ColorSchemeAssociationKind.BORDER,
        componentState = trackSelectedState
    )
    val tickScheme = skinColors.getColorScheme(
        decorationAreaType = decorationAreaType,
        associationKind = ColorSchemeAssociationKind.SEPARATOR,
        componentState = trackFillState
    )
    val fillPainter = ClassicFillPainter.INSTANCE

    val dragStartX = remember { mutableStateOf(0.0f) }
    val cumulativeDragAmount = remember { mutableStateOf(0.0f) }

    var press = remember {  mutableStateOf<PressInteraction.Press?>(null) }
    val drag = Modifier.draggable(
        state = rememberDraggableState {
            // Update the cumulative drag amount
            cumulativeDragAmount.value += it

            // Convert from pixels to value range
            var newValue = sliderValueRange.start +
                    (dragStartX.value + cumulativeDragAmount.value - drawingCache.trackRect.x) * (sliderValueRange.endInclusive - sliderValueRange.start) / drawingCache.trackRect.width
            newValue = newValue.coerceIn(sliderValueRange.start, sliderValueRange.endInclusive)

            // Snap to the closest tick if needed
            if ((tickSteps > 0) && snapToTicks) {
                val tickRange = (sliderValueRange.endInclusive - sliderValueRange.start) / (tickSteps + 1)
                val tick = ((newValue - sliderValueRange.start) / tickRange).roundToInt()
                newValue = tick * tickRange
            }

            // Update value change lambda
            onTriggerValueChange.invoke(newValue)
        },
        orientation = Orientation.Horizontal,
        reverseDirection = false,
        interactionSource = interactionSource,
        startDragImmediately = true,
        onDragStarted = { pos ->
            // Reset the drag start position and cumulative drag amount
            dragStartX.value = pos.x
            cumulativeDragAmount.value = 0.0f

            // Convert from pixels to value range
            var newValue = sliderValueRange.start +
                    (pos.x - drawingCache.trackRect.x) * (sliderValueRange.endInclusive - sliderValueRange.start) / drawingCache.trackRect.width
            newValue = newValue.coerceIn(sliderValueRange.start, sliderValueRange.endInclusive)

            // Snap to the closest tick if needed
            if ((tickSteps > 0) && snapToTicks) {
                val tickRange = (sliderValueRange.endInclusive - sliderValueRange.start) / (tickSteps + 1)
                val tick = ((newValue - sliderValueRange.start) / tickRange).roundToInt()
                newValue = tick * tickRange
            }

            // Update value change lambda
            onTriggerValueChange.invoke(newValue)

            // And add pressed state to the interaction
            press.value = PressInteraction.Press(pos)
            interactionSource.emit(press.value!!)
        },
        onDragStopped = {
            // Update value change end lambda
            onValueChangeEnd.invoke()

            // And remove pressed state to the interaction
            interactionSource.emit(PressInteraction.Release(press.value!!))
        }
    )

    // Transition for the selection state
    val selectionTransition = updateTransition(false)
    val selectedFraction by selectionTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = AuroraSkin.animationConfig.regular)
        }
    ) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the rollover state
    val rolloverTransition = updateTransition(rollover)
    val rolloverFraction by rolloverTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = AuroraSkin.animationConfig.regular)
        }
    ) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the pressed state
    val pressedTransition = updateTransition(isPressed)
    val pressedFraction by pressedTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = AuroraSkin.animationConfig.regular)
        }
    ) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // Transition for the enabled state
    val enabledTransition = updateTransition(enabled)
    val enabledFraction by enabledTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = AuroraSkin.animationConfig.regular)
        }
    ) {
        when (it) {
            false -> 0.0f
            true -> 1.0f
        }
    }

    // TODO - figure out why the animations are not running without looking
    //  at the result (and how it looks like in the new animation APIs)
    val totalFraction = selectedFraction + rolloverFraction +
            pressedFraction + enabledFraction

    val modelStateInfo = remember { ModelStateInfo(currentState.value) }
    val transitionInfo = remember { mutableStateOf<TransitionInfo?>(null) }

    StateTransitionTracker(
        modelStateInfo = modelStateInfo,
        currentState = currentState,
        transitionInfo = transitionInfo,
        enabled = enabled,
        selected = false,
        rollover = rollover,
        pressed = isPressed,
        duration = AuroraSkin.animationConfig.regular
    )

    if (transitionInfo.value != null) {
        LaunchedEffect(currentState.value) {
            val transitionFloat = Animatable(transitionInfo.value!!.from)
            val result = transitionFloat.animateTo(
                targetValue = transitionInfo.value!!.to,
                animationSpec = tween(durationMillis = transitionInfo.value!!.duration)
            ) {
                modelStateInfo.updateActiveStates(value)
            }

            if (result.endReason == AnimationEndReason.Finished) {
                modelStateInfo.updateActiveStates(1.0f)
                modelStateInfo.clear(currentState.value)
            }
        }
    }

    Box(
        modifier = Modifier.pointerMoveFilter(
            onEnter = {
                false
            },
            onExit = {
                // Reset rollover when mouse exits the component bounds
                rollover = false
                false
            },
            onMove = { position ->
                // Rollover is only "active" in the thumb rectangle
                rollover = drawingCache.thumbRect.contains(position.x, position.y)
                false
            }).then(drag)
    ) {
        // Populate the cached color scheme for filling the thumb
        // based on the current model state info
        populateColorScheme(
            drawingCache.colorScheme, modelStateInfo, currentState.value, decorationAreaType,
            ColorSchemeAssociationKind.FILL
        )

        // And retrieve the thumb fill colors
        val thumbFillUltraLight = drawingCache.colorScheme.ultraLightColor
        val thumbFillExtraLight = drawingCache.colorScheme.extraLightColor
        val thumbFillLight = drawingCache.colorScheme.lightColor
        val thumbFillMid = drawingCache.colorScheme.midColor
        val thumbFillDark = drawingCache.colorScheme.darkColor
        val thumbFillUltraDark = drawingCache.colorScheme.ultraDarkColor
        val thumbFillIsDark = drawingCache.colorScheme.isDark

        // Populate the cached color scheme for drawing the thumb border
        // based on the current model state info
        populateColorScheme(
            drawingCache.colorScheme, modelStateInfo, currentState.value, decorationAreaType,
            ColorSchemeAssociationKind.BORDER
        )
        // And retrieve the border colors
        val thumbBorderUltraLight = drawingCache.colorScheme.ultraLightColor
        val thumbBorderExtraLight = drawingCache.colorScheme.extraLightColor
        val thumbBorderLight = drawingCache.colorScheme.lightColor
        val thumbBorderMid = drawingCache.colorScheme.midColor
        val thumbBorderDark = drawingCache.colorScheme.darkColor
        val thumbBorderUltraDark = drawingCache.colorScheme.ultraDarkColor
        val thumbBorderIsDark = drawingCache.colorScheme.isDark

        val thumbFillPainter = painters.fillPainter
        val thumbBorderPainter = painters.borderPainter

        val alpha = if (currentState.value.isDisabled)
            skinColors.getAlpha(decorationAreaType, currentState.value) else 1.0f

        // Compute the text color
        val textColor = getTextColor(
            modelStateInfo = modelStateInfo,
            currState = currentState.value,
            skinColors = skinColors,
            decorationAreaType = decorationAreaType,
            isTextInFilledArea = true
        )

        var prefHeight = SliderConstants.DefaultSliderContentPadding.calculateTopPadding()
        prefHeight += SliderConstants.TrackHeight
        if ((tickSteps >= 0) && drawTicks) {
            prefHeight += SliderConstants.TrackTickGap
            prefHeight += SliderConstants.TickHeight
        }
        prefHeight += SliderConstants.DefaultSliderContentPadding.calculateBottomPadding()

        Canvas(
            modifier.size(width = SliderConstants.DefaultWidth, height = prefHeight)
        ) {
            val radius = 1.5f.dp.toPx()

            // Calculate the track rectangle
            drawingCache.trackRect.x = SliderConstants.ThumbFullSize.toPx() / 2.0f
            drawingCache.trackRect.y = SliderConstants.DefaultSliderContentPadding.calculateTopPadding().toPx()
            drawingCache.trackRect.width = size.width - SliderConstants.ThumbFullSize.toPx()
            drawingCache.trackRect.height = SliderConstants.TrackHeight.toPx()

            // Calculate the thumb rectangle
            // TODO - support RTL
            val thumbSize = SliderConstants.ThumbFullSize.toPx() *
                    (2.0f + modelStateInfo.activeStrength) / 3.0f
            val selectionCenterX = drawingCache.trackRect.x +
                    drawingCache.trackRect.width * sliderValue / (sliderValueRange.endInclusive - sliderValueRange.start)
            drawingCache.thumbRect.x = selectionCenterX - thumbSize / 2.0f
            drawingCache.thumbRect.y =
                drawingCache.trackRect.y + drawingCache.trackRect.height / 2.0f - thumbSize / 2.0f
            drawingCache.thumbRect.width = thumbSize
            drawingCache.thumbRect.height = thumbSize

            // Fill track
            fillPainter.paintContourBackground(
                drawScope = this,
                size = this.size,
                outline = Outline.Rounded(
                    RoundRect(
                        left = drawingCache.trackRect.x,
                        top = drawingCache.trackRect.y,
                        right = drawingCache.trackRect.x + drawingCache.trackRect.width,
                        bottom = drawingCache.trackRect.y + drawingCache.trackRect.height,
                        cornerRadius = CornerRadius(radius, radius)
                    )
                ),
                fillScheme = fillScheme,
                alpha = stateAlpha
            )

            // Border track
            withTransform({ translate(left = drawingCache.trackRect.x, top = drawingCache.trackRect.y) }) {
                val trackOutline = getBaseOutline(
                    width = drawingCache.trackRect.width,
                    height = drawingCache.trackRect.height,
                    radius = radius,
                    straightSides = emptySet(),
                    insets = 0.5f
                )
                drawOutline(
                    outline = trackOutline,
                    style = Stroke(width = 1.0f),
                    color = borderFillColorScheme.darkColor,
                    alpha = stateAlpha
                )
            }

            if (selectionCenterX > 0.0f) {
                // Fill selection
                fillPainter.paintContourBackground(
                    drawScope = this,
                    size = Size(selectionCenterX - drawingCache.trackRect.x, drawingCache.trackRect.height),
                    outline = Outline.Rounded(
                        RoundRect(
                            left = drawingCache.trackRect.x,
                            top = drawingCache.trackRect.y,
                            right = selectionCenterX,
                            bottom = drawingCache.trackRect.y + drawingCache.trackRect.height,
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    ),
                    fillScheme = selectionColorScheme,
                    alpha = stateAlpha
                )

                // Border selection
                withTransform({ translate(left = drawingCache.trackRect.x, top = drawingCache.trackRect.y) }) {
                    val selectionOutline = getBaseOutline(
                        width = selectionCenterX - drawingCache.trackRect.x,
                        height = drawingCache.trackRect.height,
                        radius = radius,
                        straightSides = setOf(Side.END),
                        insets = 0.5f
                    )
                    drawOutline(
                        outline = selectionOutline,
                        style = Stroke(width = 1.0f),
                        color = borderSelectionColorScheme.darkColor,
                        alpha = stateAlpha
                    )
                }
            }

            // Draw the ticks
            if ((tickSteps > 0) && drawTicks) {
                val tickHeight = SliderConstants.TickHeight.toPx()
                val tickPrimaryBrush = Brush.verticalGradient(
                    0.0f to tickScheme.separatorPrimaryColor,
                    0.75f to tickScheme.separatorPrimaryColor,
                    1.0f to tickScheme.separatorPrimaryColor.withAlpha(0.0f),
                    startY = 0.0f,
                    endY = tickHeight,
                    tileMode = TileMode.Repeated
                )
                val tickSecondaryBrush = Brush.verticalGradient(
                    0.0f to tickScheme.separatorSecondaryColor,
                    0.75f to tickScheme.separatorSecondaryColor,
                    1.0f to tickScheme.separatorSecondaryColor.withAlpha(0.0f),
                    startY = 0.0f,
                    endY = tickHeight,
                    tileMode = TileMode.Repeated
                )

                val tickTop = drawingCache.trackRect.x + drawingCache.trackRect.height +
                        SliderConstants.TrackTickGap.toPx()
                withTransform({
                    translate(left = 0.0f, top = tickTop)
                }) {
                    for (tick in 0 until tickSteps) {
                        val tickX = (drawingCache.trackRect.x +
                                drawingCache.trackRect.width * (tick + 1) / (tickSteps + 1)).toInt()

                        drawLine(
                            brush = tickPrimaryBrush,
                            start = Offset(tickX - 0.5f, 0.0f),
                            end = Offset(tickX - 0.5f, tickHeight),
                            strokeWidth = 1.0f
                        )
                        drawLine(
                            brush = tickSecondaryBrush,
                            start = Offset(tickX + 0.5f, 0.0f),
                            end = Offset(tickX + 0.5f, tickHeight),
                            strokeWidth = 1.0f
                        )
                    }
                }
            }

            // Draw the thumb
            val thumbOutline =
                Outline.Rounded(
                    roundRect = RoundRect(
                        left = 0.5f, top = 0.5f,
                        right = thumbSize - 0.5f,
                        bottom = thumbSize - 0.5f,
                        radiusX = (thumbSize - 1.0f) / 2.0f,
                        radiusY = (thumbSize - 1.0f) / 2.0f
                    )
                )

            withTransform({
                translate(left = drawingCache.thumbRect.x, top = drawingCache.thumbRect.y)
            }) {
                // Populate the cached color scheme for filling the thumb
                drawingCache.colorScheme.ultraLight = thumbFillUltraLight
                drawingCache.colorScheme.extraLight = thumbFillExtraLight
                drawingCache.colorScheme.light = thumbFillLight
                drawingCache.colorScheme.mid = thumbFillMid
                drawingCache.colorScheme.dark = thumbFillDark
                drawingCache.colorScheme.ultraDark = thumbFillUltraDark
                drawingCache.colorScheme.isDark = thumbFillIsDark
                drawingCache.colorScheme.foreground = textColor

                thumbFillPainter.paintContourBackground(
                    this, this.size, thumbOutline, drawingCache.colorScheme, alpha
                )

                // Populate the cached color scheme for drawing the thumb border
                drawingCache.colorScheme.ultraLight = thumbBorderUltraLight
                drawingCache.colorScheme.extraLight = thumbBorderExtraLight
                drawingCache.colorScheme.light = thumbBorderLight
                drawingCache.colorScheme.mid = thumbBorderMid
                drawingCache.colorScheme.dark = thumbBorderDark
                drawingCache.colorScheme.ultraDark = thumbBorderUltraDark
                drawingCache.colorScheme.isDark = thumbBorderIsDark
                drawingCache.colorScheme.foreground = textColor

                val innerThumbOutline = if (thumbBorderPainter.isPaintingInnerOutline)
                    Outline.Rounded(
                        roundRect = RoundRect(
                            left = 1.0f, top = 1.0f,
                            right = thumbSize - 1.0f,
                            bottom = thumbSize - 1.0f,
                            radiusX = (thumbSize - 2.0f) / 2.0f,
                            radiusY = (thumbSize - 2.0f) / 2.0f
                        )
                    ) else null

                thumbBorderPainter.paintBorder(
                    this, this.size, thumbOutline, innerThumbOutline, drawingCache.colorScheme, alpha
                )
            }
        }
    }
}
