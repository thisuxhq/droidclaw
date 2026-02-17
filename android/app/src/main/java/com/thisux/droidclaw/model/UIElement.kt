package com.thisux.droidclaw.model

import kotlinx.serialization.Serializable

@Serializable
data class UIElement(
    val id: String = "",
    val text: String = "",
    val type: String = "",
    val bounds: String = "",
    val center: List<Int> = listOf(0, 0),
    val size: List<Int> = listOf(0, 0),
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = false,
    val checked: Boolean = false,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val scrollable: Boolean = false,
    val longClickable: Boolean = false,
    val password: Boolean = false,
    val hint: String = "",
    val action: String = "read",
    val parent: String = "",
    val depth: Int = 0
)
