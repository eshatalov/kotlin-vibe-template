val mockkVersion: String by rootProject.extra
val javaVersion: Int by rootProject.extra

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native")
}

group = "com.github.testprocessor"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":contract"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    environment = mapOf(
        "BP_NATIVE_IMAGE" to "true",
        "BP_JVM_VERSION" to javaVersion.toString()
    )
}

graalvmNative {
    testSupport = false
    toolchainDetection = true
    binaries {
        named("main") {
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(javaVersion)
                vendor = JvmVendorSpec.ORACLE
                nativeImageCapable = true
            }
            if (System.getProperty("os.name").lowercase().contains("linux")) {
                buildArgs.add("--gc=G1")
            }
        }
    }
}
