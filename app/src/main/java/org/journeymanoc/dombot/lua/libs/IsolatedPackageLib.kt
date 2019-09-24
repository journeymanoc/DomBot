package org.journeymanoc.dombot.lua.libs

import org.luaj.vm2.*
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.VarArgFunction

class IsolatedPackageLib : PackageLib() {
    companion object {
        private val SENTINEL: LuaString = valueOf("\u0001")
    }

    var globals: Globals? = null
    @Suppress("PropertyName")
    var package_: LuaTable? = null

    override fun call(modname: LuaValue, env: LuaValue): LuaValue {
        globals = env.checkglobals()
        globals!!.set("require", Require())
        package_ = LuaTable()
        package_!!.set("loaded", LuaTable())
        package_!!.set("preload", LuaTable())
        package_!!.set("path", LuaValue.valueOf(DEFAULT_LUA_PATH))
        package_!!.set("loadlib", loadlib())
        package_!!.set("searchpath", SearchPath())
        val searchers = LuaTable()
        preload_searcher = PreloadSearcher()
        searchers.set(1, preload_searcher)
        lua_searcher = LuaSearcher()
        searchers.set(2, lua_searcher)
        package_!!.set("searchers", searchers)
        package_!!.get("loaded").set("package", package_)
        env.set("package", package_)
        globals!!.package_ = this
        return env
    }

    private inner class Require : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            val name = arg.checkstring()
            val loaded = package_!!.get("loaded")
            var result = loaded.get(name)
            if (result.toboolean()) {
                if (result == SENTINEL) {
                    error("loop or previous error loading module '$name'")
                }
                return result
            }

            /* else must load it; iterate over available loaders */
            val tbl = package_!!.get("searchers").checktable()
            val sb = StringBuffer()
            var loader: Varargs?
            var i = 0
            while (true) {
                i++
                val searcher = tbl.get(i)
                if (searcher.isnil()) {
                    error("module '$name' not found: $name$sb")
                }

                /* call loader with module name as argument */
                loader = searcher.invoke(name)
                if (loader.isfunction(1)) {
                    // load the module using the loader
                    loaded.set(name, SENTINEL)
                    result = loader.arg1().call(name, loader.arg(2))
                    if (!result.isnil()) {
                        loaded.set(name, result)
                    } else {
                        result = loaded.get(name)
                        if (result == SENTINEL) {
                            result = LuaValue.TRUE
                            loaded.set(name, result)
                        }
                    }
                    return result
                }
                if (loader.isstring(1)) {
                    sb.append(loader.tojstring(1))
                }
            }
        }
    }

    private inner class SearchPath : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            var name = args.checkjstring(1)
            val path = args.checkjstring(2)
            val sep = args.optjstring(3, ".")
            val rep = args.optjstring(4, System.getProperty("file.separator"))

            // check the path elements
            var e = -1
            val n = path.length
            var sb: StringBuffer? = null
            name = name.replace(sep[0], rep[0])
            while (e < n) {
                // find next template
                val b = e + 1
                e = path.indexOf(';', b)
                if (e < 0) {
                    e = path.length
                }
                val template = path.substring(b,e)

                // create filename
                val q = template.indexOf('?')
                var filename = template
                if (q >= 0) {
                    filename = template.substring(0,q) + name + template.substring(q + 1)
                }

                // try opening the file
                val inputStream = globals!!.finder.findResource(filename)
                if (inputStream != null) {
                    try {
                        inputStream.close()
                    } catch (ioe: java.io.IOException) {}
                    return valueOf(filename)
                }

                // report error
                if (sb == null) {
                    sb = StringBuffer()
                }
                sb.append( "\n\t"+filename )
            }
            return varargsOf(NIL, valueOf(sb.toString()))
        }
    }

    private inner class PreloadSearcher : preload_searcher() {
        override fun invoke(args: Varargs): Varargs {
            val name = args.checkstring(1)
            val value = package_!!.get("preload").get(name)
            return if (value.isnil()) {
                valueOf("\n\tno field package.preload['$name']")
            } else {
                value
            }
        }
    }

    private inner class LuaSearcher : lua_searcher() {
        override fun invoke(args: Varargs): Varargs {
            val name = args.checkstring(1)

            // get package path
            val path = package_!!.get("path")
            if (!path.isstring()) {
                return valueOf("package.path is not a string")
            }

            // get the searchpath function.
            var v = package_!!.get("searchpath").invoke(varargsOf(name, path))

            // Did we get a result?
            if (!v.isstring(1)) {
                return v.arg(2).tostring()
            }
            val filename = v.arg1().strvalue()

            // Try to load the file.
            v = globals!!.loadfile(filename.tojstring())
            if (v.arg1().isfunction()) {
                return LuaValue.varargsOf(v.arg1(), filename)
            }

            // report error
            return varargsOf(NIL, valueOf("'$filename': ${v.arg(2).tojstring()}"))
        }
    }
}