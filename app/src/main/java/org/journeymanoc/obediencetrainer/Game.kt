package org.journeymanoc.obediencetrainer

import android.content.Context
import org.journeymanoc.obediencetrainer.DataSource
import org.journeymanoc.obediencetrainer.Pointer
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.JseMathLib
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class Game private constructor(builder: Builder, context: Context) {
    val dataSource: DataSource get
    val name: String get
    val version: String get
    val author: String get
    val description: String get
    val changelog: List<String> get
    /*
    val globalVariables: List<String> get
    val initialState: String get
    val states: List<String> get
    */
    val pathToMainScript: String get
    val globals: Globals get
    private val internalLib: InternalLib

    init {
        this.dataSource = builder.dataSource
        this.name = builder.name!!
        this.version = builder.version!!
        this.author = builder.author!!
        this.description = builder.description!!
        this.changelog = builder.changelog
        /*
        this.globalVariables = builder.globalVariables
        this.initialState = builder.initialState!!
        this.states = builder.states
        */
        this.pathToMainScript = builder.pathToMainScript!!
        this.internalLib = InternalLib()
        this.globals = loadGlobals(context, false)
    }

    /**
     * A safe ("containerized") replacement for {@link JsePlatform#standardGlobals}.
     */
    private fun loadGlobals(context: Context, debug: Boolean): Globals {
        val dataSource = this.dataSource.union(DataSource.Asset(context.assets, "lua"))
        var globals = Globals()

        globals.load(IsolatedBaseLib(dataSource))
        globals.load(IsolatedPackageLib())
        globals.load(internalLib)
        globals.load(Bit32Lib())
        globals.load(CoroutineLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(JseMathLib())

        if (debug) {
            globals.load(DebugLib())
        }

        LoadState.install(globals)
        LuaC.install(globals)

        // TODO load from storage

        return globals
    }

    fun bindElementAdapter(elementAdapter: ElementAdapter) {
        internalLib.elementAdapter = elementAdapter
    }

    private fun readMainScript(): String? {
        return dataSource.readPathBuffered(pathToMainScript)?.readText()
    }

    // FIXME: Error handling
    fun run() {
        val mainScript = readMainScript()!!
        val chunk = globals.load(mainScript)!!

        try {
            chunk.call()!!
        } catch (e: LuaError) {
            System.err.println(e.message)
        }
    }

    private class Builder(dataSource: DataSource) {
        val dataSource: DataSource = dataSource; get
        var name: String? = null; get set
        var version: String? = null; get set
        var author: String? = null; get set
        var description: String? = null; get set
        val changelog: MutableList<String> = ArrayList(); get
        /*
        val globalVariables: MutableList<String> = ArrayList(); get
        var initialState: String? = null; get set
        val states: MutableList<String> = ArrayList(); get
        */
        var pathToMainScript: String? = null; get set
    }

    companion object {
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

        /*
        private fun loadStates(parser: XmlPullParser, builder: Builder) {
            parseLevel(parser) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    return@parseLevel
                }

                assert(parser.name == "states")

                var initial = false

                for (attributeIndex in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(attributeIndex)) {
                        "initial" -> {
                            if (parser.getAttributeValue(attributeIndex) == "true") {
                                initial = true
                            }
                        }
                    }
                }

                val state = loadString(parser)

                builder.states.add(state)

                if (initial) {
                    builder.initialState = state
                }
            }
        }
        */

        private fun loadMeta(parser: XmlPullParser, builder: Builder) {
            parseLevel(parser) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    return@parseLevel
                }

                when (parser.name) {
                    "name" -> builder.name = loadString(parser)
                    "version" -> builder.version = loadString(parser)
                    "author" -> builder.author = loadString(parser)
                    "description" -> builder.description = loadString(parser)
                    "changelog" -> builder.changelog += loadStringList(parser, "changelogEntry")
                    "pathToMainScript" -> builder.pathToMainScript = loadString(parser)
                    else -> throw IllegalStateException("Invalid attribute `${parser.name}`, expected one of: name, version, author, description, changelog")
                }
            }
        }

        fun load(dataSource: DataSource, context: Context): Game {
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

                    parseLevel(parser) inner@{
                        if (parser.eventType != XmlPullParser.START_TAG) {
                            return@inner
                        }

                        when (parser.name) {
                            "meta" -> loadMeta(parser, builder)
                            /*
                            "globalVariables" -> builder.globalVariables += loadStringList(parser, "variable")
                            "states" -> loadStates(parser, builder)
                            */
                            else -> throw IllegalStateException("Invalid attribute `${parser.name}`, expected one of: meta, globalVariables, initialState, states")
                        }
                    }
                }

                return Game(builder, context)
            } catch (e: IOException) {
                throw RuntimeException(e)
            } finally {
                reader?.close()
            }
        }

        fun loadFromAsset(context: Context, directoryPath: String): Game {
            return load(DataSource.Asset(context.assets, directoryPath), context)
        }
    }
}