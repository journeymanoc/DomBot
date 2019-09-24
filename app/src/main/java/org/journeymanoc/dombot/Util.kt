package org.journeymanoc.dombot

import android.content.Context
import android.text.TextWatcher
import android.widget.EditText
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.journeymanoc.dombot.lua.LuaPersistence
import java.io.File
import java.nio.charset.Charset
import kotlin.math.max

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

/**
 * One and only one of the arguments of the method {@param foreground} will be {@code null}.
 */
fun<T> async(background: (() -> T), foreground: ((T?, Throwable?) -> Unit)?): Thread {
    return Thread {
        var result: T? = null
        var error: Throwable? = null

        try {
            result = background()
        } catch (throwable: Throwable) {
            error = throwable
        }

        foreground?.let { foreground ->
            MainActivity.instance.runOnUiThread {
                foreground(result, error)
            }
        }
    }.apply {
        start()
    }
}

fun<T> async(background: (() -> T)): Thread {
    return async(background, null)
}

fun parseVersionUniversally(raw: String): IntArray {
    val version = mutableListOf<Int>()
    var segment = ""

    for (char in raw) {
        if (char in '0'..'9') {
            segment += char
        } else {
            if (segment != "") {
                version += segment.toInt()
            }

            segment = ""
        }
    }

    if (segment != "") {
        version += segment.toInt()
    }

    return version.toIntArray()
}

fun compareVersions(a: IntArray, b: IntArray): Int {
    val maxSize = max(a.size, b.size)

    for (i in 0 until maxSize) {
        val av = a.getOrNull(i) ?: 0
        val bv = b.getOrNull(i) ?: 0
        val comparison = av - bv

        when {
            comparison < 0 -> return -1
            comparison > 0 -> return  1
        }
    }

    return 0
}