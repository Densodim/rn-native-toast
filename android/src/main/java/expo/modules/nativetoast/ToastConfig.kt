package expo.modules.nativetoast

/**
 * Варианты типов тоста — соответствуют 1:1 JS ToastType
 */
enum class ToastType(val value: String) {
    ERROR("error"),
    SUCCESS("success"),
    WARNING("warning"),
    INFO("info");

    companion object {
        fun from(value: String): ToastType =
            entries.firstOrNull { it.value == value } ?: INFO
    }
}

/**
 * Позиция на экране
 */
enum class ToastPosition(val value: String) {
    TOP("top"),
    BOTTOM("bottom");

    companion object {
        fun from(value: String): ToastPosition =
            entries.firstOrNull { it.value == value } ?: TOP
    }
}

/**
 * Поведение очереди
 */
enum class ToastQueueMode(val value: String) {
    REPLACE("replace"),
    QUEUE("queue");

    companion object {
        fun from(value: String): ToastQueueMode =
            entries.firstOrNull { it.value == value } ?: REPLACE
    }
}

/**
 * Вариант стиля блюра
 */
enum class BlurStyle(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM_MATERIAL("systemMaterial");

    companion object {
        fun from(value: String): BlurStyle =
            entries.firstOrNull { it.value == value } ?: SYSTEM_MATERIAL
    }
}

/**
 * Неизменяемая конфигурация одного тоста.
 * Создаётся из JS config map с уже разрешёнными дефолтами.
 */
data class ToastConfigData(
    val id: String,
    val type: ToastType,
    val titleKey: String,
    val messageKey: String,
    val duration: Long,
    val position: ToastPosition,
    val haptic: Boolean,
    val blurStyle: BlurStyle,
    val queueMode: ToastQueueMode
) {
    companion object {
        fun fromMap(map: Map<String, Any>): ToastConfigData {
            return ToastConfigData(
                id = java.util.UUID.randomUUID().toString(),
                type = ToastType.from(map["type"] as? String ?: "info"),
                titleKey = map["titleKey"] as? String ?: "",
                messageKey = map["messageKey"] as? String ?: "",
                duration = (map["duration"] as? Number)?.toLong() ?: 4000L,
                position = ToastPosition.from(map["position"] as? String ?: "top"),
                haptic = map["haptic"] as? Boolean ?: true,
                blurStyle = BlurStyle.from(map["blurStyle"] as? String ?: "systemMaterial"),
                queueMode = ToastQueueMode.from(map["queueMode"] as? String ?: "replace")
            )
        }
    }
}
