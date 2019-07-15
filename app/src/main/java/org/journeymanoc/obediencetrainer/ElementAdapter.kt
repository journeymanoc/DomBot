package org.journeymanoc.obediencetrainer

import android.annotation.SuppressLint
import android.app.ActionBar
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Picture
import android.os.Build
import android.text.*
import android.view.*
import android.widget.*
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuView
import androidx.core.content.getSystemService
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
        fun parseInputTypeClass(raw: String): Int {
            return when (raw.toUpperCase().replace("_", "", false)) {
                "NONE", "NULL" -> InputType.TYPE_NULL
                "TEXT" -> InputType.TYPE_CLASS_TEXT
                "NUMBER" -> InputType.TYPE_CLASS_NUMBER
                "PHONE" -> InputType.TYPE_CLASS_PHONE
                "INSTANT", "DATETIME" -> InputType.TYPE_CLASS_DATETIME
                else -> throw LuaError("Invalid input type class value.")
            }
        }

        fun parseInputTypeFlag(raw: String): Int {
            return when (raw.toUpperCase().replace("_", "", false)) {
                "NONE" -> 0
                "CAPITALIZECHARACTERS" -> InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                "CAPITALIZEWORDS" -> InputType.TYPE_TEXT_FLAG_CAP_WORDS
                "CAPITALIZESENTENCES" -> InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                "AUTOCORRECT" -> InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                // Unlikely to ever be used: "" -> InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
                "MULTILINE" -> InputType.TYPE_TEXT_FLAG_MULTI_LINE
                "IMEMULTILINE" -> InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
                "NOSUGGESTIONS" -> InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                "SIGNED" -> InputType.TYPE_NUMBER_FLAG_SIGNED
                "DECIMAL" -> InputType.TYPE_NUMBER_FLAG_DECIMAL
                else -> throw LuaError("Invalid input type flag value.")
            }
        }

        fun parseInputTypeVariation(raw: String): Int {
            return when (raw.toUpperCase().replace("_", "", false)) {
                "NORMAL", "NONE" -> 0
                "URI" -> InputType.TYPE_TEXT_VARIATION_URI
                "EMAILADDRESS" -> InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "EMAILSUBJECT" -> InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT
                "SHORTMESSAGE" -> InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
                "LONGMESSAGE" -> InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
                "PERSONNAME" -> InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                "POSTALADDRESS" -> InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                "PASSWORD" -> InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                "VISIBLEPASSWORD" -> InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                // Unlikely to ever be used: "" -> InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                "FILTER" -> InputType.TYPE_TEXT_VARIATION_FILTER
                "PHONETIC" -> InputType.TYPE_TEXT_VARIATION_PHONETIC
                // Unlikely to ever be used: "" -> InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                // Unlikely to ever be used: "" -> InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                "DATE" -> InputType.TYPE_DATETIME_VARIATION_DATE
                "TIME" -> InputType.TYPE_DATETIME_VARIATION_TIME
                else -> throw LuaError("Invalid input type variation value.")
            }
        }

        fun parseInputType(raw: LuaValue): Int {
            if (!raw.istable()) {
                return InputType.TYPE_CLASS_TEXT
            }

            val root = raw.checktable()
            val clazz = parseInputTypeClass(root.rawget("class").optjstring("TEXT"))
            val variation = parseInputTypeVariation(root.rawget("variation").optjstring("NONE"))
            val luaFlags = root.rawget("flags")
            val flags = when {
                luaFlags.isstring() -> parseInputTypeFlag(luaFlags.tojstring())
                luaFlags.istable() -> {
                    val table = luaFlags.checktable()
                    var result = 0

                    for (key in table.keys()) {
                        result = result or parseInputTypeFlag(table.rawget(key).checkjstring())
                    }

                    result
                }
                luaFlags.isnil() -> parseInputTypeFlag("NONE")
                else -> throw LuaError("Invalid type of the input type flags field, expected a string or a table of strings.")
            }

            return clazz or variation or flags
        }

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
                        result = result or parseGravity(table.rawget(key).checkjstring())
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
            override fun createView(parent: ViewGroup): View {
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
            override fun createView(parent: ViewGroup): View {
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
            override fun createView(parent: ViewGroup): View {
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
            override fun createView(parent: ViewGroup): View {
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
        },
        TRANSITION_BUTTON {
            override fun createView(parent: ViewGroup): View {
                return MainActivity.instance.layoutInflater.inflate(R.layout.transition_button, null)
                //return parent.context.getSystemService<LayoutInflater>()!!.inflate(R.layout.transition_button, null)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                val primaryText: TextView = view.findViewById(R.id.transition_button_text_primary)
                val secondaryText: TextView = view.findViewById(R.id.transition_button_text_secondary)
                primaryText.text = element.getContentHtml("text")
                secondaryText.text = element.getContentHtml("subtext")
                parseLayoutParams(view, element.getContent(), matchParentWidth = true, matchParentHeight = false)
                val handler = element.getContent().get("handler")

                if (handler is LuaFunction) {
                    view.setOnClickListener {
                        handler.call()
                    }
                } else {
                    view.setOnClickListener(null)
                }
            }
        },
        CHECK_BOX {
            override fun createView(parent: ViewGroup): View {
                return CheckBox(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as CheckBox
                view.text = element.getContentHtml("text")
                parseLayoutParams(view, element.getContent(), matchParentWidth = true, matchParentHeight = false)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.CENTER)
                val handler = element.getContent().get("handler")

                if (handler is LuaFunction) {
                    view.setOnCheckedChangeListener { _, checked ->
                        handler.call(LuaValue.valueOf(checked))
                    }
                } else {
                    view.setOnClickListener(null)
                }
            }
        },
        TEXT_INPUT {
            override fun createView(parent: ViewGroup): View {
                return CustomEditText(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as CustomEditText
                view.text.clear()
                view.text.append(element.getContent().get("text").optjstring(""))
                view.hint = element.getContent().get("placeholder").optjstring(null)
                parseLayoutParams(view, element.getContent(), matchParentWidth = true, matchParentHeight = false)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.CENTER)
                view.inputType = parseInputType(element.getContent().get("inputType"))
                val handler = element.getContent().get("handler")

                if (handler is LuaFunction) {
                    view.setTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {
                            handler.call(LuaValue.valueOf(s?.toString() ?: ""))
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    })
                } else {
                    view.setOnClickListener(null)
                }
            }
        },
        NUMBER_PICKER {
            override fun createView(parent: ViewGroup): View {
                return NumberPicker(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as NumberPicker
                view.value = element.getContent().get("value").optint(0)
                view.minValue = element.getContent().get("minValue").optint(Integer.MIN_VALUE)
                view.maxValue = element.getContent().get("maxValue").optint(Integer.MAX_VALUE)
                parseLayoutParams(view, element.getContent(), matchParentWidth = false, matchParentHeight = false)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.CENTER)
                val handler = element.getContent().get("handler")

                if (handler is LuaFunction) {
                    view.setOnValueChangedListener { _, _, newValue ->
                        handler.call(LuaValue.valueOf(newValue))
                    }
                } else {
                    view.setOnClickListener(null)
                }
            }
        };

        companion object {
            fun parse(name: String): ElementType? {
                return try {
                    ElementType.valueOf(camelCaseToUpperSnakeCase(name))
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }

        abstract fun createView(parent: ViewGroup): View
        abstract fun bindView(adapter: ElementAdapter, view: View, element: Element)

        open fun createViewHolder(parent: ViewGroup): ElementViewHolder {
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
            val string = getContentAttribute(name).optjstring("")

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

        fun createBoundView(parent: ViewGroup, elementAdapter: ElementAdapter): View {
            val type = getType()
            val view = type.createView(parent)
            type.bindView(elementAdapter, view, this)

            return view
        }
    }
}
