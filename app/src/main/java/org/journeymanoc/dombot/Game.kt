package org.journeymanoc.dombot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.IOException

class Game private constructor(builder: Builder) {
    val dataSource: DataSource get
    val engineVersion: Int get
    val id: String get
    val name: String get
    val version: String get
    val author: String get
    val description: String get
    val changelog: List<String> get
    val pathToMainScript: String get
    val pathToLogo: String get
    val logo: AsyncFetch<Bitmap> get

    init {
        this.dataSource = builder.dataSource
        this.engineVersion = builder.engineVersion!!
        this.id = builder.id!!
        this.name = builder.name!!
        this.version = builder.version!!
        this.author = builder.author!!
        this.description = builder.description!!
        this.changelog = builder.changelog
        this.pathToMainScript = builder.pathToMainScript!!
        this.pathToLogo = builder.pathToLogo!!
        this.logo = AsyncFetch("Game#logo") {
            dataSource.readPath(pathToLogo).let { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }
    }

    fun readMainScript(): String? {
        return dataSource.readPathBuffered(pathToMainScript)?.readText()
    }

    fun parseVersion(): IntArray {
        return parseVersionUniversally(version)
    }

    fun nameFormatted(): String {
        return simplifyString(name)
    }

    fun descriptionFormatted(): String {
        return simplifyString(description)
    }

    private class Builder(dataSource: DataSource) {
        val dataSource: DataSource = dataSource; get
        var engineVersion: Int? = null; get set
        var id: String? = null; get set
        var name: String? = null; get set
        var version: String? = null; get set
        var author: String? = null; get set
        var description: String? = null; get set
        val changelog: MutableList<String> = ArrayList(); get
        var pathToMainScript: String? = null; get set
        var pathToLogo: String? = null; get set
    }

    companion object {
        const val DIRECTORY_GAMES = "games"

        private fun simplifyString(input: String): String {
            return input.trim().replace("\n", "").replace(Regex("\\s+"), " ")
        }

        private fun parseDeeper(parser: XmlPullParser, acc: () -> Unit) {
            val minDepth = parser.depth

            parser.next()

            while (parser.depth >= minDepth
                && !(parser.depth == minDepth && parser.eventType == XmlPullParser.END_TAG)
                && parser.eventType != XmlPullParser.END_DOCUMENT) {
                acc()
                parser.next()
            }
        }

        private fun parseLevel(parser: XmlPullParser, acc: () -> Unit) {
            val desiredDepth = parser.depth + 1

            parseDeeper(parser) {
                if (parser.depth == desiredDepth) {
                    acc()
                }
            }
        }

        private fun loadString(parser: XmlPullParser): String {
            val result = Pointer("")

            parseDeeper(parser) { result.value += parser.text }

            return result.value
        }

        private fun loadStringList(parser: XmlPullParser, tag: String): List<String> {
            val result: MutableList<String> = ArrayList()

            parseLevel(parser) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    return@parseLevel
                }

                assert(parser.name == tag)
                result.add(loadString(parser))
            }

            return result
        }

        private fun loadMeta(parser: XmlPullParser, builder: Builder) {
            parseLevel(parser) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    return@parseLevel
                }

                when (parser.name) {
                    "id" -> builder.id = loadString(parser)
                    "name" -> builder.name = loadString(parser)
                    "version" -> builder.version = loadString(parser)
                    "author" -> builder.author = loadString(parser)
                    "description" -> builder.description = loadString(parser)
                    "changelog" -> builder.changelog += loadStringList(parser, "changelogEntry")
                    "pathToMainScript" -> builder.pathToMainScript = loadString(parser)
                    "pathToLogo" -> builder.pathToLogo = loadString(parser)
                    else -> throw IllegalStateException("Invalid attribute `${parser.name}`, expected one of: name, version, author, description, changelog")
                }
            }
        }

        fun load(dataSource: DataSource): Game {
            val builder = Builder(dataSource)
            var reader: BufferedReader? = null

            try {
                reader = dataSource.readPathBuffered("meta.xml")

                if (reader == null) {
                    throw java.lang.IllegalStateException("Invalid data source.") // TODO handle gracefully
                }

                val parser = XmlPullParserFactory.newInstance()
                    .newPullParser()

                parser.setInput(reader)

                parseLevel(parser) {
                    if (parser.eventType != XmlPullParser.START_TAG) {
                        return@parseLevel
                    }

                    assert(parser.name == "game") { "The root tag must be `game`" }

                    for (attributeIndex in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(attributeIndex) == "engineVersion") {
                            builder.engineVersion = parser.getAttributeValue(attributeIndex).toInt()
                        }
                    }

                    parseLevel(parser) inner@{
                        if (parser.eventType != XmlPullParser.START_TAG) {
                            return@inner
                        }

                        when (parser.name) {
                            "meta" -> loadMeta(parser, builder)
                            else -> throw IllegalStateException("Invalid attribute `${parser.name}`, expected one of: meta, globalVariables, initialState, states")
                        }
                    }
                }

                return Game(builder)
            } catch (e: IOException) {
                throw RuntimeException(e)
            } finally {
                reader?.close()
            }
        }

        fun loadFromAsset(context: Context, directoryPath: String): Game {
            return load(DataSource.Asset(context.assets, directoryPath))
        }

        @Deprecated("Asset games should not be used, because they cannot be uninstalled.")
        fun loadBuiltinGames(context: Context): List<Game> {
            val gamePaths = context.assets.list(DIRECTORY_GAMES)!!

            return gamePaths.map {
                "$DIRECTORY_GAMES/$it"
            }.map {
                loadFromAsset(context, it)
            }
        }

        fun getInstalledGamesDataSource(context: Context): DataSource {
            return DataSource.File.relativeToAppDir(context, DIRECTORY_GAMES)
        }

        fun loadInstalledGames(context: Context): List<Game> {
            val installedGamesDataSource = getInstalledGamesDataSource(context)
            val gamePaths = installedGamesDataSource.paths(".")

            return gamePaths.map {
                val subsource = installedGamesDataSource.subsource(it)!!
                load(subsource)
            }
        }
    }
}