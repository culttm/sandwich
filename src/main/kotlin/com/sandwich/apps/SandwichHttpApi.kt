package com.sandwich.apps

import com.sandwich.common.app.App
import com.sandwich.common.app.Teardown
import com.sandwich.common.http.HttpServer
import com.sandwich.common.http.configureErrorHandling
import com.sandwich.common.http.configureMonitoring
import com.sandwich.common.http.configureRouting
import com.sandwich.common.http.configureSerialization
import com.sandwich.common.infra.Db
import com.sandwich.common.infra.seed
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun SandwichHttpApi(
    db: Db = Db().apply { seed() },
    logger: Logger = LoggerFactory.getLogger("SandwichHttpApi")
) = App {
    logger.info("Starting SandwichHttpApi")

    val server = HttpServer(8080) {
        configureSerialization()
        configureErrorHandling()
        configureMonitoring()
        configureRouting(db)
    }
    server.start()

    Teardown {
        logger.info("Stopping SandwichHttpApi")
        server.stop(1000L, 1000L)
        logger.info("Stopped SandwichHttpApi")
    }
}
