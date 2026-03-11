import java.net.URI
import java.util.Optional

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
}

// Fix GraalVM installations before toolchain probing.
// Gradle's extraction can break native-image:
// - Windows (zip): native-image.exe lives in lib/svm/bin/, not bin/. Gradle checks bin/.
// - Linux/macOS (tar.gz): symlinks break during extraction, leaving 0-byte bin/native-image.
// This block runs during settings evaluation (before toolchain resolution) to fix existing
// Oracle GraalVM installations so Gradle can detect NATIVE_IMAGE capability.
run {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val gradleJdks = java.io.File(System.getProperty("user.home"), ".gradle/jdks")
    var fixApplied = false

    // Finds the Java Home directory within a Gradle JDK installation directory.
    // Handles different layouts:
    // - Direct: <jdkDir>/bin/java (Linux extracted)
    // - macOS: <jdkDir>/<name>/Contents/Home/bin/java
    // - Nested: <jdkDir>/<name>/bin/java (some Linux extractions)
    fun findJavaHome(jdkDir: java.io.File): java.io.File? {
        val javaName = if (isWindows) "java.exe" else "java"
        if (java.io.File(jdkDir, "bin/$javaName").exists()) return jdkDir
        jdkDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { subDir ->
            val contentsHome = java.io.File(subDir, "Contents/Home")
            if (java.io.File(contentsHome, "bin/$javaName").exists()) return contentsHome
            if (java.io.File(subDir, "bin/$javaName").exists()) return subDir
        }
        return null
    }

    if (gradleJdks.exists()) {
        gradleJdks.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("oracle_corporation") }
            ?.forEach { jdkDir ->
                val javaHome = findJavaHome(jdkDir)

                // Delete corrupt installations (missing java executable) so Gradle re-downloads
                if (javaHome == null) {
                    jdkDir.deleteRecursively()
                    fixApplied = true
                    return@forEach
                }

                // Copy/fix native-image from lib/svm/bin/ to bin/
                val binDir = java.io.File(javaHome, "bin")
                val svmBinDir = java.io.File(javaHome, "lib/svm/bin")
                if (svmBinDir.exists() && binDir.exists()) {
                    svmBinDir.listFiles()?.forEach { svmBinary ->
                        val binFile = java.io.File(binDir, svmBinary.name)
                        val needsFix = (!binFile.exists() || binFile.length() == 0L) && svmBinary.length() > 0
                        if (needsFix) {
                            if (binFile.exists()) binFile.delete()
                            if (isWindows) {
                                svmBinary.copyTo(binFile)
                            } else {
                                java.nio.file.Files.createSymbolicLink(
                                    binFile.toPath(),
                                    binFile.toPath().parent.relativize(svmBinary.toPath())
                                )
                            }
                            fixApplied = true
                        }
                    }
                }
            }
    }
    // When a fix is applied, the Gradle daemon's in-memory JDK metadata cache may be stale
    // (it cached "no NATIVE_IMAGE" from a previous session). Signal fixGraalVmSymlinks task
    // to abort with a clear message to restart the daemon.
    if (fixApplied) {
        val markerFile = java.io.File(gradleJdks, ".graalvm-fix-applied")
        markerFile.writeText("fixed")
    }
}

// Custom toolchain resolver that downloads Oracle GraalVM from Oracle's official CDN.
// The foojay-resolver maps GRAAL_VM to GraalVM Community (no G1 GC support) and ORACLE
// to Oracle OpenJDK (no native-image). Neither resolves to Oracle GraalVM, so this
// custom resolver fills the gap. Combined with nativeImageCapable = true in the toolchain
// spec, this ensures Oracle GraalVM is auto-downloaded on any new machine.
// Oracle GraalVM support was not planned yet https://github.com/gradle/foojay-toolchains/issues/103
abstract class OracleGraalVmResolver : org.gradle.jvm.toolchain.JavaToolchainResolver {
    override fun resolve(request: org.gradle.jvm.toolchain.JavaToolchainRequest): Optional<org.gradle.jvm.toolchain.JavaToolchainDownload> {
        val spec = request.javaToolchainSpec

        // Only handle requests for Oracle vendor (Oracle GraalVM has java.vendor = "Oracle Corporation")
        if (!spec.vendor.isPresent) return Optional.empty()
        val vendor = spec.vendor.get()
        if (!vendor.matches("Oracle Corporation")) return Optional.empty()

        val version = spec.languageVersion.get().asInt()

        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val osStr = when {
            "mac" in os || "darwin" in os -> "macos"
            "linux" in os -> "linux"
            "windows" in os -> "windows"
            else -> return Optional.empty()
        }

        val archStr = when {
            "aarch64" in arch || "arm64" in arch -> "aarch64"
            "amd64" in arch || "x86_64" in arch -> "x64"
            else -> return Optional.empty()
        }

        val ext = if (osStr == "windows") "zip" else "tar.gz"
        val url = "https://download.oracle.com/graalvm/$version/latest/graalvm-jdk-${version}_${osStr}-${archStr}_bin.$ext"

        return Optional.of(org.gradle.jvm.toolchain.JavaToolchainDownload.fromUri(URI.create(url)))
    }
}

// Register the custom resolver using the same internal API approach as the foojay plugin
val registry = (settings as org.gradle.api.internal.SettingsInternal)
    .services.get(org.gradle.jvm.toolchain.JavaToolchainResolverRegistry::class.java)
registry.register(OracleGraalVmResolver::class.java)

toolchainManagement {
    jvm {
        javaRepositories {
            // Oracle GraalVM resolver checked first
            repository("oracle-graalvm") {
                resolverClass.set(OracleGraalVmResolver::class.java)
            }
            // Foojay resolver as fallback for other JDKs
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}

rootProject.name = "ReactiveServiceTemplate"

include("template")
include("contract")
include("test-processor")
