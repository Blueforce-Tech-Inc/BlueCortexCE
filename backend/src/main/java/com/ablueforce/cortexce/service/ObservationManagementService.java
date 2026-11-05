package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Observation read/write operations used by the memory API.
 *
 * <p>The controller kept a repository because these endpoints combine a row read,
 * a conversation-level lock and a partial update. That is transactional business
 * logic, not HTTP plumbing: here the lock and re-read stay in one method, and the
 * controller maps the outcome to a status code.
 */
@Service
public class ObservationManagementService {

    private static final Logger log = LoggerFactory.getLogger(ObservationManagementService.class);

    private final ObservationRepository observationRepository;
    private final ConversationWriteService conversationWriteService;

    public ObservationManagementService(ObservationRepository observationRepository,
                                        ConversationWriteService conversationWriteService) {
        this.observationRepository = observationRepository;
        this.conversationWriteService = conversationWriteService;
    }

    public record QualityDistribution(long high, long medium, long low, long unknown) {
    }

    /** Outcome of a mutation; {@code error} is set only for INVALID. */
    public record MutationResult(Status status, String error) {

        public enum Status { OK, NOT_FOUND, INVALID }

        public static MutationResult ok() {
            return new MutationResult(Status.OK, null);
        }

        public static MutationResult notFound() {
            return new MutationResult(Status.NOT_FOUND, null);
        }

        public static MutationResult invalid(String message) {
            return new MutationResult(Status.INVALID, message);
        }
    }

    @Transactional(readOnly = true)
    public QualityDistribution qualityDistribution(String project) {
        Object[] distribution = observationRepository.getQualityDistribution(project);
        if (distribution == null || distribution.length < 4) {
            return new QualityDistribution(0L, 0L, 0L, 0L);
        }
        return new QualityDistribution(
            ((Number) distribution[0]).longValue(),
            ((Number) distribution[1]).longValue(),
            ((Number) distribution[2]).longValue(),
            ((Number) distribution[3]).longValue());
    }

    @Transactional
    public MutationResult submitFeedback(UUID observationId, String feedbackType, String comment) {
        Optional<ObservationEntity> found = observationRepository.findById(observationId);
        if (found.isEmpty()) {
            return MutationResult.notFound();
        }
        conversationWriteService.lockForChildMutation(found.get().getConversationId());
        Optional<ObservationEntity> locked = observationRepository.findById(observationId);
        if (locked.isEmpty()) {
            return MutationResult.notFound();
        }
        ObservationEntity observation = locked.get();
        observation.setFeedbackType(feedbackType);
        if (comment != null) {
            observation.setUserComment(comment);
        }
        observation.setFeedbackUpdatedAt(OffsetDateTime.now());
        observationRepository.save(observation);
        return MutationResult.ok();
    }

    /**
     * PATCH semantics: an absent key is left unchanged, an explicit null clears.
     * The caller's 400 messages are preserved verbatim so the wire contract does
     * not change.
     */
    @Transactional
    public MutationResult updateObservation(UUID observationId, Map<String, Object> body) {
        ObservationEntity observation = observationRepository.findById(observationId).orElse(null);
        if (observation == null) {
            return MutationResult.notFound();
        }
        conversationWriteService.lockForChildMutation(observation.getConversationId());
        observation = observationRepository.findById(observationId).orElse(null);
        if (observation == null) {
            return MutationResult.notFound();
        }

        if (body.containsKey("title")) {
            Object val = body.get("title");
            if (val == null) {
                observation.setTitle(null);
            } else if (val instanceof String s) {
                observation.setTitle(s);
            } else {
                return MutationResult.invalid("title must be a string");
            }
        }
        if (body.containsKey("content") || body.containsKey("narrative")) {
            Object val = body.getOrDefault("content", body.get("narrative"));
            if (val == null) {
                observation.setContent(null);
            } else if (val instanceof String s) {
                observation.setContent(s);
            } else {
                return MutationResult.invalid("content/narrative must be a string");
            }
        }
        if (body.containsKey("subtitle")) {
            Object val = body.get("subtitle");
            if (val == null) {
                observation.setSubtitle(null);
            } else if (val instanceof String s) {
                observation.setSubtitle(s);
            } else {
                return MutationResult.invalid("subtitle must be a string");
            }
        }
        if (body.containsKey("source")) {
            Object val = body.get("source");
            if (val == null) {
                observation.setSource(null);
            } else if (val instanceof String s) {
                observation.setSource(s);
            } else {
                return MutationResult.invalid("source must be a string");
            }
        }

        if (body.containsKey("facts")) {
            Object val = body.get("facts");
            if (val == null) {
                observation.setFacts(null);
            } else if (val instanceof List<?> list) {
                List<String> strings = validateStringList(list, "facts");
                if (strings == null) {
                    return MutationResult.invalid("facts must be a list of strings");
                }
                observation.setFacts(strings);
            } else {
                return MutationResult.invalid("facts must be a list of strings");
            }
        }
        if (body.containsKey("concepts")) {
            Object val = body.get("concepts");
            if (val == null) {
                observation.setConcepts(null);
            } else if (val instanceof List<?> list) {
                List<String> strings = validateStringList(list, "concepts");
                if (strings == null) {
                    return MutationResult.invalid("concepts must be a list of strings");
                }
                observation.setConcepts(strings);
            } else {
                return MutationResult.invalid("concepts must be a list of strings");
            }
        }

        if (body.containsKey("extractedData")) {
            Object val = body.get("extractedData");
            if (val == null) {
                observation.setExtractedData(null);
            } else if (val instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> extractedData = (Map<String, Object>) val;
                observation.setExtractedData(extractedData);
            } else {
                return MutationResult.invalid("extractedData must be a JSON object");
            }
        }

        observationRepository.save(observation);
        return MutationResult.ok();
    }

    @Transactional
    public boolean deleteObservation(UUID observationId) {
        ObservationEntity observation = observationRepository.findById(observationId).orElse(null);
        if (observation == null) {
            return false;
        }
        conversationWriteService.lockForChildMutation(observation.getConversationId());
        if (!observationRepository.existsById(observationId)) {
            return false;
        }
        observationRepository.deleteById(observationId);
        return true;
    }

    private List<String> validateStringList(List<?> raw, String fieldName) {
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String s) {
                result.add(s);
            } else {
                log.warn("Non-string item in '{}' list (type {})", fieldName,
                    item != null ? item.getClass().getName() : "null");
                return null;
            }
        }
        return result;
    }
}
