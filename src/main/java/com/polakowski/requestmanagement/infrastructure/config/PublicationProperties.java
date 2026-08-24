package com.polakowski.requestmanagement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the publication number source.
 *
 * <p>The statement is configurable because the syntax for drawing the next value of a sequence is
 * vendor specific; the domain only ever sees the resulting number.
 *
 * @param sequenceQuery native statement returning the next publication number
 */
@ConfigurationProperties(prefix = "request-management.publication")
public record PublicationProperties(String sequenceQuery) {

    public PublicationProperties {
        if (sequenceQuery == null || sequenceQuery.isBlank()) {
            throw new IllegalStateException("request-management.publication.sequence-query must be set");
        }
    }
}
