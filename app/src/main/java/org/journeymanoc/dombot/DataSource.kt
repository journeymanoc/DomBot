package org.journeymanoc.dombot

import android.content.Context
import android.content.res.AssetManager
import org.luaj.vm2.lib.ResourceFinder
import java.io.*
import java.net.MalformedURLException

interface DataSource : ResourceFinder {
    companion object {
        const val ESCAPE_CHAR = '\\'
        const val PATH_SEPARATOR = '/'

        fun isEscapable(char: Char): Boolean {
            return when (char) {
                ESCAPE_CHAR, PATH_SEPARATOR -> true
                else -> false
            }
        }

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

        /**
         * Converts a path with special segments such as `.` and `..` to a relative path without those segments.
         * Ensures that the path does not change its root (by using too many `..` segments), in that case, `null` is returned.
         */
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

        @Suppress("NAME_SHADOWING")
        fun escapePathSegment(segment: String): String? {
            val segment = segment.replace('.', '_')
            val builder = StringBuilder(segment.length)

            for (char in segment.toCharArray()) {
                if (isEscapable(char)) {
                    builder.append(ESCAPE_CHAR)
                }

                builder.append(char)
            }

            return builder.toString()
        }
    }

    fun paths(relativePath: String): List<String>
    fun containsPath(relativePath: String): Boolean
    fun readPath(relativePath: String): InputStream?
    fun canWrite(): Boolean
    fun write(relativePath: String): OutputStream?
    fun delete(relativePath: String): Boolean
    fun subsource(relativePath: String): DataSource?

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

    class File(private val baseFile: java.io.File) : DataSource {
        companion object {
            fun relativeToAppDir(context: Context, path: String): File {
                return File(context.filesDir.resolve(path))
            }
        }

        private fun resolveFile(relativePath: String): java.io.File? {
            val resolved = resolvePath(relativePath)

            return if (resolved === null) {
                null
            } else {
                baseFile.resolve(relativePath)
            }
        }

        override fun paths(relativePath: String): List<String> {
            return resolveFile(relativePath)?.let { dir ->
                val dirAbsolutePath = dir.absolutePath

                dir.listFiles()?.map {
                    it.absolutePath.substring(dirAbsolutePath.length)
                }
            } ?: emptyList()
        }

        override fun containsPath(relativePath: String): Boolean {
            return resolveFile(relativePath)?.exists() ?: false
        }

        override fun readPath(relativePath: String): InputStream? {
            return resolveFile(relativePath)?.let { file ->
                try {
                    file.inputStream()
                } catch (e: FileNotFoundException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
        }

        override fun canWrite(): Boolean {
            return true
        }

        override fun write(relativePath: String): OutputStream? {
            return resolveFile(relativePath)?.let { file ->
                try {
                    file.outputStream()
                } catch (e: FileNotFoundException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
        }

        override fun delete(relativePath: String): Boolean {
            return resolveFile(relativePath)?.deleteRecursively() ?: false
        }

        override fun subsource(relativePath: String): DataSource? {
            return resolveFile(relativePath)?.let {
                File(it)
            }
        }
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

        override fun canWrite(): Boolean {
            return false
        }

        override fun write(relativePath: String): OutputStream? {
            return null
        }

        override fun delete(relativePath: String): Boolean {
            return false
        }

        override fun subsource(relativePath: String): DataSource? {
            return absolutePathOf(relativePath)?.let { absolutePath ->
                Asset(assetManager, absolutePath)
            }
        }
    }

    class URL: DataSource {
        val baseUrl: String

        constructor(baseUrl: String) {
            var url = baseUrl

            while (url.endsWith('/')) {
                url = url.substring(0, url.length - 1)
            }

            this.baseUrl = url
        }

        private fun absolutePathOf(relativePath: String): java.net.URL? {
            val resolved = resolvePath(relativePath)

            return try {
                return if (resolved === null) {
                    java.net.URL(baseUrl)
                } else {
                    java.net.URL("$baseUrl/$relativePath")
                }
            } catch (e: MalformedURLException) {
                System.err.println("URL DataSource tried creating a malformed URL: $baseUrl/$relativePath")
                null
            }
        }

        override fun paths(relativePath: String): List<String> {
            return emptyList()
        }

        override fun containsPath(relativePath: String): Boolean {
            return false
        }

        override fun readPath(relativePath: String): InputStream? {
            return absolutePathOf(relativePath)?.let { absolutePath ->
                try {
                    absolutePath.openStream()
                } catch (e: IOException) {
                    System.err.println("Failed to open an InputStream to URL DataSource `$absolutePath`, error: ${e.message}")
                    null
                }
            }
        }

        override fun canWrite(): Boolean {
            return false
        }

        override fun write(relativePath: String): OutputStream? {
            return null
        }

        override fun delete(relativePath: String): Boolean {
            return false
        }

        override fun subsource(relativePath: String): DataSource? {
            return absolutePathOf(relativePath)?.let { absolutePath ->
                URL(absolutePath.toExternalForm())
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

        override fun canWrite(): Boolean {
            return sources.all { it.canWrite() }
        }

        override fun write(relativePath: String): OutputStream? {
            for (source in sources) {
                val stream = source.write(relativePath)

                if (stream !== null) {
                    return stream
                }
            }

            return null
        }

        override fun delete(relativePath: String): Boolean {
            if (!canWrite()) {
                return false
            }

            var deleted = false

            for (source in sources) {
                deleted = source.delete(relativePath) or deleted
            }

            return deleted
        }

        override fun subsource(relativePath: String): DataSource? {
            val newSources = sources.mapNotNull {
                it.subsource(relativePath)
            }

            return if (newSources.isEmpty()) {
                null
            } else {
                Union(newSources.toTypedArray())
            }
        }
    }
}
