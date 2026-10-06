package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.ViewerSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ViewerSessionController {

    private final ViewerSessionService viewerSessionService;

    public ViewerSessionController(ViewerSessionService viewerSessionService) {
        this.viewerSessionService = viewerSessionService;
    }

    @GetMapping("/sessions")
    public ViewerSessionService.CatalogPage getSessions(
        @RequestParam(required = false) String project,
        @RequestParam(required = false) String platformSource,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return viewerSessionService.getSessions(project, platformSource, offset, limit);
    }

    @DeleteMapping("/sessions/{platformSource}/{contentSessionId}")
    public ResponseEntity<Void> deleteSession(
        @PathVariable String platformSource,
        @PathVariable String contentSessionId
    ) {
        viewerSessionService.deleteSession(platformSource, contentSessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/observation/{id}")
    public ResponseEntity<Void> deleteObservation(@PathVariable UUID id) {
        viewerSessionService.deleteObservation(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/summary/{id}")
    public ResponseEntity<Void> deleteSummary(@PathVariable UUID id) {
        viewerSessionService.deleteSummary(id);
        return ResponseEntity.noContent().build();
    }
}
