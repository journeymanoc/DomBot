package org.journeymanoc.obediencetrainer.lua

import com.google.gson.*
import com.google.gson.internal.LazilyParsedNumber
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.*
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import java.util.*

class LuaPersistence {
    companion object {
        val GSON: Gson = GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create()

        private fun luaToJsonTree(lua: LuaValue, silentFail: Boolean, visitedTables: Stack<LuaTable>): JsonElement? {
            return when (lua.type()) {
                LuaValue.TINT -> JsonPrimitive(lua.checkint())
                //LuaValue.TNONE -> return null
                LuaValue.TNIL -> JsonNull.INSTANCE
                LuaValue.TBOOLEAN -> JsonPrimitive(lua.checkboolean())
                LuaValue.TNUMBER -> when {
                    lua.isinttype() -> JsonPrimitive(lua.checkint())
                    else -> JsonPrimitive(lua.checkdouble())
                }
                LuaValue.TSTRING -> JsonPrimitive(lua.tojstring())
                LuaValue.TTABLE -> {
                    val table = lua.checktable()
                    val root = JsonArray()
                    var currentKey = LuaValue.NIL

                    if (visitedTables.contains(table)) {
                        return null
                    }

                    visitedTables.push(table)

                    while (true) {
                        val n = table.next(currentKey)
                        currentKey = n.arg1()
                        if (currentKey.isnil()) {
                            break
                        }

                        val serializedKey =
                            luaToJsonTree(
                                currentKey,
                                silentFail,
                                visitedTables
                            )
                        val serializedValue =
                            luaToJsonTree(
                                n.arg(2),
                                silentFail,
                                visitedTables
                            )

                        if (serializedKey !== null && serializedValue !== null) {
                            val entry = JsonObject()

                            entry.add("key", serializedKey)
                            entry.add("value", serializedValue)
                            root.add(entry)
                        }
                    }

                    visitedTables.pop()

                    return root
                }
                else -> if (!silentFail) {
                    throw IllegalStateException("Encountered the following unsupported Lua value while serializing: ${lua.typename()}")
                } else {
                    null
                }
            }
        }

        private fun luaFromJsonTree(json: JsonElement): LuaValue {
            return when {
                json is JsonNull -> LuaValue.NIL
                json is JsonPrimitive && json.isBoolean -> LuaValue.valueOf(json.asBoolean)
                json is JsonPrimitive && json.isNumber -> {
                    val number = json.asString.let {
                        try {
                            it.toInt()
                        } catch (_: NumberFormatException) {
                            it.toDouble()
                        } as Number
                    }

                    when (number) {
                        is Int -> LuaValue.valueOf(number)
                        is Double -> LuaValue.valueOf(number)
                        else -> throw IllegalStateException("Unexpected numeric type.")
                    }
                }
                json is JsonPrimitive && json.isString -> LuaValue.valueOf(json.asString)
                json is JsonArray -> {
                    val table = LuaTable()

                    for (entry in json.map { it.asJsonObject }) {
                        val key =
                            luaFromJsonTree(entry.get("key"))
                        val value =
                            luaFromJsonTree(entry.get("value"))

                        table[key] = value
                    }

                    return table
                }
                else -> throw IllegalStateException("Encountered a malformed Lua value tree.")
            }
        }

        fun luaToJson(lua: LuaValue, silentFail: Boolean): JsonObject {
            val result = JsonObject()
            val tree =
                luaToJsonTree(lua, silentFail, Stack())

            result.addProperty("version", 1)

            if (tree !== null) {
                result.add("content", tree)
            }

            return result
        }

        fun luaFromJson(json: JsonElement): LuaValue {
            assert(json.isJsonObject)

            val obj = json.asJsonObject

            assert(obj.getAsJsonPrimitive("version").asInt == 1)

            return luaFromJsonTree(obj.get("content"))
        }

        fun writeLuaAsJson(lua: LuaValue, writer: Writer, silentFail: Boolean) {
            GSON.toJson(
                luaToJson(
                    lua,
                    silentFail
                ), writer)
        }

        fun readJsonAsLua(reader: Reader): LuaValue {
            val json = JsonParser().parse(reader)

            return luaFromJson(json)
        }

        fun luaToString(lua: LuaValue, silentFail: Boolean): String {
            val writer = StringWriter()

            writeLuaAsJson(lua, writer, silentFail)

            return writer.toString()
        }

        fun luaFromString(string: String): LuaValue {
            return readJsonAsLua(StringReader(string))
        }

        fun cloneLua(data: LuaValue, silentFail: Boolean): LuaValue {
            val input = PipedInputStream()
            val output = PipedOutputStream(input)
            val reader = InputStreamReader(input)
            val writer = OutputStreamWriter(output)

            writer.use {
                LuaPersistence.writeLuaAsJson(data, writer, silentFail)
            }

            return reader.use {
                LuaPersistence.readJsonAsLua(reader)
            }
        }
    }
}