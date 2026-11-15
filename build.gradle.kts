import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    `java-test-fixtures`
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "com.barisozy"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

/**
 * Gradle's native BOM support replaces the `io.spring.dependency-management` plugin: the platform
 * is declared in every configuration that contains a version-less dependency.
 */
val springBootBom = dependencies.platform(SpringBootPlugin.BOM_COORDINATES)

springBoot {
    buildInfo {
        // A build timestamp would make every jar different.
        excludes = setOf("time")
    }
}

// `test` and `testFixtures` use Gradle's standard directories. The integration tests add one
// source set and reuse the test profile from `src/test/resources`.
val integrationTest: SourceSet = sourceSets.create("integrationTest")

integrationTest.resources.srcDir("src/test/resources")

configurations.named(integrationTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named(integrationTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation(springBootBom)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.springdoc.openapi.starter.webmvc.scalar)

    compileOnly(springBootBom)
    compileOnly(libs.jspecify)
    annotationProcessor(springBootBom)
    annotationProcessor(libs.spring.boot.configuration.processor)

    runtimeOnly(springBootBom)
    runtimeOnly(libs.postgresql)
    developmentOnly(springBootBom)
    developmentOnly(libs.spring.boot.docker.compose)

    errorprone(libs.errorprone.core)

    testImplementation(springBootBom)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.archunit.junit6)
    testImplementation(testFixtures(project()))
    testCompileOnly(springBootBom)
    testCompileOnly(libs.jspecify)
    testRuntimeOnly(springBootBom)
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesImplementation(springBootBom)
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.spring.security.crypto)
    testFixturesCompileOnly(springBootBom)
    testFixturesCompileOnly(libs.jspecify)

    // The rest of the test classpath is inherited through the extendsFrom relationships above.
    add(integrationTest.implementationConfigurationName, springBootBom)
    add(integrationTest.implementationConfigurationName, libs.testcontainers.postgresql)
    add(integrationTest.implementationConfigurationName, libs.testcontainers.junit.jupiter)
    add(integrationTest.compileOnlyConfigurationName, springBootBom)
    add(integrationTest.compileOnlyConfigurationName, libs.jspecify)
    add(integrationTest.runtimeOnlyConfigurationName, springBootBom)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
    }
}

/**
 * Byte-for-byte reproducible archives: the jar must not embed the build timestamp or rely on the
 * file-system order.
 */
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("user.timezone", "UTC")
}

tasks.named<Test>("test") {
    description = "Runs unit, web slice and architecture tests."
    finalizedBy(tasks.jacocoTestReport)
}

/**
 * The integration tests need a PostgreSQL Testcontainer, and therefore a Docker daemon. They are
 * a separate task so that `check` stays runnable without Docker.
 */
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against a PostgreSQL Testcontainer."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

/** The single coverage gate: the domain and the application layer must be covered. */
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("classes/java/main")) {
                include("**/domain/**", "**/application/**")
            },
        ),
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target(
            "*.md",
            "*.kts",
            "docs/**/*.md",
            ".agents/**/*.md",
            ".github/**/*.md",
            ".github/**/*.yml",
            "*.yml",
            "*.yaml",
            "src/**/*.yaml",
            "*.http",
            ".editorconfig",
            ".gitattributes",
            ".gitignore",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

/**
 * `./gradlew spotlessApply check` is a documented workflow. When the formatter rewrites a source
 * file, the parallel build must not compile the half-written file, so compilation is ordered after
 * the apply task whenever both are requested. This adds no dependency: `check` alone never formats.
 */
tasks.withType<JavaCompile>().configureEach {
    mustRunAfter("spotlessApply")
}

tasks.named("check") {
    dependsOn("spotlessCheck", "jacocoTestCoverageVerification")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName.set("${project.name}:${project.version}")
    environment.set(mapOf("BP_JVM_VERSION" to "25"))
}

tasks.register("renameProject") {
    group = "template"
    description = "Renames the project package, group and artifact. Usage: -Pgroup=... -Partifact=... -Ppackage=..."
    notCompatibleWithConfigurationCache("The rename task rewrites the build itself.")

    doLast {
        // The literals are deliberately split: this task rewrites its own source file and would
        // otherwise replace the constants that it needs to locate the old names.
        val oldGroup = "com" + ".barisozy"
        val oldArtifact = "clean" + "architecture"
        val oldPackage = oldGroup + "." + oldArtifact

        val newGroup = providers.gradleProperty("group").orNull?.trim()
            ?: error("Missing -Pgroup, for example -Pgroup=com.example")
        val newArtifact = providers.gradleProperty("artifact").orNull?.trim()
            ?: error("Missing -Partifact, for example -Partifact=demo")
        val newPackage = providers.gradleProperty("package").orNull?.trim()
            ?: error("Missing -Ppackage, for example -Ppackage=com.example.demo")

        val segment = Regex("[a-z][a-z0-9_]*")
        val packagePattern = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
        require(packagePattern.matches(newPackage)) { "Invalid package: $newPackage" }
        require(newGroup.split('.').all(segment::matches)) { "Invalid group: $newGroup" }
        require(Regex("[a-z][a-z0-9-]*").matches(newArtifact)) { "Invalid artifact: $newArtifact" }
        require(newPackage != oldPackage) { "The project already uses package $newPackage" }

        val sourceRoots = listOf(
            "src/main/java",
            "src/test/java",
            "src/testFixtures/java",
            "src/integrationTest/java",
        )
        val oldPath = oldPackage.replace('.', '/')
        val newPath = newPackage.replace('.', '/')

        sourceRoots.forEach { root ->
            val oldDirectory = file("$root/$oldPath")
            if (!oldDirectory.exists()) {
                return@forEach
            }
            val newDirectory = file("$root/$newPath")
            check(!newDirectory.exists()) { "Destination already exists: ${newDirectory.path}" }
            newDirectory.parentFile.mkdirs()
            oldDirectory.copyRecursively(newDirectory, overwrite = false)
            oldDirectory.deleteRecursively()

            var parent = oldDirectory.parentFile
            val stop = file(root)
            while (parent != null && parent != stop && parent.listFiles()?.isEmpty() == true) {
                parent.delete()
                parent = parent.parentFile
            }
        }

        val textExtensions = setOf(
            "java", "kt", "kts", "yaml", "yml", "md", "http", "sql", "xml",
            "properties", "toml", "json", "txt", "sh", "ps1", "adoc", "example",
        )
        // The artifact name also carries a suffix, for example in "demo-0.0.1-SNAPSHOT.jar" or in
        // a container name. The boundaries therefore exclude word characters and dots only.
        val artifactPattern = Regex("(?<![A-Za-z0-9._])${Regex.escape(oldArtifact)}(?![A-Za-z0-9._])")
        val artifactExtensions = setOf("java", "md", "yaml", "yml", "http", "example")

        fileTree(projectDir) {
            exclude(
                ".git/**",
                ".gradle/**",
                "build/**",
                "**/build/**",
                "gradle/wrapper/**",
            )
        }.forEach { candidate ->
            if (!candidate.isFile || candidate.extension !in textExtensions || candidate.length() > 2_000_000) {
                return@forEach
            }
            val original = candidate.readText()
            var updated = original.replace(oldPackage, newPackage)
            updated = updated.replace(oldPath, newPath)
            updated = updated.replace(oldGroup, newGroup)
            updated = updated.replace(oldGroup.replace('.', '/'), newGroup.replace('.', '/'))
            if (candidate.name == "settings.gradle.kts") {
                updated = updated.replace(
                    Regex("(?m)^rootProject\\.name\\s*=\\s*\".*\"$"),
                    "rootProject.name = \"$newArtifact\"",
                )
            }
            if (candidate.extension in artifactExtensions) {
                updated = updated.replace(artifactPattern, newArtifact)
                if (candidate.name == "README.md") {
                    updated = updated.replaceFirst(
                        Regex("(?m)^# (?:${Regex.escape(oldArtifact)}|Clean Architecture)(.*)$"),
                        "# $newArtifact$1",
                    )
                }
            }
            if (candidate.name == "application.yaml") {
                updated = updated.replace(
                    Regex("(?m)^(\\s*)name:\\s*${Regex.escape(oldArtifact)}\\s*$"),
                    "$1name: $newArtifact",
                )
            }
            if (updated != original) {
                candidate.writeText(updated)
            }
        }

        // The verification workflow describes the template itself; a derived project must not run
        // it, and its negative grep would otherwise match the renamed coordinates.
        val templateWorkflow = file(".github/workflows/template.yml")
        if (templateWorkflow.exists() && !templateWorkflow.delete()) {
            error("Could not delete ${templateWorkflow.path}")
        }

        logger.lifecycle("Renamed project to group=$newGroup artifact=$newArtifact package=$newPackage")
        logger.lifecycle(
            "Run ./gradlew spotlessApply and review README, docker-compose.yaml, JWT issuer/audience and CORS values.")
    }
}
