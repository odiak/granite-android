package net.odiak.granite

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.whenStarted
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.odiak.granite.ui.theme.GraniteTheme
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.parser.MarkdownParser
import java.io.*

class EditActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URI = "URI"
        const val EXTRA_NAME = "NAME"
    }

    private val name by lazy { intent.getStringExtra(EXTRA_NAME)!! }
    private val uri by lazy { intent.getStringExtra(EXTRA_URI)!! }
    private val root by lazy {
        DocumentFile.fromTreeUri(
            this, Uri.parse(uri)
        )!!
    }

    private val pref by lazy { GranitePreference(this) }

    private val recentFiles = mutableStateOf(emptyList<SimpleFile>())
    private val currentFile = mutableStateOf<SimpleFile?>(null)
    private val tree = mutableStateOf<ASTNode?>(null)
    private val src = mutableStateOf("")

    private val parser = MarkdownParser(GFMWithWikiLinkFlavourDescriptor())

    @OptIn(DelicateCoroutinesApi::class, androidx.compose.runtime.ExperimentalComposeApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val drawerState = DrawerState(DrawerValue.Open)

        setContent {
            val scope = rememberCoroutineScope()

            LaunchedEffect(currentFile.value) {
                println("effect!")

                pref.updateLastOpened(LastOpened(uri = uri, name = currentFile.value?.name))
            }

            BackHandler(enabled = drawerState.isOpen) {
                scope.launch {
                    drawerState.close()
                }
            }

            GraniteTheme {
                ModalDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        DrawerContent(
                            recentFiles.value,
                            onSelected = { file ->
                                openFile(file)
                                scope.launch {
                                    drawerState.close()
                                }
                            })
                    }) {
                    Surface(
                        color = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.onSurface
                    ) {
                        Column {
                            TopAppBar(
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "open menu")
                                    }
                                },
                                title = {
                                    Text(text = "Granite")
                                })

                            LazyColumn {
                                tree.value?.let {
                                    items(it.children) { node ->
                                        TopLevelBlock(src = src.value, node = node)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateRecentFiles()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun updateRecentFiles() {
        GlobalScope.launch(Dispatchers.IO) {
            whenStarted {
                recentFiles.value = getRecentFiles(contentResolver, root.uri)
                    .sortedByDescending { it.lastModified }
                    .take(100)
                    .toList()
            }
        }
    }

    private fun openFile(file: SimpleFile) {
        currentFile.value = file
        contentResolver.openFileDescriptor(file.uri, "r")!!.use { desc ->
            FileInputStream(desc.fileDescriptor).bufferedReader().use { reader ->
                val s = reader.readText()
                tree.value = parser.buildMarkdownTreeFromString(s)
                src.value = s
            }
        }
    }
}

private data class SimpleFile(val uri: Uri, val name: String, val lastModified: Long)

private fun getRecentFiles(
    resolver: ContentResolver,
    parent: Uri,
    parentName: String? = null
): Sequence<SimpleFile> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        parent,
        DocumentsContract.getDocumentId(parent)
    )
    val cursor = resolver.query(childrenUri, null, null, null) ?: return emptySequence()

    return sequence {
        cursor.use { cur ->
            while (cur.moveToNext()) {
                val docId =
                    cur.getString(cur.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent, docId)
                val name =
                    cur.getString(cur.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val fullName = if (parentName == null) name else "$parentName/$name"
                val mimeType =
                    cur.getString(cur.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    yieldAll(getRecentFiles(resolver, uri, fullName))
                } else if (fullName.endsWith(".md")) {
                    val lm =
                        cur.getLong(cur.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                    yield(SimpleFile(uri, fullName.removeSuffix(".md"), lm))
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(recentFiles: List<SimpleFile>, onSelected: (SimpleFile) -> Unit) {
    Column {
        Text(fontSize = 20.sp, text = "Recent")
        LazyColumn {
            itemsIndexed(recentFiles) { i, file ->
                if (i != 0) Divider()
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(file) }
                        .padding(10.dp),
                    text = file.name
                )
            }
        }
    }
}