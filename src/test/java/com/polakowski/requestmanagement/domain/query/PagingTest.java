package com.polakowski.requestmanagement.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polakowski.requestmanagement.domain.workflow.RequestState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Paging and filtering")
class PagingTest {

    @Nested
    @DisplayName("A page query")
    class Queries {

        @Test
        @DisplayName("refuses a negative page index")
        void refusesANegativePageIndex() {
            assertThatThrownBy(() -> PageQuery.of(-1, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page must not be negative");
        }

        @Test
        @DisplayName("refuses an empty page")
        void refusesAnEmptyPage() {
            assertThatThrownBy(() -> PageQuery.of(0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("size must be positive");
        }
    }

    @Nested
    @DisplayName("A page of results")
    class Results {

        @Test
        @DisplayName("counts its pages and knows whether more follow")
        void countsItsPages() {
            PageResult<String> page = new PageResult<>(List.of("a", "b"), 0, 2, 5);

            assertThat(page.totalPages()).isEqualTo(3);
            assertThat(page.hasNext()).isTrue();
            assertThat(new PageResult<>(List.of("e"), 2, 2, 5).hasNext()).isFalse();
        }

        @Test
        @DisplayName("has no pages when nothing matched")
        void hasNoPagesWhenNothingMatched() {
            assertThat(new PageResult<>(List.of(), 0, 10, 0).totalPages()).isZero();
        }

        @Test
        @DisplayName("keeps its metadata when its content is mapped")
        void keepsItsMetadataWhenMapped() {
            PageResult<Integer> mapped = new PageResult<>(List.of("one", "three"), 1, 2, 7)
                    .map(String::length);

            assertThat(mapped.content()).containsExactly(3, 5);
            assertThat(mapped.page()).isEqualTo(1);
            assertThat(mapped.size()).isEqualTo(2);
            assertThat(mapped.totalElements()).isEqualTo(7);
        }

        @Test
        @DisplayName("cannot be modified through the list it was built from")
        void cannotBeModifiedThroughItsSource() {
            java.util.List<String> source = new java.util.ArrayList<>(List.of("a"));
            PageResult<String> page = new PageResult<>(source, 0, 10, 1);
            source.clear();

            assertThat(page.content()).containsExactly("a");
        }
    }

    @Nested
    @DisplayName("Search criteria")
    class Criteria {

        @Test
        @DisplayName("treat a blank name as no filter at all")
        void treatABlankNameAsNoFilter() {
            assertThat(RequestSearchCriteria.of("   ", null).nameFragment()).isEmpty();
            assertThat(RequestSearchCriteria.none().nameFragment()).isEmpty();
            assertThat(RequestSearchCriteria.none().requiredState()).isEmpty();
        }

        @Test
        @DisplayName("trim the name they are given")
        void trimTheName() {
            RequestSearchCriteria criteria = RequestSearchCriteria.of("  basel  ", RequestState.VERIFIED);

            assertThat(criteria.nameFragment()).contains("basel");
            assertThat(criteria.requiredState()).contains(RequestState.VERIFIED);
        }
    }
}
