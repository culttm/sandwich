package com.sandwich

import com.sandwich.common.app.AppOf
import kotlinx.coroutines.test.runTest

fun withTestApp(vararg apps: suspend () -> () -> Unit, test: suspend () -> Unit) = runTest {
    val app = AppOf(*apps)
    val teardown = app()
    try {
        test()
    } finally {
        teardown()
    }
}