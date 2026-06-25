package com.kaon.core.logger

interface Logger {

    fun debug(tag: String, msg: String)

    fun info(tag: String, msg: String)

    fun warn(tag: String, msg: String)

    fun error(tag: String, msg: String)
}
