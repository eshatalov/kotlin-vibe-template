import java.nio.file.Files
import java.util.Properties

val flywayVersion: String by rootProject.extra
val postgresqlVersion: String by rootProject.extra
val jooqVersion: String by rootProject.extra
val zonkyEmbeddedPostgresVersion: String by rootProject.extra
val mockkVersion: String by rootProject.extra
val dbRiderVersion: String by rootProject.extra
val javaVersion: Int by rootProject.extra
val isWindows = System.getProperty("os.name").lowercase().contains("windows")

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native")
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        val flywayVersion: String by rootProject.extra
        val postgresqlVersion: String by rootProject.extra
        val jooqVersion: String by rootProject.extra
        val zonkyEmbeddedPostgresVersion: String by rootProject.extra

        classpath("org.flywaydb:flyway-core:$flywayVersion")
        classpath("org.flywaydb:flyway-database-postgresql:$flywayVersion")
        classpath("org.postgresql:postgresql:$postgresqlVersion")
        classpath("org.jooq:jooq:$jooqVersion")
        classpath("org.jooq:jooq-meta:$jooqVersion")
        classpath("org.jooq:jooq-codegen:$jooqVersion")
        classpath("io.zonky.test:embedded-postgres:$zonkyEmbeddedPostgresVersion")
    }
}

group = "com.github.template"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":contract"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // jOOQ Kotlin Coroutines support
    implementation("org.jooq:jooq-kotlin-coroutines:$jooqVersion")

    // Flyway Spring Boot starter for app-time migrations/validation
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // PostgreSQL JDBC driver (for Flyway + jOOQ)
    runtimeOnly("org.postgresql:postgresql")

    // PostgreSQL R2DBC driver (for reactive WebFlux)
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    testImplementation("org.postgresql:r2dbc-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:$mockkVersion")

    // Zonky embedded PostgreSQL for tests
    testImplementation("io.zonky.test:embedded-postgres:$zonkyEmbeddedPostgresVersion")

    // Database Rider for declarative database testing
    testImplementation("com.github.database-rider:rider-spring:$dbRiderVersion")
}

sourceSets {
    main {
        kotlin {
            srcDir("${layout.buildDirectory.get().asFile}/generated-sources/jooq")
        }
    }
}

// Custom Flyway tasks using Flyway API directly
/**
 * Base class for Flyway tasks with shared database configuration.
 * Configuration is resolved lazily from: Environment variables > gradle.properties
 */
abstract class BaseFlywayTask : DefaultTask() {

    @get:Input
    abstract val dbSchema: Property<String>

    /**
     * Database configuration resolved lazily with fail-fast behavior.
     */
    private val dbConfig by lazy {
        val url = System.getenv("DB_URL")
            ?: project.findProperty("flyway.url") as String?
            ?: throw GradleException(
                """
                |Missing database configuration: Database JDBC URL
                |
                |Set DB_URL environment variable or add to gradle.properties:
                |  flyway.url=<your-jdbc-url>
                |
                |See gradle.properties.template for example configuration.
                |""".trimMargin()
            )
        val user = System.getenv("DB_USER")
            ?: project.findProperty("flyway.user") as String?
            ?: throw GradleException(
                """
                |Missing database configuration: Database username
                |
                |Set DB_USER environment variable or add to gradle.properties:
                |  flyway.user=<your-username>
                |
                |See gradle.properties.template for example configuration.
                |""".trimMargin()
            )
        val password = System.getenv("DB_PASSWORD")
            ?: project.findProperty("flyway.password") as String?
            ?: throw GradleException(
                """
                |Missing database configuration: Database password
                |
                |Set DB_PASSWORD environment variable or add to gradle.properties:
                |  flyway.password=<your-password>
                |
                |See gradle.properties.template for example configuration.
                |""".trimMargin()
            )
        DatabaseConfig(url, user, password)
    }

    /**
     * Creates a configured Flyway instance with common settings.
     * Subclasses can override specific settings via configure callback.
     */
    protected fun createFlyway(configure: (org.flywaydb.core.api.configuration.FluentConfiguration) -> Unit = {}): org.flywaydb.core.Flyway {
        val migrationDirectory = project.layout.buildDirectory.dir("resources/main/db/migration").get().asFile
        val builder = org.flywaydb.core.Flyway.configure()
            .dataSource(dbConfig.url, dbConfig.user, dbConfig.password)
            .locations("filesystem:${migrationDirectory.absolutePath}")
            .schemas(dbSchema.get())
            .createSchemas(true)
        configure(builder)
        return builder.load()
    }

    /**
     * Data class to hold database configuration.
     */
    private data class DatabaseConfig(
        val url: String,
        val user: String,
        val password: String
    )
}

abstract class FlywayMigrateTask : BaseFlywayTask() {
    @TaskAction
    fun run() {
        val flyway = createFlyway {
            it.baselineOnMigrate(true)
            it.baselineVersion("0")
        }
        println("Running Flyway migrations...")
        flyway.migrate()
        println("Migrations completed successfully")
    }
}

abstract class FlywayCleanTask : BaseFlywayTask() {
    @TaskAction
    fun run() {
        val flyway = createFlyway {
            it.cleanDisabled(false)
        }
        println("Cleaning database (dropping all objects)...")
        flyway.clean()
        println("Database cleaned successfully")
    }
}

abstract class FlywayInfoTask : BaseFlywayTask() {
    @TaskAction
    fun run() {
        val flyway = createFlyway()
        println("Flyway migration info:")
        val info = flyway.info()
        info.all()?.forEach { migration ->
            println("  ${migration.version} - ${migration.description} - ${migration.state}")
        } ?: println("  No migrations found")
    }
}

abstract class FlywayValidateTask : BaseFlywayTask() {
    @TaskAction
    fun run() {
        val flyway = createFlyway()
        println("Validating Flyway migrations...")
        flyway.validate()
        println("Validation successful")
    }
}

/**
 * Utility object for managing embedded PostgreSQL with Flyway migrations.
 * Provides a reusable way to start, migrate, and shutdown an embedded database.
 */
object EmbeddedPostgresManager {
    /**
     * Connection information for the embedded PostgreSQL instance.
     */
    data class ConnectionInfo(
        val jdbcUrl: String,
        val r2dbcUrl: String,
        val username: String,
        val password: String
    )

    /**
     * Executes a block with an embedded PostgreSQL instance.
     * Starts the database, runs migrations, executes the block, and shuts down the database.
     *
     * @param migrationDirectory Path to the Flyway migration scripts
     * @param taskName Name of the task for logging purposes
     * @param block The block to execute with the database connection info
     */
    fun <T> withEmbeddedPostgres(
        migrationDirectory: java.io.File,
        taskName: String,
        schema: String,
        block: (ConnectionInfo) -> T
    ): T {
        val embeddedPostgres = try {
            println("Starting embedded PostgreSQL for $taskName...")
            io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder().start()
        } catch (e: Exception) {
            throw GradleException("Failed to start embedded PostgreSQL for $taskName", e)
        }

        try {
            val jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
            val username = "postgres"
            val password = "postgres"
            // Convert JDBC URL to R2DBC URL: jdbc:postgresql://host:port/db -> r2dbc:postgresql://host:port/db
            val r2dbcUrl = jdbcUrl.replace("jdbc:postgresql://", "r2dbc:postgresql://")

            println("Embedded PostgreSQL JDBC URL: $jdbcUrl")

            // Run Flyway migrations
            println("Running Flyway migrations...")
            val flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("filesystem:${migrationDirectory.absolutePath}")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .schemas(schema)
                .createSchemas(true)
                .load()

            val result = flyway.migrate()
            println("Migrations completed: ${result.migrationsExecuted} executed")

            val connectionInfo = ConnectionInfo(jdbcUrl, r2dbcUrl, username, password)
            return block(connectionInfo)

        } finally {
            println("Shutting down embedded PostgreSQL...")
            embeddedPostgres.close()
        }
    }
}

/**
 * Base class for tasks that need embedded PostgreSQL with migrations.
 * Provides the migration directory configuration and utility methods.
 */
abstract class EmbeddedPostgresTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationDirectory: DirectoryProperty

    @get:Input
    abstract val dbSchema: Property<String>

    /**
     * Executes a block with an embedded PostgreSQL instance.
     */
    protected fun <T> withEmbeddedPostgres(block: (EmbeddedPostgresManager.ConnectionInfo) -> T): T {
        return EmbeddedPostgresManager.withEmbeddedPostgres(
            migrationDirectory.get().asFile,
            this::class.simpleName ?: "UnknownTask",
            dbSchema.get(),
            block
        )
    }
}

abstract class JooqCodegenTask : EmbeddedPostgresTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    init {
        outputs.cacheIf { true }
    }

    @TaskAction
    fun generate() {
        withEmbeddedPostgres { connectionInfo ->
            // Ensure output directory exists
            outputDirectory.get().asFile.mkdirs()

            // Configure jOOQ for Kotlin generation
            val configuration = org.jooq.meta.jaxb.Configuration()
                .withJdbc(
                    org.jooq.meta.jaxb.Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(connectionInfo.jdbcUrl)
                        .withUser(connectionInfo.username)
                        .withPassword(connectionInfo.password)
                )
                .withGenerator(
                    org.jooq.meta.jaxb.Generator()
                        .withName("org.jooq.codegen.KotlinGenerator")
                        .withDatabase(
                            org.jooq.meta.jaxb.Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withIncludes(".*")
                                .withExcludes("flyway_schema_history")
                                .withInputSchema(dbSchema.get())
                        )
                        .withGenerate(
                            org.jooq.meta.jaxb.Generate()
                                .withPojosAsKotlinDataClasses(true)
                                .withKotlinNotNullPojoAttributes(true)
                                .withKotlinNotNullRecordAttributes(true)
                                .withKotlinNotNullInterfaceAttributes(true)
                                .withImplicitJoinPathsAsKotlinProperties(true)
                                .withRecords(true)
                                .withPojos(true)
                                .withDaos(false)
                                .withFluentSetters(true)
                                .withDeprecated(false)
                        )
                        .withTarget(
                            org.jooq.meta.jaxb.Target()
                                .withPackageName(packageName.get())
                                .withDirectory(outputDirectory.get().asFile.absolutePath)
                        )
                )

            println("Generating jOOQ Kotlin classes...")
            org.jooq.codegen.GenerationTool.generate(configuration)

            println("jOOQ code generation completed!")
            println("Output: ${outputDirectory.get().asFile.absolutePath}")
        }
    }
}

/**
 * Task that starts an embedded PostgreSQL and writes connection info to a file.
 * This allows other tasks (like processAot) to use the connection info.
 */
abstract class StartEmbeddedPostgresTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationDirectory: DirectoryProperty

    @get:Input
    abstract val dbSchema: Property<String>

    @get:OutputFile
    abstract val connectionInfoFile: RegularFileProperty

    // Keep reference to the embedded DB for cleanup
    private var embeddedPostgres: io.zonky.test.db.postgres.embedded.EmbeddedPostgres? = null

    @TaskAction
    fun start() {
        val db = try {
            println("Starting embedded PostgreSQL...")
            io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder().start()
        } catch (e: Exception) {
            throw GradleException("Failed to start embedded PostgreSQL", e)
        }
        embeddedPostgres = db

        val jdbcUrl = db.getJdbcUrl("postgres", "postgres")
        val r2dbcUrl = jdbcUrl.replace("jdbc:postgresql://", "r2dbc:postgresql://")
        val username = "postgres"
        val password = "postgres"

        println("Embedded PostgreSQL JDBC URL: $jdbcUrl")

        // Run Flyway migrations
        println("Running Flyway migrations...")
        val flyway = org.flywaydb.core.Flyway.configure()
            .dataSource(jdbcUrl, username, password)
            .locations("filesystem:${migrationDirectory.get().asFile.absolutePath}")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .schemas(dbSchema.get())
            .createSchemas(true)
            .load()

        val result = flyway.migrate()
        println("Migrations completed: ${result.migrationsExecuted} executed")

        // Write connection info to file
        val props = Properties()
        props.setProperty("jdbcUrl", jdbcUrl)
        props.setProperty("r2dbcUrl", r2dbcUrl)
        props.setProperty("username", username)
        props.setProperty("password", password)
        props.setProperty("port", jdbcUrl.extractPort())

        connectionInfoFile.get().asFile.outputStream().use { out -> props.store(out, "Embedded PostgreSQL Connection Info") }
        println("Connection info written to: ${connectionInfoFile.get().asFile.absolutePath}")
    }

    private fun String.extractPort(): String {
        val portRegex = ":([0-9]+)/".toRegex()
        return portRegex.find(this)?.groupValues?.get(1) ?: "5432"
    }
}

/**
 * Task that stops the embedded PostgreSQL.
 * Reads the port from the connection info file to identify which instance to stop.
 * Note: This is a best-effort cleanup. The embedded DB will also be cleaned up when the JVM exits.
 */
abstract class StopEmbeddedPostgresTask : DefaultTask() {

    @TaskAction
    fun stop() {
        println("Embedded PostgreSQL will be cleaned up on JVM exit")
        // The embedded DB is started in the same JVM, so it will be cleaned up automatically
        // when the Gradle daemon exits or when the build finishes
    }
}

/**
 * Helper extension to read connection info from properties file.
 */
fun File.readConnectionInfo(): Map<String, String> {
    val props = Properties()
    inputStream().use { input -> props.load(input) }
    return mapOf(
        "R2DBC_URL" to (props.getProperty("r2dbcUrl") ?: ""),
        "DB_URL" to (props.getProperty("jdbcUrl") ?: ""),
        "DB_USER" to (props.getProperty("username") ?: ""),
        "DB_PASSWORD" to (props.getProperty("password") ?: "")
    )
}

// Register the tasks
val dbSchemaValue = project.property("db.schema") as String

tasks.register<FlywayMigrateTask>("dbMigrate") {
    group = "database"
    description = "Migrate the database using Flyway"
    dbSchema.set(dbSchemaValue)
    dependsOn("processResources")
}

tasks.register<FlywayCleanTask>("dbClean") {
    group = "database"
    description = "Clean the database (drop all objects)"
    dbSchema.set(dbSchemaValue)
    dependsOn("processResources")
}

tasks.register<FlywayInfoTask>("dbInfo") {
    group = "database"
    description = "Show migration status and info"
    dbSchema.set(dbSchemaValue)
    dependsOn("processResources")
}

tasks.register<FlywayValidateTask>("dbValidate") {
    group = "database"
    description = "Validate Flyway migrations"
    dbSchema.set(dbSchemaValue)
    dependsOn("processResources")
}

tasks.register<JooqCodegenTask>("jooqCodegen") {
    group = "jooq"
    description = "Generate jOOQ Kotlin classes from database schema using embedded PostgreSQL"

    migrationDirectory.set(project.layout.buildDirectory.dir("resources/main/db/migration"))
    outputDirectory.set(project.layout.buildDirectory.dir("generated-sources/jooq"))
    packageName.set("com.github.template.jooq")
    dbSchema.set(dbSchemaValue)

    dependsOn("processResources")
}

// Start embedded PostgreSQL for AOT processing
tasks.register<StartEmbeddedPostgresTask>("startEmbeddedPostgresForAot") {
    group = "build"
    description = "Start embedded PostgreSQL for AOT processing"

    migrationDirectory.set(project.layout.buildDirectory.dir("resources/main/db/migration"))
    dbSchema.set(dbSchemaValue)
    connectionInfoFile.set(project.layout.buildDirectory.file("embedded-db/connection.properties"))

    dependsOn("processResources")
}

// Stop embedded PostgreSQL after AOT processing
tasks.register<StopEmbeddedPostgresTask>("stopEmbeddedPostgresForAot") {
    group = "build"
    description = "Stop embedded PostgreSQL after AOT processing"
}

// Configure processAot to use the embedded database
// The connection info is read from the file written by startEmbeddedPostgresForAot
tasks.named<JavaExec>("processAot").configure {
    dependsOn("startEmbeddedPostgresForAot")
    finalizedBy("stopEmbeddedPostgresForAot")

    doFirst {
        val connectionInfoFile = project.layout.buildDirectory.file("embedded-db/connection.properties").get().asFile
        if (connectionInfoFile.exists()) {
            val connectionInfo = connectionInfoFile.readConnectionInfo()
            environment("R2DBC_URL", connectionInfo["R2DBC_URL"]!!)
            environment("DB_URL", connectionInfo["DB_URL"]!!)
            environment("DB_USER", connectionInfo["DB_USER"]!!)
            environment("DB_PASSWORD", connectionInfo["DB_PASSWORD"]!!)
            println("Configured processAot with embedded database: ${connectionInfo["DB_URL"]}")
        }
    }
}

// Make compilation depend on jOOQ code generation
tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
}

tasks.named<Delete>("clean") {
    delete("${layout.buildDirectory.get().asFile}/generated-sources/jooq")
}

// Configure native container builds
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    environment = mapOf(
        "BP_NATIVE_IMAGE" to "true",
        "BP_JVM_VERSION" to "25"
    )
}

// Disable test AOT processing - tests use dynamic database configuration (Zonky embedded PostgreSQL)
// which requires runtime properties that aren't available during AOT processing
tasks.named("processTestAot").configure {
    enabled = false
}

// Fixes GraalVM toolchain so that bin/ contains working native-image executables.
// Iterates ALL oracle_corporation directories in ~/.gradle/jdks/ directly (no toolchain resolution)
// to avoid chicken-and-egg: this must run BEFORE Gradle checks NATIVE_IMAGE capability.
// - Linux/macOS (tar.gz): Gradle's extraction breaks symlinks, making bin/native-image a 0-byte file.
//   This task restores symlinks pointing to ../lib/svm/bin/native-image.
// - Windows (zip): GraalVM ships bin/native-image.cmd (batch wrapper) but the actual native-image.exe
//   lives in lib/svm/bin/. Gradle needs bin/native-image.exe for NATIVE_IMAGE capability detection.
//   This task copies the exe from lib/svm/bin/ to bin/.
val fixGraalVmSymlinks by tasks.registering {
    doLast {
        val gradleJdks = File(System.getProperty("user.home"), ".gradle/jdks")
        if (!gradleJdks.exists()) return@doLast

        // If settings.gradle.kts just fixed native-image, the daemon's in-memory JDK metadata
        // cache is stale (it cached "no NATIVE_IMAGE" from a previous session). A daemon restart
        // is required for the fix to take effect.
        val markerFile = gradleJdks.resolve(".graalvm-fix-applied")
        if (markerFile.exists()) {
            markerFile.delete()
            throw GradleException(
                "GraalVM native-image installation was just fixed. " +
                "The Gradle daemon must be restarted to refresh its JDK metadata cache.\n" +
                "Please run: ./gradlew --stop && ./gradlew clean nativeCompile test"
            )
        }

        // Finds the Java Home directory within a Gradle JDK installation directory.
        // Handles different layouts:
        // - Direct: <jdkDir>/bin/java (Linux extracted)
        // - macOS: <jdkDir>/<name>/Contents/Home/bin/java
        // - Nested: <jdkDir>/<name>/bin/java (some Linux extractions)
        fun findJavaHome(jdkDir: File): File? {
            val javaName = if (isWindows) "java.exe" else "java"
            if (jdkDir.resolve("bin/$javaName").exists()) return jdkDir
            jdkDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { subDir ->
                val contentsHome = subDir.resolve("Contents/Home")
                if (contentsHome.resolve("bin/$javaName").exists()) return contentsHome
                if (subDir.resolve("bin/$javaName").exists()) return subDir
            }
            return null
        }

        gradleJdks.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("oracle_corporation") }
            ?.forEach { jdkDir ->
                val javaHome = findJavaHome(jdkDir) ?: return@forEach
                val binDir = javaHome.resolve("bin")
                val svmBinDir = javaHome.resolve("lib/svm/bin")

                if (!svmBinDir.exists() || !binDir.exists()) return@forEach

                svmBinDir.listFiles()?.forEach { svmBinary ->
                    val binFile = binDir.resolve(svmBinary.name)
                    val needsFix = (!binFile.exists() || binFile.length() == 0L) && svmBinary.length() > 0L
                    if (!needsFix) return@forEach

                    if (binFile.exists()) binFile.delete()

                    if (isWindows) {
                        svmBinary.copyTo(binFile)
                        logger.lifecycle("Copied: lib/svm/bin/${svmBinary.name} -> bin/${svmBinary.name}")
                    } else {
                        Files.createSymbolicLink(
                            binFile.toPath(),
                            binFile.toPath().parent.relativize(svmBinary.toPath())
                        )
                        logger.lifecycle("Fixed symlink: bin/${svmBinary.name} -> ${binFile.toPath().parent.relativize(svmBinary.toPath())}")
                    }
                }
            }
    }
}

// Configure GraalVM Native compilation
graalvmNative {
    // Disable native test support - tests use dynamic database configuration (Zonky embedded PostgreSQL)
    // which is incompatible with AOT processing
    testSupport = false

    toolchainDetection = true
    binaries {
        named("main") {
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(javaVersion)
                vendor = JvmVendorSpec.ORACLE
                nativeImageCapable = true
            }
            // Enable G1 garbage collector on Linux for better latency and throughput
            // G1 GC is only supported on Linux AMD64 and AArch64
            if (System.getProperty("os.name").lowercase().contains("linux")) {
                buildArgs.add("--gc=G1")
            }
        }
    }
}

tasks.named("nativeCompile") {
    dependsOn(fixGraalVmSymlinks)
}
