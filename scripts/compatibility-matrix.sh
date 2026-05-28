#!/usr/bin/env bash
set -euo pipefail

scenario="${1:-}"
if [[ -z "${scenario}" ]]; then
  echo "Usage: $0 <android-baseline|android-modern|ios-modern|jvm-modern>" >&2
  exit 64
fi

case "${scenario}" in
  android-baseline)
    export KENVY_COMPATIBILITY_STACK="android-baseline"
    export KOTLIN_VERSION="${KOTLIN_VERSION:-2.1.20}"
    export AGP_VERSION="${AGP_VERSION:-8.5.2}"
    export JAVA_VERSION="${JAVA_VERSION:-17}"
    export JAVA_TOOLCHAIN_VERSION="${JAVA_TOOLCHAIN_VERSION:-17}"
    ;;
  android-modern)
    export KENVY_COMPATIBILITY_STACK="android-modern"
    export KOTLIN_VERSION="${KOTLIN_VERSION:-2.3.20}"
    export AGP_VERSION="${AGP_VERSION:-9.2.0}"
    export JAVA_VERSION="${JAVA_VERSION:-17}"
    export JAVA_TOOLCHAIN_VERSION="${JAVA_TOOLCHAIN_VERSION:-17}"
    ;;
  ios-modern)
    export KENVY_COMPATIBILITY_STACK="ios-modern"
    export KOTLIN_VERSION="${KOTLIN_VERSION:-2.3.20}"
    export JAVA_VERSION="${JAVA_VERSION:-17}"
    ;;
  jvm-modern)
    export KENVY_COMPATIBILITY_STACK="jvm-modern"
    export KOTLIN_VERSION="${KOTLIN_VERSION:-2.3.20}"
    export JAVA_VERSION="${JAVA_VERSION:-21}"
    export JAVA_TOOLCHAIN_VERSION="${JAVA_TOOLCHAIN_VERSION:-21}"
    ;;
  *)
    echo "Unknown compatibility scenario: ${scenario}" >&2
    exit 64
    ;;
esac

echo "Kenvy compatibility scenario: ${scenario}"
echo "Kotlin: ${KOTLIN_VERSION}"
echo "AGP: ${AGP_VERSION:-n/a}"
echo "Gradle wrapper: $(./gradlew --version --quiet | awk '/^Gradle / { print $2; exit }')"
echo "JAVA_VERSION env (informational; actual JVM set by JAVA_HOME or CI setup-java): ${JAVA_VERSION}"
echo "Java toolchain target: ${JAVA_TOOLCHAIN_VERSION:-n/a}"

./gradlew :kenvy-plugin:functionalTest --tests '*KenvyToolchainCompatibilityFunctionalTest' --rerun-tasks
