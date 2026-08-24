package com.polakowski.requestmanagement.domain.port;

import com.polakowski.requestmanagement.domain.model.RequestId;

/**
 * Outbound port handing out the identity of a new request.
 *
 * <p>The application asks for an identifier instead of manufacturing one, so that where identifiers
 * come from stays a decision of the outside world: random today, a client supplied idempotency key
 * or a broker allocated identity tomorrow, and something predictable in tests.
 */
@FunctionalInterface
public interface RequestIdGenerator {

    RequestId next();
}
