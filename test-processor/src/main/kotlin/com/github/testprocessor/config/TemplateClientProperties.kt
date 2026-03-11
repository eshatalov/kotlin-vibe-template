package com.github.testprocessor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.template")
data class TemplateClientProperties(
    var baseUrl: String = "http://localhost:8080"
)
