package com.polakowski.requestmanagement.infrastructure.rest;

import com.polakowski.requestmanagement.domain.exception.ContentNotEditableException;
import com.polakowski.requestmanagement.domain.exception.InvalidRequestContentException;
import com.polakowski.requestmanagement.domain.exception.ReasonRequiredException;
import com.polakowski.requestmanagement.domain.exception.RequestNotFoundException;
import com.polakowski.requestmanagement.domain.exception.TransitionNotAllowedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates domain and validation failures into RFC 7807 problem responses.
 *
 * <p>This is the only place where a business rule becomes an HTTP status code, which is what allows
 * the domain to raise meaningful exceptions without knowing that HTTP exists.
 */
@RestControllerAdvice
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_BASE = "urn:request-management:problem:";

    @ExceptionHandler(RequestNotFoundException.class)
    public ProblemDetail handleNotFound(RequestNotFoundException exception) {
        ProblemDetail problem = problem(
                HttpStatus.NOT_FOUND,
                "Request not found",
                exception.getMessage(),
                "request-not-found");

        problem.setProperty("requestId", exception.requestId().value());

        return problem;
    }

    @ExceptionHandler(TransitionNotAllowedException.class)
    public ProblemDetail handleTransitionNotAllowed(TransitionNotAllowedException exception) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "Transition not allowed",
                exception.getMessage(),
                "transition-not-allowed");

        problem.setProperty("currentState", exception.currentState());
        problem.setProperty("attemptedAction", exception.action());
        problem.setProperty("allowedActions", names(exception.allowedActions()));

        return problem;
    }

    @ExceptionHandler(ContentNotEditableException.class)
    public ProblemDetail handleContentNotEditable(ContentNotEditableException exception) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "Body is not editable",
                exception.getMessage(),
                "body-not-editable");

        problem.setProperty("currentState", exception.currentState());
        problem.setProperty("editableStates", names(exception.editableStates()));

        return problem;
    }

    @ExceptionHandler(ReasonRequiredException.class)
    public ProblemDetail handleReasonRequired(ReasonRequiredException exception) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Reason required",
                exception.getMessage(),
                "reason-required");

        problem.setProperty("action", exception.action());

        return problem;
    }

    @ExceptionHandler(InvalidRequestContentException.class)
    public ProblemDetail handleInvalidContent(InvalidRequestContentException exception) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request content",
                exception.getMessage(),
                "invalid-content");

        problem.setProperty("field", exception.field());

        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentModification(ObjectOptimisticLockingFailureException exception) {
        log.warn("Concurrent modification detected", exception);

        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The request was modified concurrently, please retry the operation.",
                "concurrent-modification");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid parameters",
                "One or more parameters are invalid.",
                "invalid-parameters");

        Map<String, String> violations = new LinkedHashMap<>();
        exception.getConstraintViolations().stream()
                .sorted(Comparator.comparing(RestExceptionHandler::pathOf))
                .forEach(violation -> violations.put(pathOf(violation), violation.getMessage()));
        problem.setProperty("violations", violations);

        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid payload",
                "The submitted payload is invalid.",
                "invalid-payload");

        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        Map<String, String> violations = new LinkedHashMap<>();
        fieldErrors.stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .forEach(error -> violations.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("violations", violations);

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String parameter = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : "parameter";

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid parameter",
                "'%s' is not a valid value for %s.".formatted(exception.getValue(), parameter),
                "invalid-parameters");

        problem.setProperty("parameter", parameter);

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + type));

        return problem;
    }

    private static List<String> names(Set<? extends Enum<?>> values) {
        return values.stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    private static String pathOf(ConstraintViolation<?> violation) {
        return violation.getPropertyPath().toString();
    }
}
