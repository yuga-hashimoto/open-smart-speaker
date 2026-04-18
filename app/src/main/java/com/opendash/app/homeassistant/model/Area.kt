package com.opendash.app.homeassistant.model

data class Area(
    val areaId: String,
    val name: String,
    val aliases: List<String> = emptyList()
)
