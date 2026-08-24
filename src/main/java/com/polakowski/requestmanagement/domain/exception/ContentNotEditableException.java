package com.polakowski.requestmanagement.domain.exception;

import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Raised when the body of a request is modified in a state that does not allow editing. */
@Getter
@Accessors(fluent = true)
public class ContentNotEditableException extends DomainException {

    private final RequestState currentState;
    private final Set<RequestState> editableStates;

    public ContentNotEditableException(RequestState currentState, Set<RequestState> editableStates) {
        super("The body of a request cannot be modified while it is %s; it is editable in %s".formatted(
                currentState,
                sorted(editableStates)));
        this.currentState = currentState;
        this.editableStates = Set.copyOf(editableStates);
    }

    private static List<String> sorted(Set<RequestState> states) {
        return states.stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }
}
