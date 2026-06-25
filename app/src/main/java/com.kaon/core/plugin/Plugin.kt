package com.kaon.core.plugin

interface Plugin {

    val id: String

    fun initialize()

    fun destroy()
}