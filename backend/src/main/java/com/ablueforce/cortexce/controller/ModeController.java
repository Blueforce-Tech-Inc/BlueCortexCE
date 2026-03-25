package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.config.ModeConfig.Mode;
import com.ablueforce.cortexce.config.ModeConfig.ObservationType;
import com.ablueforce.cortexce.config.ModeConfig.ObservationConcept;
import com.ablueforce.cortexce.service.ModeService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for Mode management.
 * <p>
 * Provides endpoints to:
 * - Get current active mode
 * - Switch active mode
 * - List available modes
 * - Get mode details
 * <p>
 * This API goes beyond TS version by providing runtime mode management.
 */
@RestController
@RequestMapping("/api/mode")
public class ModeController {

    private final ModeService modeService;

    public ModeController(ModeService modeService) {
        this.modeService = modeService;
    }

    /**
     * Get current active mode information.
     */
    @GetMapping
    public ResponseEntity<ModeResponse> getActiveMode() {
        Mode mode = modeService.getActiveMode();
        return ResponseEntity.ok(new ModeResponse(
            modeService.getConfiguredMode(),
            mode.name(),
            mode.description(),
            mode.version(),
            mode.observation_types(),
            mode.observation_concepts()
        ));
    }

    /**
     * Switch to a different mode.
     * Supports both base modes (e.g., "code") and inherited modes (e.g., "code--zh").
     */
    @PutMapping
    public ResponseEntity<ModeResponse> setActiveMode(@RequestBody ModeSwitchRequest request) {
        String modeId = request.modeId();
        if (modeId == null || modeId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            modeService.setActiveMode(modeId);
            Mode mode = modeService.getActiveMode();
            return ResponseEntity.ok(new ModeResponse(
                modeId,
                mode.name(),
                mode.description(),
                mode.version(),
                mode.observation_types(),
                mode.observation_concepts()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ModeResponse(
                    modeId, "error", "Invalid mode: " + modeId, null, null, null
                ));
        }
    }

    /**
     * Get observation types for current mode.
     */
    @GetMapping("/types")
    public ResponseEntity<List<ObservationType>> getObservationTypes() {
        return ResponseEntity.ok(modeService.getObservationTypes());
    }

    /**
     * Get observation concepts for current mode.
     */
    @GetMapping("/concepts")
    public ResponseEntity<List<ObservationConcept>> getObservationConcepts() {
        return ResponseEntity.ok(modeService.getObservationConcepts());
    }

    /**
     * Validate an observation type ID.
     */
    @GetMapping("/types/{typeId}/validate")
    public ResponseEntity<Map<String, Boolean>> validateType(@PathVariable String typeId) {
        return ResponseEntity.ok(Map.of("valid", modeService.isValidType(typeId)));
    }

    /**
     * Get emoji for an observation type.
     */
    @GetMapping("/types/{typeId}/emoji")
    public ResponseEntity<Map<String, String>> getTypeEmoji(@PathVariable String typeId) {
        return ResponseEntity.ok(Map.of(
            "emoji", modeService.getTypeEmoji(typeId),
            "workEmoji", modeService.getWorkEmoji(typeId),
            "label", modeService.getTypeLabel(typeId)
        ));
    }

    /**
     * Get valid type IDs for current mode.
     */
    @GetMapping("/types/valid")
    public ResponseEntity<List<String>> getValidTypeIds() {
        return ResponseEntity.ok(modeService.getValidTypeIds());
    }

    /**
     * Get valid concept IDs for current mode.
     */
    @GetMapping("/concepts/valid")
    public ResponseEntity<List<String>> getValidConceptIds() {
        return ResponseEntity.ok(modeService.getValidConceptIds());
    }

    // Response records
    public record ModeResponse(
        String modeId,
        String name,
        String description,
        String version,
        List<ObservationType> observationTypes,
        List<ObservationConcept> observationConcepts
    ) {}

    public record ModeSwitchRequest(String modeId) {}
}
