#!/usr/bin/env bash
set -euo pipefail

KENVY_PUBLIC_VERSION="${KENVY_PUBLIC_VERSION:-0.1.2}"
KOTLIN_VERSION="${KOTLIN_VERSION:-2.1.20}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SMOKE_DIR="${KENVY_PUBLIC_SMOKE_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/kenvy-public-consumer-smoke.XXXXXX")}"
trap 'rm -rf "${SMOKE_DIR}"' EXIT

mkdir -p "${SMOKE_DIR}/gradle/wrapper"
cp "${ROOT_DIR}/gradlew" "${SMOKE_DIR}/gradlew"
cp "${ROOT_DIR}/gradlew.bat" "${SMOKE_DIR}/gradlew.bat"
cp "${ROOT_DIR}/gradle/wrapper/gradle-wrapper.jar" "${SMOKE_DIR}/gradle/wrapper/gradle-wrapper.jar"
cp "${ROOT_DIR}/gradle/wrapper/gradle-wrapper.properties" "${SMOKE_DIR}/gradle/wrapper/gradle-wrapper.properties"
chmod +x "${SMOKE_DIR}/gradlew"

cat > "${SMOKE_DIR}/settings.gradle.kts" <<'EOF_SETTINGS'
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kenvy-public-consumer-smoke"
EOF_SETTINGS

cat > "${SMOKE_DIR}/build.gradle.kts" <<EOF_BUILD
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "${KOTLIN_VERSION}"
    id("io.github.adriandleon.kenvy") version "${KENVY_PUBLIC_VERSION}"
}

group = "com.example.smoke"

kotlin {
    jvm()
}
EOF_BUILD

cat > "${SMOKE_DIR}/kenvy.toml" <<'EOF_TOML'
[properties.base_url]
type = "String"
default = "https://example.com"
EOF_TOML

mkdir -p "${SMOKE_DIR}/src/commonMain/kotlin/com/example/smoke"
cat > "${SMOKE_DIR}/src/commonMain/kotlin/com/example/smoke/UseKenvy.kt" <<'EOF_KOTLIN'
package com.example.smoke

fun smokeBaseUrl(): String = Kenvy.baseUrl
EOF_KOTLIN

if grep -R -n -E "mavenLocal\\(|includeBuild\\(|withPluginClasspath\\(" "${SMOKE_DIR}"; then
    echo "Public consumer smoke project contains a local plugin shortcut." >&2
    exit 1
fi

echo "Kenvy public consumer smoke"
echo "  version: ${KENVY_PUBLIC_VERSION}"
echo "  project: ${SMOKE_DIR}"

(
    cd "${SMOKE_DIR}"
    ./gradlew --no-daemon generateKenvy generateKenvyExample compileKotlinJvm
)

GENERATED_FILE="${SMOKE_DIR}/build/generated/kenvy/commonMain/kotlin/com/example/smoke/Kenvy.kt"
EXAMPLE_FILE="${SMOKE_DIR}/local.properties.example"

test -f "${GENERATED_FILE}"
test -f "${EXAMPLE_FILE}"

echo "Verified generated file: ${GENERATED_FILE}"
echo "Verified example file: ${EXAMPLE_FILE}"
