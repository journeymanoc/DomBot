package org.journeymanoc.dombot

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GameAdapter(val gameRepositories: AsyncFetch<GameRepositories>): RecyclerView.Adapter<GameAdapter.ViewHolder>() {
    companion object {
        val ITEM_VIEW_TYPE_GAMES_LOADING = 0
        val ITEM_VIEW_TYPE_GAME_LOADING = 1
        val ITEM_VIEW_TYPE_GAME_LOADED  = 2
    }

    private fun onClickLoaded(game: Game, holder: GameLoadedViewHolder, position: Int) {
        val view = MainActivity.instance.layoutInflater.inflate(R.layout.remote_game_detail, null)
        val imageLogo: ImageView = view.findViewById(R.id.game_logo)
        val textName: TextView = view.findViewById(R.id.game_name)
        val textId: TextView = view.findViewById(R.id.game_id)
        val textDescription: TextView = view.findViewById(R.id.game_description)
        val installed = MainActivity.instance.games.any { it.id == game.id }
        val mainActionText = if (installed) "Uninstall" else "Install"

        imageLogo.setImageBitmap(game.logo.syncGetIfFinished())
        textName.text = game.nameFormatted()
        textId.text = game.id
        textDescription.text = game.descriptionFormatted()

        val cancelled = Pointer(false)
        val builder = AlertDialog.Builder(holder.itemView.context).apply {
            // TODO use R.strings
            setPositiveButton(mainActionText, null)
            setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            setView(view)
            setOnCancelListener { cancelled.value = true }
            setOnDismissListener {
                if (!cancelled.value) {
                    // PERFORM CHANGES HERE
                }
            }
        }

        val dialog = builder.create()
        dialog.show()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_GAMES_LOADING -> GamesLoadingViewHolder(MainActivity.instance.layoutInflater.inflate(R.layout.item_games_loading, null))
            ITEM_VIEW_TYPE_GAME_LOADING -> GameLoadingViewHolder(MainActivity.instance.layoutInflater.inflate(R.layout.item_game_loading, null))
            ITEM_VIEW_TYPE_GAME_LOADED -> GameLoadedViewHolder(MainActivity.instance.layoutInflater.inflate(R.layout.item_game_loaded, null))
            else -> throw IllegalStateException("Invalid View Type.")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when {
            holder is GamesLoadingViewHolder -> {
                // No need to update anything
            }
            holder is GameLoadingViewHolder -> {
                val gameRepository = gameRepositories.syncGetIfFinished()!!.get(position)!!

                holder.name.text = gameRepository.name.trim()
                holder.id.text = gameRepository.id
            }
            holder is GameLoadedViewHolder -> {
                val gameRepository = gameRepositories.syncGetIfFinished()!!.get(position)!!
                val game = gameRepository.game.syncGetIfFinished()!!

                holder.logo.setImageBitmap(game.logo.syncGetIfFinished())
                holder.name.text = game.nameFormatted()
                holder.description.text = game.descriptionFormatted()
                holder.itemView.setOnClickListener { onClickLoaded(game, holder, position) }
            }
        }
    }

    override fun getItemCount(): Int {
        this.gameRepositories.syncGetIfFinished().let {
            return if (it === null) {
                1
            } else {
                it.size()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        this.gameRepositories.syncGetIfFinished().let { gameRepositories ->
            return if (gameRepositories === null) {
                ITEM_VIEW_TYPE_GAMES_LOADING
            } else {
                gameRepositories.get(position)!!.game.syncGetIfFinished().let { game ->
                    if (game === null) {
                        ITEM_VIEW_TYPE_GAME_LOADING
                    } else {
                        ITEM_VIEW_TYPE_GAME_LOADED
                    }
                }
            }
        }
    }

    abstract class ViewHolder(view: View): RecyclerView.ViewHolder(view)

    class GamesLoadingViewHolder(view: View): ViewHolder(view)

    class GameLoadingViewHolder(view: View) : ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.game_name)
        val id: TextView = view.findViewById(R.id.game_id)
    }

    class GameLoadedViewHolder(view: View) : ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.game_logo)
        val name: TextView = view.findViewById(R.id.game_name)
        val description: TextView = view.findViewById(R.id.game_description)
    }
}