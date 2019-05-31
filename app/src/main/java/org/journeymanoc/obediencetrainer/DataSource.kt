package org.journeymanoc.obediencetrainer

import android.content.Context
import android.content.res.AssetManager
import org.luaj.vm2.lib.ResourceFinder
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import java.util.stream.Stream
import kotlin.collections.HashSet
import kotlin.math.abs

interface DataSource : ResourceFinder {
    fun paths(relativePath: String): List<String>
    fun containsPath(relativePath: String): Boolean
    fun readPath(relativePath: String): InputStream?

    fun readPathBuffered(relativePath: String): BufferedReader? {
        val inputStream = readPath(relativePath)

        return if (inputStream != null) {
            BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        } else {
            null
        }
    }

    override fun findResource(filename: String?): InputStream? {
        return if (filename === null) {
            null
        } else {
            readPath(filename)
        }
    }

    fun union(vararg other: DataSource): DataSource {
        return Union(arrayOf(this, *other))
    }

    class Asset(assetManager: AssetManager, basePathRaw: String) : DataSource {
        private val assetManager: AssetManager
        private val basePath: String

        init {
            var directoryPath = basePathRaw

            while (directoryPath.endsWith('/')) {
                directoryPath = directoryPath.substring(0, directoryPath.length - 1)
            }

            this.assetManager = assetManager
            this.basePath = directoryPath
        }

        private fun absolutePathOf(relativePath: String): String = "$basePath/$relativePath"

        override fun paths(relativePath: String): List<String> {
            val absolutePath = absolutePathOf(relativePath)

            return assetManager.list(absolutePath)!!
                .map { it!!.substring(absolutePath.length) }
        }

        override fun containsPath(relativePath: String): Boolean {
            val absolutePath = absolutePathOf(relativePath)

            return assetManager.list(absolutePathOf(relativePath))!!.contains(absolutePath)
        }

        override fun readPath(relativePath: String): InputStream? {
            val absolutePath = absolutePathOf(relativePath)

            return try {
                assetManager.open(absolutePath)
            } catch (e: FileNotFoundException) {
                null
            }
        }
    }

    class Union(private val sources: Array<DataSource>) : DataSource {
        override fun paths(relativePath: String): List<String> {
            return sources.asSequence() // sequence is the equivalent of Java 8 Streams, making the iteration lazy
                .flatMap { it.paths(relativePath).asSequence() }
                .distinct()
                .toCollection(ArrayList())
        }

        override fun containsPath(relativePath: String): Boolean {
            return sources.asSequence() // sequence is the equivalent of Java 8 Streams, making the iteration lazy
                       .map { it.containsPath(relativePath) }
                       .any { it }
        }

        override fun readPath(relativePath: String): InputStream? {
            for (source in sources) {
                val stream = source.readPath(relativePath)

                if (stream !== null) {
                    return stream
                }
            }

            return null
        }

    }
}
