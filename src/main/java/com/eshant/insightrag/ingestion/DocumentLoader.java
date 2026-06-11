package com.eshant.insightrag.ingestion;

import com.eshant.insightrag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Loads markdown/text documents from the configured data directory. */
@Component
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private final RagProperties props;

    public DocumentLoader(RagProperties props) {
        this.props = props;
    }

    /** Reads every {@code .md}/{@code .markdown}/{@code .txt} file under the data directory. */
    public List<RawDocument> loadAll() {
        Path dir = Path.of(props.getIngestion().getDataDir());
        if (!Files.isDirectory(dir)) {
            log.warn("Data directory '{}' does not exist — no documents loaded", dir.toAbsolutePath());
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            List<RawDocument> docs = files
                    .filter(Files::isRegularFile)
                    .filter(DocumentLoader::isTextDoc)
                    .sorted(Comparator.naturalOrder())
                    .map(this::read)
                    .toList();
            log.info("Loaded {} document(s) from {}", docs.size(), dir.toAbsolutePath());
            return docs;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan data directory " + dir, e);
        }
    }

    private static boolean isTextDoc(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt");
    }

    private RawDocument read(Path p) {
        try {
            return new RawDocument(p.getFileName().toString(), Files.readString(p));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + p, e);
        }
    }
}
