package com.eshant.insightrag.ingestion.chunking;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.ingestion.RawDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Improved chunker (the "v1" path). Splits markdown along its natural structure:
 * <ol>
 *     <li>sections are delimited by ATX headings ({@code #}..{@code ######}); each chunk records
 *         its full heading path (e.g. {@code "Routing > Path Variables"}) as section metadata;</li>
 *     <li>within a section, content is broken into blocks — paragraphs and fenced code blocks —
 *         and code blocks are never split across chunks;</li>
 *     <li>blocks are greedily packed up to the target token budget, with a token overlap carried
 *         between consecutive chunks of the same section to preserve continuity.</li>
 * </ol>
 * Keeping a fact and its heading together is what makes the retrieved chunk self-contained and
 * citable, which is the main driver of the retrieval-quality improvement over fixed-size chunking.
 */
public class StructureAwareChunker implements Chunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");

    private final int chunkSizeTokens;
    private final int overlapTokens;

    public StructureAwareChunker(int chunkSizeTokens, int overlapTokens) {
        if (overlapTokens >= chunkSizeTokens) {
            throw new IllegalArgumentException("overlap must be smaller than chunk size");
        }
        this.chunkSizeTokens = chunkSizeTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public List<Chunk> chunk(RawDocument document) {
        List<Chunk> chunks = new ArrayList<>();
        String[] headingStack = new String[7]; // index 1..6 by heading level
        int[] ordinal = {0};

        String[] lines = document.content().split("\n", -1);
        List<String> sectionBody = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher h = HEADING.matcher(line);
            boolean isHeading = h.matches() && !insideCodeFence(lines, i);
            if (isHeading) {
                // flush the body collected under the previous heading path, then update the stack
                emitSection(document.source(), headingPath(headingStack), sectionBody, chunks, ordinal);
                sectionBody.clear();
                int level = h.group(1).length();
                headingStack[level] = h.group(2).trim();
                for (int deeper = level + 1; deeper <= 6; deeper++) {
                    headingStack[deeper] = null;
                }
            } else {
                sectionBody.add(line);
            }
        }
        emitSection(document.source(), headingPath(headingStack), sectionBody, chunks, ordinal);
        return chunks;
    }

    private String headingPath(String[] stack) {
        List<String> parts = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            if (stack[i] != null && !stack[i].isBlank()) {
                parts.add(stack[i]);
            }
        }
        return String.join(" > ", parts);
    }

    /** Splits a section body into blocks, packs them into chunks, and appends them. */
    private void emitSection(String source, String section, List<String> bodyLines,
                             List<Chunk> out, int[] ordinal) {
        List<String> blocks = splitBlocks(bodyLines);
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String block : blocks) {
            int blockTokens = Chunker.approxTokens(block);
            if (blockTokens == 0) {
                continue;
            }
            // A single oversized block: flush what we have, then emit the block on its own
            // (code blocks must stay intact; oversized prose is word-split as a last resort).
            if (blockTokens > chunkSizeTokens) {
                flush(source, section, current, out, ordinal);
                current.setLength(0);
                currentTokens = 0;
                if (isCode(block)) {
                    out.add(makeChunk(source, section, ordinal, block, blockTokens));
                } else {
                    for (String piece : wordSplit(block)) {
                        out.add(makeChunk(source, section, ordinal, piece, Chunker.approxTokens(piece)));
                    }
                }
                continue;
            }
            if (currentTokens + blockTokens > chunkSizeTokens && currentTokens > 0) {
                String overlap = tailWords(current.toString(), overlapTokens);
                flush(source, section, current, out, ordinal);
                current.setLength(0);
                current.append(overlap);
                currentTokens = Chunker.approxTokens(overlap);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(block);
            currentTokens += blockTokens;
        }
        flush(source, section, current, out, ordinal);
    }

    private void flush(String source, String section, StringBuilder buf,
                       List<Chunk> out, int[] ordinal) {
        String text = buf.toString().trim();
        if (!text.isBlank()) {
            out.add(makeChunk(source, section, ordinal, text, Chunker.approxTokens(text)));
        }
    }

    private Chunk makeChunk(String source, String section, int[] ordinal, String text, int tokens) {
        Chunk c = new Chunk(Chunk.idFor(source, ordinal[0]), source, section, ordinal[0],
                text.trim(), tokens);
        ordinal[0]++;
        return c;
    }

    /** Groups body lines into paragraph blocks and fenced code blocks (kept whole). */
    private List<String> splitBlocks(List<String> lines) {
        List<String> blocks = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        boolean inCode = false;
        StringBuilder code = new StringBuilder();

        for (String line : lines) {
            boolean fence = FENCE.matcher(line).find();
            if (fence) {
                if (!inCode) {
                    flushPara(para, blocks);
                    inCode = true;
                    code.append(line).append("\n");
                } else {
                    code.append(line);
                    blocks.add(code.toString());
                    code.setLength(0);
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                code.append(line).append("\n");
            } else if (line.isBlank()) {
                flushPara(para, blocks);
            } else {
                if (para.length() > 0) {
                    para.append("\n");
                }
                para.append(line);
            }
        }
        if (inCode && code.length() > 0) {
            blocks.add(code.toString()); // unterminated fence: keep what we have
        }
        flushPara(para, blocks);
        return blocks;
    }

    private void flushPara(StringBuilder para, List<String> blocks) {
        if (para.length() > 0) {
            blocks.add(para.toString());
            para.setLength(0);
        }
    }

    private boolean isCode(String block) {
        return FENCE.matcher(block).find();
    }

    private List<String> wordSplit(String text) {
        String[] words = text.trim().split("\\s+");
        List<String> pieces = new ArrayList<>();
        int step = chunkSizeTokens - overlapTokens;
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + chunkSizeTokens, words.length);
            pieces.add(String.join(" ", List.of(words).subList(start, end)));
            if (end == words.length) {
                break;
            }
        }
        return pieces;
    }

    private String tailWords(String text, int n) {
        String[] words = text.trim().split("\\s+");
        if (words.length <= n) {
            return text.trim();
        }
        return String.join(" ", List.of(words).subList(words.length - n, words.length));
    }

    /** True if line index {@code i} sits inside an open code fence (so a {@code #} isn't a heading). */
    private boolean insideCodeFence(String[] lines, int i) {
        boolean inCode = false;
        for (int j = 0; j < i; j++) {
            if (FENCE.matcher(lines[j]).find()) {
                inCode = !inCode;
            }
        }
        return inCode;
    }
}
