package com.polakowski.requestmanagement.infrastructure.persistence;

import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.model.RequestSnapshot;
import com.polakowski.requestmanagement.domain.model.StateChange;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The mapping itself.
 *
 * <p>Audit entries are only ever appended: an update copies the mutable columns and adds the
 * history entries the row does not have yet, so a stored transition can never be rewritten.
 */
@Component
class DefaultRequestEntityMapper implements RequestEntityMapper {

    @Override
    public RequestEntity toNewEntity(Request request) {
        RequestSnapshot snapshot = request.toSnapshot();
        RequestEntity entity = new RequestEntity(snapshot.id().value());

        entity.setCreatedAt(snapshot.createdAt());
        copyMutableState(snapshot, entity);
        appendMissingHistory(snapshot, entity);

        return entity;
    }

    @Override
    public void updateEntity(RequestEntity entity, Request request) {
        RequestSnapshot snapshot = request.toSnapshot();

        copyMutableState(snapshot, entity);
        appendMissingHistory(snapshot, entity);
    }

    @Override
    public Request toDomain(RequestEntity entity) {
        List<StateChange> history = entity.getHistory().stream()
                .sorted(Comparator.comparingInt(StateChangeEntity::getSequenceNumber))
                .map(DefaultRequestEntityMapper::toDomain)
                .toList();

        RequestSnapshot snapshot = new RequestSnapshot(
                RequestId.of(entity.getId()),
                entity.getName(),
                entity.getBody(),
                entity.getState(),
                entity.getPublicationNumber(),
                entity.getCreatedAt(),
                entity.getLastModifiedAt(),
                history);

        return Request.fromSnapshot(snapshot);
    }

    private static StateChange toDomain(StateChangeEntity entity) {
        return new StateChange(
                entity.getSequenceNumber(),
                entity.getFromState(),
                entity.getToState(),
                entity.getAction(),
                entity.getReason(),
                entity.getOccurredAt());
    }

    private static void copyMutableState(RequestSnapshot snapshot, RequestEntity entity) {
        entity.setName(snapshot.name());
        entity.setBody(snapshot.body());
        entity.setState(snapshot.state());
        entity.setPublicationNumber(snapshot.publicationNumber());
        entity.setLastModifiedAt(snapshot.lastModifiedAt());
    }

    private static void appendMissingHistory(RequestSnapshot snapshot, RequestEntity entity) {
        int alreadyStored = entity.getHistory().size();

        snapshot.history().stream()
                .filter(change -> change.sequenceNumber() > alreadyStored)
                .map(DefaultRequestEntityMapper::toEntity)
                .forEach(entity::addStateChange);
    }

    private static StateChangeEntity toEntity(StateChange change) {
        return new StateChangeEntity(
                change.sequenceNumber(),
                change.from(),
                change.to(),
                change.action(),
                change.reason(),
                change.occurredAt());
    }
}
