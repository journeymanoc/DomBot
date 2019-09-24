package org.journeymanoc.dombot.lua

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction

abstract class TableArgFunction : OneArgFunction() {
    override fun call(arg: LuaValue?): LuaValue {
        return call(if (arg!!.istable()) (arg as LuaTable) else LuaTable())
    }

    abstract fun call(arg: LuaTable): LuaValue
}