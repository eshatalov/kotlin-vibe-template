import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

val springBootVersion: String by rootProject.extra

plugins {
    kotlin("jvm")
    id("org.openapi.generator")
}

group = "com.github.template"
version = "0.0.1-SNAPSHOT"

val generatedSourceDir = "${layout.buildDirectory.get().asFile}/generated/src/main/kotlin"
val generatedOutputDir = "${layout.buildDirectory.get().asFile}/generated"
val ownerServicePackage = "com.github.template"
val modelPackageName = "$ownerServicePackage.model"
val clientPackageName = "$ownerServicePackage.client"
val messagePackageName = "$ownerServicePackage.message"

val commonConfigOptions = mapOf(
    "useSpringBoot3" to "true",
    "serializationLibrary" to "jackson",
    "dateLibrary" to "java8",
    "enumPropertyNaming" to "UPPERCASE",
    "omitGradleWrapper" to "true",
)

val sharedModelNames = listOf(
    "ProblemDetail",
    "SaveTestTableRequest",
    "TestTableMetadata",
    "TestTableResponse"
)

val sharedModelTypeMappings = sharedModelNames.associateWith { it }
val sharedModelImportMappings = sharedModelNames.associateWith { "$modelPackageName.$it" }

fun GenerateTask.configureCommonKotlinGeneration() {
    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")
    outputDir.set(generatedOutputDir)
    configOptions.set(commonConfigOptions)
    generateModelTests.set(false)
    generateApiTests.set(false)
    generateModelDocumentation.set(false)
    generateApiDocumentation.set(false)
    cleanupOutput.set(false)
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

sourceSets {
    main {
        kotlin {
            srcDir(generatedSourceDir)
        }
    }
}

tasks.named<GenerateTask>("openApiGenerate").configure {
    configureCommonKotlinGeneration()
    inputSpec.set("$projectDir/src/main/resources/rest/template-rest.yaml")
    modelPackage.set(modelPackageName)
    apiPackage.set(clientPackageName)
    packageName.set(clientPackageName)
    globalProperties.set(
        mapOf(
            "apis" to "",
            "models" to "false",
            "supportingFiles" to "",
            "modelDocs" to "false",
            "apiDocs" to "false",
            "modelTests" to "false",
            "apiTests" to "false",
        )
    )
    typeMappings.set(sharedModelTypeMappings)
    importMappings.set(sharedModelImportMappings)
}

val generateContractModels by tasks.registering(GenerateTask::class) {
    configureCommonKotlinGeneration()
    inputSpec.set("$projectDir/src/main/resources/contract/template-contract.yaml")
    modelPackage.set(modelPackageName)
    packageName.set(modelPackageName)
    globalProperties.set(
        mapOf(
            "models" to "",
            "apis" to "false",
            "supportingFiles" to "false",
            "modelDocs" to "false",
            "apiDocs" to "false",
            "modelTests" to "false",
            "apiTests" to "false",
        )
    )
}

val generateStreamMessages by tasks.registering(GenerateTask::class) {
    configureCommonKotlinGeneration()
    inputSpec.set("$projectDir/src/main/resources/stream/template-stream.yaml")
    modelPackage.set(messagePackageName)
    packageName.set(messagePackageName)
    globalProperties.set(
        mapOf(
            "models" to "",
            "apis" to "false",
            "supportingFiles" to "false",
            "modelDocs" to "false",
            "apiDocs" to "false",
            "modelTests" to "false",
            "apiTests" to "false",
        )
    )
    typeMappings.set(mapOf("TestTableResponse" to "TestTableResponse"))
    importMappings.set(mapOf("TestTableResponse" to "$modelPackageName.TestTableResponse"))
}

val generateOpenApiSources by tasks.registering {
    dependsOn(tasks.named("openApiGenerate"), generateContractModels, generateStreamMessages)
}

// Post-processing: migrate generated code from Jackson 2 to Jackson 3 and fix Spring 7 incompatibilities.
// Jackson 3 moved most packages from com.fasterxml.jackson to tools.jackson, but jackson-annotations
// stayed at com.fasterxml.jackson.annotation (artifact com.fasterxml.jackson.core:jackson-annotations:2.20).
// Spring 7 renamed Jackson2Json* codecs to JacksonJson* and requires JsonMapper instead of ObjectMapper.
val migrateToJackson3 by tasks.registering {
    group = "openapi"
    description = "Migrate generated OpenAPI code from Jackson 2 to Jackson 3 and fix Spring 7 incompatibilities"

    doLast {
        val generatedDir = file(generatedSourceDir)
        if (!generatedDir.exists()) {
            logger.warn("Generated source directory does not exist: $generatedSourceDir")
            return@doLast
        }

        generatedDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                if (
                    file.parentFile.name == "message" &&
                    (file.name == "com.github.template.model.TestTableResponse.kt" || file.name == "TestTableResponseMetadata.kt")
                ) {
                    file.delete()
                    return@forEach
                }

                var content = file.readText()
                val originalContent = content

                // Jackson 3: replace package prefixes for databind/module/datatype,
                // but NOT for annotations (they stayed at com.fasterxml.jackson.annotation)
                content = content.replace("com.fasterxml.jackson.databind", "tools.jackson.databind")
                content = content.replace("com.fasterxml.jackson.module", "tools.jackson.module")
                content = content.replace("com.fasterxml.jackson.datatype", "tools.jackson.datatype")

                // Spring 7: renamed Jackson2* codec classes (dropped the "2")
                content = content.replace("Jackson2JsonEncoder", "JacksonJsonEncoder")
                content = content.replace("Jackson2JsonDecoder", "JacksonJsonDecoder")
                content = content.replace("jackson2JsonEncoder", "jacksonJsonEncoder")
                content = content.replace("jackson2JsonDecoder", "jacksonJsonDecoder")

                // Spring 7: JacksonJsonEncoder/Decoder constructors require JsonMapper, not ObjectMapper.
                // ApiClient.request() has T: Any? but Spring's toEntity() requires T: Any.
                if (file.name == "ApiClient.kt") {
                    content = content.replace("reified T: Any?>", "reified T: Any>")
                }

                if (file.name == "TestTableApi.kt") {
                    content = content.replace("com.github.template.model.SaveTestTableRequest", "SaveTestTableRequest")
                    content = content.replace("com.github.template.model.TestTableResponse", "TestTableResponse")
                    content = content.replace("comGithubTemplateModelSaveTestTableRequest", "saveTestTableRequest")
                    content = content.replace(
                        "import com.fasterxml.jackson.annotation.JsonProperty\n\n",
                        "import com.fasterxml.jackson.annotation.JsonProperty\n\nimport com.github.template.model.SaveTestTableRequest\nimport com.github.template.model.TestTableResponse\n\n"
                    )
                    content = content.replace(
                        "createTestTable(comGithubTemplateModelSaveTestTableRequest: SaveTestTableRequest)",
                        "createTestTable(saveTestTableRequest: SaveTestTableRequest)"
                    )
                    content = content.replace(
                        "createTestTableWithHttpInfo(comGithubTemplateModelSaveTestTableRequest = comGithubTemplateModelSaveTestTableRequest)",
                        "createTestTableWithHttpInfo(saveTestTableRequest = saveTestTableRequest)"
                    )
                    content = content.replace(
                        "createTestTableWithHttpInfo(comGithubTemplateModelSaveTestTableRequest: SaveTestTableRequest)",
                        "createTestTableWithHttpInfo(saveTestTableRequest: SaveTestTableRequest)"
                    )
                    content = content.replace(
                        "createTestTableRequestConfig(comGithubTemplateModelSaveTestTableRequest = comGithubTemplateModelSaveTestTableRequest)",
                        "createTestTableRequestConfig(saveTestTableRequest = saveTestTableRequest)"
                    )
                    content = content.replace(
                        "createTestTableRequestConfig(comGithubTemplateModelSaveTestTableRequest: SaveTestTableRequest)",
                        "createTestTableRequestConfig(saveTestTableRequest: SaveTestTableRequest)"
                    )
                    content = content.replace(
                        "val localVariableBody = comGithubTemplateModelSaveTestTableRequest",
                        "val localVariableBody = saveTestTableRequest"
                    )
                    content = content.replace(
                        "updateTestTable(id: java.util.UUID, createTestTableRequest: SaveTestTableRequest)",
                        "updateTestTable(id: java.util.UUID, saveTestTableRequest: SaveTestTableRequest)"
                    )
                    content = content.replace(
                        "updateTestTableWithHttpInfo(id = id, createTestTableRequest = createTestTableRequest)",
                        "updateTestTableWithHttpInfo(id = id, saveTestTableRequest = saveTestTableRequest)"
                    )
                    content = content.replace(
                        "updateTestTableWithHttpInfo(id: java.util.UUID, createTestTableRequest: SaveTestTableRequest)",
                        "updateTestTableWithHttpInfo(id: java.util.UUID, saveTestTableRequest: SaveTestTableRequest)"
                    )
                    content = content.replace(
                        "updateTestTableRequestConfig(id = id, createTestTableRequest = createTestTableRequest)",
                        "updateTestTableRequestConfig(id = id, saveTestTableRequest = saveTestTableRequest)"
                    )
                    content = content.replace(
                        "updateTestTableRequestConfig(id: java.util.UUID, createTestTableRequest: SaveTestTableRequest)",
                        "updateTestTableRequestConfig(id: java.util.UUID, saveTestTableRequest: SaveTestTableRequest)"
                    )
                    content = content.replace(
                        "val localVariableBody = createTestTableRequest",
                        "val localVariableBody = saveTestTableRequest"
                    )
                }

                if (file.name == "TestTableStreamMessage.kt") {
                    content = content.replace("com.github.template.model.TestTableResponse", "TestTableResponse")
                    content = content.replace(
                        "import com.github.template.message.TestTableEventType\n\n",
                        "import com.github.template.message.TestTableEventType\nimport com.github.template.model.TestTableResponse\n\n"
                    )
                }

                // ResponseEntity.body is nullable; generated API methods expect non-null return.
                content = content.replace(".map { it.body }", ".map { it.body!! }")

                // Serializer.kt: complete rewrite for Jackson 3 (ObjectMapper is abstract,
                // jacksonObjectMapper() removed, modules registered via JsonMapper.builder())
                if (file.name == "Serializer.kt") {
                    val packageLine = content.lines().first { it.startsWith("package ") }
                    content = buildSerializerJackson3(packageLine)
                }

                if (content != originalContent) {
                    file.writeText(content)
                    logger.lifecycle("Migrated to Jackson 3: ${file.relativeTo(generatedDir)}")
                }
            }
    }
}

fun buildSerializerJackson3(packageLine: String): String = """
$packageLine

import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.module.kotlin.KotlinModule

// Jackson 3: Java Time support is built into core (no separate JavaTimeModule).
// WRITE_DATES_AS_TIMESTAMPS moved from SerializationFeature to DateTimeFeature.
// KotlinModule constructor is internal; use Builder instead.
object Serializer {
    @JvmStatic
    val jacksonObjectMapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
}
""".trimIndent() + "\n"

// Task wiring: openApiGenerate -> migrateToJackson3 -> compileKotlin
migrateToJackson3 {
    dependsOn(generateOpenApiSources)
}

tasks.named("compileKotlin") {
    dependsOn(migrateToJackson3)
}
