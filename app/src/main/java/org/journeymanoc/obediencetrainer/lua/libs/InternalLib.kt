package org.journeymanoc.obediencetrainer.lua.libs

import org.journeymanoc.obediencetrainer.ElementAdapter
import org.journeymanoc.obediencetrainer.GameInstance
import org.journeymanoc.obediencetrainer.lua.LuaPersistence
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import java.lang.Exception
import java.util.*

class InternalLib(val gameInstance: GameInstance) : TwoArgFunction() {
    companion object {
        fun calendarToLua(calendar: Calendar): LuaValue {
            val result = LuaValue.tableOf()

            result.set("millisecondOfSecond", calendar.get(Calendar.MILLISECOND)) // ℤ ∩ <0; 1000)
            result.set("secondOfMinute", calendar.get(Calendar.SECOND)) // ℤ ∩ <0; 60)
            result.set("minuteOfHour", calendar.get(Calendar.MINUTE)) // ℤ ∩ <0; 60)
            result.set("hourOfDay", calendar.get(Calendar.HOUR_OF_DAY)) // ℤ ∩ <0; 24)
            result.set("dayOfMonth", calendar.get(Calendar.DAY_OF_MONTH)) // ℤ ∩ <1; x>
            result.set("monthOfYear", calendar.get(Calendar.MONTH) + 1) // ℤ ∩ <1; 12>
            result.set("year", calendar.get(Calendar.YEAR)) // ℤ

            // Misc.
            result.set("hourOfMorningOrAfternoon", calendar.get(Calendar.HOUR))
            result.set("morningOrAfternoonOfDay", calendar.get(Calendar.AM_PM))
            result.set("dayOfWeek", calendar.get(Calendar.DAY_OF_WEEK))
            result.set("dayOfWeekInMonth", calendar.get(Calendar.DAY_OF_WEEK_IN_MONTH))
            result.set("dayOfYear", calendar.get(Calendar.DAY_OF_YEAR))
            result.set("weekOfMonth", calendar.get(Calendar.WEEK_OF_MONTH))
            result.set("weekOfYear", calendar.get(Calendar.WEEK_OF_YEAR))

            return result
        }

        fun calendarFromLua(lua: LuaValue): Calendar {
            val result = Calendar.getInstance()

            result.set(Calendar.YEAR, lua.get("year").checkint())
            result.set(Calendar.MONTH, lua.get("monthOfYear").checkint() - 1)
            result.set(Calendar.DAY_OF_MONTH, lua.get("dayOfMonth").checkint())
            result.set(Calendar.HOUR_OF_DAY, lua.get("hourOfDay").checkint())
            result.set(Calendar.MINUTE, lua.get("minuteOfHour").checkint())
            result.set(Calendar.SECOND, lua.get("secondOfMinute").checkint())
            result.set(Calendar.MILLISECOND, lua.get("millisecondOfSecond").checkint())

            return result
        }
    }

    var elementAdapter: ElementAdapter? = null; get set

    /** Perform one-time initialization on the library by creating a table
     * containing the library functions, adding that table to the supplied environment,
     * adding the table to package.loaded, and returning table as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, typically a Globals instance.
     */
    override fun call(modname: LuaValue, env: LuaValue): LuaValue {
        val internal = LuaTable()
        internal.set("processElementRenderQueue", ProcessElementRenderQueue())
        internal.set("commitPersistentData", CommitPersistentData())
        internal.set("getCurrentInstant", GetCurrentInstant())
        internal.set("addDurationToInstant", AddDurationToInstant())
        internal.set("scheduleNotificationAt", ScheduleNotificationAt())
        internal.set("getNotification", GetNotification())
        internal.set("cancelNotification", CancelNotification())
        env.get("package").get("loaded").set("internal", internal)
        return NIL
    }

    private inner class ProcessElementRenderQueue : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            if (!args.isnil(1) && elementAdapter !== null) {
                val table = args.checktable(1)!!

                println("processElementRenderQueue: " + LuaPersistence.luaToString(table, true))

                elementAdapter!!.elementRenderQueue = table
                elementAdapter!!.notifyDataSetChanged()
            }

            return LuaValue.varargsOf(arrayOf())
        }
    }

    private inner class CommitPersistentData : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            gameInstance.commitPersistentData()

            return LuaValue.varargsOf(arrayOf())
        }
    }

    private inner class GetCurrentInstant : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val calendar = Calendar.getInstance()
            val result = calendarToLua(calendar)

            return LuaValue.varargsOf(arrayOf(result))
        }
    }

    private inner class AddDurationToInstant : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val duration = args.arg(1).checktable()
            val calendar = calendarFromLua(args.arg(2))

            calendar.add(Calendar.MILLISECOND, duration.get("milliseconds").optint(0))
            calendar.add(Calendar.SECOND, duration.get("seconds").optint(0))
            calendar.add(Calendar.MINUTE, duration.get("minutes").optint(0))
            calendar.add(Calendar.HOUR_OF_DAY, duration.get("hours").optint(0))
            calendar.add(Calendar.DAY_OF_MONTH, duration.get("days").optint(0))
            calendar.add(Calendar.MONTH, duration.get("months").optint(0))
            calendar.add(Calendar.YEAR, duration.get("years").optint(0))

            val result = calendarToLua(calendar)

            return LuaValue.varargsOf(arrayOf(result))
        }
    }

    private inner class ScheduleNotificationAt : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val id = args.arg(1).optjstring(null)
            val instant = calendarFromLua(args.arg(2))
            val data = args.arg(3)

            gameInstance.scheduleNotify(id, instant, LuaPersistence.cloneLua(data, true))

            return LuaValue.varargsOf(arrayOf())
        }
    }

    private inner class GetNotification : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val id = args.arg(1).checkjstring()
            val notification = gameInstance.getNotification(id)?.toLua() ?: LuaValue.NIL

            return LuaValue.varargsOf(arrayOf(notification))
        }
    }

    private inner class CancelNotification : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val id = args.arg(1).checkjstring()
            val notification = gameInstance.cancelNotification(id)?.toLua() ?: LuaValue.NIL

            return LuaValue.varargsOf(arrayOf(notification))
        }
    }
}
