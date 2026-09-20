import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.pambrose.envvar)
    alias(libs.plugins.pambrose.kotlinter)
}

// Group and version are defined in gradle.properties. `-PoverrideVersion=...` replaces the version, e.g. for the
// -SNAPSHOT builds published by `make publish-snapshot` and `make publish-local-snapshot`.
providers.gradleProperty("overrideVersion").orNull?.let { version = it }

repositories {
    mavenCentral()
}

dependencies {
    // api, not implementation: each of these three shows up in the public API, so a consumer compiles
    // against it. HttpClientEngine and JsonElement appear in signatures, and the suspend functions and the
    // inline runBlocking wrappers need the coroutines types.
    api(libs.ktor.client.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}

// jev4k is embedded in other people's applications, so it targets an older JDK than it builds with: JDK 25 compiles
// it, but the bytecode and the published org.gradle.jvm.version stay at 17, the current mainstream LTS floor.
val jvmTargetVersion = libs.versions.jvm.target.get()
val jvmToolchainVersion = libs.versions.jvm.toolchain.get().toInt()

kotlin {
    jvmToolchain(jvmToolchainVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(jvmTargetVersion)
        // jvmTarget alone only sets the class-file version. This also limits the JDK API surface, so building on 25
        // can't silently link against a method that doesn't exist on 17.
        freeCompilerArgs.add("-Xjdk-release=$jvmTargetVersion")
        // kotlinx.serialization marks parts of its API experimental (the JsonArrayBuilder.addAll overloads, for
        // one). Opting in here keeps @OptIn annotations out of the sources, including the documentation examples,
        // where they would be noise on the page. It does not leak to consumers: opt-in is a compile-time setting
        // for this project only.
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
}

// KDoc site: `make kdocs` writes it to build/dokka/html, and the docs workflow publishes it at /kdocs/.
val repoUrl = "https://github.com/pambrose/jev4k"

dokka {
    moduleName = "jev4k"
    moduleVersion = project.version.toString()
    // Hide members inherited from supertypes (e.g. Throwable's message/cause on every exception);
    // each page links to its supertype instead.
    suppressInheritedMembers = true

    dokkaPublications.html {
        outputDirectory = layout.buildDirectory.dir("dokka/html")
        includes.from("docs/packages.md")
    }

    pluginsConfiguration.html {
        homepageLink = repoUrl
        footerMessage = "jev4k: a Kotlin DSL for TypeSafe's Jev System One model"
    }

    // Document only the library: test sources (test classes, fixtures, the example) are never included.
    dokkaSourceSets.matching { it.name != "main" }.configureEach {
        suppress = true
    }

    dokkaSourceSets.main {
        documentedVisibilities(VisibilityModifier.Public)
        // Link JDK types to the docs for the oldest JDK this library supports.
        jdkVersion = jvmTargetVersion.toInt()

        perPackageOption {
            matchingRegex = "com\\.pambrose\\.jev4k\\.internal.*"
            suppress = true
        }

        // "View source" links on every declaration.
        sourceLink {
            localDirectory = file("src/main/kotlin")
            remoteUrl("$repoUrl/tree/master/src/main/kotlin")
            remoteLineSuffix = "#L"
        }

        // Link JsonElement, HttpClientEngine, etc. to their libraries' API docs.
        externalDocumentationLinks.register("kotlinx.serialization") {
            url("https://kotlinlang.org/api/kotlinx.serialization/")
        }
        externalDocumentationLinks.register("ktor") {
            url("https://api.ktor.io/")
        }
    }
}

// Maven Central publishing: the Dokka HTML is packaged as the javadoc jar,
// releases are published automatically, and artifacts are signed only when a GPG key is supplied
// (the Makefile's publish targets pass one via ORG_GRADLE_PROJECT_signingInMemoryKey*).
mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        ),
    )

    pom {
        name = project.name
        description = "A Kotlin DSL and client for TypeSafe's Jev System One model."
        url = repoUrl
        licenses {
            license {
                name = "Apache License 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "pambrose"
                name = "Paul Ambrose"
                email = "paul@pambrose.com"
            }
        }
        scm {
            connection = "scm:git:git://github.com/pambrose/jev4k.git"
            developerConnection = "scm:git:ssh://github.com/pambrose/jev4k.git"
            url = repoUrl
        }
    }

    publishToMavenCentral(automaticRelease = true)
    // Skip signing when no GPG key is provided (e.g., local publishing)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
    baseline = layout.projectDirectory.file("config/detekt/baseline.xml").asFile
}

// Code coverage: CI uploads build/reports/kover/report.xml to Codecov.
// Floors that catch a slide, not targets to chase. They sit a few points below what is actually measured, so
// ordinary churn doesn't break the build; raise them once the real numbers have moved up and stayed there.
// `make coverage-log` prints the current figure rather than repeating it here, where it would go stale.
val minLineCoveragePct = 92
val minBranchCoveragePct = 75

kover {
    reports {
        total {
            // Print a coverage summary to the console when koverLog runs.
            log {
                onCheck = true
                format = "<entity> line coverage: <value>%"
                coverageUnits = CoverageUnit.LINE
                aggregationForGroup = AggregationType.COVERED_PERCENTAGE
            }

            verify {
                // Not onCheck: koverVerify can't tell a real regression from "no tests ran", so it would fail every
                // `build -x test`. CI runs it explicitly alongside the tests, and `make coverage-verify` runs it locally.
                onCheck = false
                rule("Line coverage floor") {
                    minBound(minLineCoveragePct, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
                }
                rule("Branch coverage floor") {
                    minBound(minBranchCoveragePct, CoverageUnit.BRANCH, AggregationType.COVERED_PERCENTAGE)
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // MockK attaches its agent at runtime; JDK 21+ warns unless dynamic loading is allowed.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.jar {
    manifest {
        attributes(
            // JevClient reports this in its User-Agent header.
            "Implementation-Version" to project.version,
            // Pin the JPMS module name so it doesn't shift with the jar's file name.
            "Automatic-Module-Name" to "com.pambrose.jev4k",
        )
    }
}

tasks.register<JavaExec>("runExample") {
    group = "application"
    description = "Runs the Triage example against the live API (needs TYPESAFE_API_KEY)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.pambrose.jev4k.examples.TriageExampleKt"
}

// A pre-release qualifier is a `.` or `-` delimiter followed by a known unstable
// keyword. `m\d` matches milestones (`-M1`/`.M2`) without catching stable classifiers
// like `-macos`/`-MR1`, and the `[.-]` delimiter catches both dash-style (`-alpha`)
// and dot-style (`.Beta1`) qualifiers while leaving `-jre`/`.Final` stable.
val preReleaseQualifier =
    Regex("""[.-](rc|beta|alpha|m\d|cr|snapshot|eap|dev|milestone|pre)""", RegexOption.IGNORE_CASE)

fun isNonStable(version: String): Boolean = preReleaseQualifier.containsMatchIn(version)

tasks.withType<DependencyUpdatesTask>().configureEach {
    notCompatibleWithConfigurationCache("the dependency updates plugin is not compatible with the configuration cache")
    // Reject a pre-release candidate only when the current version is stable, so
    // dependencies intentionally tracked on a pre-release line still surface updates.
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}
