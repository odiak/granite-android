package net.odiak.granite

/**
 * Classes related to Markdown parser extension.
 *
 * Based on two repositories' code:
 * - https://github.com/karino2/MDTouch/blob/main/app/src/main/java/io/github/karino2/mdtouch/Parser.kt
 * - https://github.com/JetBrains/markdown
 */

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughParser
import org.intellij.markdown.flavours.gfm.lexer._GFMLexer
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.constraints.getCharsEaten
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.sequentialparsers.*
import org.intellij.markdown.parser.sequentialparsers.impl.*
import kotlin.math.min

open class GFMWithWikiLinkFlavourDescriptor : CommonMarkFlavourDescriptor() {
    companion object {
        @JvmField
        val WIKI_LINK: IElementType = MarkdownElementType("WIKI_LINK")
    }

    override fun createInlinesLexer(): MarkdownLexer {
        return MarkdownLexer(_GFMLexer())
    }

    override val sequentialParserManager = object : SequentialParserManager() {
        override fun getParserSequence(): List<SequentialParser> {
            return listOf(
                AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
                BacktickParser(),
                WikiLinkParser(),
                ImageParser(),
                InlineLinkParser(),
                ReferenceLinkParser(),
                StrikeThroughParser(),
                EmphStrongParser()
            )
        }
    }

    override val markerProcessorFactory = MyMarkerProcessor.Factory
}

class WikiLinkParser : SequentialParser {
    private fun parseWikiLink(iterator: TokensCache.Iterator): LocalParsingResult? {
        val startIndex = iterator.index
        var it = iterator
        val delegate = RangesListBuilder()

        assert(it.type == MarkdownTokenTypes.LBRACKET)

        it = it.advance()
        if (it.type != MarkdownTokenTypes.LBRACKET) {
            return null
        }
        it = it.advance()
        while (it.type != null) {
            if (it.type == MarkdownTokenTypes.RBRACKET) {
                it = it.advance()
                if (it.type == MarkdownTokenTypes.RBRACKET) {
                    // success
                    return LocalParsingResult(
                        it,
                        listOf(
                            SequentialParser.Node(
                                startIndex..it.index + 1,
                                GFMWithWikiLinkFlavourDescriptor.WIKI_LINK
                            )
                        ),
                        delegate.get()
                    )
                }
                return null
            }
            delegate.put(it.index)
            it = it.advance()
        }
        return null
    }

    override fun parse(
        tokens: TokensCache,
        rangesToGlue: List<IntRange>
    ): SequentialParser.ParsingResult {
        var result = SequentialParser.ParsingResultBuilder()
        val delegateIndices = RangesListBuilder()
        var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

        while (iterator.type != null) {
            iterator.start
            if (iterator.type == MarkdownTokenTypes.LBRACKET) {
                val wikiLink = parseWikiLink(iterator)
                if (wikiLink != null) {
                    iterator = wikiLink.iteratorPosition.advance()
                    result = result.withOtherParsingResult(wikiLink)
                    continue
                }
            }

            delegateIndices.put(iterator.index)
            iterator = iterator.advance()
        }

        return result.withFurtherProcessing(delegateIndices.get())
    }
}

class MyMarkerProcessor(
    productionHolder: ProductionHolder,
    constraintsBase: CommonMarkdownConstraints
) : CommonMarkMarkerProcessor(productionHolder, constraintsBase) {

    private val markerBlockProviders =
        listOf(FrontMatterProvider()) + super.getMarkerBlockProviders() + listOf(
            GitHubTableMarkerProvider()
        )

    override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> {
        return markerBlockProviders
    }

    override fun populateConstraintsTokens(
        pos: LookaheadText.Position,
        constraints: MarkdownConstraints,
        productionHolder: ProductionHolder
    ) {
        if (constraints !is GFMConstraints || !constraints.hasCheckbox()) {
            super.populateConstraintsTokens(pos, constraints, productionHolder)
            return
        }

        val line = pos.currentLine
        var offset = pos.offsetInCurrentLine
        while (offset < line.length && line[offset] != '[') {
            offset++
        }
        if (offset == line.length) {
            super.populateConstraintsTokens(pos, constraints, productionHolder)
            return
        }

        val type = when (constraints.types.lastOrNull()) {
            '>' ->
                MarkdownTokenTypes.BLOCK_QUOTE
            '.', ')' ->
                MarkdownTokenTypes.LIST_NUMBER
            else ->
                MarkdownTokenTypes.LIST_BULLET
        }
        val middleOffset = pos.offset - pos.offsetInCurrentLine + offset
        val endOffset = min(
            pos.offset - pos.offsetInCurrentLine + constraints.getCharsEaten(pos.currentLine),
            pos.nextLineOrEofOffset
        )

        productionHolder.addProduction(
            listOf(
                SequentialParser.Node(pos.offset..middleOffset, type),
                SequentialParser.Node(middleOffset..endOffset, GFMTokenTypes.CHECK_BOX)
            )
        )
    }

    object Factory : MarkerProcessorFactory {
        override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> {
            return MyMarkerProcessor(productionHolder, GFMConstraints.BASE)
        }
    }
}

class FrontMatterProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
    companion object {
        @JvmField
        val FRONT_MATTER: IElementType = MarkdownElementType("FRONT_MATTER")

        @JvmField
        val FRONT_MATTER_START: IElementType = MarkdownElementType("FRONT_MATTER_START", true)

        @JvmField
        val FRONT_MATTER_CONTENT: IElementType = MarkdownElementType("FRONT_MATTER_CONTENT", true)

        @JvmField
        val FRONT_MATTER_END: IElementType = MarkdownElementType("FRONT_MATTER_END", true)
    }

    override fun createMarkerBlocks(
        pos: LookaheadText.Position,
        productionHolder: ProductionHolder,
        stateInfo: MarkerProcessor.StateInfo
    ): List<MarkerBlock> {
        if (pos.offsetInCurrentLine != 0 || pos.prevLine != null || pos.currentLine != "---") {
            return emptyList()
        }

        productionHolder.addProduction(
            listOf(
                SequentialParser.Node(
                    pos.offset..pos.nextLineOrEofOffset,
                    FRONT_MATTER_START
                )
            )
        )

        return listOf(FrontMatterMarkerBlock(stateInfo.currentConstraints, productionHolder))
    }

    override fun interruptsParagraph(
        pos: LookaheadText.Position,
        constraints: MarkdownConstraints
    ): Boolean {
        return pos.offsetInCurrentLine == 0 && pos.prevLine == null && pos.currentLine == "---"
    }
}

class FrontMatterMarkerBlock(
    myConstraints: MarkdownConstraints,
    private val productionHolder: ProductionHolder
) : MarkerBlockImpl(myConstraints, productionHolder.mark()) {
    override fun allowsSubBlocks(): Boolean = false

    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean =
        true

    private var realInterestingOffset = 0

    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int {
        return pos.nextLineOrEofOffset
    }

    override fun getDefaultAction(): MarkerBlock.ClosingAction {
        return MarkerBlock.ClosingAction.DONE
    }

    override fun doProcessToken(
        pos: LookaheadText.Position,
        currentConstraints: MarkdownConstraints
    ): MarkerBlock.ProcessingResult {
        if (pos.offset < realInterestingOffset) {
            return MarkerBlock.ProcessingResult.CANCEL
        }

        if (pos.offsetInCurrentLine != -1) {
            return MarkerBlock.ProcessingResult.CANCEL
        }

        val nextLineOffset = pos.nextLineOrEofOffset
        realInterestingOffset = nextLineOffset

        if (pos.currentLine == "---") {
            productionHolder.addProduction(
                listOf(
                    SequentialParser.Node(
                        pos.offset + 1..pos.nextLineOrEofOffset,
                        FrontMatterProvider.FRONT_MATTER_END
                    )
                )
            )
            scheduleProcessingResult(nextLineOffset, MarkerBlock.ProcessingResult.DEFAULT)
        } else {
            productionHolder.addProduction(
                listOf(
                    SequentialParser.Node(
                        pos.offset + 1..pos.nextLineOrEofOffset,
                        FrontMatterProvider.FRONT_MATTER_CONTENT
                    )
                )
            )
        }

        return MarkerBlock.ProcessingResult.CANCEL
    }

    override fun getDefaultNodeType(): IElementType {
        return FrontMatterProvider.FRONT_MATTER
    }
}