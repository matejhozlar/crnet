package com.saunhardy.crnet.util;

/**
 * URL manipulation helpers.
 */
public final class UrlUtils {

    private UrlUtils() {}

    /**
     * Joins a base URL and a relative path, ensuring exactly one {@code /} between them.
     *
     * @param base the base URL (e.g. {@code "http://localhost:5001"})
     * @param path the relative path (e.g. {@code "/presence/update"})
     * @return the joined URL string
     */
    public static String safeJoin(String base, String path) {
        if (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty()) {
            return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        }
        if (!base.endsWith("/")) base += "/";
        return base + path;
    }
}
