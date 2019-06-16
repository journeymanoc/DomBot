package org.journeymanoc.obediencetrainer

import android.content.Context
import org.journeymanoc.obediencetrainer.lua.LuaPersistence
import org.journeymanoc.obediencetrainer.lua.libs.InternalLib
import org.journeymanoc.obediencetrainer.lua.libs.IsolatedBaseLib
import org.journeymanoc.obediencetrainer.lua.libs.IsolatedPackageLib
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.JseMathLib
import java.io.File
import java.nio.charset.Charset

class GameInstance(val game: Game, val instanceId: String, context: Context) {
    companion object {
        const val DIRECTORY_INSTANCES = "instances"
        const val FILE_NAME_PERSISENT = "persistent.json"
    }

    val instanceDirectory: File
    val globals: Globals
    private val internalLib: InternalLib

    init {
        assert(instanceId.isNotBlank())

        this.instanceDirectory = context.filesDir
            .resolve(DIRECTORY_INSTANCES)
            .resolve(DataSource.escapePathSegment(instanceId)!!)
        this.internalLib = InternalLib(this)
        this.globals = setupGlobals(context, false)

        loadPersistentData()
    }

    /**
     * A safe ("containerized") replacement for {@link JsePlatform#standardGlobals}.
     */
    private fun setupGlobals(context: Context, debug: Boolean): Globals {
        // load application-provided lua libraries
        val dataSource = this.game.dataSource.union(DataSource.Asset(context.assets, "lua"))
        var globals = Globals()

        globals.load(IsolatedBaseLib(dataSource))
        globals.load(IsolatedPackageLib())
        globals.load(internalLib)
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

        return globals
    }

    fun bindElementAdapter(elementAdapter: ElementAdapter) {
        internalLib.elementAdapter = elementAdapter
    }

    private fun filePersistent(): File = instanceDirectory.resolve(FILE_NAME_PERSISENT)

    fun loadPersistentData() {
        val filePersistent = filePersistent()

        if (filePersistent.isFile) {
            val persistent = filePersistent.reader(Charset.forName("UTF-8")).use {
                LuaPersistence.readJsonAsLua(it)
            }

            globals.rawset("persistent", persistent)
        }
    }

    fun commitPersistentData() {
        val persistent = globals.rawget("persistent")

        println("Persisting: " + LuaPersistence.luaToString(persistent, true))

        instanceDirectory.mkdirs()
        val filePersistent = filePersistent()

        if (!filePersistent.isFile) {
            filePersistent.deleteRecursively()
        }

        if (!filePersistent.exists()) {
            filePersistent.createNewFile()
        }

        filePersistent.writer(Charset.forName("UTF-8")).use {
            LuaPersistence.writeLuaAsJson(persistent, it, true)
        }
    }

    // FIXME: Error handling
    fun run() {
        val mainScript = game.readMainScript()!!
        val chunk = globals.load(mainScript)!!

        try {
            chunk.call()!!
        } catch (e: LuaError) {
            System.err.println(e.message)
        }
    }
}