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

class InternalLib(val elementAdapter: ElementAdapter) : TwoArgFunction() {
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
        private fun renderGroup(content: LuaTable) {}
        private fun renderText(content: LuaTable) {}
        private fun renderImage(content: LuaTable) {}
        private fun renderButton(content: LuaTable) {}

        override fun invoke(args: Varargs): Varargs {
            //TODO: clear root view

            if (!args.isnil(1)) {
                val table = args.checktable(1)!!

                elementAdapter.elementRenderQueue = table
                elementAdapter.notifyDataSetChanged()

                /*
                println("processElementRenderQueue invoked with: " + LuaPersistence.luaToString(table, true))

                var currentKey = LuaValue.NIL

                loop@while (true) {
                    val n = table.next(currentKey)
                    currentKey = n.arg1()

                    if (currentKey.isnil()) {
                        break
                    }

                    val currentValue = n.arg(2).checktable()
                    val ty = currentValue["type"].checkjstring()
                    val content = currentValue["content"].checktable()

                    when (ty) {
                        "group" -> renderGroup(content)
                        "text" -> renderText(content)
                        "image" -> renderImage(content)
                        "button" -> renderButton(content)
                        else -> continue@loop
                    }
                }
                */
            }

            return LuaValue.varargsOf(arrayOf())
        }
    }
}
