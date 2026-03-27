package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.config.ModeConfig.Mode;
import com.ablueforce.cortexce.config.ModeConfig.ObservationType;
import com.ablueforce.cortexce.config.ModeConfig.ObservationConcept;
import com.ablueforce.cortexce.service.ModeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Mode", description = "Mode management API for runtime configuration of observation types and concepts")
public class ModeController {

    private final ModeService modeService;

    public ModeController(ModeService modeService) {
        this.modeService = modeService;
    }

    /**
     * Get current active mode information.
     */
    @GetMapping
    @Operation(summary = "Get current active mode",
        description = "Returns the currently active mode configuration including mode ID, name, description, version, observation types, and observation concepts.")
    @ApiResponse(responseCode = "200", description = "Active mode configuration returned")
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
    @Operation(summary = "Set active mode",
        description = "Switches the active mode at runtime. Supports base modes (e.g., 'code') and inherited modes (e.g., 'code--zh'). Returns error if the mode ID is invalid.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mode switched successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid mode ID or empty modeId provided")
    })
    public ResponseEntity<ModeResponse> setActiveMode(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with modeId field containing the mode ID to activate", required = true)
            @org.springframework.web.bind.annotation.RequestBody ModeSwitchRequest request) {
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
    @Operation(summary = "Get observation types",
        description = "Returns the list of valid observation types for the current active mode (e.g., bugfix, feature, architecture, how-it-works, gotcha).")
    @ApiResponse(responseCode = "200", description = "Observation types list returned")
    public ResponseEntity<List<ObservationType>> getObservationTypes() {
        return ResponseEntity.ok(modeService.getObservationTypes());
    }

    /**
     * Get observation concepts for current mode.
     */
    @GetMapping("/concepts")
    @Operation(summary = "Get observation concepts",
        description = "Returns the list of valid observation concepts for the current active mode.")
    @ApiResponse(responseCode = "200", description = "Observation concepts list returned")
    public ResponseEntity<List<ObservationConcept>> getObservationConcepts() {
        return ResponseEntity.ok(modeService.getObservationConcepts());
    }

    /**
     * Validate an observation type ID.
     */
    @GetMapping("/types/{typeId}/validate")
    @Operation(summary = "Validate observation type ID",
        description = "Checks whether a given observation type ID is valid for the current active mode.")
    @ApiResponse(responseCode = "200", description = "Validation result returned")
    public ResponseEntity<Map<String, Boolean>> validateType(
            @Parameter(description = "Observation type ID to validate", required = true, example = "bugfix")
            @PathVariable String typeId) {
        return ResponseEntity.ok(Map.of("valid", modeService.isValidType(typeId)));
    }

    /**
     * Get emoji for an observation type.
     */
    @GetMapping("/types/{typeId}/emoji")
    @Operation(summary = "Get emoji and label for observation type",
        description = "Returns the emoji (icon), work emoji (for active state), and human-readable label for a given observation type ID.")
    @ApiResponse(responseCode = "200", description = "Emoji and label returned")
    public ResponseEntity<Map<String, String>> getTypeEmoji(
            @Parameter(description = "Observation type ID", required = true, example = "bugfix")
            @PathVariable String typeId) {
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
    @Operation(summary = "Get all valid observation type IDs",
        description = "Returns a list of all valid observation type IDs for the current active mode.")
    @ApiResponse(responseCode = "200", description = "Valid type IDs returned")
    public ResponseEntity<List<String>> getValidTypeIds() {
        return ResponseEntity.ok(modeService.getValidTypeIds());
    }

    /**
     * Get valid concept IDs for current mode.
     */
    @GetMapping("/concepts/valid")
    @Operation(summary = "Get all valid observation concept IDs",
        description = "Returns a list of all valid observation concept IDs for the current active mode.")
    @ApiResponse(responseCode = "200", description = "Valid concept IDs returned")
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
