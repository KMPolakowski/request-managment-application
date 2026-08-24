package com.polakowski.requestmanagement.domain.port;

/**
 * Outbound port handing out the unique numeric identifier a request receives on publication.
 *
 * <p>Implementations must guarantee that a number is never handed out twice, including under
 * concurrent publication of different requests.
 */
public interface PublicationNumberGenerator {

    long next();
}
