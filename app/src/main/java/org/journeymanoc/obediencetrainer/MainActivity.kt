package org.journeymanoc.obediencetrainer

import android.os.Bundle
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import android.view.MenuItem
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import java.util.function.Predicate

/**
 * TODO:
 * - Buttons are ugly unless used for dialogs, add menu items
 * - Custom styling (fonts, colors)
 * - Game instance manipulation
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

        var activityState = State.NONEXISTENT
    }

    private lateinit var mainLayout: ConstraintLayout
    private lateinit var navigationView: NavigationView
    private lateinit var games: MutableList<Game>
    private var gameInstances: MutableList<GameInstance> = mutableListOf()
    private var currentInstanceIndex: Int? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        constructImmutableUserInterface()

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
        super.onPause()
        gameInstances.forEach(GameInstance::onPause)
        activityState = Companion.State.CREATED
    }

    override fun onDestroy() {
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
                R.id.drawer_item_settings -> {
                    println("Clicked settings")
                }
            }
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
