package com.polakowski.requestmanagement.infrastructure.identity;

import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.port.RequestIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Identifies requests with a random UUID.
 *
 * <p>Trivial, and that is the point: because the application depends on the
 * {@link RequestIdGenerator} port rather than on {@code UUID.randomUUID()}, swapping this for a
 * sequential or externally allocated identity is a matter of publishing another bean, and tests can
 * hand the service predictable identifiers.
 */
@Component
public class UuidRequestIdGenerator implements RequestIdGenerator {

    @Override
    public RequestId next() {
        return RequestId.of(UUID.randomUUID());
    }
}
