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
        if (!base.endsWith("/")) base += "/";
        if (path.startsWith("/")) path = path.substring(1);
        return base + path;
    }
}
