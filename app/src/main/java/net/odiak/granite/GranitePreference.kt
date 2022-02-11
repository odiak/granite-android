package net.odiak.granite

import android.content.Context
import com.google.gson.Gson
import java.io.IOException
import java.lang.IllegalStateException
import java.util.*

data class Workspace(
    val uri: String,
    val name: String
)

data class LastOpened(
    val uri: String,
    val name: String?
)

class GranitePreference(context: Context) {
    companion object {
        const val PREF = "Granite"
        const val KEY_WORKSPACES = "workspaces"
        const val KEY_LAST_OPENED = "lastOpened"
    }

    private val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private val workspacesAdapter = Gson().getAdapter(Array<Workspace>::class.java)
    private val lastOpenedAdapter = Gson().getAdapter(LastOpened::class.java)

    fun fetchWorkspaces(): List<Workspace>? {
        val json = pref.getString(KEY_WORKSPACES, null) ?: return emptyList()
        return try {
            workspacesAdapter.fromJson(json).toList()
        } catch (e: IOException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    fun updateWorkspaces(workspaces: List<Workspace>): Boolean {
        val json = try {
            workspacesAdapter.toJson(workspaces.toTypedArray())
        } catch (e: IOException) {
            // ignore error
            return false
        }
        pref.edit().putString(KEY_WORKSPACES, json).apply()
        return true
    }

    fun fetchLastOpened(): LastOpened? {
        val json = pref.getString(KEY_LAST_OPENED, null) ?: return null
        return try {
            lastOpenedAdapter.fromJson(json)
        } catch (e: IOException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    fun updateLastOpened(lastOpened: LastOpened): Boolean {
        val json = try {
            lastOpenedAdapter.toJson(lastOpened)
        } catch (e: IOException) {
            return false
        }
        pref.edit().putString(KEY_LAST_OPENED, json).apply()
        return true
    }
}