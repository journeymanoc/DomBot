package org.journeymanoc.obediencetrainer

import org.luaj.vm2.*
import org.luaj.vm2.lib.LibFunction
import org.luaj.vm2.lib.ResourceFinder
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import java.io.InputStream

class IsolatedBaseLib : org.luaj.vm2.lib.BaseLib(), ResourceFinder {

    private var globals: Globals? = null


    /** Perform one-time initialization on the library by adding base functions
     * to the supplied environment, and returning it as the return value.
     * @param modname the module name supplied if this is loaded via 'require'.
     * @param env the environment to load into, which must be a Globals instance.
     */
    override fun call(modname: LuaValue?, env: LuaValue?): LuaValue? {
        globals = env!!.checkglobals()
        globals!!.finder = this
        globals!!.baselib = this
        env.set( "_G", env )
        env.set( "_VERSION", Lua._VERSION )
        env.set("assert", Assert())
        env.set("collectgarbage", CollectGarbage())
        //env.set("dofile", dofile())
        env.set("error", Error())
        env.set("getmetatable", GetMetatable())
        //env.set("load", load())
        //env.set("loadfile", loadfile())
        env.set("pcall", PCall())
        env.set("print", Print())
        env.set("rawequal", RawEqual())
        env.set("rawget", RawGet())
        env.set("rawlen", RawLen())
        env.set("rawset", RawSet())
        env.set("select", Select())
        env.set("setmetatable", SetMetatable())
        env.set("tonumber", ToNumber())
        env.set("tostring", ToString())
        env.set("type", Type())
        env.set("xpcall", XPCall())

        val next = Next()
        env.set("next", next)
        env.set("pairs", Pairs(next))
        env.set("ipairs", IPairs())

        return env
    }

    /** ResourceFinder implementation
     *
     * Tries to open the file as a resource, which can work for JSE and JME.
     */
    override fun findResource(filename: String): InputStream? {
        return null
    }


    // "assert", // ( v [,message] ) -> v, message | ERR
    private inner class Assert : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            if ( !args.arg1().toboolean() )
                error( if (args.narg()>1) { args.optjstring(2,"assertion failed!") } else { "assertion failed!" } )
            return args
        }
    }

    // "collectgarbage", // ( opt [,arg] ) -> value
    private inner class CollectGarbage : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val s = args.optjstring(1, "collect")
            when (s) {
                "collect" -> {
                    System.gc()
                    return ZERO
                }
                "count" -> {
                    val rt = Runtime.getRuntime()
                    val used = rt.totalMemory() - rt.freeMemory()
                    return varargsOf(valueOf(used / 1024.0), valueOf(used % 1024.0))
                }
                "step" -> {
                    System.gc()
                    return LuaValue.TRUE
                }
                else -> this.argerror("gc op")
            }
            return NIL
        }
    }

    /*
    // "dofile", // ( filename ) -> result1, ...
    private class dofile : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs  {
            args.argcheck(args.isstring(1) || args.isnil(1), 1, "filename must be string or nil");
            val filename = if (args.isstring(1)) args.tojstring(1) else null
            val v = if (filename == null) {
                loadStream(globals.STDIN, "=stdin", "bt", globals)
            } else {
                loadFile(args.checkjstring(1), "bt", globals)
            }
            return if (v.isnil(1)) error(v.tojstring(2)) else v.arg1().invoke()
        }
    }
    */

    // "error", // ( message [,level] ) -> ERR
    private inner class Error : TwoArgFunction() {
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
            throw when {
                arg1.isnil() -> LuaError(null, arg2.optint(1))
                arg1.isstring() -> LuaError(arg1.tojstring(), arg2.optint(1))
                else -> LuaError (arg1)
            }
        }
    }

    // "getmetatable", // ( object ) -> table
    private inner class GetMetatable : LibFunction() {
        override fun call(): LuaValue {
            return argerror(1, "value")
        }
        override fun call(arg: LuaValue): LuaValue {
            val mt = arg.getmetatable()
            return if (mt != null) mt.rawget(METATABLE).optvalue(mt) else NIL
        }
    }

    /*
    // "load", // ( ld [, source [, mode [, env]]] ) -> chunk | nil, msg
    private class load : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val ld = args.arg1()
            args.argcheck(ld.isstring() || ld.isfunction(), 1, "ld must be string or function")
            val source = args.optjstring(2, if (ld.isstring()) ld.tojstring() else "=(load)")
            val mode = args.optjstring(3, "bt")
            val env = args.optvalue(4, globals)
            return loadStream(
                if (ld.isstring()) {
                    ld.strvalue().toInputStream()
                } else {
                    StringInputStream(ld.checkfunction())
                },
                source, mode, env)
        }
    }

    // "loadfile", // ( [filename [, mode [, env]]] ) -> chunk | nil, msg
    final class loadfile extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            args.argcheck(args.isstring(1) || args.isnil(1), 1, "filename must be string or nil");
            String filename = args.isstring(1)? args.tojstring(1): null;
            String mode = args.optjstring(2, "bt");
            LuaValue env = args.optvalue(3, globals);
            return filename == null?
            loadStream( globals.STDIN, "=stdin", mode, env ):
            loadFile( filename, mode, env );
        }
    }
    */

    // "pcall", // (f, arg1, ...) -> status, result1, ...
    private inner class PCall : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val func = args.checkvalue(1)
            if (globals != null && globals!!.debuglib != null)
                globals!!.debuglib.onCall(this)
            return try {
                varargsOf(TRUE, func.invoke(args.subargs(2)))
            } catch ( le: LuaError  ) {
                varargsOf(FALSE, le.messageObject ?: NIL)
            } catch ( e: Exception  ) {
                varargsOf(FALSE, valueOf(e.message ?: e.toString()))
            } finally {
                if (globals != null && globals!!.debuglib != null)
                    globals!!.debuglib.onReturn()
            }
        }
    }

    // "print", // (...) -> void
    private inner class Print : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val tostring = globals!!.get("tostring")
            for (i in 1..args.narg()) {
                if (i > 1) {
                    globals!!.STDOUT.print('\t')
                }
                val s = tostring.call( args.arg(i) ).strvalue()
                globals!!.STDOUT.print(s.tojstring())
            }
            globals!!.STDOUT.println()
            return NONE
        }
    }


    // "rawequal", // (v1, v2) -> boolean
    private inner class RawEqual : LibFunction() {
        override fun call(): LuaValue {
            return argerror(1, "value")
        }
        override fun call(arg: LuaValue): LuaValue {
            return argerror(2, "value")
        }
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
            return valueOf(arg1.raweq(arg2))
        }
    }

    // "rawget", // (table, index) -> value
    private inner class RawGet : LibFunction() {
        override fun call(): LuaValue {
            return argerror(1, "value")
        }
        override fun call(arg: LuaValue): LuaValue {
            return argerror(2, "value")
        }
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
            return arg1.checktable().rawget(arg2)
        }
    }


    // "rawlen", // (v) -> value
    private inner class RawLen : LibFunction() {
        override fun call(arg: LuaValue): LuaValue {
            return valueOf(arg.rawlen())
        }
    }

    // "rawset", // (table, index, value) -> table
    private inner class RawSet : LibFunction() {
        override fun call(table: LuaValue): LuaValue {
            return argerror(2,"value")
        }
        override fun call(table: LuaValue, index: LuaValue): LuaValue {
            return argerror(3,"value")
        }
        override fun call(table: LuaValue, index: LuaValue, value: LuaValue): LuaValue {
            val t = table.checktable()
            t.rawset(index.checknotnil(), value)
            return t
        }
    }

    // "select", // (f, ...) -> value1, ...
    private inner class Select : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val n = args.narg() - 1
            if (args.arg1() == valueOf("#")) {
                return valueOf(n)
            }
            val i = args.checkint(1)
            if (i == 0 || i < -n) {
                argerror(1, "index out of range")
            }
            return args.subargs(if (i < 0) { n + i+2 } else { i + 1 })
        }
    }

    // "setmetatable", // (table, metatable) -> table
    private inner class SetMetatable : LibFunction() {
        override fun call(table: LuaValue): LuaValue {
            return argerror(2,"value")
        }
        override fun call(table: LuaValue, metatable: LuaValue): LuaValue {
            val mt0 = table.checktable().getmetatable()
            if (mt0 != null && !mt0.rawget(METATABLE).isnil()) {
                error("cannot change a protected metatable")
            }
            return table.setmetatable(if (metatable.isnil()) null else metatable.checktable())
        }
    }

    // "tonumber", // (e [,base]) -> value
    private inner class ToNumber : LibFunction() {
        override fun call(e: LuaValue): LuaValue {
            return e.tonumber()
        }
        override fun call(e: LuaValue, base: LuaValue): LuaValue {
            if (base.isnil()) {
                return e.tonumber()
            }
            val b = base.checkint()
            if ( b < 2 || b > 36 ) {
                argerror(2, "base out of range")
            }
            return e.checkstring().tonumber(b)
        }
    }

    // "tostring", // (e) -> value
    private inner class ToString : LibFunction() {
        override fun call(arg: LuaValue): LuaValue {
            val h = arg.metatag(TOSTRING)
            if (!h.isnil()) {
                return h.call(arg)
            }
            val v = arg.tostring()
            if (!v.isnil()) {
                return v
            }
            return valueOf(arg.tojstring())
        }
    }

    // "type",  // (v) -> value
    private inner class Type : LibFunction() {
        override fun call(arg: LuaValue): LuaValue {
            return valueOf(arg.typename())
        }
    }

    // "xpcall", // (f, err) -> result1, ...
    private inner class XPCall : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            val t = globals!!.running
            val preverror = t.errorfunc
            t.errorfunc = args.checkvalue(2)
            try {
                if (globals != null && globals!!.debuglib != null)
                    globals!!.debuglib.onCall(this)
                return try {
                    varargsOf(TRUE, args.arg1().invoke(args.subargs(3)))
                } catch (le: LuaError) {
                    varargsOf(FALSE, le.messageObject ?: NIL)
                } catch (e: java.lang.Exception) {
                    varargsOf(FALSE, valueOf(e.message ?: e.toString()))
                } finally {
                    if (globals != null && globals!!.debuglib != null)
                        globals!!.debuglib.onReturn()
                }
            } finally {
                t.errorfunc = preverror
            }
        }
    }

    // "pairs" (t) -> iter-func, t, nil
    private inner class Pairs(val next: Next) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            return varargsOf(next, args.checktable(1), NIL)
        }
    }

    // // "ipairs", // (t) -> iter-func, t, 0
    private inner class IPairs : VarArgFunction() {
        val inext: INext = INext()

        override fun invoke(args: Varargs): Varargs {
            return varargsOf( inext, args.checktable(1), ZERO )
        }
    }

    // "next"  ( table, [index] ) -> next-index, next-value
    private inner class Next : VarArgFunction() {
        override fun invoke(args: Varargs ): Varargs {
            return args.checktable(1).next(args.arg(2))
        }
    }

    // "inext" ( table, [int-index] ) -> next-index, next-value
    private inner class INext : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            return args.checktable(1).inext(args.arg(2))
        }
    }

    /*
    /**
     * Load from a named file, returning the chunk or nil,error of can't load
     * @param env
     * @param mode
     * @return Varargs containing chunk, or NIL,error-text on error
     */
    public Varargs loadFile(String filename, String mode, LuaValue env) {
        InputStream is = globals.finder.findResource(filename);
        if ( is == null )
        return varargsOf(NIL, valueOf("cannot open "+filename+": No such file or directory"));
        try {
            return loadStream(is, "@"+filename, mode, env);
        } finally {
            try {
                is.close();
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }
    }

    public Varargs loadStream(InputStream is, String chunkname, String mode, LuaValue env) {
        try {
            if ( is == null )
            return varargsOf(NIL, valueOf("not found: "+chunkname));
            return globals.load(is, chunkname, mode, env);
        } catch (Exception e) {
            return varargsOf(NIL, valueOf(e.getMessage()));
        }
    }


    private static class StringInputStream extends InputStream {
        final LuaValue func;
        byte[] bytes;
        int offset, remaining = 0;
        StringInputStream(LuaValue func) {
            this.func = func;
        }
        public int read() throws IOException {
            if ( remaining <= 0 ) {
                LuaValue s = func.call();
                if ( s.isnil() )
                    return -1;
                LuaString ls = s.strvalue();
                bytes = ls.m_bytes;
                offset = ls.m_offset;
                remaining = ls.m_length;
                if (remaining <= 0)
                    return -1;
            }
            --remaining;
            return bytes[offset++];
        }
    }
    */
}
