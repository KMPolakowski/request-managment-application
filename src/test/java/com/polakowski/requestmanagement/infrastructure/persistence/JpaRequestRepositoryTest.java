package com.polakowski.requestmanagement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polakowski.requestmanagement.domain.model.Request;
import com.polakowski.requestmanagement.domain.model.RequestId;
import com.polakowski.requestmanagement.domain.model.StateChange;
import com.polakowski.requestmanagement.domain.port.PublicationNumberGenerator;
import com.polakowski.requestmanagement.domain.port.RequestRepository;
import com.polakowski.requestmanagement.domain.query.PageQuery;
import com.polakowski.requestmanagement.domain.query.PageResult;
import com.polakowski.requestmanagement.domain.query.RequestSearchCriteria;
import com.polakowski.requestmanagement.domain.workflow.RequestAction;
import com.polakowski.requestmanagement.domain.workflow.RequestState;
import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import com.polakowski.requestmanagement.domain.workflow.WorkflowDefinition;
import com.polakowski.requestmanagement.domain.port.RequestIdGenerator;
import com.polakowski.requestmanagement.infrastructure.config.PublicationProperties;
import com.polakowski.requestmanagement.testsupport.SequentialRequestIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
        JpaRequestRepository.class,
        DefaultRequestEntityMapper.class,
        DefaultRequestSpecificationFactory.class,
        SequencePublicationNumberGenerator.class,
        JpaRequestRepositoryTest.PublicationTestConfiguration.class})
@DisplayName("The JPA request repository")
class JpaRequestRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T08:30:00Z");

    @org.springframework.boot.test.context.TestConfiguration
    static class PublicationTestConfiguration {

        @org.springframework.context.annotation.Bean
        PublicationProperties publicationProperties() {
            return new PublicationProperties("SELECT NEXT VALUE FOR publication_number_seq");
        }
    }

    private final RequestWorkflow workflow = new RequestWorkflow(WorkflowDefinition.fromStateDiagram());
    private final RequestIdGenerator requestIds = new SequentialRequestIdGenerator();

    @Autowired
    private RequestRepository repository;

    @Autowired
    private PublicationNumberGenerator publicationNumbers;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("stores a request and reads it back unchanged")
    void storesAndReadsBackARequest() {
        Request stored = repository.save(newRequest("Liquidity coverage ratio", NOW));

        Request found = reload(stored.id());

        assertThat(found.name()).isEqualTo("Liquidity coverage ratio");
        assertThat(found.body()).isEqualTo("Draft content");
        assertThat(found.state()).isEqualTo(RequestState.CREATED);
        assertThat(found.publicationNumber()).isEmpty();
        assertThat(found.createdAt()).isEqualTo(NOW);
        assertThat(found.history()).singleElement()
                .satisfies(change -> assertThat(change.to()).isEqualTo(RequestState.CREATED));
    }

    @Test
    @DisplayName("appends audit entries instead of rewriting them")
    void appendsAuditEntries() {
        Request request = repository.save(newRequest("Liquidity coverage ratio", NOW));
        request.apply(RequestAction.VERIFY, null, workflow, NOW, failOnPublication());
        repository.save(request);
        request.apply(RequestAction.REJECT, "Evidence missing", workflow, NOW, failOnPublication());
        repository.save(request);

        Request found = reload(request.id());

        assertThat(found.state()).isEqualTo(RequestState.REJECTED);
        assertThat(found.history()).extracting(StateChange::sequenceNumber).containsExactly(1, 2, 3);
        assertThat(found.history()).extracting(StateChange::to).containsExactly(
                RequestState.CREATED, RequestState.VERIFIED, RequestState.REJECTED);
        assertThat(found.history()).last()
                .satisfies(change -> assertThat(change.reason()).isEqualTo("Evidence missing"));
    }

    @Test
    @DisplayName("stores the publication number handed out by the database sequence")
    void storesThePublicationNumber() {
        Request request = repository.save(newRequest("Liquidity coverage ratio", NOW));
        request.apply(RequestAction.VERIFY, null, workflow, NOW, failOnPublication());
        request.apply(RequestAction.ACCEPT, null, workflow, NOW, failOnPublication());
        request.apply(RequestAction.PUBLISH, null, workflow, NOW, publicationNumbers::next);
        repository.save(request);

        Request found = reload(request.id());

        assertThat(found.state()).isEqualTo(RequestState.PUBLISHED);
        assertThat(found.publicationNumber()).isPresent();
    }

    @Test
    @DisplayName("hands out a different number on every call of the sequence")
    void handsOutDistinctPublicationNumbers() {
        long first = publicationNumbers.next();
        long second = publicationNumbers.next();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("refuses to store the same publication number twice")
    void refusesADuplicatePublicationNumber() {
        publishWithNumber("First", 4242L);

        assertThatThrownBy(() -> publishWithNumber("Second", 4242L))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("finds nothing for an unknown identifier")
    void findsNothingForAnUnknownIdentifier() {
        assertThat(repository.findById(requestIds.next())).isEmpty();
    }

    @Test
    @DisplayName("lists the most recently created requests first")
    void listsTheMostRecentFirst() {
        repository.save(newRequest("Older", NOW.minus(1, ChronoUnit.HOURS)));
        repository.save(newRequest("Newer", NOW));

        PageResult<Request> page = repository.search(RequestSearchCriteria.none(), PageQuery.of(0, 10));

        assertThat(page.content()).extracting(Request::name).containsExactly("Newer", "Older");
    }

    @Test
    @DisplayName("filters on a fragment of the name, whatever its case")
    void filtersOnANameFragment() {
        repository.save(newRequest("Liquidity coverage ratio", NOW));
        repository.save(newRequest("Net stable funding ratio", NOW));

        PageResult<Request> page = repository.search(
                RequestSearchCriteria.of("LIQUIDITY", null), PageQuery.of(0, 10));

        assertThat(page.content()).extracting(Request::name).containsExactly("Liquidity coverage ratio");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("filters on the state")
    void filtersOnTheState() {
        repository.save(newRequest("Still a draft", NOW));
        Request verified = newRequest("Checked", NOW);
        verified.apply(RequestAction.VERIFY, null, workflow, NOW, failOnPublication());
        repository.save(verified);

        PageResult<Request> page = repository.search(
                RequestSearchCriteria.of(null, RequestState.VERIFIED), PageQuery.of(0, 10));

        assertThat(page.content()).extracting(Request::name).containsExactly("Checked");
    }

    @Test
    @DisplayName("combines both filters")
    void combinesBothFilters() {
        Request matching = newRequest("Liquidity coverage ratio", NOW);
        matching.apply(RequestAction.VERIFY, null, workflow, NOW, failOnPublication());
        repository.save(matching);
        repository.save(newRequest("Liquidity buffer", NOW));

        PageResult<Request> page = repository.search(
                RequestSearchCriteria.of("liquidity", RequestState.VERIFIED), PageQuery.of(0, 10));

        assertThat(page.content()).extracting(Request::name).containsExactly("Liquidity coverage ratio");
    }

    @Test
    @DisplayName("cuts the result into pages of the requested size")
    void cutsTheResultIntoPages() {
        for (int index = 0; index < 5; index++) {
            repository.save(newRequest("Request " + index, NOW.plus(index, ChronoUnit.MINUTES)));
        }

        PageResult<Request> firstPage = repository.search(RequestSearchCriteria.none(), PageQuery.of(0, 2));
        PageResult<Request> thirdPage = repository.search(RequestSearchCriteria.none(), PageQuery.of(2, 2));

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(thirdPage.content()).hasSize(1);
        assertThat(thirdPage.hasNext()).isFalse();
    }

    private void publishWithNumber(String name, long publicationNumber) {
        Request request = newRequest(name, NOW);
        request.apply(RequestAction.VERIFY, null, workflow, NOW, failOnPublication());
        request.apply(RequestAction.ACCEPT, null, workflow, NOW, failOnPublication());
        request.apply(RequestAction.PUBLISH, null, workflow, NOW, () -> publicationNumber);
        repository.save(request);
        entityManager.flush();
    }

    private Request reload(RequestId id) {
        entityManager.flush();
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    private Request newRequest(String name, Instant createdAt) {
        return Request.create(requestIds.next(), name, "Draft content", workflow, createdAt);
    }

    private static java.util.function.LongSupplier failOnPublication() {
        return () -> {
            throw new AssertionError("No publication number should have been drawn");
        };
    }
}
