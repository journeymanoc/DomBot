package org.journeymanoc.obediencetrainer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.journeymanoc.obediencetrainer.lua.LuaPersistence
import org.luaj.vm2.LuaValue
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream

class Notification(val instant: Long, val data: LuaValue) : Comparable<Notification> {
    companion object {
        val WHAT: Int = 0

        fun fromJson(json: JsonElement): Notification {
            val root = json.asJsonObject

            return Notification(root.get("instant").asLong, LuaPersistence.luaFromJson(root.get("data")))
        }
    }

    fun toJson(silentFail: Boolean): JsonElement {
        val result = JsonObject()

        result.addProperty("instant", instant)
        result.add("data", LuaPersistence.luaToJson(data, silentFail))

        return result
    }

    override fun compareTo(other: Notification): Int {
        return compareValues(this.instant, other.instant)
    }

    override fun toString(): String {
        return "Notification(instant=$instant, data=$data)"
    }
}
