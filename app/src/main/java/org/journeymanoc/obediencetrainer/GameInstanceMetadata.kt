package org.journeymanoc.obediencetrainer

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.charset.Charset

class GameInstanceMetadata(val file: File, val gameId: String, val instanceId: String) {
    companion object {
        const val PROPERTY_GAME_ID = "gameId"

        fun load(context: Context, instanceId: String): GameInstanceMetadata {
            val instancesDirectory = GameInstance.getInstancesDirectory(context)
            val instanceDirectory = instancesDirectory.resolve(instanceId)
            val metadataFile = instanceDirectory.resolve(GameInstance.FILE_NAME_METADATA)
            val json = readJson(metadataFile)
            val gameId = json.asJsonObject.getAsJsonPrimitive(PROPERTY_GAME_ID).asString

            return GameInstanceMetadata(metadataFile, gameId, instanceId)
        }

        fun create(context: Context, gameId: String): GameInstanceMetadata {
            val instancesDirectory = GameInstance.getInstancesDirectory(context)
            val instanceId = createUniqueId(instancesDirectory, gameId)
            val instanceDirectory = instancesDirectory.resolve(instanceId)
            val metadataFile = instanceDirectory.resolve(GameInstance.FILE_NAME_METADATA)
            val metadata = GameInstanceMetadata(metadataFile, gameId, instanceId)

            metadata.save()

            return metadata
        }

        private fun createUniqueId(instancesDirectory: File, gameId: String): String {
            var instanceId: String
            var index = 0

            while (true) {
                instanceId = "$gameId-$index"

                if (!instancesDirectory.resolve(instanceId).exists()) {
                    return instanceId
                }

                index++
            }
        }
    }

    fun save() {
        val json = JsonObject()

        json.addProperty(PROPERTY_GAME_ID, gameId)
        writeJson(file, json)
    }
}
