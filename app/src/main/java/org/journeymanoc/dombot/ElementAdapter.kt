package org.journeymanoc.dombot

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.text.*
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class ElementAdapter(val game: Game, var elementRenderQueue: LuaTable) : RecyclerView.Adapter<ElementAdapter.ElementViewHolder>() {
    private val idToElement: MutableMap<String, Element> = HashMap()

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

    fun notifyElementChanged(element: Element) {
        notifyItemChanged(element.itemIndex)
    }

    fun unregisterIds() {
        idToElement.clear()
    }

    fun registerIds() {
        for (i in 0 until itemCount) {
            getElement(i).registerIds()
        }
    }

    fun updateElement(id: String, elementData: LuaTable): Boolean {
        val element = idToElement[id]

        if (element === null) {
            //throw LuaError("No UI element with ID `$id` found")
            return false
        }

        element.unregisterIds()

        val content = element.getContent()

        for (key in elementData.keys()) {
            content.set(key, elementData.rawget(key))
        }

        notifyElementChanged(element)
        element.registerIds()

        return true
    }

    fun getElement(itemIndex: Int): Element {
        return Element(elementRenderQueue.get(itemIndex + 1).checktable(), itemIndex)
    }

    companion object {
        const val MARGIN_DEFAULT_HORIZONTAL = 32
        const val MARGIN_DEFAULT_VERTICAL   = 24

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

        fun parseLayoutParams(view: View, element: Element, matchParentWidth: Boolean, matchParentHeight: Boolean, marginDefaultStart: Int?, marginDefaultTop: Int?, marginDefaultEnd: Int?, marginDefaultBottom: Int?) {
            if (view.layoutParams === null) {
                view.layoutParams = LinearLayout.LayoutParams(
                    if (matchParentWidth) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                    if (matchParentHeight) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val luaWidth = element.getContent().get("width")
            val luaHeight = element.getContent().get("height")

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
                (view.layoutParams as LinearLayout.LayoutParams).weight = element.getContent().get("weight").optdouble(1.0).toFloat()
            }

            if (view.layoutParams is ViewGroup.MarginLayoutParams) {
                val layoutParams: ViewGroup.MarginLayoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
                val lua = element.getContent().get("margin")
                val constraints = getConstraints(lua, marginDefaultStart, marginDefaultTop, marginDefaultEnd, marginDefaultBottom)
                val prevMarginStart = layoutParams.marginStart
                val prevMarginEnd   = layoutParams.marginEnd

                layoutParams.setMargins(0, // we don't want to set left/right, but rather start/end instead
                                        constraints.top ?: layoutParams.topMargin,
                                        0, // we don't want to set left/right, but rather start/end instead
                                        constraints.bottom ?: layoutParams.bottomMargin)
                layoutParams.marginStart = constraints.start ?: prevMarginStart
                layoutParams.marginEnd   = constraints.end   ?: prevMarginEnd
            }
        }

        fun parseLayoutParams(view: View, element: Element, matchParentWidth: Boolean, matchParentHeight: Boolean, marginDefaultHorizontal: Int?, marginDefaultVertical: Int?) {
            Companion.parseLayoutParams(view, element, matchParentWidth, matchParentHeight, marginDefaultHorizontal, marginDefaultVertical, marginDefaultHorizontal, marginDefaultVertical)
        }

        fun parseLayoutParams(view: View, element: Element, matchParentWidth: Boolean, matchParentHeight: Boolean, marginDefault: Int?) {
            Companion.parseLayoutParams(view, element, matchParentWidth, matchParentHeight, marginDefault, marginDefault)
        }

        fun parseLayoutParams(view: View, element: Element, matchParentWidth: Boolean, matchParentHeight: Boolean) {
            Companion.parseLayoutParams(view, element, matchParentWidth, matchParentHeight, MARGIN_DEFAULT_HORIZONTAL, MARGIN_DEFAULT_VERTICAL)
        }

        fun useColors(view: View, element: Element) {
            val backgroundColor = element.getContent().get("background").optinteger(null)

            if (backgroundColor !== null) {
                if (view.background === null) {
                    val value = TypedValue()
                    view.context.theme.resolveAttribute(android.R.attr.windowBackground, value, true)

                    val baseColor = if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        value.data
                    } else {
                        Color.WHITE
                    }

                    view.setBackgroundColor(baseColor)
                }

                // TODO: Add a way to customize the mode
                view.background.setColorFilter(backgroundColor.checkint(), PorterDuff.Mode.MULTIPLY)
            } else {
                view.background?.clearColorFilter()
            }

            view.alpha = element.getContent().get("opacity").optdouble(1.0).toFloat()
        }

        fun useEnabled(view: View, element: Element) {
            view.isEnabled = element.getContent().get("enabled").optboolean(true)
        }

        fun useHandlerAsOnClickListener(view: View, element: Element) {
            val handler = element.getContent().get("handler")

            if (handler is LuaFunction) {
                view.setOnClickListener {
                    handler.call()
                }
                view.isFocusable = true
                view.isClickable = true
            } else {
                view.setOnClickListener(null)
                view.isFocusable = false
                view.isClickable = false
            }
        }

        fun getConstraints(lua: LuaValue, defaultStart: Int?, defaultTop: Int?, defaultEnd: Int?, defaultBottom: Int?): Constraints {
            var start = defaultStart
            var top = defaultTop
            var end = defaultEnd
            var bottom = defaultBottom

            when {
                lua.isnil() -> { /* keep default values */ }
                lua.isnumber() -> {
                    val all = lua.checkint()
                    start = all
                    top = all
                    end = all
                    bottom = all
                }
                lua.istable() -> {
                    val table = lua.checktable()

                    table.rawget("horizontal").let { if (it.isnumber()) it.checkint() else null }?.also { start  = it; end    = it }
                    table.rawget("vertical"  ).let { if (it.isnumber()) it.checkint() else null }?.also { top    = it; bottom = it }

                    table.rawget("start" ).let { if (it.isnumber()) it.checkint() else null }?.also { start  = it }
                    table.rawget("top"   ).let { if (it.isnumber()) it.checkint() else null }?.also { top    = it }
                    table.rawget("end"   ).let { if (it.isnumber()) it.checkint() else null }?.also { end    = it }
                    table.rawget("bottom").let { if (it.isnumber()) it.checkint() else null }?.also { bottom = it }
                }
                else -> throw LuaError("Invalid constraints, must be either a table or a number.")
            }

            return Constraints(start, top, end, bottom)
        }

        fun usePadding(view: View, element: Element, defaultStart: Int?, defaultTop: Int?, defaultEnd: Int?, defaultBottom: Int?) {
            val lua = element.getContent().get("padding")
            val constraints = getConstraints(lua, defaultStart, defaultTop, defaultEnd, defaultBottom)

            view.setPadding(constraints.start ?: view.paddingStart,
                            constraints.top ?: view.paddingTop,
                            constraints.end ?: view.paddingEnd,
                            constraints.bottom ?: view.paddingBottom)

            if (view is ViewGroup) {
                view.clipToPadding = false
            }
        }

        fun usePadding(view: View, element: Element, defaultHorizontal: Int?, defaultVertical: Int?) {
            usePadding(view, element, defaultHorizontal, defaultVertical, defaultHorizontal, defaultVertical)
        }

        fun usePadding(view: View, element: Element, defaultPadding: Int?) {
            usePadding(view, element, defaultPadding, defaultPadding)
        }

        fun usePadding(view: View, element: Element) {
            usePadding(view, element, null)
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
                view.removeAllViews()

                for (child in getChildren(element)) {
                    val childView = child.createBoundView(view, adapter)

                    view.addView(childView)
                }

                parseLayoutParams(view, element, matchParentWidth = true, matchParentHeight = false)
                usePadding(view, element)
                useColors(view, element)
                useEnabled(view, element)
                useHandlerAsOnClickListener(view, element)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.TOP or Gravity.START)
                view.orientation = if (element.getContent().get("horizontal").toboolean()) {
                    LinearLayout.HORIZONTAL
                } else {
                    LinearLayout.VERTICAL
                }
            }

            override fun getChildren(element: Element): List<Element> {
                val children = element.getContent().get("children").checktable()
                val result = ArrayList<Element>(children.rawlen())

                for (i in 1..children.rawlen()) {
                    val child = element.createChild(children.get(i).checktable())

                    result.add(child)
                }

                return result
            }
        },
        TEXT {
            override fun createView(parent: ViewGroup): View {
                return MainActivity.instance.layoutInflater.inflate(R.layout.transition_button, null)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as LinearLayout
                val primaryText: TextView = view.findViewById(R.id.transition_button_text_primary)
                val secondaryText: TextView = view.findViewById(R.id.transition_button_text_secondary)
                val gravity = parseGravity(element.getContent().get("gravity"), Gravity.START or Gravity.CENTER_VERTICAL)

                element.getContentHtml("text").also { content ->
                    if (content !== null) {
                        primaryText.visibility = View.VISIBLE
                        primaryText.text = content
                    } else {
                        primaryText.visibility = View.GONE
                        primaryText.text = null
                    }
                }

                element.getContentHtml("subtext").also { content ->
                    if (content !== null) {
                        secondaryText.visibility = View.VISIBLE
                        secondaryText.text = content
                    } else {
                        secondaryText.visibility = View.GONE
                        secondaryText.text = null
                    }
                }

                parseLayoutParams(view, element, matchParentWidth = true, matchParentHeight = false, marginDefault = 0)
                usePadding(view, element, MARGIN_DEFAULT_HORIZONTAL, MARGIN_DEFAULT_VERTICAL)
                useColors(view, element)
                useEnabled(view, element)
                primaryText.gravity = gravity
                secondaryText.gravity = gravity
                view.gravity = gravity
                useHandlerAsOnClickListener(view, element)
            }
        },
        IMAGE {
            override fun createView(parent: ViewGroup): View {
                return ImageView(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as ImageView
                val path = element.getContent().get("path").checkjstring()
                val inputStream = adapter.game.dataSource.readPath(path)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                view.setImageBitmap(bitmap)
                parseLayoutParams(view, element, matchParentWidth = false, matchParentHeight = false)
                usePadding(view, element)
                useColors(view, element)
                useEnabled(view, element)
                useHandlerAsOnClickListener(view, element)
            }
        },
        BUTTON {
            override fun createView(parent: ViewGroup): View {
                return Button(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as Button
                view.text = element.getContentHtml("text")
                parseLayoutParams(view, element, matchParentWidth = false, matchParentHeight = false)
                usePadding(view, element)
                useColors(view, element)
                useEnabled(view, element)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.CENTER)
                useHandlerAsOnClickListener(view, element)

                /*
                view.setTextColor(view.context.resources.getColor(R.color.primary_material_light))
                view.setBackgroundColor(view.context.resources.getColor(R.color.primary_material_dark))
                */
            }
        },
        CHECK_BOX {
            override fun createView(parent: ViewGroup): View {
                return CheckBox(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as CheckBox
                view.isChecked = element.getContent().get("checked").toboolean()
                view.text = element.getContentHtml("text")
                parseLayoutParams(view, element, matchParentWidth = true, matchParentHeight = false)
                usePadding(view, element, MARGIN_DEFAULT_VERTICAL, null, null, null)
                useColors(view, element)
                useEnabled(view, element)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.START or Gravity.CENTER_VERTICAL)
                view.textSize = 16f

                element.getContent().get("handler").also { handler ->
                    if (handler is LuaFunction) {
                        view.setOnCheckedChangeListener { _, checked ->
                            handler.call(LuaValue.valueOf(checked))
                        }
                    } else {
                        view.setOnClickListener(null)
                    }
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
                parseLayoutParams(view, element, matchParentWidth = true, matchParentHeight = false)
                usePadding(view, element)
                useColors(view, element)
                useEnabled(view, element)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.CENTER)
                view.inputType = parseInputType(element.getContent().get("inputType"))

                element.getContent().get("handler").also { handler ->
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
            }
        },
        NUMBER_PICKER {
            override fun createView(parent: ViewGroup): View {
                return NumberPicker(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as NumberPicker
                view.minValue = element.getContent().get("minValue").optint(Integer.MIN_VALUE)
                view.maxValue = element.getContent().get("maxValue").optint(Integer.MAX_VALUE)
                view.value = element.getContent().get("value").optint(0)
                view.wrapSelectorWheel = element.getContent().get("wrap").optboolean(false)
                parseLayoutParams(view, element, matchParentWidth = false, matchParentHeight = false)
                usePadding(view, element)
                useColors(view, element)
                useEnabled(view, element)
                view.gravity = parseGravity(element.getContent().get("gravity"), Gravity.CENTER)

                element.getContent().get("handler").also { handler ->
                    if (handler is LuaFunction) {
                        view.setOnValueChangedListener { _, _, newValue -> handler.call(LuaValue.valueOf(newValue)) }
                    } else {
                        view.setOnClickListener(null)
                    }
                }

                element.getContent().get("formatter").also { formatter ->
                    if (formatter is LuaFunction) {
                        view.setFormatter { value -> formatter.call(LuaValue.valueOf(value)).tojstring() }
                    } else {
                        view.setFormatter(null)
                    }

                    try { // Force re-formatting of the first value, which by default is unformatted due to a bug
                        val f = NumberPicker::class.java.getDeclaredField("mInputText")
                        f.isAccessible = true
                        val inputText = f.get(view) as EditText
                        inputText.filters = emptyArray()
                    } catch (e: Throwable) {}
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

        open fun getChildren(element: Element): List<Element> {
            return emptyList()
        }
    }

    class ElementViewHolder(val type: ElementType, view: View) : RecyclerView.ViewHolder(view)

    inner class Element(private val element: LuaTable, val itemIndex: Int) {
        fun createChild(childElement: LuaTable): Element {
            return Element(childElement, itemIndex)
        }

        fun getContent(): LuaTable {
            return element.get("content").checktable()
        }

        fun getContentAttribute(name: String): LuaValue {
            return getContent().get(name)
        }

        fun getContentHtml(name: String): Spanned? {
            val string = getContentAttribute(name).optjstring(null)

            return when {
                string === null -> null
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> Html.fromHtml(string, Html.FROM_HTML_MODE_LEGACY)
                else -> Html.fromHtml(string)
            }
        }

        fun getType(): ElementType {
            return element.get("type").checkjstring()
                .let { ElementType.parse(it)!! }
        }

        fun getViewType(): Int {
            return getType().ordinal
        }

        fun getChildren(): List<Element> {
            return getType().getChildren(this)
        }

        fun unregisterIds() {
            val id = getContent().get("id").optjstring(null)

            id?.also { idToElement.remove(id) }
            getChildren().forEach(Element::unregisterIds)
        }

        fun registerIds() {
            val id = getContent().get("id").optjstring(null)

            id?.also { idToElement[id] = this }
            getChildren().forEach(Element::registerIds)
        }

        fun createBoundView(parent: ViewGroup, elementAdapter: ElementAdapter): View {
            val type = getType()
            val view = type.createView(parent)
            type.bindView(elementAdapter, view, this)

            return view
        }
    }
}
