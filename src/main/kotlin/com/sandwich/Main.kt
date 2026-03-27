package com.sandwich

import com.sandwich.apps.SandwichHttpApi
import com.sandwich.common.app.run

suspend fun main(args: Array<String> = emptyArray()) {
    when(args.firstOrNull()) {
        else -> SandwichHttpApi().run()
    }
}