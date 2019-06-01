package org.journeymanoc.obediencetrainer

import android.content.res.AssetManager
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import android.view.MenuItem
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.RuntimeException

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    /**
     * A safe ("containerized") replacement for {@link JsePlatform#standardGlobals}.
     */
    fun loadGlobals(gameDataSource: DataSource, debug: Boolean): Globals {
        val dataSource = gameDataSource.union(DataSource.Asset(applicationContext.assets, "lua"))
        var globals = Globals()

        globals.load(IsolatedBaseLib(dataSource))
        globals.load(IsolatedPackageLib())
        globals.load(InternalLib())
        globals.load(Bit32Lib())
        globals.load(CoroutineLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(JseMathLib())

        if (debug) {
            globals.load(DebugLib())
        }

        LoadState.install(globals)
        LuaC.install(globals)

        // TODO load from storage

        return globals
    }

    fun loadAssetAsString(path: String): String {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(
                    InputStreamReader(applicationContext.assets.open(path), "UTF-8")
            )

            return reader.readText()
        } catch (e: IOException) {
            throw RuntimeException(e)
        } finally {
            reader?.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val game = Game.loadFromAsset(applicationContext, "games/ltdct")
        val globals = loadGlobals(game.dataSource, true)
        val script = game.dataSource.readPathBuffered("${game.initialState}.lua")!!.readText()

        println(script)

        val chunk = globals.load(script)!!

        val result = try {
            chunk.call()!!
        } catch (e: LuaError) {
            System.err.println(e.message)
            return
        }

        println("persistent: " + LuaPersistence.luaToString(globals.get("_G").get("persistent"), true))

        // Original example code follows

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val mainRecyclerView: RecyclerView = findViewById(R.id.main_recycler_view)

        mainRecyclerView.setHasFixedSize(true)
        mainRecyclerView.layoutManager = null
        mainRecyclerView.adapter = null

        /*
        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
        */
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_home -> {
                // Handle the camera action
            }
            R.id.nav_gallery -> {

            }
            R.id.nav_slideshow -> {

            }
            R.id.nav_tools -> {

            }
            R.id.nav_share -> {

            }
            R.id.nav_send -> {

            }
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
