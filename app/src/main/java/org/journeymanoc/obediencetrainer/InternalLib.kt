package org.journeymanoc.obediencetrainer

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import java.lang.IllegalArgumentException

class InternalLib : TwoArgFunction() {
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
        env.get("package").get("loaded").set("internal", internal)
        return NIL
    }

    private inner class ProcessElementRenderQueue : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            //TODO: clear root view

            if (!args.isnil(1) && elementAdapter !== null) {
                val table = args.checktable(1)!!

                println("processElementRenderQueue: " + LuaPersistence.luaToString(table, true))

                elementAdapter!!.elementRenderQueue = table
                elementAdapter!!.notifyDataSetChanged()
            }

            return LuaValue.varargsOf(arrayOf())
        }
    }
}
