package com.devson.syntaxtor.ui.utils

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun Modifier.repeatingClickable(
    initialDelay: Long = 500L,
    repeatDelay: Long = 200L,
    onClick: () -> Unit
): Modifier = this.pointerInput(onClick) {
    val isPressed = MutableStateFlow(false)
    coroutineScope {
        launch {
            isPressed.collectLatest { pressed ->
                if (pressed) {
                    onClick()
                    delay(initialDelay)
                    while (true) {
                        onClick()
                        delay(repeatDelay)
                    }
                }
            }
        }

        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                isPressed.value = true
                waitForUpOrCancellation()
                isPressed.value = false
            }
        }
    }
}
