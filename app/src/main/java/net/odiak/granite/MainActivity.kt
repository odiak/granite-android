package net.odiak.granite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import net.odiak.granite.ui.theme.GraniteTheme
import java.io.IOException

class MainActivity : ComponentActivity() {

    companion object {
        const val PREF = "Granite"
        const val KEY_WORKSPACES = "workspaces"
    }

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it != null) {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                addWorkspace(Workspace(uri = it.toString(), name = "Untitled"))
            }
        }

    private val pref by lazy { getSharedPreferences(PREF, MODE_PRIVATE) }
    private val workspaces = mutableStateListOf<Workspace>()
    private val workspaceAdapter = Gson().getAdapter(Array<Workspace>::class.java)

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadWorkspacesFromPref()

        val showMenu = mutableStateOf<Workspace?>(null)

        setContent {
            GraniteTheme {
                showMenu.value?.let {
                    MenuDialog(
                        it,
                        onClose = { showMenu.value = null },
                        onDelete = { removeWorkspace(it) })
                }

                Scaffold(topBar = {
                    TopAppBar(title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(modifier = Modifier.weight(1f), text = "Workspaces")
                            IconButton(onClick = { open() }) {
                                Icon(Icons.Filled.Add, contentDescription = "new folder")
                            }
                        }
                    })
                }) {
                    LazyColumn(contentPadding = PaddingValues(10.dp)) {
                        items(workspaces) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            val intent =
                                                Intent(this@MainActivity, EditActivity::class.java)
                                            intent.putExtra(EditActivity.EXTRA_URI, item.uri)
                                            intent.putExtra(EditActivity.EXTRA_NAME, item.name)
                                            startActivity(intent)
                                        },
                                        onLongClick = {
                                            showMenu.value = item
                                        })
                                    .padding(10.dp)
                            ) {
                                Text(fontSize = 20.sp, text = item.name)
                                Text(
                                    text = item.uri
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun open() {
        openLauncher.launch(null)
    }

    private fun loadWorkspacesFromPref() {
        val json = pref.getString(KEY_WORKSPACES, null) ?: return
        val array = try {
            workspaceAdapter.fromJson(json)
        } catch (e: IOException) {
            // ignore error
            return
        }
        workspaces.clear()
        workspaces.addAll(array)
    }

    private fun saveWorkspacesToPref() {
        val json = try {
            workspaceAdapter.toJson(workspaces.toTypedArray())
        } catch (e: IOException) {
            // ignore error
            return
        }
        pref.edit().putString(KEY_WORKSPACES, json).apply()
    }

    private fun addWorkspace(workspace: Workspace) {
        workspaces.add(workspace)
        saveWorkspacesToPref()
    }

    private fun removeWorkspace(workspace: Workspace) {
        workspaces.remove(workspace)
        saveWorkspacesToPref()
    }
}

data class Workspace(val uri: String, val name: String)

@Composable
fun MenuDialog(workspace: Workspace, onClose: () -> Unit, onDelete: (w: Workspace) -> Unit) {
    Dialog(onDismissRequest = { onClose() }) {
        Surface(
            color = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface,
            shape = MaterialTheme.shapes.medium,
            elevation = 5.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .clickable {
                            onDelete(workspace)
                            onClose()
                        }
                        .padding(horizontal = 12.dp, vertical = 18.dp),
                    text = "Delete"
                )
                Text(modifier = Modifier
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 18.dp), text = "Cancel")
            }
        }
    }
}
