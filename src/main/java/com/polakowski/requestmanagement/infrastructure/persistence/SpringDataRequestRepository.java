package com.polakowski.requestmanagement.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data repository backing {@link JpaRequestRepository}; not visible outside this package. */
interface SpringDataRequestRepository
        extends JpaRepository<RequestEntity, UUID>, JpaSpecificationExecutor<RequestEntity> {
}
