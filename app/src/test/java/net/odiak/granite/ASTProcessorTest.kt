package net.odiak.granite

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Test
import org.junit.Assert.*

private val dummyASTNode = object : ASTNode {
    override val children: List<ASTNode> = emptyList()
    override val parent: ASTNode? = null
    override val type: IElementType = MarkdownTokenTypes.TEXT
    override val startOffset: Int = 0
    override val endOffset: Int = 0
}

class ASTProcessorTest {
    private fun parse(src: String): List<TopLevelNodeWrapper> {
        val parser = MarkdownParser(GFMWithWikiLinkFlavourDescriptor())
        val astNode = parser.buildMarkdownTreeFromString(src)
        return ASTProcessor.convertTopLevelASTNodeToNodeWrappers(src, astNode)
    }

    @Test
    fun simple() {
        val nodeWrappers = parse(
            """
            # this is h1
            
            hello world
            
            ## this is h2
            """.trimIndent()
        )


        val (h1, p, h2) = nodeWrappers.map { it.node }
        assertEquals(
            H1Node(listOf(TextNode("this is h1"))),
            h1
        )
        assertEquals(
            ParagraphNode(listOf(TextNode("hello world"))),
            p
        )
        assertEquals(
            H2Node(listOf(TextNode("this is h2"))),
            h2
        )
    }

    @Test
    fun paragraph() {
        val nodeWrappers = parse(
            """
            hello world
            
            I'm a student of
             XYZ University.
            """.trimIndent()
        )

        val (p1, p2) = nodeWrappers.map { it.node }
        assertEquals(
            ParagraphNode(listOf(TextNode("hello world"))),
            p1
        )
        assertEquals(
            ParagraphNode(
                listOf(
                    TextNode("I'm a student of"),
                    LineBreakNode,
                    TextNode("XYZ University.")
                )
            ),
            p2
        )
    }

    @Test
    fun blockquote() {
        val (bq) = parse(
            """
            > aaa bbb ccc
            > dddd ee fffff
            >kkk
            >
            > xxx yyy zzz
            """.trimIndent()
        ).map { it.node }

        assertEquals(
            BlockQuoteNode(
                listOf(
                    ParagraphNode(
                        listOf(
                            TextNode("aaa bbb ccc"),
                            LineBreakNode,
                            TextNode("dddd ee fffff"),
                            LineBreakNode,
                            TextNode("kkk")
                        )
                    ),
                    ParagraphNode(
                        listOf(
                            TextNode("xxx yyy zzz")
                        )
                    )
                )
            ),
            bq
        )
    }

    @Test
    fun `blockquote nested`() {
        val (bq) = parse(
            """
            > aaa bbb ccc
            > ddd eee fff
            > > world touch help cheese
            > > chocolate
            """.trimIndent()
        ).map { it.node }

        assertTrue(bq is BlockQuoteNode)

        val (p, bqChild) = (bq as BlockQuoteNode).children

        assertEquals(
            ParagraphNode(
                listOf(
                    TextNode("aaa bbb ccc"),
                    LineBreakNode,
                    TextNode("ddd eee fff")
                )
            ),
            p
        )
        assertEquals(
            BlockQuoteNode(
                listOf(
                    ParagraphNode(
                        listOf(
                            TextNode("world touch help cheese"),
                            LineBreakNode,
                            TextNode("chocolate")
                        )
                    )
                )
            ),
            bqChild
        )
    }

    @Test
    fun `blockquote decorated`() {
        val (bq) = parse(
            """
            > # heading *one*
            > aaa *bbb* **ccc**
            > ddd [eee fff ](https://example.com )
            """.trimIndent()
        ).map { it.node }

        val (h, p) = (bq as BlockQuoteNode).children

        assertEquals(
            H1Node(listOf(TextNode("heading "), EmphasisNode(listOf(TextNode("one"))))),
            h
        )
        assertEquals(
            ParagraphNode(
                listOf(
                    TextNode("aaa "),
                    EmphasisNode(listOf(TextNode("bbb"))),
                    TextNode(" "),
                    StrongEmphasisNode(listOf(TextNode("ccc"))),
                    LineBreakNode,
                    TextNode("ddd "),
                    LinkNode(listOf(TextNode("eee fff")), "https://example.com")
                )
            ),
            p
        )
    }
}