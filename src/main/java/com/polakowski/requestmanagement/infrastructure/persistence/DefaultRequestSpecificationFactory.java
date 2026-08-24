package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Builds the predicates: a case insensitive fragment of the name, and an exact state. */
@Component
class DefaultRequestSpecificationFactory implements RequestSpecificationFactory {

    @Override
    public Specification<RequestEntity> matching(RequestSearchCriteria criteria) {
        Specification<RequestEntity> specification = (root, query, builder) -> builder.conjunction();

        if (criteria.nameFragment().isPresent()) {
            specification = specification.and(nameContains(criteria.nameFragment().get()));
        }
        if (criteria.requiredState().isPresent()) {
            specification = specification.and(stateIs(criteria.requiredState().get()));
        }

        return specification;
    }

    private static Specification<RequestEntity> nameContains(String fragment) {
        String pattern = "%" + fragment.toLowerCase(Locale.ROOT) + "%";

        return (root, query, builder) -> builder.like(
                builder.lower(root.get("name")),
                pattern);
    }

    private static Specification<RequestEntity> stateIs(RequestState state) {
        return (root, query, builder) -> builder.equal(root.get("state"), state);
    }
}
