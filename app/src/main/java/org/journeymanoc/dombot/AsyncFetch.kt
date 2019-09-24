package org.journeymanoc.dombot

import java.util.*
import kotlin.collections.HashSet

/**
 * A utility for asynchronous resource acquisition.
 */
class AsyncFetch<T>(val description: String, private val syncFetch: () -> T) {
    private val concurrencyLock = Any()
    private var consumerQueueConcurrent: Queue<(T?, Throwable?) -> Unit>? = null
    private var onceSet: MutableSet<String> = HashSet()
    private var resultConcurrent: T? = null
    private var throwableConcurrent: Throwable? = null

    private fun asyncFetch(): Thread {
        @Suppress("DEPRECATION")
        return async(syncFetch, ::asyncFetchForeground)
    }

    private fun asyncFetchForeground(result: T?, throwable: Throwable?) {
        if (throwable !== null) {
            System.err.println("An error occurred while acquiring an asynchronous resource. $this")
        }

        synchronized (concurrencyLock) {
            resultConcurrent = result
            throwableConcurrent = throwable

            for (consumer in consumerQueueConcurrent!!) {
                consumer.invoke(result, throwable)
            }

            consumerQueueConcurrent = null
        }
    }

    /**
     * If the resource has been acquired, return it or throw the exception wrapped in {@link AsyncFetchException} that
     * occurred while acquiring the resource. This method does not initiate the resource acquisition.
     *
     * {@throws AsyncFetchException}
     *
     * {@see #asyncGet}
     * {@see #asyncGetOnce}
     */
    @Throws(AsyncFetchException::class)
    fun syncGetIfFinished(): T? {
        if (resultConcurrent !== null) {
            return resultConcurrent!!
        }

        if (throwableConcurrent !== null) {
            throw AsyncFetchException(throwableConcurrent!!)
        }

        synchronized (concurrencyLock) {
            if (resultConcurrent !== null) {
                return resultConcurrent!!
            }

            if (throwableConcurrent !== null) {
                throw AsyncFetchException(throwableConcurrent!!)
            }
        }

        return null
    }

    /**
     * Asynchronously acquires the resource, and when completed, executes {@param foreground} on the UI thread.
     *
     * {@see #asyncGetOnce}
     * {@see #syncGetIfFinished}
     */
    fun asyncGet(foreground: (T?, Throwable?) -> Unit) {
        if (resultConcurrent !== null || throwableConcurrent !== null) {
            foreground.invoke(resultConcurrent, throwableConcurrent)
            return
        }

        synchronized (concurrencyLock) {
            if (resultConcurrent !== null || throwableConcurrent !== null) {
                foreground.invoke(resultConcurrent, throwableConcurrent)
                return
            }

            if (consumerQueueConcurrent === null) {
                consumerQueueConcurrent = ArrayDeque()
                consumerQueueConcurrent!! += foreground

                asyncFetch()
            } else {
                consumerQueueConcurrent!! += foreground
            }
        }
    }

    /**
     * Like {@link #asyncGet}, but only registers the {@param foreground} handler if this method has not previously
     * been called with the same {@param id}. Useful for error handling.
     *
     * {@see #asyncGet}
     * {@see #syncGetIfFinished}
     */
    fun asyncGetOnce(id: String, foreground: (T?, Throwable?) -> Unit) {
        if (onceSet.add(id)) {
            asyncGet(foreground)
        }
    }

    override fun toString(): String {
        return "AsyncFetch(description='$description')"
    }
}

class AsyncFetchException(cause: Throwable?) : Exception(cause)