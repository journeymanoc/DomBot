package org.journeymanoc.obediencetrainer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import org.journeymanoc.obediencetrainer.lua.LuaPersistence
import org.journeymanoc.obediencetrainer.lua.libs.InternalLib
import org.journeymanoc.obediencetrainer.lua.libs.IsolatedBaseLib
import org.journeymanoc.obediencetrainer.lua.libs.IsolatedPackageLib
import org.luaj.vm2.*
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.JseMathLib
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class GameInstance(val game: Game, val metadata: GameInstanceMetadata, context: Context) {
    companion object {
        const val DIRECTORY_INSTANCES = "instances"
        const val FILE_NAME_METADATA = "metadata.json"
        const val FILE_NAME_PERSISTENT = "persistent.json"
        const val FILE_NAME_NOTIFICATIONS = "notifications.json"

        fun getInstancesDirectory(context: Context): File {
            return context.filesDir.resolve(DIRECTORY_INSTANCES)
        }

        fun loadInstances(context: Context, games: List<Game>): List<GameInstance> {
            var instances = ArrayList<GameInstance>()

            for (instanceId in getInstancesDirectory(context).list() ?: emptyArray()) {
                val metadata = GameInstanceMetadata.load(context, instanceId)
                val game = games.find { metadata.gameId == it.id }

                game?.also {
                    val instance = GameInstance(game, metadata, context)

                    instances.add(instance)
                }
            }

            return instances
        }
    }

    val instanceDirectory: File
    val globals: Globals
    val view: View
    private val internalLib: InternalLib
    private val notifyHandler: Handler
    private var notifyHandlerPreparationOffsetMillis: Long? = null
    private val notifications: PriorityQueue<Notification> = PriorityQueue()
    private var notificationsLoaded: Boolean

    init {
        this.instanceDirectory = getInstancesDirectory(context)
            .resolve(DataSource.escapePathSegment(metadata.instanceId)!!)
        this.internalLib = InternalLib(this)
        this.globals = setupGlobals(context, false)
        this.notifyHandler = Handler(Looper.getMainLooper(), NotifyCallbackHandler())
        this.notificationsLoaded = false

        loadPersistentData()

        // Set up the element adapter
        val elementAdapter = ElementAdapter(game, LuaTable())
        val mainRecyclerView: RecyclerView = RecyclerView(context).apply {
            id = R.id.main_recycler_view
            //setPadding(32, 24, 32, 24)
            //clipToPadding = false
        }

        mainRecyclerView.setHasFixedSize(true)
        mainRecyclerView.layoutManager = LinearLayoutManager(mainRecyclerView.context)
        mainRecyclerView.adapter = elementAdapter

        view = mainRecyclerView

        bindElementAdapter(elementAdapter)
    }

    /**
     * A safe ("containerized") replacement for {@link JsePlatform#standardGlobals}.
     */
    private fun setupGlobals(context: Context, debug: Boolean): Globals {
        // load application-provided lua libraries
        val dataSource = this.game.dataSource.union(DataSource.Asset(context.assets, "lua"))
        val globals = Globals()

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

    private fun bindElementAdapter(elementAdapter: ElementAdapter) {
        internalLib.elementAdapter = elementAdapter
    }

    private fun fileMetadata(): File = instanceDirectory.resolve(FILE_NAME_METADATA)
    private fun filePersistent(): File = instanceDirectory.resolve(FILE_NAME_PERSISTENT)
    private fun fileNotifications(): File = instanceDirectory.resolve(FILE_NAME_NOTIFICATIONS)

    fun loadPersistentData() {
        val filePersistent = filePersistent()

        if (filePersistent.isFile) {
            val json = readJson(filePersistent)
            val persistent = LuaPersistence.luaFromJson(json)

            globals.rawset("persistent", persistent)
        }
    }

    fun commitPersistentData() {
        val persistent = globals.rawget("persistent")

        println("Persisting: " + LuaPersistence.luaToString(persistent, true))

        val json = LuaPersistence.luaToJson(persistent, true)

        writeJson(filePersistent(), json)
    }

    inner class NotifyCallbackHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            println("HANDLING MESSAGE")
            val notification = msg.obj as Notification

            synchronized(notifications) {
                if (!notifications.remove(notification)) {
                    println("HANDLING ABORTED, MESSAGE NOT IN QUEUE")
                    return false
                }
            }

            val onNotify = globals.get("onNotify")

            if (onNotify.isfunction()) {
                onNotify.checkfunction().call(notification.toLua())
            } else {
                System.err.println("No `onNotify` notification handler found, define it as a function in the global scope.")
            }

            return false // keep handling
        }
    }

    fun getNotification(id: String): Notification? {
        synchronized(notifications) {
            return notifications.find { it.id == id }
        }
    }

    fun cancelNotification(id: String): Notification? {
        synchronized(notifications) {
            val iter = notifications.iterator()

            while (iter.hasNext()) {
                val notification = iter.next()

                if (notification.id == id) {
                    iter.remove()
                    notifyHandler.removeMessages(Notification.WHAT, notification)
                    return notification
                }
            }
        }

        return null
    }

    fun initializeNotifyHandler() {
        val uptimeMillis = SystemClock.uptimeMillis()
        val instantMillis = Calendar.getInstance().timeInMillis
        notifyHandlerPreparationOffsetMillis = uptimeMillis - instantMillis
        notifyHandler.removeMessages(Notification.WHAT)
    }

    fun loadNotifications() {
        val fileNotifications = fileNotifications()

        initializeNotifyHandler()

        synchronized(notifications) {
            notifications.clear()

            if (fileNotifications.isFile) {
                val json = readJson(fileNotifications)
                val array = if (json.isJsonArray) json.asJsonArray else JsonArray()

                for (element in array) {
                    val notification = Notification.fromJson(element)
                    scheduleNotify(notification, false)
                    println(notification)
                }
            }
        }

        notificationsLoaded = true
    }

    fun commitNotifications() {
        val json = JsonArray()

        synchronized(notifications) {
            for (notification in notifications) {
                json.add(notification.toJson(true))
            }
        }

        writeJson(fileNotifications(), json)
    }

    fun stopNotificationHandlerAndCommitNotifications() {
        notifyHandler.removeMessages(Notification.WHAT)
        commitNotifications()
        notificationsLoaded = false
    }

    fun scheduleNotify(notification: Notification, skipInsert: Boolean): Notification? {
        var cancelled: Notification? = null

        synchronized(notifications) {
            if (notification.id !== null) {
                cancelled = cancelNotification(notification.id)
            }

            val uptimeMillis = notifyHandlerPreparationOffsetMillis!! + notification.instant
            val message = notifyHandler.obtainMessage(Notification.WHAT, notification)
            notifyHandler.sendMessageAtTime(message, uptimeMillis)

            if (!skipInsert) {
                notifications.add(notification)
            }
        }

        return cancelled
    }

    fun scheduleNotify(id: String?, instant: Long, data: LuaValue): Notification? {
        return scheduleNotify(Notification(id, instant, data), false)
    }

    fun scheduleNotify(id: String?, instant: Calendar, data: LuaValue): Notification? {
        return scheduleNotify(id, instant.timeInMillis, data)
    }

    // FIXME: Error handling
    private fun run() {
        val mainScript = game.readMainScript()!!
        val chunk = globals.load(mainScript)!!

        try {
            chunk.call()!!
        } catch (e: LuaError) {
            val regex = "^(.{0,64})(.*):(\\d+) (.*?)$".toRegex(RegexOption.DOT_MATCHES_ALL)
            val errorMessage = regex.matchEntire(e.message!!)?.let { matchResult ->
                val truncatedFile = matchResult.groups[1]!!.value.replace('\n', ' ') +
                    if (matchResult.groups[2]!!.range.let { it.last - it.first } == 0) "" else "…"
                val line = matchResult.groups[3]!!.value
                val message = matchResult.groups[4]!!.value
                "`$truncatedFile`:$line $message"
            } ?: e.message

            throw RuntimeException(errorMessage)
        }
    }

    /**
     * Called when the instance is created and added to the list of available instances
     */
    fun add() {
        load()
    }

    fun load() {
        if (MainActivity.activityState >= MainActivity.Companion.State.CREATED) onCreate()
        if (MainActivity.activityState >= MainActivity.Companion.State.RESUMED) onResume()
    }

    fun delete() {
        instanceDirectory.deleteRecursively()
    }

    /**
     * Called when the instance is created and added to the list of available instances
     */
    fun unload() {
        stopNotificationHandlerAndCommitNotifications()
    }

    fun onCreate() {
        println("Handling `onCreate` of game instance `${metadata.instanceId}`")
        loadNotifications()
        run()
    }

    fun onResume() {
        println("Handling `onResume` of game instance `${metadata.instanceId}`")
        if (!notificationsLoaded) {
            loadNotifications()
        }
    }

    fun onPause() {
        println("Handling `onPause` of game instance `${metadata.instanceId}`")
        stopNotificationHandlerAndCommitNotifications()
    }

    fun onDestroy() {
        println("Handling `onDestroy` of game instance `${metadata.instanceId}`")
        // Commented out, because we already do that in `onPause`, which is called every time before `onDestroy`
        //stopNotificationHandlerAndCommitNotifications()
    }
}