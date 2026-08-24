package com.polakowski.requestmanagement.testsupport;

import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.port.RequestRepository;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Hand written test double for the repository port.
 *
 * <p>It stores snapshots rather than aggregates, so a stored request can never be mutated through a
 * reference the caller kept - exactly like a real database round trip. Having the port makes this
 * possible at all, which is the point of keeping persistence behind an interface.
 */
public class InMemoryRequestRepository implements RequestRepository {

    private final Map<RequestId, Request> stored = new LinkedHashMap<>();

    @Override
    public Request save(Request request) {
        stored.put(request.id(), Request.fromSnapshot(request.toSnapshot()));
        return copyOf(request);
    }

    @Override
    public Optional<Request> findById(RequestId id) {
        return Optional.ofNullable(stored.get(id)).map(InMemoryRequestRepository::copyOf);
    }

    @Override
    public PageResult<Request> search(RequestSearchCriteria criteria, PageQuery page) {
        List<Request> matching = stored.values().stream()
                .filter(request -> matchesName(request, criteria))
                .filter(request -> matchesState(request, criteria))
                .sorted(Comparator.comparing(Request::createdAt).reversed())
                .toList();
        int from = Math.min(page.page() * page.size(), matching.size());
        int to = Math.min(from + page.size(), matching.size());
        List<Request> content = matching.subList(from, to).stream()
                .map(InMemoryRequestRepository::copyOf)
                .toList();
        return new PageResult<>(content, page.page(), page.size(), matching.size());
    }

    public int size() {
        return stored.size();
    }

    private static boolean matchesName(Request request, RequestSearchCriteria criteria) {
        return criteria.nameFragment()
                .map(fragment -> request.name().toLowerCase(Locale.ROOT)
                        .contains(fragment.toLowerCase(Locale.ROOT)))
                .orElse(true);
    }

    private static boolean matchesState(Request request, RequestSearchCriteria criteria) {
        return criteria.requiredState().map(state -> request.state() == state).orElse(true);
    }

    private static Request copyOf(Request request) {
        return Request.fromSnapshot(request.toSnapshot());
    }
}
