package com.sandwich.common.app

import kotlinx.coroutines.runBlocking

typealias Teardown = () -> Unit
typealias App = suspend () -> Teardown

suspend fun App.run() {
    val teardown = this()

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down...")
        teardown()
        println("Shut down")
    })

    Thread.currentThread().join()
}

suspend fun App.runOnce() {
    val teardown = this()
    teardown()
}

fun AppOf(vararg apps: App): App = {
    val teardowns = apps.map { runBlocking { it() } }
    Teardown { teardowns.reversed().forEach { it() } }
}

inline fun Teardown(crossinline block: suspend () -> Unit): Teardown = {
    runBlocking { block() }
}

inline fun App(crossinline app: App): App = { app() }
