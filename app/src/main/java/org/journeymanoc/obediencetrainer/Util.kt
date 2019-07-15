package org.journeymanoc.obediencetrainer

import android.content.Context
import android.text.TextWatcher
import android.widget.EditText
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.journeymanoc.obediencetrainer.lua.LuaPersistence
import java.io.File
import java.nio.charset.Charset

fun prepareRegularFile(file: File): File {
    if (!file.isFile) {
        file.deleteRecursively()
    }

    if (!file.exists()) {
        file.parentFile?.mkdirs()
        file.createNewFile()
    }

    return file
}

fun readJson(file: File): JsonElement {
    return file.reader(Charset.forName("UTF-8")).use {
        JsonParser().parse(it)
    }
}

fun writeJson(file: File, json: JsonElement) {
    prepareRegularFile(file).writer(Charset.forName("UTF-8")).use {
        LuaPersistence.GSON.toJson(json, it)
    }
}

fun camelCaseToUpperSnakeCase(string: String): String {
    var result = ""

    for (char in string.toCharArray()) {
        if (char.isUpperCase()) {
            result += '_'
        }

        result += char.toUpperCase()
    }

    return result
}

class CustomEditText(context: Context?) : EditText(context) {
    val textChangedListeners: MutableSet<TextWatcher> = mutableSetOf()

    override fun addTextChangedListener(watcher: TextWatcher?) {
        super.addTextChangedListener(watcher)
        watcher?.also { textChangedListeners.add(watcher) }
    }

    fun clearTextChangedListeners() {
        textChangedListeners.forEach { super.removeTextChangedListener(it) }
        textChangedListeners.clear()
    }

    fun setTextChangedListener(watcher: TextWatcher?) {
        clearTextChangedListeners()
        addTextChangedListener(watcher)
    }
}