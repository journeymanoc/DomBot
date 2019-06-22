package org.journeymanoc.obediencetrainer

import android.app.ActionBar
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Picture
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class ElementAdapter(val game: Game, var elementRenderQueue: LuaTable) : RecyclerView.Adapter<ElementAdapter.ElementViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ElementViewHolder {
        val elementType = ElementType.values()[viewType]

        return elementType.createViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ElementViewHolder, position: Int) {
        holder.type.bindViewHolder(this, holder, getElement(position))
    }

    override fun getItemViewType(position: Int): Int {
        return getElement(position).getViewType()
    }

    override fun getItemCount(): Int {
        return elementRenderQueue.rawlen()
    }

    fun getElement(position: Int): Element {
        return Element(elementRenderQueue.get(position + 1).checktable())
    }

    companion object {
        fun parseGravity(raw: String): Int {
            return when (raw.toUpperCase().replace("_", "", false)) {
                "NONE" -> 0
                "TOP" -> Gravity.TOP
                "BOTTOM" -> Gravity.BOTTOM
                "START" -> Gravity.START
                "END" -> Gravity.END
                "CENTERVERTICAL" -> Gravity.CENTER_VERTICAL
                "FILLVERTICAL" -> Gravity.FILL_VERTICAL
                "CLIPVERTICAL" -> Gravity.CLIP_VERTICAL
                "CENTERHORIZONTAL" -> Gravity.CENTER_HORIZONTAL
                "FILLHORIZONTAL" -> Gravity.FILL_HORIZONTAL
                "CLIPHORIZONTAL" -> Gravity.CLIP_HORIZONTAL
                "CENTER" -> Gravity.CENTER
                "FILL" -> Gravity.FILL
                "CLIP" -> Gravity.CLIP_HORIZONTAL or Gravity.CLIP_VERTICAL
                else -> throw LuaError("Invalid gravity value.")
            }
        }

        fun parseGravity(lua: LuaValue, default: Int): Int {
            return when {
                lua.isstring() -> parseGravity(lua.tojstring())
                lua.istable() -> {
                    val table = lua.checktable()
                    var result = 0

                    for (key in table.keys()) {
                        result = result or parseGravity(table.get(key).checkjstring())
                    }

                    result
                }
                lua.isnil() -> default
                else -> throw LuaError("Invalid gravity type, expected a string or a table of strings.")
            }
        }

        fun parseLayoutParams(view: View, content: LuaTable, matchParentWidth: Boolean, matchParentHeight: Boolean) {
            if (view.layoutParams === null) {
                view.layoutParams = LinearLayout.LayoutParams(
                    if (matchParentWidth) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                    if (matchParentHeight) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val luaWidth = content.get("width")
            val luaHeight = content.get("height")

            val width = when {
                luaWidth.isnumber() && luaWidth.checkint() >= 0 -> luaWidth.checkint()
                luaWidth.isstring() && luaWidth.checkjstring().toLowerCase() == "wrapcontent" -> ViewGroup.LayoutParams.WRAP_CONTENT
                luaWidth.isstring() && luaWidth.checkjstring().toLowerCase() == "matchparent" -> ViewGroup.LayoutParams.MATCH_PARENT
                luaWidth.isnil() -> null
                else -> throw LuaError("Invalid width. Must be either a positive integer, 'wrapContent' or 'matchParent'.")
            }

            val height = when {
                luaHeight.isnumber() && luaHeight.checkint() >= 0 -> luaHeight.checkint()
                luaHeight.isstring() && luaHeight.checkjstring().toLowerCase() == "wrapcontent" -> ViewGroup.LayoutParams.WRAP_CONTENT
                luaHeight.isstring() && luaHeight.checkjstring().toLowerCase() == "matchparent" -> ViewGroup.LayoutParams.MATCH_PARENT
                luaHeight.isnil() -> null
                else -> throw LuaError("Invalid height. Must be either a positive integer, 'wrapContent' or 'matchParent'.")
            }

            width?.also { view.layoutParams.width = it }
            height?.also { view.layoutParams.height = it }

            if (view.layoutParams is LinearLayout.LayoutParams) {
                (view.layoutParams as LinearLayout.LayoutParams).weight = content.get("weight").optdouble(1.0).toFloat()
            }
        }
    }

    enum class ElementType {
        GROUP {
            override fun createView(parent: View): View {
                return LinearLayout(parent.context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as LinearLayout
                val content = element.getContent()
                val children = content.get("children").checktable()

                view.removeAllViews()

                for (i in 1..children.rawlen()) {
                    val child = Element(children.get(i).checktable())
                    val childView = child.createBoundView(view, adapter)

                    view.addView(childView)
                }

                parseLayoutParams(view, content, matchParentWidth = true, matchParentHeight = false)
                view.gravity = parseGravity(content.get("gravity"), Gravity.TOP or Gravity.START)
                view.orientation = if (content.get("horizontal").toboolean()) {
                    LinearLayout.HORIZONTAL
                } else {
                    LinearLayout.VERTICAL
                }

                //view.invalidate()
            }
        },
        TEXT {
            override fun createView(parent: View): View {
                return TextView(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as TextView
                view.text = element.getContentHtml("text")
                parseLayoutParams(view, element.getContent(), matchParentWidth = true, matchParentHeight = false)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.TOP or Gravity.START)
            }
        },
        IMAGE {
            override fun createView(parent: View): View {
                return ImageView(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as ImageView
                val content = element.getContent()
                val path = content.get("path").checkjstring()
                val inputStream = adapter.game.dataSource.readPath(path)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                view.setImageBitmap(bitmap)
                parseLayoutParams(view, element.getContent(), matchParentWidth = false, matchParentHeight = false)
            }
        },
        BUTTON {
            override fun createView(parent: View): View {
                return Button(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as TextView
                view.text = element.getContentHtml("text")
                parseLayoutParams(view, element.getContent(), matchParentWidth = false, matchParentHeight = false)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.CENTER)
                val handler = element.getContent().get("handler")

                if (handler is LuaFunction) {
                    view.setOnClickListener {
                        handler.call()
                    }
                } else {
                    view.setOnClickListener(null)
                }
            }
        };

        companion object {
            fun parse(name: String): ElementType? {
                return try {
                    ElementType.valueOf(name.toUpperCase())
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }

        abstract fun createView(parent: View): View
        abstract fun bindView(adapter: ElementAdapter, view: View, element: Element)

        open fun createViewHolder(parent: View): ElementViewHolder {
            return ElementViewHolder(this, createView(parent))
        }

        open fun bindViewHolder(adapter: ElementAdapter, viewHolder: ElementViewHolder, element: Element) {
            bindView(adapter, viewHolder.itemView, element)
        }
    }

    class ElementViewHolder(val type: ElementType, view: View) : RecyclerView.ViewHolder(view)

    class Element(private val element: LuaTable) {
        fun getContent(): LuaTable {
            return element.get("content").checktable()
        }

        fun getContentAttribute(name: String): LuaValue {
            return getContent().get(name)
        }

        fun getContentHtml(name: String): Spanned {
            val string = getContentAttribute(name).checkjstring()

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(string, Html.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(string)
            }
        }

        fun getType(): ElementType {
            return element.get("type").checkjstring()
                .let { ElementType.parse(it)!! }
        }

        fun getViewType(): Int {
            return getType().ordinal
        }

        fun createBoundView(parent: View, elementAdapter: ElementAdapter): View {
            val type = getType()
            val view = type.createView(parent)
            type.bindView(elementAdapter, view, this)

            return view
        }
    }
}
