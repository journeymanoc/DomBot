package org.journeymanoc.obediencetrainer

import android.app.ActionBar
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Picture
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
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

                view.orientation = if (content.get("horizontal").toboolean()) {
                    LinearLayout.HORIZONTAL
                } else {
                    LinearLayout.VERTICAL
                }
            }
        },
        TEXT {
            override fun createView(parent: View): View {
                return TextView(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as TextView
                view.text = element.getContentHtml("text")
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
            }
        },
        BUTTON {
            override fun createView(parent: View): View {
                return Button(parent.context)
            }

            override fun bindView(adapter: ElementAdapter, view: View, element: Element) {
                view as TextView
                view.text = element.getContentHtml("text")
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
