package io.github.adriandleon.kenvy

import org.gradle.api.GradleException
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal data class KenvyExternalProviderRequest(
    val propertyName: String,
    val providerName: String
)

internal data class KenvyExternalProviderBatchRequest(
    val providerName: String,
    val propertyNames: List<String>
)

internal fun interface KenvyExternalProviderResolver {
    fun resolve(request: KenvyExternalProviderBatchRequest): Map<String, String>
}

internal class KenvyExternalProviderTimeoutGate(
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val executorFactory: () -> ExecutorService = { Executors.newSingleThreadExecutor(daemonThreadFactory()) }
) {

    fun resolve(
        requests: List<KenvyExternalProviderRequest>,
        resolver: KenvyExternalProviderResolver
    ): Map<String, String> {
        if (requests.isEmpty()) return emptyMap()
        validateUniquePropertyProviders(requests)

        val resolvedValues = linkedMapOf<String, String>()
        requests.groupBy { it.providerName }.forEach { (providerName, providerRequests) ->
            resolvedValues.putAll(resolveProvider(providerName, providerRequests, resolver))
        }
        return resolvedValues
    }

    private fun resolveProvider(
        providerName: String,
        providerRequests: List<KenvyExternalProviderRequest>,
        resolver: KenvyExternalProviderResolver
    ): Map<String, String> {
        val propertyNames = providerRequests.map { it.propertyName }.distinct()
        val executor = executorFactory()

        try {
            val future = executor.submit(
                Callable {
                    resolver.resolve(KenvyExternalProviderBatchRequest(providerName, propertyNames))
                }
            )
            val result = try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                throw GradleException(
                    "Kenvy: External provider '$providerName' timed out after ${timeout.toMessageText()} " +
                        "while resolving properties: ${propertyNames.joinToString(", ")}."
                )
            }

            return propertyNames.mapNotNull { propertyName ->
                result[propertyName]?.let { propertyName to it }
            }.toMap()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GradleException(
                "Kenvy: External provider '$providerName' was interrupted while resolving properties: " +
                    propertyNames.joinToString(", ") + ".",
                exception
            )
        } catch (exception: ExecutionException) {
            throw GradleException(
                "Kenvy: External provider '$providerName' failed while resolving properties: " +
                    propertyNames.joinToString(", ") + "."
            )
        } finally {
            executor.shutdownNow()
        }
    }

    companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}

private fun validateUniquePropertyProviders(requests: List<KenvyExternalProviderRequest>) {
    val providersByProperty = requests.groupBy { it.propertyName }
        .filterValues { providerRequests -> providerRequests.map { it.providerName }.distinct().size > 1 }

    if (providersByProperty.isEmpty()) return

    val conflicts = providersByProperty.entries.joinToString("; ") { (propertyName, providerRequests) ->
        val providers = providerRequests.map { it.providerName }.distinct().joinToString(", ")
        "$propertyName -> $providers"
    }
    throw GradleException(
        "Kenvy: External provider requests assign the same property to multiple providers: $conflicts."
    )
}

private fun daemonThreadFactory(): ThreadFactory =
    ThreadFactory { runnable ->
        Thread(runnable, "kenvy-external-provider").apply {
            isDaemon = true
        }
    }

private fun Duration.toMessageText(): String =
    if (toMillis() % 1000L == 0L) "${seconds}s" else "${toMillis()}ms"
