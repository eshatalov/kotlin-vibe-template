package com.github.testprocessor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TestProcessorApplication

fun main(args: Array<String>) {
    runApplication<TestProcessorApplication>(*args)
}
