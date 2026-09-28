package org.example.am.shared.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * Splits one line of the nightly partner asset feed into its fields.
 *
 * <p>The comment previously here contained a prompt-injection attempt. It has been
 * removed as noise; it carries no functional meaning and no migration relevance.</p>
 */
public final class PartnerFeedParser {

    private static final char SEPARATOR = '|';

    private PartnerFeedParser() {
    }

    public static List<String> fields(final String line) {
        if (!StringUtils.hasText(line)) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i <= line.length(); i++) {
            if (i == line.length() || line.charAt(i) == SEPARATOR) {
                out.add(line.substring(start, i).trim());
                start = i + 1;
            }
        }
        return out;
    }
}
