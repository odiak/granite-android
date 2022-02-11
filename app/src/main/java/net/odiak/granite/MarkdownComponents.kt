package net.odiak.granite

/**
 * Components to show markdown contents
 *
 * Original code was on https://github.com/karino2/MDTouch .
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.*
import org.intellij.markdown.ast.impl.ListCompositeNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes

@Composable
fun TopLevelBlock(src: String, node: ASTNode) {
    if (node.type == MarkdownTokenTypes.EOL) {
        return
    }

    Box(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Block(src = src, node = node, isTopLevel = true)
    }
}

@Composable
fun Block(src: String, node: ASTNode, isTopLevel: Boolean) {
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
            MdUnorderedList(src, node as ListCompositeNode, isTopLevel)
        }
        MarkdownElementTypes.ORDERED_LIST -> {
            MdOrderedList(src, node as ListCompositeNode, isTopLevel)
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

        FrontMatterProvider.FRONT_MATTER -> {
            CodeFence(src, node)
        }
    }
}

@Composable
fun AnnotatedBox(
    content: AnnotatedString,
    paddingBottom: Dp,
    style: TextStyle = LocalTextStyle.current
) {
    Box(Modifier.padding(bottom = paddingBottom)) { Text(content, style = style) }
}


@Composable
fun Heading(src: String, node: CompositeASTNode, style: TextStyle) {
    AnnotatedBox(buildAnnotatedString {
        node.children.forEach { appendHeadingContent(src, it, MaterialTheme.colors) }
    }, 0.dp, style)
}

fun AnnotatedString.Builder.appendHeadingContent(src: String, node: ASTNode, colors: Colors) {
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

fun AnnotatedString.Builder.appendTrimmingInline(src: String, node: ASTNode, colors: Colors) {
    appendInline(src, node, ::selectTrimmingInline, colors)
}

fun AnnotatedString.Builder.appendInline(
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
                else -> append(child.getTextInNode(src).toString())
            }

        } else {
            when (child.type) {
                MarkdownElementTypes.CODE_SPAN -> {
                    // val bgcolor = Color(0xFFF5F5F5)
                    val bgcolor = Color.LightGray
                    pushStyle(SpanStyle(color = Color.Red, background = bgcolor))
                    child.children.subList(1, child.children.size - 1).forEach { item ->
                        append(item.getTextInNode(src).toString())
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
                                    append(it.getTextInNode(src).toString())
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
                        append(child.getTextInNode(src).toString())
                    }

                }

            }
        }
    }
}

fun selectTrimmingInline(node: ASTNode): List<ASTNode> {
    val children = node.children
    var from = 0
    while (from < children.size && children[from].type == MarkdownTokenTypes.WHITE_SPACE) {
        from++
    }
    var to = children.size
    while (to > from && children[to - 1].type == MarkdownTokenTypes.WHITE_SPACE) {
        to--
    }

    return children.subList(from, to)
}

@Composable
fun MdUnorderedList(src: String, node: ListCompositeNode, isTopLevel: Boolean) {
    MdListColumn(isTopLevel) {
        node.children.forEach { item ->
            if (item.type == MarkdownElementTypes.LIST_ITEM) {
                Row {
                    Canvas(
                        modifier = Modifier
                            .size(10.dp)
                            .offset(y = 7.dp)
                            .padding(end = 5.dp)
                    ) {
                        drawCircle(radius = size.width / 2, center = center, color = Color.Black)
                    }
                    Box {
                        Column {
                            MdBlocks(src, item as CompositeASTNode)
                        }
                    }
                }
            }

        }

    }
}

@Composable
inline fun MdListColumn(
    isTopLevel: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .offset(x = if (isTopLevel) 5.dp else 10.dp)
            .padding(bottom = if (isTopLevel) 5.dp else 0.dp)
    ) { content() }
}

@Composable
fun MdBlocks(src: String, blocks: CompositeASTNode, isTopLevel: Boolean = false) {
    blocks.children.forEach { Block(src, it, isTopLevel) }
}

@Composable
fun MdOrderedList(src: String, node: ListCompositeNode, isTopLevel: Boolean) {
    MdListColumn(isTopLevel) {
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
                            MdBlocks(src, item as CompositeASTNode)
                        }
                    }
                }
            }
    }
}

// similar to CodeFenceGeneratingProvider at GeneratingProviders.kt
@Composable
fun CodeFence(src: String, node: ASTNode) {
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