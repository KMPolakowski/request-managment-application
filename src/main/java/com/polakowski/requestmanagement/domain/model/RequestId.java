package com.polakowski.requestmanagement.domain.model;

import com.polakowski.requestmanagement.domain.port.RequestIdGenerator;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a request.
 *
 * <p>Requests are identified by a UUID from the moment they are created. The publication number is
 * deliberately a different concept: it is a business identifier handed out only on publication.
 *
 * <p>There is no factory minting a fresh identifier here on purpose; that is the job of a
 * {@link RequestIdGenerator}, so the choice of where identity comes from stays injectable.
 */
public record RequestId(UUID value) {

    public RequestId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RequestId of(UUID value) {
        return new RequestId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
