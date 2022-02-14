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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.whenStarted
import kotlinx.coroutines.*
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
    private val editingText = mutableStateOf(TextFieldValue())

    private val parser = MarkdownParser(GFMWithWikiLinkFlavourDescriptor())

    private val drawerState = DrawerState(DrawerValue.Open)

    @OptIn(DelicateCoroutinesApi::class, androidx.compose.runtime.ExperimentalComposeApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scope = rememberCoroutineScope()
            val fr = remember { FocusRequester() }

            LaunchedEffect(currentFile.value) {
                if (currentFile.value != null || pref.fetchLastOpened()?.uri != uri) {
                    pref.updateLastOpened(LastOpened(uri = uri, name = currentFile.value?.name))
                }
            }

            LaunchedEffect(editingNode.value) {
                val text = editingNode.value?.getTextInNode(src.value)?.toString() ?: ""
                editingText.value =
                    editingText.value.copy(text = text, selection = TextRange(text.length))

                if (editingNode.value != null) {
                    fr.requestFocus()
                }
            }

            LaunchedEffect(recentFiles.value) block@{
                if (currentFile.value != null || recentFiles.value.isEmpty()) return@block
                val lastOpenedName = pref.fetchLastOpened()?.name ?: return@block
                val file = recentFiles.value.find { it.name == lastOpenedName } ?: return@block
                openFile(file)
                scope.launch {
                    drawerState.close()
                }
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
                                    Text(text = currentFile.value?.name ?: "Granite")
                                })

                            Surface(
                                color = MaterialTheme.colors.surface,
                                contentColor = MaterialTheme.colors.onSurface,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                LazyColumn {
                                    tree.value?.let {
                                        items(it.children) { node ->
                                            if (node === editingNode.value) {
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
                                                    onClick = { editingNode.value = node },
                                                    onChangeCheckbox = if (editingNode.value == null) ::saveCheckboxChange else null
                                                )
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
                editingText.value.text
            else
                it.getTextInNode(src.value)
        }
        save(newSrc, file)
    }

    private fun save(newSrc: String, file: SimpleFile) {
        contentResolver.openFileDescriptorForWriting(file.uri)!!.use { desc ->
            FileOutputStream(desc.fileDescriptor).bufferedWriter().use { writer ->
                writer.write(newSrc)
            }
        }
        editingNode.value = null
        tree.value = parser.buildMarkdownTreeFromString(newSrc)
        src.value = newSrc
    }

    private fun saveCheckboxChange(node: ASTNode, checked: Boolean) {
        if (editingNode.value != null) return

        val file = currentFile.value ?: return
        val text = if (checked) "[x] " else "[ ] "
        val range = node.startOffset until node.endOffset
        val newSrc = src.value.replaceRange(range, text)
        save(newSrc, file)
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