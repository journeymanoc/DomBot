package org.journeymanoc.dombot

import com.google.gson.JsonParser

class GameRepository(val repositoryName: String, val id: String, val name: String) {
    val game: AsyncFetch<Game> = AsyncFetch("GameRepository#game", ::syncFetchGame)

    companion object {
        val REPOSITORY_NAME_PATTERN = Regex("^[\\d\\w\\-._/]+$")
    }

    init {
        // Sanitize repositoryName so it can be safely used in URLs
        if (repositoryName.filter { it == '/' }.length != 1) {
            throw IllegalArgumentException("Invalid repository name. Must contain exactly one forward slash (/).")
        }

        if (!REPOSITORY_NAME_PATTERN.matches(repositoryName)) {
            throw IllegalArgumentException("Invalid repository name. The repository name contains illegal characters.")
        }

        val split = repositoryName.split('/')

        if (split[0] == "." || split[0] == ".." || split[1] == "." || split[1] == "..") {
            throw IllegalArgumentException("Invalid repository name. Special names not allowed.")
        }
    }

    @Deprecated("Use `GameRepository#game` instead.")
    private fun syncFetchGame(): Game {
        val latestReleaseJson = DataSource.URL("https://api.github.com/repos/$repositoryName/releases/latest")
            .readPathBuffered("")?.let { JsonParser().parse(it) }?.asJsonObject

        if (latestReleaseJson === null) {
            throw IllegalStateException("Could not retrieve information about the latest release of game repository `$repositoryName`.")
        }

        val latestReleaseTag = latestReleaseJson["tag_name"].asString

        if (latestReleaseTag === null) {
            throw IllegalStateException("No tag name specified for the latest release of game repository `$repositoryName`. Make sure the release has a tag associated with it.")
        }

        val dataSource = DataSource.URL("https://raw.githubusercontent.com/$repositoryName/$latestReleaseTag")
        val game = Game.load(dataSource)

        if (!game.parseVersion().contentEquals(parseVersionUniversally(latestReleaseTag))) {
            throw IllegalStateException("Invalid latest release of game repository `$repositoryName`: Tag name does not match the version in `meta.xml`. Tag: $latestReleaseTag; meta.xml: ${game.version}")
        }

        return game
    }

    override fun toString(): String {
        return "GameRepository(repositoryName='$repositoryName', id='$id', name='$name')"
    }
}

open class GameRepositories(private val list: List<GameRepository>, private val map: Map<String, GameRepository>) {
    constructor(): this(listOf(), mapOf())

    fun get(gameId: String): GameRepository? {
        return map[gameId]
    }

    fun get(index: Int): GameRepository? {
        return if (index >= 0 && index < list.size) { list[index] } else { null }
    }

    fun size(): Int {
        return list.size
    }

    fun list(): List<GameRepository> {
        return list
    }

    override fun toString(): String {
        return list.toString()
    }
}

class MutableGameRepositories(private val list: MutableList<GameRepository>, private val map: MutableMap<String, GameRepository>): GameRepositories(list, map) {
    constructor(): this(mutableListOf(), mutableMapOf())

    fun add(gameRepository: GameRepository): Boolean {
        if (map[gameRepository.id] !== null) {
            return false
        }

        map[gameRepository.id] = gameRepository
        list += gameRepository
        return true
    }
}