package com.example.experiment_service.controller;

import com.example.experiment_service.dto.SortNameSuggestionDto;
import com.example.experiment_service.service.ChuteMasterService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/chute-master/sortname-suggestions?query=201&limit=20
 * Any logged-in user may call it. (In the old app it was open to everybody. Now a login is needed.
 * The FlexSim worker will get its own service token in a later step.)
 */
@RestController
@RequestMapping("/api/chute-master")
public class ChuteMasterController {

    private final ChuteMasterService service;

    public ChuteMasterController(ChuteMasterService service) {
        this.service = service;
    }

    @GetMapping("/sortname-suggestions")
    public List<SortNameSuggestionDto> sortNameSuggestions(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return service.getSortNameSuggestions(query, limit);
    }
}