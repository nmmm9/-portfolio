package com.impacttracker.backend.repo;

import com.impacttracker.backend.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByCorpCode(String corpCode);
    Optional<Organization> findByStockCode(String stockCode);

    /** DART에서 수집 가능한 대상(법인코드 존재)만 전수 조회 */
    @Query("select o from Organization o where o.corpCode is not null and o.corpCode <> ''")
    List<Organization> findAllDartEnabled();

    /** 벌크로 존재여부 확인(성능 개선용) */
    List<Organization> findAllByCorpCodeIn(Collection<String> corpCodes);
}
