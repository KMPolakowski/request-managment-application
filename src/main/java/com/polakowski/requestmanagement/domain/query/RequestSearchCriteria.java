package com.polakowski.requestmanagement.domain.query;

import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.util.Optional;

/**
 * Filter applied when browsing requests. Every criterion is optional; omitting all of them lists
 * every request.
 *
 * @param name  case insensitive fragment the request name must contain
 * @param state exact state the request must be in
 */
public record RequestSearchCriteria(String name, RequestState state) {

    private static final RequestSearchCriteria NONE = new RequestSearchCriteria(null, null);

    public RequestSearchCriteria {
        name = (name == null || name.isBlank()) ? null : name.trim();
    }

    public static RequestSearchCriteria none() {
        return NONE;
    }

    public static RequestSearchCriteria of(String name, RequestState state) {
        return new RequestSearchCriteria(name, state);
    }

    public Optional<String> nameFragment() {
        return Optional.ofNullable(name);
    }

    public Optional<RequestState> requiredState() {
        return Optional.ofNullable(state);
    }
}
