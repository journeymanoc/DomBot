package org.journeymanoc.obediencetrainer

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URL
import java.nio.charset.Charset

/**
 * TODO:
 * - Buttons are ugly unless used for dialogs, add menu items
 * - Custom styling (fonts, colors)
 * - Game instance manipulation
 * - Document the way games are supposed to be developed, streamline it and make it easy to prototype games
 * - Add a way to donate to support the development
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        private val MAIN_LAYOUT_CONSTRAINTS: ConstraintSet = ConstraintSet().apply {
            constrainWidth(R.id.main_recycler_view, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(R.id.main_recycler_view, ConstraintSet.MATCH_CONSTRAINT)
            connect(R.id.main_recycler_view, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT)
            connect(R.id.main_recycler_view, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT)
            connect(R.id.main_recycler_view, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(R.id.main_recycler_view, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }

        enum class State: Comparable<State> {
            NONEXISTENT,
            CREATED,
            RESUMED;
        }

        lateinit var instance: MainActivity
        var activityState = State.NONEXISTENT
    }

    init {
        instance = this
    }

    private lateinit var mainLayout: ConstraintLayout
    private lateinit var navigationView: NavigationView
    private lateinit var settingsView: View
    private lateinit var gamesView: RecyclerView
    private lateinit var toolbarTitle: TextView
    private lateinit var updateButton: Button
    private lateinit var games: MutableList<Game>
    private var gameInstances: MutableList<GameInstance> = mutableListOf()
    private var currentInstanceIndex: Int? = null
    private var gameRepositories: AsyncFetch<GameRepositories> = AsyncFetch("MainActivity#gameRepositories", ::syncFetchGameRepositories)

    private fun bindToolbarToDrawer() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        invalidateOptionsMenu()
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        updateButton = toolbar.findViewById(R.id.update_button)
    }

    private fun setUpNavigationListener() {
        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
    }

    private fun constructImmutableUserInterface() {
        setContentView(R.layout.activity_main)
        bindToolbarToDrawer()
        setUpNavigationListener()

        mainLayout = findViewById(R.id.main_layout)
        navigationView = findViewById(R.id.nav_view)
        gamesView = RecyclerView(applicationContext).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = GameAdapter(gameRepositories)
        }
        settingsView = LinearLayout(applicationContext)
    }

    private fun loadGames(): List<Game> {
        games = Game.loadBuiltinGames(applicationContext).toMutableList()
        return games
    }

    private fun unloadGameInstances() {
        gameInstances.forEach(GameInstance::unload)
        gameInstances.clear()
    }

    private fun loadGameInstances(): List<GameInstance> {
        gameInstances = GameInstance.loadInstances(applicationContext, games).toMutableList()
        gameInstances.forEach(GameInstance::load)
        currentInstanceIndex?.let { showGameInstance(it) }
        return gameInstances
    }

    private fun reloadGameInstances(): List<GameInstance> {
        unloadGameInstances()
        return loadGameInstances()
    }

    private fun updateGameInstancesMenu() {
        val instancesItem = navigationView.menu.findItem(R.id.drawer_item_instances) as MenuItem

        instancesItem.subMenu.clear()

        for ((i, instance) in gameInstances.withIndex()) {
            val item = instancesItem.subMenu.add(R.id.drawer_group_instances, i, i, instance.metadata.instanceName)
            item.isCheckable = true
            item.isChecked = currentInstanceIndex == i
        }
    }

    private fun showGameInstanceDialog() {
        // TODO: Display help when no games are downloaded

        val selectedItem = Pointer(-1)
        val cancelled = Pointer(false)
        val builder = AlertDialog.Builder(this).apply {
            // TODO use R.strings
            setTitle("Add game instance")
            setPositiveButton("Add", null)
            setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            setSingleChoiceItems(games.map { "${it.name} - ${it.version}" }.toTypedArray(), -1) { _, id -> selectedItem.value = id }
            setOnCancelListener { cancelled.value = true }
            setOnDismissListener {
                if (!cancelled.value && selectedItem.value != -1) {
                    val game = games[selectedItem.value]
                    val instanceTmp = GameInstanceMetadata.create(applicationContext, game)
                    val instanceId = instanceTmp.instanceId
                    reloadGameInstances()
                    showGameInstance { it.metadata.instanceId == instanceId }
                    updateGameInstancesMenu()
                }
            }
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun showRenameCurrentInstanceDialog() {
        if (currentInstanceIndex === null) {
            return
        }

        val currentInstance = gameInstances[currentInstanceIndex!!]
        val field = EditText(applicationContext).apply {
            setText(currentInstance.metadata.instanceName)
        }
        val cancelled = Pointer(false)
        val builder = AlertDialog.Builder(this).apply {
            // TODO use R.strings
            setTitle("Rename game instance")
            setPositiveButton("Rename", null)
            setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            setView(field)
            setOnCancelListener { cancelled.value = true }
            setOnDismissListener {
                if (!cancelled.value) {
                    currentInstance.metadata.instanceName = field.text.toString()
                    currentInstance.metadata.save()
                    updateGameInstancesMenu()
                    updateToolbarTitle()
                }
            }
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun showDeleteCurrentInstanceDialog() {
        if (currentInstanceIndex === null) {
            return
        }

        val currentInstance = gameInstances[currentInstanceIndex!!]
        val cancelled = Pointer(false)
        val builder = AlertDialog.Builder(this).apply {
            // TODO use R.strings
            setTitle("Delete game instance")
            setMessage("Are you sure you want to delete the instance \"${currentInstance.metadata.instanceName}\"? The instance data will be irreversibly lost.")
            setPositiveButton("Delete", null)
            setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            setOnCancelListener { cancelled.value = true }
            setOnDismissListener {
                if (!cancelled.value) {
                    deleteCurrentInstance()
                }
            }
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun clearMainLayout() {
        mainLayout.removeAllViews()
    }

    private fun showGameInstance(gameInstanceIndex: Int) {
        val gameInstance = gameInstances[gameInstanceIndex]

        clearMainLayout()
        mainLayout.addView(gameInstance.view)
        MAIN_LAYOUT_CONSTRAINTS.applyTo(mainLayout)
        currentInstanceIndex = gameInstanceIndex
        updateToolbarTitle()
        requestRemoteGameIfNotAcquiredFor(gameInstance.game)
        updateUpdateButton()
    }

    private fun showGames() {
        clearMainLayout()
        mainLayout.addView(gamesView)
        currentInstanceIndex = null
        toolbarTitle.setText(R.string.drawer_item_games_title)
        updateUpdateButton()

        gameRepositories.asyncGetOnce("showGames") { gameRepositories, error ->
            if (error !== null) {
                error.printStackTrace()
                return@asyncGetOnce
            }

            gamesView.adapter!!.notifyDataSetChanged()

            gameRepositories!!.list().forEachIndexed { index, gameRepository ->
                gameRepository.game.asyncGetOnce("showGames") { gameRepository, error ->
                    if (error !== null) {
                        error.printStackTrace()
                        return@asyncGetOnce
                    }

                    gamesView.adapter!!.notifyItemChanged(index)
                }
            }
        }
    }

    private fun showSettings() {
        clearMainLayout()
        mainLayout.addView(settingsView)
        currentInstanceIndex = null
        toolbarTitle.setText(R.string.drawer_item_settings_title)
        updateUpdateButton()
    }

    private fun showGameInstance(predicate: (GameInstance) -> Boolean) {
        showGameInstance(gameInstances.indexOfFirst(predicate))
    }

    private fun deleteCurrentInstance() {
        if (currentInstanceIndex === null) {
            return
        }

        val instanceToDelete = gameInstances.removeAt(currentInstanceIndex!!)

        instanceToDelete.delete()
        clearMainLayout()
        currentInstanceIndex = null
        updateGameInstancesMenu()
    }

    @Deprecated("Use the async version.")
    private fun syncFetchGameRepositories(): GameRepositories {
        var repositoryName: String? = "journeymanoc/ObedienceTrainerGames"
        var root: JsonObject

        do {
            // Use Raw file retrieval for single file downloads:
            // curl https://raw.githubusercontent.com/journeymanoc/ObedienceTrainerGames/master/games.json
            // Use JGit for cloning repos.
            root = URL("https://raw.githubusercontent.com/$repositoryName/master/games.json")
                .openConnection().getInputStream().reader(Charset.forName("UTF-8")).use {
                    JsonParser().parse(it)
                }.asJsonObject
            repositoryName = root.get("redirect")?.asString
        } while (repositoryName != null)

        val minimumVersionCode = root.get("minimumVersionCode")?.asLong

        if (minimumVersionCode !== null && BuildConfig.VERSION_CODE < minimumVersionCode) {
            throw RuntimeException("Outdated version. Please update the application.")
        }

        val games = root.get("games").asJsonArray
        val gameRepositories = MutableGameRepositories()

        for (game in games) {
            val game = game.asJsonObject
            val gameRepositoryName = game.get("repository").asString!!
            val gameId = game.get("id").asString!!
            val gameName = game.get("name").asString!!

            val gameRepository = try {
                GameRepository(gameRepositoryName, gameId, gameName)
            } catch (e: Exception) {
                System.err.println("Skipping game repository `$gameId`, reason: ${e.message}")
                continue
            }

            if (!gameRepositories.add(gameRepository)) {
                System.err.println(
                    "Ignoring game with duplicate id `${gameRepository.id}` with repository `${gameRepository.repositoryName}`, keeping `${gameRepositories.get(
                        gameRepository.id
                    )!!.repositoryName}`."
                )
                continue
            }
        }

        // TODO: Check signatures
        // TODO: Add a fallback so the application keeps working even if there is no `games` repository
        return gameRepositories
    }

    fun updateToolbarTitle() {
        if (currentInstanceIndex === null) {
            toolbarTitle.text = null
        } else {
            val currentInstance = gameInstances[currentInstanceIndex!!]

            toolbarTitle.text = currentInstance.metadata.instanceName
        }
    }

    fun isThereKnownToBeAnUpdateForGame(game: Game): Boolean {
        try {
            val gameRepositories = gameRepositories.syncGetIfFinished()

            if (gameRepositories === null) {
                return false
            }

            val remoteRepository = gameRepositories.get(game.id)
            val remoteGame = remoteRepository?.game?.syncGetIfFinished()

            if (remoteGame !== null && compareVersions(game.parseVersion(), remoteGame.parseVersion()) < 0) {
                return true
            }
            // TODO: Do not forget to check the engine version against the meta.xml
        } catch (e: AsyncFetchException) {}

        return false
    }

    fun updateUpdateButton() {
        var show = false

        if (currentInstanceIndex !== null) {
            val currentInstance = gameInstances[currentInstanceIndex!!]
            val currentGame = currentInstance.game

            if (isThereKnownToBeAnUpdateForGame(currentGame)) {
                show = true
            }
        }

        updateButton.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun requestRemoteGameIfNotAcquiredFor(game: Game) {
        gameRepositories.asyncGet { gameRepositories, throwable ->
            if (throwable !== null) {
                throwable.printStackTrace()
                return@asyncGet
            }

            gameRepositories!!

            val remoteGame = gameRepositories.get(game.id)?.game

            if (remoteGame !== null) {
                remoteGame.asyncGetOnce("requestRemoteGameIfNotAcquiredFor[${game.id}]") { remoteGame, throwable ->
                    if (throwable !== null) {
                        throwable.printStackTrace()
                        return@asyncGetOnce
                    }

                    remoteGame!!
                    val currentGameInstance = getCurrentGameInstance()

                    if (currentGameInstance !== null && currentGameInstance.game.id == remoteGame.id) {
                        updateUpdateButton()
                    }
                }
            }
        }
    }

    fun getCurrentGameInstance(): GameInstance? {
        return currentInstanceIndex?.let { gameInstances[it] }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        constructImmutableUserInterface()

        gameRepositories.asyncGetOnce("onCreate") { result, error ->
            if (error !== null) {
                error.printStackTrace()
                return@asyncGetOnce
            }

            result!!
            val currentGameInstance = getCurrentGameInstance()

            if (currentGameInstance !== null) {
                requestRemoteGameIfNotAcquiredFor(currentGameInstance.game)
            }

            updateUpdateButton()
            // TODO: Update GameAdapter
        }

        loadGames()
        loadGameInstances()
        updateGameInstancesMenu()
        gameInstances.forEach(GameInstance::onCreate)
        activityState = Companion.State.CREATED
    }

    override fun onResume() {
        super.onResume()
        gameInstances.forEach(GameInstance::onResume)
        activityState = Companion.State.RESUMED
    }

    override fun onPause() {
        println("ON PAUSE")
        super.onPause()
        gameInstances.forEach(GameInstance::onPause)
        activityState = Companion.State.CREATED
    }

    override fun onDestroy() {
        println("ON DESTROY")
        super.onDestroy()
        gameInstances.forEach(GameInstance::onDestroy)
        activityState = Companion.State.NONEXISTENT
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    // Call invalidateOptionsMenu to recreate it with new activityState
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        val menu = menuInflater.inflate(R.menu.main, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_rename_instance -> {
                showRenameCurrentInstanceDialog()
                true
            }
            R.id.action_delete_instance -> {
                showDeleteCurrentInstanceDialog()
                true
            }
            //R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.groupId) {
            R.id.drawer_group_instances -> {
                println("Instance ${item.itemId} clicked.")
                showGameInstance(item.itemId)
            }
            R.id.drawer_group_other -> when (item.itemId) {
                R.id.drawer_item_add_instance -> {
                    println("Clicked add instance")
                    showGameInstanceDialog()
                }
                R.id.drawer_item_games -> {
                    println("Clicked games")
                    showGames()
                }
                R.id.drawer_item_settings -> {
                    println("Clicked settings")
                    showSettings()
                }
            }
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
