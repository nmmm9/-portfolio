package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByOrganization_Id(Long organizationId);

    List<Donation> findByYear(Integer year);

    List<Donation> findByOrganization_IdAndYear(Long organizationId, Integer year);

    Optional<Donation> findByOrganization_IdAndYearAndQuarter(Long organizationId, Integer year, Integer quarter);

    @Query("SELECT DISTINCT d.year FROM Donation d ORDER BY d.year DESC")
    List<Integer> findDistinctYears();

    @Query("SELECT d FROM Donation d WHERE d.year BETWEEN :startYear AND :endYear")
    List<Donation> findByYearRange(Integer startYear, Integer endYear);
}