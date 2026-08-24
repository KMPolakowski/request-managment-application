package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.port.PublicationNumberGenerator;
import com.polakowski.requestmanagement.infrastructure.config.PublicationProperties;
import jakarta.persistence.EntityManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Draws publication numbers from a database sequence.
 *
 * <p>Delegating to the database is what makes the number unique even when several instances of the
 * service publish at the same time. The statement itself is injected as configuration because its
 * syntax differs between vendors.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SequencePublicationNumberGenerator implements PublicationNumberGenerator {

    private final EntityManager entityManager;
    private final PublicationProperties properties;

    @Override
    public long next() {
        Object value = entityManager
                .createNativeQuery(properties.sequenceQuery())
                .getSingleResult();

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalStateException(
                "The publication number sequence returned a non numeric value: " + value);
    }
}
