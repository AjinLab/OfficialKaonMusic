package com.kaon.music.plugins.defaultui.screens

fun formatTime(ms: Long): String {

    if (ms <= 0)
        return "00:00"

    val totalSeconds = ms / 1000

    val minutes = totalSeconds / 60

    val seconds = totalSeconds % 60

    return "%02d:%02d".format(
        minutes,
        seconds
    )
}
