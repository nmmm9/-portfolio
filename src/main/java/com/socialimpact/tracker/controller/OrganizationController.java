// com.socialimpact.tracker.controller.OrganizationController
package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrganizationController {
    private final OrganizationRepository orgRepo;

    @GetMapping
    public List<Organization> list() {
        return orgRepo.findAll();
    }
}
