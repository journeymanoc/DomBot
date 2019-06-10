package org.journeymanoc.obediencetrainer

import android.content.Context
import android.content.res.AssetManager
import org.luaj.vm2.lib.ResourceFinder
import java.io.*
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.*
import java.util.stream.Stream
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.abs

interface DataSource : ResourceFinder {
    companion object {
        const val ESCAPE_CHAR = '\\'
        const val PATH_SEPARATOR = '/'

        fun isEscaped(path: String, charIndex: Int): Boolean {
            return charIndex > 0 && path[charIndex - 1] == ESCAPE_CHAR && !isEscaped(path, charIndex - 1)
        }

        fun splitPath(path: String): MutableList<String> {
            val result = ArrayList<String>()
            var searchStartIndex = 0
            var segmentStartIndex = 0

            while (true) {
                val potentialIndex = path.indexOf(PATH_SEPARATOR, searchStartIndex)

                if (potentialIndex == -1) { // not found
                    break
                }

                searchStartIndex = potentialIndex + 1

                if (isEscaped(path, potentialIndex)) {
                    continue
                }

                result.add(path.substring(segmentStartIndex, potentialIndex))

                segmentStartIndex = searchStartIndex
            }

            result.add(path.substring(segmentStartIndex))

            return result
        }

        fun resolvePath(path: String): String? {
            val segments = splitPath(path)
            var index = 0

            while (index < segments.size) {
                val segment = segments[index]

                when (segment) {
                    "", "." -> {
                        segments.removeAt(index)
                    }
                    ".." -> {
                        if (index == 0) {
                            return null
                        }

                        segments.removeAt(index - 1)
                        segments.removeAt(index - 1)
                        index--
                    }
                    else -> {
                        index++
                    }
                }
            }

            return if (segments.isEmpty()) {
                null
            } else {
                segments.joinToString(PATH_SEPARATOR.toString())
            }
        }
    }

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

        private fun absolutePathOf(relativePath: String): String? {
            val resolved = resolvePath(relativePath)

            return if (resolved === null) {
                null
            } else {
                "$basePath/$relativePath"
            }
        }

        override fun paths(relativePath: String): List<String> {
            return absolutePathOf(relativePath)?.let { absolutePath ->
                assetManager.list(absolutePath)!!
                    .map { it!!.substring(absolutePath.length) }
            } ?: emptyList()
        }

        override fun containsPath(relativePath: String): Boolean {
            return absolutePathOf(relativePath)?.let { absolutePath ->
                assetManager.list(absolutePath)!!.contains(absolutePath)
            } ?: false
        }

        override fun readPath(relativePath: String): InputStream? {
            return absolutePathOf(relativePath)?.let { absolutePath ->
                try {
                    assetManager.open(absolutePath)
                } catch (e: FileNotFoundException) {
                    null
                }
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
