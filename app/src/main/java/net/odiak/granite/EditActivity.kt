package net.odiak.granite

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.parser.MarkdownParser
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

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

    private val editingNode = mutableStateOf<ASTNode?>(null)
    private val editingText = mutableStateOf("")

    private val parser = MarkdownParser(GFMWithWikiLinkFlavourDescriptor())

    @OptIn(DelicateCoroutinesApi::class, androidx.compose.runtime.ExperimentalComposeApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val drawerState = DrawerState(DrawerValue.Open)

        setContent {
            val scope = rememberCoroutineScope()

            LaunchedEffect(currentFile.value) {
                pref.updateLastOpened(LastOpened(uri = uri, name = currentFile.value?.name))
            }

            LaunchedEffect(editingNode.value) {
                editingText.value = editingNode.value?.getTextInNode(src.value)?.toString() ?: ""
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
                                        if (node === editingNode.value) {
                                            val fr = remember { FocusRequester() }
                                            LaunchedEffect(Unit) {
                                                fr.requestFocus()
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                EditingActionButton(
                                                    onClick = { editingNode.value = null },
                                                    text = "Cancel"
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                EditingActionButton(
                                                    onClick = { saveEditing() },
                                                    text = "Done"
                                                )
                                            }
                                            TextField(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(fr),
                                                value = editingText.value,
                                                onValueChange = { editingText.value = it }
                                            )
                                        } else {
                                            TopLevelBlock(
                                                src = src.value,
                                                node = node,
                                                onClick = { editingNode.value = node })
                                        }
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

    private fun saveEditing() {
        val children = tree.value?.children ?: return
        val file = currentFile.value ?: return
        val newSrc = children.joinToString("") {
            if (it == editingNode.value)
                editingText.value
            else
                it.getTextInNode(src.value)
        }
        contentResolver.openFileDescriptorForWriting(file.uri)!!.use { desc ->
            FileOutputStream(desc.fileDescriptor).bufferedWriter().use { writer ->
                writer.write(newSrc)
            }
        }
        editingNode.value = null
        tree.value = parser.buildMarkdownTreeFromString(newSrc)
        src.value = newSrc
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

private fun ContentResolver.openFileDescriptorForWriting(uri: Uri): ParcelFileDescriptor? =
    try {
        openFileDescriptor(uri, "wt")
    } catch (_: FileNotFoundException) {
        openFileDescriptor(uri, "w")
    }

@Composable
private fun EditingActionButton(onClick: () -> Unit, text: String) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(fontSize = 12.sp, text = text)
    }
}