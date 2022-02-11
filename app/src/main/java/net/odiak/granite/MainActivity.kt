package net.odiak.granite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import net.odiak.granite.ui.theme.GraniteTheme

class MainActivity : ComponentActivity() {
    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it ->
            if (it != null) {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val uriStr = it.toString()
                if (workspaces.any { it.uri == uriStr }) {
                    Toast.makeText(this, "That folder is already added", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                addWorkspace(Workspace(uri = uriStr, name = "Untitled"))
            }
        }

    private val pref by lazy { GranitePreference(this) }
    private val workspaces = mutableStateListOf<Workspace>()

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadWorkspacesFromPref()

        pref.fetchLastOpened()?.let { lo ->
            workspaces.find { it.uri == lo.uri }?.let {
                openWorkspace(it)
            }
        }

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
                                            openWorkspace(item)
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

    private fun openWorkspace(item: Workspace) {
        val intent =
            Intent(this@MainActivity, EditActivity::class.java)
        intent.putExtra(EditActivity.EXTRA_URI, item.uri)
        intent.putExtra(EditActivity.EXTRA_NAME, item.name)
        startActivity(intent)
    }

    private fun open() {
        openLauncher.launch(null)
    }

    private fun loadWorkspacesFromPref() {
        val ws = pref.fetchWorkspaces() ?: return
        workspaces.clear()
        workspaces.addAll(ws)
    }

    private fun saveWorkspacesToPref() {
        pref.updateWorkspaces(workspaces)
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
