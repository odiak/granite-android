package net.odiak.granite

/**
 * Components to show markdown contents
 *
 * Original code was based on https://github.com/karino2/MDTouch/blob/main/app/src/main/java/io/github/karino2/mdtouch/ui/Markdown.kt .
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.*
import org.intellij.markdown.ast.impl.ListCompositeNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@Composable
fun TopLevelBlock(
    src: String,
    node: ASTNode,
    onClick: () -> Unit,
    onChangeCheckbox: ((ASTNode, Boolean) -> Unit)? = null
) {
    if (node.type == MarkdownTokenTypes.EOL) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Block(src = src, node = node, isTopLevel = true, onChangeCheckbox)
    }
}

@Composable
private fun Block(
    src: String,
    node: ASTNode,
    isTopLevel: Boolean,
    onChangeCheckbox: ((ASTNode, Boolean) -> Unit)? = null
) {
    when (node.type) {
        MarkdownElementTypes.ATX_1 -> {
            Heading(src, node as CompositeASTNode, MaterialTheme.typography.h3)
        }
        MarkdownElementTypes.ATX_2 -> {
            Heading(src, node as CompositeASTNode, MaterialTheme.typography.h4)
        }
        MarkdownElementTypes.ATX_3 -> {
            Heading(src, node as CompositeASTNode, MaterialTheme.typography.h5)
        }
        MarkdownElementTypes.ATX_4 -> {
            Heading(src, node as CompositeASTNode, MaterialTheme.typography.h6)
        }
        MarkdownElementTypes.ATX_5 -> {
            Heading(src, node as CompositeASTNode, MaterialTheme.typography.subtitle1)
        }
        MarkdownElementTypes.ATX_6 -> {
            Heading(src, node as CompositeASTNode, MaterialTheme.typography.subtitle2)
        }

        MarkdownElementTypes.PARAGRAPH -> {
            AnnotatedBox(buildAnnotatedString {
                appendTrimmingInline(src, node, MaterialTheme.colors)
            }, if (isTopLevel) 8.dp else 0.dp)
        }

        MarkdownElementTypes.UNORDERED_LIST -> {
            UnorderedList(src, node as ListCompositeNode, isTopLevel, onChangeCheckbox)
        }

        MarkdownElementTypes.ORDERED_LIST -> {
            OrderedList(src, node as ListCompositeNode, isTopLevel)
        }

        MarkdownElementTypes.CODE_FENCE -> {
            CodeFence(src, node)
        }

        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            // for click target.
            Box(modifier = Modifier.height(10.dp)) {
                Divider(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.DarkGray,
                    thickness = 2.dp
                )
            }
        }

        MarkdownElementTypes.BLOCK_QUOTE -> {
            BlockQuoteBox {
                Column {
                    Blocks(src = src, blocks = node as CompositeASTNode)
                }
            }
        }

        FrontMatterProvider.FRONT_MATTER -> {
            CodeFence(src, node)
        }
    }
}

@Composable
private fun AnnotatedBox(
    content: AnnotatedString,
    paddingBottom: Dp,
    style: TextStyle = LocalTextStyle.current
) {
    Box(Modifier.padding(bottom = paddingBottom)) { Text(content, style = style) }
}


@Composable
private fun Heading(src: String, node: CompositeASTNode, style: TextStyle) {
    AnnotatedBox(buildAnnotatedString {
        node.children.forEach { appendHeadingContent(src, it, MaterialTheme.colors) }
    }, 0.dp, style)
}

private fun AnnotatedString.Builder.appendHeadingContent(
    src: String,
    node: ASTNode,
    colors: Colors
) {
    when (node.type) {
        MarkdownTokenTypes.ATX_CONTENT -> {
            appendTrimmingInline(src, node, colors)
            return
        }
    }
    if (node is CompositeASTNode) {
        node.children.forEach { appendHeadingContent(src, it, colors) }
        return
    }
}

private fun AnnotatedString.Builder.appendTrimmingInline(
    src: String,
    node: ASTNode,
    colors: Colors
) {
    appendInline(src, node, ::selectTrimmingInline, colors)
}

private fun AnnotatedString.Builder.appendInline(
    src: String,
    node: ASTNode,
    childrenSelector: (ASTNode) -> List<ASTNode>,
    colors: Colors
) {
    val targets = childrenSelector(node)
    targets.forEachIndexed { index, child ->
        if (child is LeafASTNode) {
            when (child.type) {
                MarkdownTokenTypes.EOL,
                MarkdownTokenTypes.HARD_LINE_BREAK -> append("\n")
                MarkdownTokenTypes.BLOCK_QUOTE -> {}
                else -> append(child.getTextInNodeWithEscape(src))
            }

        } else {
            when (child.type) {
                MarkdownElementTypes.CODE_SPAN -> {
                    // val bgcolor = Color(0xFFF5F5F5)
                    val bgcolor = Color.LightGray
                    pushStyle(SpanStyle(color = Color.Red, background = bgcolor))
                    child.children.subList(1, child.children.size - 1).forEach { item ->
                        append(item.getTextInNodeWithEscape(src))
                    }
                    pop()
                }
                MarkdownElementTypes.STRONG -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(
                            src,
                            child,
                            { parent -> parent.children.subList(2, parent.children.size - 2) },
                            colors
                        )
                    }
                }
                MarkdownElementTypes.EMPH -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(
                            src,
                            child,
                            { parent -> parent.children.subList(1, parent.children.size - 1) },
                            colors
                        )
                    }
                }
                MarkdownElementTypes.INLINE_LINK -> {
                    withStyle(
                        SpanStyle(
                            colors.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        child.children.filter { it.type == MarkdownElementTypes.LINK_TEXT }
                            .forEach { linktext ->
                                linktext.children.subList(1, linktext.children.size - 1).forEach {
                                    append(it.getTextInNodeWithEscape(src))
                                }
                            }
                    }
                }
                GFMElementTypes.STRIKETHROUGH -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(
                            src,
                            child,
                            { parent -> parent.children.subList(2, parent.children.size - 2) },
                            colors
                        )
                    }
                }
                GFMWithWikiLinkFlavourDescriptor.WIKI_LINK -> {
                    // [[WikiName]]
                    assert(child.children.size == 5)

                    // Render [[WikiName]] as [[WikiName]] with link like decoration.
                    withStyle(
                        SpanStyle(
                            colors.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(child.getTextInNodeWithEscape(src))
                    }
                }
            }
        }
    }
}

private fun selectTrimmingInline(node: ASTNode): List<ASTNode> {
    val specificTypes = setOf(MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.BLOCK_QUOTE)
    val children = node.children
    val result = mutableListOf<ASTNode>()
    var i = 0
    while (i < children.size) {
        var j = children.indexOfFirstFrom(i) { it.type == MarkdownTokenTypes.EOL }
        if (j == -1) j = children.size

        var from = i
        while (from < j && children[from].type in specificTypes) {
            from++
        }
        var to = j
        while (to > from && children[to - 1].type in specificTypes) {
            to--
        }
        result.addAll(children.subList(from, to))
        if (j != children.size) result.add(children[j])

        i = j + 1
    }

    return result
}

private inline fun <T> List<T>.indexOfFirstFrom(from: Int, predicate: (T) -> Boolean): Int {
    var i = from
    while (i < size) {
        if (predicate(this[i])) {
            return i
        }
        i++
    }
    return -1
}

@Composable
private fun UnorderedList(
    src: String,
    node: ListCompositeNode,
    isTopLevel: Boolean,
    onChangeCheckbox: ((ASTNode, Boolean) -> Unit)? = null
) {
    ListColumn(isTopLevel) {
        node.children.forEach { item ->
            if (item.type == MarkdownElementTypes.LIST_ITEM) {
                Row {
                    val checkboxNode = item.children.getOrNull(1)
                    if (checkboxNode?.type == GFMTokenTypes.CHECK_BOX) {
                        Checkbox(
                            modifier = Modifier.size(20.dp),
                            checked = checkboxNode.getTextInNode(src).trim() == "[x]",
                            enabled = onChangeCheckbox != null,
                            onCheckedChange = {
                                onChangeCheckbox?.invoke(checkboxNode, it)
                            })
                    } else {
                        Canvas(
                            modifier = Modifier.size(20.dp)
                        ) {
                            drawCircle(radius = 2.dp.toPx(), center = center, color = Color.Black)
                        }
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Box {
                        Column {
                            Blocks(
                                src,
                                item as CompositeASTNode,
                                onChangeCheckbox = onChangeCheckbox
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private inline fun ListColumn(
    isTopLevel: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.padding(bottom = if (isTopLevel) 5.dp else 0.dp)
    ) { content() }
}

@Composable
private fun Blocks(
    src: String,
    blocks: CompositeASTNode,
    isTopLevel: Boolean = false,
    onChangeCheckbox: ((ASTNode, Boolean) -> Unit)? = null
) {
    blocks.children.forEach { Block(src, it, isTopLevel, onChangeCheckbox) }
}

@Composable
private fun OrderedList(src: String, node: ListCompositeNode, isTopLevel: Boolean) {
    ListColumn(isTopLevel) {
        val items = node.children
            .filter { it.type == MarkdownElementTypes.LIST_ITEM }

        val heads = items.runningFold(0) { aggr, item ->
            if (aggr == 0) {
                item.findChildOfType(MarkdownTokenTypes.LIST_NUMBER)
                    ?.getTextInNode(src)?.toString()?.trim()?.let {
                        val number = it.substring(0, it.length - 1).trimStart('0')
                        if (number.isEmpty()) 0 else number.toInt()
                    } ?: 1
            } else {
                aggr + 1
            }
        }.drop(1)

        heads.zip(items)
            .forEach { (head, item) ->
                val mark = "${head}."
                Row {
                    Box(Modifier.padding(end = 5.dp)) {
                        Text(mark)
                    }
                    Box {
                        Column {
                            Blocks(src, item as CompositeASTNode)
                        }
                    }
                }
            }
    }
}

// similar to CodeFenceGeneratingProvider at GeneratingProviders.kt
@Composable
private fun CodeFence(src: String, node: ASTNode) {
    if (node.children.isEmpty()) return

    Column(
        modifier = Modifier
            .background(Color.LightGray)
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        val codeStyle = TextStyle(fontFamily = FontFamily.Monospace)
        val builder = StringBuilder()

        var childrenToConsider = node.children
        if (childrenToConsider.last().type == MarkdownTokenTypes.CODE_FENCE_END) {
            childrenToConsider = childrenToConsider.subList(0, childrenToConsider.size - 1)
        }

        var lastChildWasContent = false

        var renderStart = false
        for (child in childrenToConsider) {
            if (!renderStart && child.type == MarkdownTokenTypes.EOL) {
                renderStart = true
            } else {
                when (child.type) {
                    MarkdownTokenTypes.CODE_FENCE_CONTENT,
                    FrontMatterProvider.FRONT_MATTER_CONTENT -> {
                        builder.append(child.getTextInNode(src))
                        lastChildWasContent = true
                    }
                    MarkdownTokenTypes.EOL -> {
                        Text(
                            style = codeStyle,
                            text = builder.toString()
                        )
                        builder.clear()
                        lastChildWasContent = false
                    }
                }

            }
        }
        if (lastChildWasContent) {
            Text(
                style = codeStyle,
                text = builder.toString()
            )
        }

    }
}

@Composable
private fun BlockQuoteBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier
        .drawBehind {
            drawLine(
                Color.LightGray,
                Offset(0f, 0f),
                Offset(0f, size.height),
                3.dp.toPx()
            )
        }
        .padding(start = 10.dp)) {
        content()
    }
}

private val symbolPattern = Regex("""\\([!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~])""")
private fun ASTNode.getTextInNodeWithEscape(src: String): String {
    return getTextInNode(src).toString().replace(symbolPattern) { m -> m.groups[1]!!.value }
}