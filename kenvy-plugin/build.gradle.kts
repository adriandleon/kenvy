plugins {
    `kotlin-dsl`
    signing
    id("com.gradle.plugin-publish") version "2.0.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

group = "io.github.adriandleon.kenvy"
version = "0.1.0"

// Functional test source set — implementation begins in Story 1.2
val functionalTest by sourceSets.creating

gradlePlugin {
    website.set("https://github.com/adriandleon/kenvy")
    vcsUrl.set("https://github.com/adriandleon/kenvy.git")
    testSourceSets(functionalTest)
    plugins {
        create("kenvyPlugin") {
            id = "io.github.adriandleon.kenvy"
            implementationClass = "io.github.adriandleon.kenvy.KenvyPlugin"
            displayName = "Kenvy KMP Configuration Plugin"
            description = "Modern environment configuration for Kotlin Multiplatform"
            tags.set(listOf("kotlin", "multiplatform", "configuration", "environment", "kmp"))
        }
    }
}

// Signing is opt-in: only activated when in-memory PGP credentials are present.
// Required for Gradle Plugin Portal publication; not required for publishToMavenLocal.
val signingKey = findProperty("signing.key") as String? ?: System.getenv("SIGNING_KEY")
val signingPassword = findProperty("signing.password") as String? ?: System.getenv("SIGNING_PASSWORD")
if (signingKey != null && signingPassword != null) {
    configure<SigningExtension> {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}

tasks.withType<Sign>().configureEach {
    onlyIf { signingKey != null && signingPassword != null }
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.ktoml.core)
    compileOnly(libs.kotlin.gradlePlugin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestRuntimeOnly"(libs.junit.platform.launcher)
}

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())

tasks.test { useJUnitPlatform() }

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}
functionalTestTask.configure { shouldRunAfter(tasks.test) }
tasks.check { dependsOn(functionalTestTask) }
