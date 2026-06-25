package com.kaon.music.core.plugin

interface Plugin {

    val id: String

    fun initialize()

    fun destroy()
}