package org.journeymanoc.obediencetrainer

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
