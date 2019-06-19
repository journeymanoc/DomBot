package org.journeymanoc.obediencetrainer

import java.io.File

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
