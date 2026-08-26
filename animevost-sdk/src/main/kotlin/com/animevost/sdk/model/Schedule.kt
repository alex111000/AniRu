package com.animevost.sdk.model

data class ScheduleDay(
    val weekday: Weekday,
    val entries: List<ScheduleEntry>,
)

data class ScheduleEntry(
    val title: String,
    val url: String,
    val timeLabel: String?,
    val posterUrl: String? = null,
)

enum class Weekday(
    val containerId: String,
    val displayName: String,
) {
    MONDAY("raspisMon", "Понедельник"),
    TUESDAY("raspisTue", "Вторник"),
    WEDNESDAY("raspisWed", "Среда"),
    THURSDAY("raspisThu", "Четверг"),
    FRIDAY("raspisFri", "Пятница"),
    SATURDAY("raspisSat", "Суббота"),
    SUNDAY("raspisSun", "Воскресенье"),
}
