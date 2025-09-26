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

    /** DART 대상 중 '상장사'만 (corp_code 있고, stock_code도 있는 회사) */
    @Query("""
      select o from Organization o
      where o.corpCode  is not null and o.corpCode  <> ''
        and o.stockCode is not null and o.stockCode <> ''
    """)
    List<Organization> findAllDartEnabled();

    /** 벌크로 존재여부 확인(성능 개선용) */
    List<Organization> findAllByCorpCodeIn(Collection<String> corpCodes);

    // 선택: 화면/모니터링용 카운트 쿼리 (원하면 사용)
    @Query("select count(o) from Organization o where o.corpCode is not null and o.corpCode <> '' and o.stockCode is not null and o.stockCode <> ''")
    long countListed();

    @Query("select count(o) from Organization o where o.corpCode is not null and o.corpCode <> '' and (o.stockCode is null or o.stockCode = '')")
    long countUnlisted();

}
