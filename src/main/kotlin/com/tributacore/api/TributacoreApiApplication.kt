package com.tributacore.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TributacoreApiApplication

fun main(args: Array<String>) {
    runApplication<TributacoreApiApplication>(*args)
}
