package com.eshant.insightrag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Loads the labelled evaluation cases from {@code eval/dataset.json} on the classpath.
 *
 * <p>Keeping the dataset as a JSON resource (rather than hard-coded Java) means the full set of
 * questions and their ground-truth answers can be read and reviewed at a glance, and extended without
 * recompiling logic — the dataset is data, the harness is code.
 */
public final class EvalDataset {

    private static final String RESOURCE = "eval/dataset.json";

    private EvalDataset() {
    }

    /** Reads and parses every {@link EvalCase} from the bundled dataset resource. */
    public static List<EvalCase> load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, EvalCase.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load eval dataset from " + RESOURCE, e);
        }
    }
}
