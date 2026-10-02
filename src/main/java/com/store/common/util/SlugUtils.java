package com.store.common.util;

import java.text.Normalizer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private SlugUtils() {
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        String slug = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");
        return slug.isBlank() ? "item" : slug;
    }

    public static String uniqueSlugify(String input, Predicate<String> existsByCandidate) {
        String base = slugify(input);
        String candidate = base;
        int suffix = 2;
        while (existsByCandidate.test(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
}
