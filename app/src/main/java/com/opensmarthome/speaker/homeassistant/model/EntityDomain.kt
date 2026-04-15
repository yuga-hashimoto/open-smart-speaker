package com.opensmarthome.speaker.homeassistant.model

enum class EntityDomain(val value: String) {
    LIGHT("light"),
    SWITCH("switch"),
    CLIMATE("climate"),
    MEDIA_PLAYER("media_player"),
    COVER("cover"),
    FAN("fan"),
    SENSOR("sensor"),
    BINARY_SENSOR("binary_sensor"),
    AUTOMATION("automation"),
    SCENE("scene"),
    SCRIPT("script"),
    INPUT_BOOLEAN("input_boolean"),
    LOCK("lock"),
    CAMERA("camera"),
    WEATHER("weather");

    companion object {
        fun fromString(value: String): EntityDomain? =
            entries.find { it.value == value }
    }
}
