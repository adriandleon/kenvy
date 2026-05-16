package io.github.adriandleon.kenvy

import org.gradle.api.GradleException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KenvyExternalProviderTimeoutTest {

    @Test fun `slow provider times out and names provider and property`() {
        val gate = KenvyExternalProviderTimeoutGate(Duration.ofMillis(50))

        val error = assertFailsWith<GradleException> {
            gate.resolve(
                requests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault")),
                resolver = KenvyExternalProviderResolver {
                    Thread.sleep(200)
                    mapOf("api_key" to "PROVIDER_SECRET_VALUE")
                }
            )
        }

        assertContains(error.message.orEmpty(), "ci-vault")
        assertContains(error.message.orEmpty(), "api_key")
        assertContains(error.message.orEmpty(), "50ms")
        assertFalse(error.message.orEmpty().contains("PROVIDER_SECRET_VALUE"))
    }

    @Test fun `multiple properties on same provider produce one consolidated timeout error`() {
        val gate = KenvyExternalProviderTimeoutGate(Duration.ofMillis(50))

        val error = assertFailsWith<GradleException> {
            gate.resolve(
                requests = listOf(
                    KenvyExternalProviderRequest("api_key", "ci-vault"),
                    KenvyExternalProviderRequest("signing_key", "ci-vault")
                ),
                resolver = KenvyExternalProviderResolver {
                    Thread.sleep(200)
                    mapOf(
                        "api_key" to "PROVIDER_SECRET_VALUE",
                        "signing_key" to "ANOTHER_SECRET_VALUE"
                    )
                }
            )
        }

        assertContains(error.message.orEmpty(), "ci-vault")
        assertContains(error.message.orEmpty(), "api_key")
        assertContains(error.message.orEmpty(), "signing_key")
        assertFalse(error.message.orEmpty().contains("PROVIDER_SECRET_VALUE"))
        assertFalse(error.message.orEmpty().contains("ANOTHER_SECRET_VALUE"))
    }

    @Test fun `provider returning before timeout resolves once per provider`() {
        val calls = AtomicInteger(0)
        val gate = KenvyExternalProviderTimeoutGate(Duration.ofMillis(200))

        val values = gate.resolve(
            requests = listOf(
                KenvyExternalProviderRequest("api_key", "ci-vault"),
                KenvyExternalProviderRequest("signing_key", "ci-vault")
            ),
            resolver = KenvyExternalProviderResolver { request ->
                calls.incrementAndGet()
                assertEquals("ci-vault", request.providerName)
                mapOf(
                    "api_key" to "provider-api",
                    "signing_key" to "provider-signing"
                )
            }
        )

        assertEquals(1, calls.get())
        assertEquals("provider-api", values["api_key"])
        assertEquals("provider-signing", values["signing_key"])
    }

    @Test fun `provider failure message does not include raw provider exception text`() {
        val gate = KenvyExternalProviderTimeoutGate(Duration.ofMillis(200))

        val error = assertFailsWith<GradleException> {
            gate.resolve(
                requests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault")),
                resolver = KenvyExternalProviderResolver {
                    throw IllegalStateException("provider returned PROVIDER_SECRET_VALUE")
                }
            )
        }

        assertContains(error.message.orEmpty(), "ci-vault")
        assertContains(error.message.orEmpty(), "api_key")
        assertFalse(error.message.orEmpty().contains("PROVIDER_SECRET_VALUE"))
        assertEquals(null, error.cause)
    }

    @Test fun `same property cannot be assigned to multiple providers`() {
        val gate = KenvyExternalProviderTimeoutGate(Duration.ofMillis(200))

        val error = assertFailsWith<GradleException> {
            gate.resolve(
                requests = listOf(
                    KenvyExternalProviderRequest("api_key", "ci-vault"),
                    KenvyExternalProviderRequest("api_key", "backup-vault")
                ),
                resolver = KenvyExternalProviderResolver {
                    mapOf("api_key" to "provider-api")
                }
            )
        }

        assertContains(error.message.orEmpty(), "api_key")
        assertContains(error.message.orEmpty(), "ci-vault")
        assertContains(error.message.orEmpty(), "backup-vault")
    }

    @Test fun `blank provider value is preserved for downstream type validation`() {
        val gate = KenvyExternalProviderTimeoutGate(Duration.ofMillis(200))

        val values = gate.resolve(
            requests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault")),
            resolver = KenvyExternalProviderResolver {
                mapOf("api_key" to "")
            }
        )

        assertEquals("", values["api_key"])
    }

    @Test fun `timeout returns even when provider ignores interruption`() {
        val gate = KenvyExternalProviderTimeoutGate(Duration.ofMillis(50))
        val start = System.nanoTime()

        assertFailsWith<GradleException> {
            gate.resolve(
                requests = listOf(KenvyExternalProviderRequest("api_key", "ci-vault")),
                resolver = KenvyExternalProviderResolver {
                    val stopAt = System.nanoTime() + Duration.ofMillis(500).toNanos()
                    while (System.nanoTime() < stopAt) {
                        try {
                            Thread.sleep(25)
                        } catch (_: InterruptedException) {
                            // Simulate provider code that does not cooperate with cancellation.
                        }
                    }
                    mapOf("api_key" to "PROVIDER_SECRET_VALUE")
                }
            )
        }

        val elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis()
        assertTrue(elapsedMillis < 300, "timeout should not wait for the provider loop to finish")
        assertTrue(
            Thread.getAllStackTraces().keys.any { it.name == "kenvy-external-provider" && it.isDaemon },
            "timed-out provider work should run on a daemon thread"
        )
    }

    @Test fun `empty provider requests avoid constructing timeout machinery`() {
        var executorCreated = false
        var resolverCalled = false
        val gate = KenvyExternalProviderTimeoutGate(
            timeout = Duration.ofMillis(50),
            executorFactory = {
                executorCreated = true
                Executors.newSingleThreadExecutor()
            }
        )

        val values = gate.resolve(
            requests = emptyList(),
            resolver = KenvyExternalProviderResolver {
                resolverCalled = true
                emptyMap()
            }
        )

        assertTrue(values.isEmpty())
        assertFalse(executorCreated)
        assertFalse(resolverCalled)
    }
}
