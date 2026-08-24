package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.port.RequestRepository;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Adapter implementing the {@link RequestRepository} port on top of JPA.
 *
 * <p>It is the only class that knows both the aggregate and the entity, which is what keeps Spring
 * Data out of the domain and the domain out of the database. Both of its helpers arrive through the
 * constructor as interfaces, so this class states what it needs and never reaches for a static.
 */
@Repository
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class JpaRequestRepository implements RequestRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final SpringDataRequestRepository repository;
    private final RequestEntityMapper mapper;
    private final RequestSpecificationFactory specifications;

    @Override
    public Request save(Request request) {
        RequestEntity entity = repository
                .findById(request.id().value())
                .map(existing -> updated(existing, request))
                .orElseGet(() -> mapper.toNewEntity(request));

        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Request> findById(RequestId id) {
        return repository
                .findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<Request> search(RequestSearchCriteria criteria, PageQuery page) {
        PageRequest pageRequest = PageRequest.of(page.page(), page.size(), NEWEST_FIRST);
        Page<RequestEntity> found = repository.findAll(specifications.matching(criteria), pageRequest);

        List<Request> content = found.getContent().stream()
                .map(mapper::toDomain)
                .toList();

        return new PageResult<>(
                content,
                page.page(),
                page.size(),
                found.getTotalElements());
    }

    private RequestEntity updated(RequestEntity existing, Request request) {
        mapper.updateEntity(existing, request);

        return existing;
    }
}
