package com.eshant.insightrag.ingestion.chunking;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.ingestion.RawDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the two chunking strategies behind the v0/v1 split: the naive fixed-size window and the
 * structure-aware splitter. The structure-aware behaviour (heading paths, intact code blocks) is the
 * main reason v1 out-retrieves v0, so it's worth pinning down.
 */
class ChunkingTest {

    @Test
    void fixedSizeWindowsWithOverlapAndNoSection() {
        String content = "w0 w1 w2 w3 w4 w5 w6 w7 w8 w9 w10 w11 w12 w13";
        FixedSizeChunker chunker = new FixedSizeChunker(10, 2); // step = 8

        List<Chunk> chunks = chunker.chunk(new RawDocument("doc.md", content));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("w0 w1 w2 w3 w4 w5 w6 w7 w8 w9");
        // chunk 1 starts at index 8, so its first two words are chunk 0's last two — the overlap.
        assertThat(chunks.get(1).text()).startsWith("w8 w9");
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.source()).isEqualTo("doc.md");
            assertThat(c.section()).isEmpty();        // fixed-size is structure-blind
        });
        assertThat(chunks.get(0).id()).isEqualTo("doc.md#0");
    }

    @Test
    void fixedSizeRejectsOverlapNotSmallerThanChunkSize() {
        assertThatThrownBy(() -> new FixedSizeChunker(10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structureAwareCapturesHeadingPath() {
        String content = String.join("\n",
                "# Routing",
                "",
                "Routing maps URLs to handlers.",
                "",
                "## Path Variables",
                "",
                "Path variables use the {name} syntax.");
        StructureAwareChunker chunker = new StructureAwareChunker(500, 80);

        List<Chunk> chunks = chunker.chunk(new RawDocument("nimbus-routing.md", content));

        Chunk pathVar = chunks.stream()
                .filter(c -> c.text().contains("{name}"))
                .findFirst().orElseThrow();
        assertThat(pathVar.section()).isEqualTo("Routing > Path Variables");
        assertThat(chunks).allMatch(c -> c.source().equals("nimbus-routing.md"));
    }

    @Test
    void structureAwareKeepsCodeBlockIntactAndIgnoresHashInsideFence() {
        String content = String.join("\n",
                "# Example",
                "",
                "Here is some code:",
                "",
                "```java",
                "// configure the route",
                "# this hash is inside code, not a heading",
                "route(\"/x\");",
                "```",
                "",
                "Done.");
        StructureAwareChunker chunker = new StructureAwareChunker(500, 80);

        List<Chunk> chunks = chunker.chunk(new RawDocument("doc.md", content));

        // The '#' line inside the fence must not have started a new section.
        assertThat(chunks).noneMatch(c -> c.section().contains("this hash is inside code"));
        // The fenced block survives as one contiguous piece of text.
        Chunk withCode = chunks.stream()
                .filter(c -> c.text().contains("route(\"/x\");"))
                .findFirst().orElseThrow();
        assertThat(withCode.text()).contains("// configure the route");
    }
}
