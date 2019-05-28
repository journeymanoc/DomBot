package org.journeymanoc.obediencetrainer

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.PackageLib.DEFAULT_LUA_PATH
import org.luaj.vm2.lib.TwoArgFunction

class IsolatedPackageLib : PackageLib() {
    var globals: Globals? = null

    override fun call(modname: LuaValue, env: LuaValue): LuaValue {
        globals = env.checkglobals()
        //globals.set("require", require())
        val pkg = LuaTable()
        pkg.set("loaded", LuaTable())
        pkg.set("preload", LuaTable())
        pkg.set("path", LuaValue.valueOf(DEFAULT_LUA_PATH))
        //package_.set("loadlib", loadlib())
        //package_.set("searchpath", searchpath())
        val searchers = LuaTable()
        //searchers.set(1, preload_searcher = preload_searcher())
        //searchers.set(2, lua_searcher     = lua_searcher())
        //searchers.set(3, java_searcher    = java_searcher())
        pkg.set("searchers", searchers)
        pkg.get("loaded").set("package", pkg)
        env.set("package", pkg)
        globals!!.package_ = this
        return env
    }
}