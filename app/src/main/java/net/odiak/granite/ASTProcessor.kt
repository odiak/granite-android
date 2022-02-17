package net.odiak.granite

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

object ASTProcessor {
    fun convertTopLevelASTNodeToNodeWrappers(
        src: String,
        astNode: ASTNode
    ): List<TopLevelNodeWrapper> {
        val wrappers = mutableListOf<TopLevelNodeWrapper>()
        for (child in astNode.children) {
            val node = convertASTNode(src, child)
            if (node == LineBreakNode) continue
            wrappers.add(TopLevelNodeWrapper(node = node, src = src, originalNode = astNode))
        }
        return wrappers
    }

    private fun convertASTNode(src: String, astNode: ASTNode): Node {
        println("convertASTNode: ${astNode.type}")
        return when (astNode.type) {
            MarkdownTokenTypes.EOL -> {
                LineBreakNode
            }
            MarkdownElementTypes.ATX_1 -> {
                H1Node(convertInlineNodes(src, astNode.atxContents))
            }
            MarkdownElementTypes.ATX_2 -> {
                H2Node(convertInlineNodes(src, astNode.atxContents))
            }
            MarkdownElementTypes.ATX_3 -> {
                H3Node(convertInlineNodes(src, astNode.atxContents))
            }
            MarkdownElementTypes.ATX_4 -> {
                H4Node(convertInlineNodes(src, astNode.atxContents))
            }
            MarkdownElementTypes.ATX_5 -> {
                H5Node(convertInlineNodes(src, astNode.atxContents))
            }
            MarkdownElementTypes.ATX_6 -> {
                H6Node(convertInlineNodes(src, astNode.atxContents))
            }
            MarkdownElementTypes.PARAGRAPH -> {
                ParagraphNode(convertInlineNodes(src, astNode.children))
            }
            MarkdownElementTypes.BLOCK_QUOTE -> {
                val typesToIgnore = setOf(
                    MarkdownTokenTypes.BLOCK_QUOTE,
                    MarkdownTokenTypes.EOL,
                    MarkdownTokenTypes.WHITE_SPACE
                )
                BlockQuoteNode(astNode.children.mapNotNull {
                    if (it.type in typesToIgnore) null
                    else convertASTNode(src, it)
                })
            }
            else -> {
                error("unexpected!")
            }
        }
    }

    private fun convertInlineNodes(src: String, astNodes: List<ASTNode>): List<InlineNode> {
        return astNodes.mapInlineChildrenNotNull { child ->
            println("convertInlineNodes: ${child.type}")
            when (child.type) {
                MarkdownElementTypes.EMPH -> {
                    EmphasisNode(convertInlineNodes(src, child.children.dropFirstAndLast()))
                }
                MarkdownElementTypes.STRONG -> {
                    StrongEmphasisNode(convertInlineNodes(src, child.children.dropFirstAndLast(2)))
                }
                MarkdownElementTypes.INLINE_LINK -> {
                    val linkText =
                        child.children.find { it.type == MarkdownElementTypes.LINK_TEXT }!!
                    val linkDestination =
                        child.children.find { it.type == MarkdownElementTypes.LINK_DESTINATION }!!
                    LinkNode(
                        convertInlineNodes(src, linkText.children.dropFirstAndLast()),
                        linkDestination.getTextInNodeWithEscape(src)
                    )
                }
                else -> {
                    TextNode(child.getTextInNodeWithEscape(src))
                }
            }
        }.concatTextNodes()
    }
}

private val List<ASTNode>.types: List<IElementType>
    get() = map { it.type }

private inline fun List<ASTNode>.mapInlineChildrenNotNull(f: (ASTNode) -> InlineNode?): List<InlineNode> {
    val trimmedTypes = setOf(MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.BLOCK_QUOTE)
    var i = 0
    val result = mutableListOf<InlineNode>()
    while (i < size) {
        while (i < size && this[i].type in trimmedTypes) i++
        var j = i
        while (j < size && this[j].type != MarkdownTokenTypes.EOL) j++
        val iEol = j
        j--
        while (j > i && this[j].type in trimmedTypes) j--
        while (i <= j) {
            val mapped = f(this[i])
            if (mapped != null) {
                result.add(mapped)
            }
            i++
        }
        if (iEol < size) {
            result.add(LineBreakNode)
        }
        i = iEol + 1
    }
    return result
}

private fun List<InlineNode>.concatTextNodes(): List<InlineNode> {
    val result = mutableListOf<InlineNode>()
    var i = 0
    while (i < size) {
        val node = this[i]
        if (node is TextNode) {
            var text = node.text
            var j = i + 1
            while (j < size) {
                val otherNode = this[j]
                if (otherNode !is TextNode) break
                text += otherNode.text
                j++
            }
            result.add(TextNode(text))
            i = j
        } else {
            result.add(node)
            i++
        }
    }
    return result
}

private val ASTNode.atxContents: List<ASTNode>
    get() = children.find { it.type == MarkdownTokenTypes.ATX_CONTENT }!!.children

private val symbolPattern = Regex("""\\([!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~])""")
private fun ASTNode.getTextInNodeWithEscape(src: String): String {
    return getTextInNode(src).toString().replace(symbolPattern) { m -> m.groups[1]!!.value }
}

private fun <T> List<T>.dropFirstAndLast(n: Int = 1): List<T> = subList(n, size - n)


class TopLevelNodeWrapper(
    val node: Node,
    val src: String,
    val originalNode: ASTNode,
    private val checkboxNodes: List<ListItemNode> = emptyList()
) {
    fun toggleCheckbox(node: ListItemNode, checked: Boolean): String? {
        val checkboxState = node.checkboxState ?: return null
        val text = if (checked) "[x] " else "[ ] "
        val originalStart = originalNode.startOffset
        val range =
            (checkboxState.originalNode.startOffset - originalStart) until (checkboxState.originalNode.endOffset - originalStart)
        return src.replaceRange(range, text)
    }
}

sealed interface Node
interface LeafNode : Node
interface InlineNode : Node
interface ListNode : Node
interface ListItemNode : Node {
    val checkboxState: CheckboxState?
}

data class CheckboxState(val checked: Boolean, val originalNode: ASTNode) {
    override fun equals(other: Any?): Boolean {
        return other is CheckboxState && other.checked == checked
    }

    override fun hashCode(): Int {
        return checked.hashCode()
    }
}

data class H1Node(val children: List<InlineNode>) : Node
data class H2Node(val children: List<InlineNode>) : Node
data class H3Node(val children: List<InlineNode>) : Node
data class H4Node(val children: List<InlineNode>) : Node
data class H5Node(val children: List<InlineNode>) : Node
data class H6Node(val children: List<InlineNode>) : Node
data class ParagraphNode(val children: List<InlineNode>) : Node
data class UnorderedListNode(val children: List<UnorderedListItemNode>) : Node, ListNode
data class OrderedListNode(val children: List<OrderedListItemNode>) : Node, ListNode
data class CodeBlockNode(val code: String, val lang: String?) : LeafNode
object HorizontalRuleNode : LeafNode
data class BlockQuoteNode(val children: List<Node>) : Node

object LineBreakNode : LeafNode, InlineNode

data class FrontMatterNode(val text: String) : LeafNode, InlineNode

data class UnorderedListItemNode(
    val children: List<Node>, val subList: ListNode? = null,
    override val checkboxState: CheckboxState?
) : ListItemNode

data class OrderedListItemNode(
    val children: List<Node>,
    val subList: ListNode? = null,
    override val checkboxState: CheckboxState?
) : ListItemNode

data class EmphasisNode(val children: List<InlineNode>) : Node, InlineNode
data class StrongEmphasisNode(val children: List<InlineNode>) : Node, InlineNode
data class CodeSpanNode(val text: String) : LeafNode, InlineNode
data class LinkNode(
    val children: List<InlineNode>,
    val link: String,
    val isInternal: Boolean = false
) :
    Node,
    InlineNode

data class ImageNode(val src: String) : LeafNode, InlineNode
data class TextNode(val text: String) : LeafNode, InlineNode