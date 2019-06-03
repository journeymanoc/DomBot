package org.journeymanoc.obediencetrainer

import android.os.Build
import android.text.Html
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class ElementAdapter(var elementRenderQueue: LuaTable) : RecyclerView.Adapter<ElementAdapter.ElementViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ElementViewHolder {
        val elementType = ElementType.values()[viewType]

        return elementType.createViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ElementViewHolder, position: Int) {
        holder.type.bindViewHolder(holder, getElement(position))
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
            override fun createViewHolder(parent: ViewGroup): ElementViewHolder {
                return ElementViewHolder(this, LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                })
            }

            override fun bindViewHolder(viewHolder: ElementViewHolder, element: Element) {
                val view = viewHolder.itemView as LinearLayout
                val content = element.getContent()
                //view.text = content.get("text").checkjstring()
            }
        },
        TEXT {
            override fun createViewHolder(parent: ViewGroup): ElementViewHolder {
                return ElementViewHolder(this, TextView(parent.context))
            }

            override fun bindViewHolder(viewHolder: ElementViewHolder, element: Element) {
                val view = viewHolder.itemView as TextView
                view.text = element.getContentHtml("text")
            }
        },
        IMAGE {
            override fun createViewHolder(parent: ViewGroup): ElementViewHolder {
                return ElementViewHolder(this, ImageView(parent.context))
            }

            override fun bindViewHolder(viewHolder: ElementViewHolder, element: Element) {
                val view = viewHolder.itemView as ImageView
                val content = element.getContent()
                // TODO
            }
        },
        BUTTON {
            override fun createViewHolder(parent: ViewGroup): ElementViewHolder {
                return ElementViewHolder(this, Button(parent.context))
            }

            override fun bindViewHolder(viewHolder: ElementViewHolder, element: Element) {
                val view = viewHolder.itemView as Button
                view.text = element.getContentHtml("text")
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

        abstract fun createViewHolder(parent: ViewGroup): ElementViewHolder
        abstract fun bindViewHolder(viewHolder: ElementViewHolder, element: Element)
    }

    class ElementViewHolder(val type: ElementType, view: View) : RecyclerView.ViewHolder(view)

    inner class Element(private val element: LuaTable) {
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
    }
}
