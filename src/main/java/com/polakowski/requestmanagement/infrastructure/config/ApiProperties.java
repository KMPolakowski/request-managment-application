package com.polakowski.requestmanagement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning of the browsing endpoint.
 *
 * @param defaultPageSize page size applied when the caller does not ask for one
 * @param maxPageSize     upper bound protecting the service from unbounded reads
 */
@ConfigurationProperties(prefix = "request-management.api")
public record ApiProperties(int defaultPageSize, int maxPageSize) {

    public ApiProperties {
        if (defaultPageSize < 1) {
            throw new IllegalStateException("request-management.api.default-page-size must be positive");
        }
        if (maxPageSize < defaultPageSize) {
            throw new IllegalStateException(
                    "request-management.api.max-page-size must not be smaller than the default page size");
        }
    }

    /** Applies the configured default and cap to a page size asked for by a caller. */
    public int resolvePageSize(Integer requestedSize) {
        if (requestedSize == null || requestedSize < 1) {
            return defaultPageSize;
        }
        return Math.min(requestedSize, maxPageSize);
    }
}
