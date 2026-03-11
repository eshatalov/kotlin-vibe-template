package com.github.testprocessor.config

import com.github.template.client.TestTableApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TemplateClientConfig {

    @Bean
    fun testTableApi(properties: TemplateClientProperties): TestTableApi {
        return TestTableApi(properties.baseUrl)
    }
}
