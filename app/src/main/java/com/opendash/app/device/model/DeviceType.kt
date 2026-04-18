package com.opendash.app.device.model

enum class DeviceType(val value: String) {
    LIGHT("light"),
    SWITCH("switch"),
    CLIMATE("climate"),
    MEDIA_PLAYER("media_player"),
    SENSOR("sensor"),
    COVER("cover"),
    FAN("fan"),
    LOCK("lock"),
    CAMERA("camera"),
    OTHER("other");

    companion object {
        fun fromString(value: String): DeviceType =
            entries.find { it.value == value } ?: OTHER
    }
}
