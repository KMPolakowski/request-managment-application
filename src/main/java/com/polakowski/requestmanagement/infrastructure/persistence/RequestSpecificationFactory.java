package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns the domain search criteria into something JPA understands.
 *
 * <p>Behind an interface for the same reason as the mapper: the repository asks a collaborator for
 * a predicate, it does not build one itself.
 */
interface RequestSpecificationFactory {

    Specification<RequestEntity> matching(RequestSearchCriteria criteria);
}
