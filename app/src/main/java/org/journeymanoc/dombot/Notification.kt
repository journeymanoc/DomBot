package org.journeymanoc.dombot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.journeymanoc.dombot.lua.LuaPersistence
import org.journeymanoc.dombot.lua.libs.InternalLib
import org.luaj.vm2.LuaValue
import java.util.*

class Notification(val id: String?, val instant: Long, data: LuaValue) : Comparable<Notification> {
    val data: LuaValue = LuaPersistence.cloneLua(data, true)

    companion object {
        val WHAT: Int = 0

        fun fromJson(json: JsonElement): Notification {
            val root = json.asJsonObject
            val idElement = root.get("id")
            val id = if (idElement.isJsonPrimitive && idElement.asJsonPrimitive.isString) idElement.asString else null

            return Notification(id, root.get("instant").asLong, LuaPersistence.luaFromJson(root.get("data")))
        }
    }

    fun toJson(silentFail: Boolean): JsonElement {
        val result = JsonObject()

        id?.also { result.addProperty("id", it) }
        result.addProperty("instant", instant)
        result.add("data", LuaPersistence.luaToJson(data, silentFail))

        return result
    }

    fun toLua(): LuaValue {
        val result = LuaValue.tableOf()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = instant

        id?.also { result.set("id", it) }
        result.set("instant", InternalLib.calendarToLua(calendar))
        result.set("data", data)

        return result
    }

    override fun compareTo(other: Notification): Int {
        return compareValues(this.instant, other.instant)
    }

    override fun toString(): String {
        return "Notification(id=$id, instant=$instant, data=$data)"
    }
}
