package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrganizationController {
    private final OrganizationRepository orgRepo;

    @GetMapping
    public List<OrganizationDTO> list() {
        return orgRepo.findAll().stream()
                .map(org -> new OrganizationDTO(
                        org.getId(),
                        org.getName(),
                        org.getType(),
                        org.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    // DTO 클래스
    record OrganizationDTO(
            Long id,
            String name,
            String type,
            java.time.LocalDateTime createdAt
    ) {}
}