import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
}

// REQUIRED: Register the convention plugin with a stable ID.
// Without this block, KenvyKotlinPlugin cannot be applied from any other module
// via plugins { id("kenvy.kotlin") }. The kotlin-dsl plugin does NOT auto-register
// convention plugins — explicit registration is always required.
gradlePlugin {
    plugins {
        create("kenvyKotlin") {
            id = "kenvy.kotlin"
            implementationClass = "KenvyKotlinPlugin"
        }
    }
}
