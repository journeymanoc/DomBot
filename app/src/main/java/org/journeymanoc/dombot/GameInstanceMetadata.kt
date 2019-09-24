package org.journeymanoc.dombot

import android.content.Context
import com.google.gson.JsonObject
import java.io.File

class GameInstanceMetadata(val file: File, val gameId: String, val instanceId: String, var instanceName: String) {
    companion object {
        const val PROPERTY_GAME_ID = "gameId"
        const val PROPERTY_INSTANCE_NAME = "instanceName"

        fun load(context: Context, instanceId: String): GameInstanceMetadata {
            val instancesDirectory = GameInstance.getInstancesDirectory(context)
            val instanceDirectory = instancesDirectory.resolve(instanceId)
            val metadataFile = instanceDirectory.resolve(GameInstance.FILE_NAME_METADATA)
            val json = readJson(metadataFile)
            val gameId = json.asJsonObject.getAsJsonPrimitive(PROPERTY_GAME_ID).asString!!
            val instanceName = json.asJsonObject.getAsJsonPrimitive(PROPERTY_INSTANCE_NAME).asString ?: instanceId

            return GameInstanceMetadata(metadataFile, gameId, instanceId, instanceName)
        }

        fun create(context: Context, game: Game): GameInstanceMetadata {
            val instancesDirectory = GameInstance.getInstancesDirectory(context)
            val instanceId = createUniqueId(instancesDirectory, game.id)
            val instanceDirectory = instancesDirectory.resolve(instanceId)
            val metadataFile = instanceDirectory.resolve(GameInstance.FILE_NAME_METADATA)
            val metadata = GameInstanceMetadata(metadataFile, game.id, instanceId, game.name + ": " + instanceId)

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

    init {
        assert(instanceId.isNotBlank())
    }

    fun save() {
        val json = JsonObject()

        json.addProperty(PROPERTY_GAME_ID, gameId)
        json.addProperty(PROPERTY_INSTANCE_NAME, instanceName)
        writeJson(file, json)
    }
}
