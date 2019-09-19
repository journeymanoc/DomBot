package org.journeymanoc.obediencetrainer

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GameAdapter(val gameRepositories: AsyncFetch<GameRepositories>): RecyclerView.Adapter<GameAdapter.ViewHolder>() {
    companion object {
        val ITEM_VIEW_TYPE_GAMES_LOADING = 0
        val ITEM_VIEW_TYPE_GAME_LOADING = 1
        val ITEM_VIEW_TYPE_GAME_LOADED  = 2
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

                holder.name.text = gameRepository.name
                holder.id.text = gameRepository.id
            }
            holder is GameLoadedViewHolder -> {
                val gameRepository = gameRepositories.syncGetIfFinished()!!.get(position)!!
                val game = gameRepository.game.syncGetIfFinished()!!

                holder.name.text = game.name
                holder.description.text = game.description
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
        val name: TextView = view.findViewById(R.id.game_name)
        val description: TextView = view.findViewById(R.id.game_description)

    }
}