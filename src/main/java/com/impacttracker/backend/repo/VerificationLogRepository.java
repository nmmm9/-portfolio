package com.impacttracker.backend.repo;

import com.impacttracker.backend.domain.VerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationLogRepository extends JpaRepository<VerificationLog, Long> {}
