package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.Emission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmissionRepository extends JpaRepository<Emission, Long> {

    List<Emission> findByOrganizationId(Long organizationId);

    List<Emission> findByYear(Integer year);

    Optional<Emission> findByOrganizationIdAndYear(Long organizationId, Integer year);

    @Query("SELECT e FROM Emission e WHERE e.year = :year ORDER BY e.totalEmissions DESC")
    List<Emission> findTopEmittersByYear(@Param("year") Integer year);

    @Query("SELECT SUM(e.totalEmissions) FROM Emission e WHERE e.year = :year")
    Double getTotalEmissionsByYear(@Param("year") Integer year);

    @Query("""
  select e from Emission e
  where (:orgId is null or e.organization.id = :orgId)
    and (:fromY is null or e.year >= :fromY)
    and (:toY   is null or e.year <= :toY)
""")
    List<Emission> search(@Param("orgId") Long orgId,
                          @Param("fromY") Integer fromY,
                          @Param("toY") Integer toY);
}