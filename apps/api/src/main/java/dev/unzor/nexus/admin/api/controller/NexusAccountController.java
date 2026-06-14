package dev.unzor.nexus.admin.api.controller;

import dev.unzor.nexus.admin.api.dto.NexusAccountDetails;
import dev.unzor.nexus.admin.api.requests.CreateNexusAccountRequest;
import dev.unzor.nexus.admin.application.service.CreateNexusAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/panel/v1/accounts")
class NexusAccountController {

    private final CreateNexusAccountService createNexusAccountService;

    NexusAccountController(CreateNexusAccountService createNexusAccountService) {
        this.createNexusAccountService = createNexusAccountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NexusAccountDetails create(@Valid @RequestBody CreateNexusAccountRequest request) {
        return createNexusAccountService.create(request);
    }
}
