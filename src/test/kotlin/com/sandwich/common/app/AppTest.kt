package com.sandwich.common.app

import com.sandwich.common.app.App
import com.sandwich.common.app.AppOf
import com.sandwich.common.app.Teardown
import com.sandwich.common.app.runOnce
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {

    @Test
    fun `app starts and tears down`() = runTest {
        val log = mutableListOf<String>()

        val app = App {
            log.add("started")
            Teardown { log.add("stopped") }
        }

        app.runOnce()

        assertEquals(listOf("started", "stopped"), log)
    }

    @Test
    fun `AppOf composes apps - starts in order, tears down in reverse`() = runTest {
        val log = mutableListOf<String>()

        val db = App { log.add("db up"); Teardown { log.add("db down") } }
        val server = App { log.add("server up"); Teardown { log.add("server down") } }

        val app = AppOf(db, server)
        app.runOnce()

        assertEquals(
            listOf("db up", "server up", "server down", "db down"),
            log
        )
    }

    @Test
    fun `runOnce can be called multiple times`() = runTest {
        var counter = 0

        val app = App {
            counter++
            Teardown { counter-- }
        }

        app.runOnce()
        app.runOnce()
        app.runOnce()

        assertEquals(0, counter)
    }
}
