package org.journeymanoc.obediencetrainer

import android.content.Context
import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import java.util.stream.Stream
import kotlin.collections.HashSet
import kotlin.math.abs

interface DataSource {
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
}
