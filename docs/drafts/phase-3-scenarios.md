# Phase 3 Extended Scenario Walkthrough

**Date**: 2026-03-22
**Purpose**: More thorough walkthrough with various scenarios

---

## Scenario 1: Family Assistant with Multiple Members

### Situation
```
Family "Zhang" has 4 members:
- Dad (zhang_dad) - prefers Chinese food
- Mom (zhang_mom) - allergic to seafood
- Son (zhang_son) - likes video games
- Daughter (zhang_daughter) - allergic to peanuts

Chat history:
1. "Mom can't eat shrimp, it makes her sick"
2. "Dad loves spicy food"
3. "My son spent 5 hours playing games yesterday"
4. "Daughter's school has peanut-free policy"
```

### Challenge: How to attribute correctly?

```java
// Problem: Observation has no user attribution
ObservationEntity {
    content: "Mom can't eat shrimp, it makes her sick"
    // Who is "Mom"? How does the system know?
    // In conversation, "Mom" is mentioned, but not as a userId
}

// Solution: Need person/entity extraction first
ExtractionTemplate {
    name: "family_member_info"
    prompt: |
        From conversation, extract family member information:
        - Who is mentioned (name/relation)
        - Their attributes (allergies, preferences, habits)
    outputSchema: |
        {
            "type": "object",
            "properties": {
                "member_id": {"type": "string"},
                "relation": {"type": "string"},
                "allergies": {"type": "array"},
                "preferences": {"type": "object"}
            }
        }
}

// Result stored:
{
    "type": "extracted_family_member",
    "extractedData": {
        "member_id": "mom",
        "relation": "mother",
        "allergies": ["seafood"],
        "preferences": {}
    },
    // Special session for family data
    contentSessionId: "cortex:family:zhang:members"
}
```

### Key Insight
**First extract WHO, then extract WHAT about them.**

```
Step 1: Entity Extraction → Who are the family members?
Step 2: Attribute Extraction → What are their allergies/preferences?
```

### Updated Extraction Pipeline

```java
public void extractFamilyData(String projectPath) {
    // Step 1: Extract family member identities
    ExtractionTemplate memberTemplate = loadTemplate("family_member");
    List<ObservationEntity> conversations = findConversations(projectPath);
    List<Map<String, Object>> members = llm.extract(memberTemplate, conversations);
    
    // Store each member in family session
    for (Map<String, Object> member : members) {
        String memberId = (String) member.get("member_id");
        storeInFamilySession(projectPath, memberId, member);
    }
    
    // Step 2: Extract attributes for each member
    for (Map<String, Object> member : members) {
        String memberId = (String) member.get("member_id");
        List<ObservationEntity> memberConversations = filterForMember(
            conversations, memberId);
        
        for (String attrType : ["allergy", "preference", "schedule"]) {
            ExtractionTemplate attrTemplate = loadTemplate(attrType);
            Map<String, Object> attr = llm.extract(attrTemplate, memberConversations);
            storeAttribute(projectPath, memberId, attrType, attr);
        }
    }
}
```

---

## Scenario 2: User with Multiple Sessions (Work vs Personal)

### Situation
```
User "chen" has:
- Session "work-001": "I prefer minimalist design for presentations"
- Session "work-002": "Let's use blue color scheme for the app"
- Session "personal-001": "I hate mornings, prefer evening calls"
- Session "personal-002": "Weekend hiking sounds great"
```

### Question: Should preferences be session-scoped or user-scoped?

**Answer: BOTH, depending on context.**

```java
// Template decides the scope
ExtractionTemplate {
    name: "work_preference"
    user-scoped: false  // Keep in original work session
    scope: "session_group:work"
    
    // Query: Find all sessions with same user AND same scope
    // Result stored in: "work-001" (original session)
}

ExtractionTemplate {
    name: "personal_preference"
    user-scoped: true   // Aggregate to user's personal profile
    scope: "user_global"
    
    // Query: Find all personal sessions for user
    // Result stored in: "cortex:user:chen:personal"
}
```

### Session Grouping

```java
// Group sessions by type (work/personal/travel)
@Entity
public class SessionEntity {
    String contentSessionId;
    String userId;
    String sessionGroup;  // "work", "personal", "travel"
}

// Query: Find all sessions in same group
List<String> workSessions = sessionRepository
    .findByUserIdAndSessionGroup(userId, "work")
    .stream()
    .map(Session::getContentSessionId)
    .collect(toList());
```

### YAML Configuration

```yaml
templates:
  - name: "work_preference"
    scope: "session_group:work"    # Aggregate within work sessions
    user-scoped: false
    
  - name: "personal_preference"
    scope: "user_global"          # Aggregate across ALL personal sessions
    user-scoped: true
    
  - name: "travel_preference"
    scope: "session_group:travel"
    user-scoped: true
```

---

## Scenario 3: Temporal Preference Evolution

### Situation
```
2025-01: "I love Sony headphones"
2025-06: "Actually, Bose noise cancellation is better"
2026-01: "I've been using AirPods lately, they're convenient"
```

### Challenge: Track preference evolution over time

```java
// Solution: Store temporal history in extractedData
{
    "type": "extracted_brand_preference",
    "source": "extraction:brand_preference",
    "extractedData": {
        "category": "headphones",
        "current_value": "AirPods",
        "evolution": [
            {"value": "Sony", "from": "2025-01", "confidence": 0.8},
            {"value": "Bose", "from": "2025-06", "confidence": 0.9},
            {"value": "AirPods", "from": "2026-01", "confidence": 0.95}
        ]
    }
}
```

### Evolution Detection Logic

```java
public void detectEvolution(String userId, String category) {
    // Find existing extraction for this category
    ObservationEntity existing = observationRepository
        .findByContentSessionIdAndType(
            "cortex:user:" + userId + ":preferences",
            "extracted_" + category)
        .stream().findFirst().orElse(null);
    
    Map<String, Object> existingData = existing.getExtractedData();
    String existingValue = (String) existingData.get("current_value");
    
    // Compare with new extraction
    Map<String, Object> newData = extractNew(category);
    String newValue = (String) newData.get("value");
    
    if (!existingValue.equals(newValue)) {
        // Evolution detected!
        List<Map<String, Object>> evolution = 
            (List<Map<String, Object>>) existingData.get("evolution");
        evolution.add(Map.of(
            "value", newValue,
            "from", LocalDate.now().toString(),
            "confidence", newData.get("confidence")
        ));
        
        // Update with evolution
        existingData.put("current_value", newValue);
        existingData.put("evolution", evolution);
        existing.setExtractedData(existingData);
        observationRepository.save(existing);
    }
}
```

---

## Scenario 4: Conflict Detection

### Situation
```
Session 1: "I prefer quiet restaurants"
Session 2: "Actually, I don't mind loud bars for drinks"
```

### Are these conflicts?

**Not necessarily** - context matters (restaurant vs bar).

```java
// Conflict detection needs context comparison
public class ConflictDetector {
    
    public ConflictResult detect(List<ObservationEntity> observations) {
        // Group by category/context
        Map<String, List<ObservationEntity>> byCategory = observations.stream()
            .collect(groupingBy(obs -> extractCategory(obs)));
        
        for (Map.Entry<String, List<ObservationEntity>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<ObservationEntity> sameCategory = entry.getValue();
            
            // Find semantic conflicts within same category
            if (hasSemanticConflict(sameCategory)) {
                return ConflictResult.builder()
                    .category(category)
                    .observations(sameCategory)
                    .severity(calculateSeverity(sameCategory))
                    .resolution(suggestResolution(sameCategory))
                    .build();
            }
        }
        return ConflictResult.NO_CONFLICT;
    }
    
    private boolean hasSemanticConflict(List<ObservationEntity> observations) {
        // LLM evaluates if preferences are truly contradictory
        String prompt = String.format("""
            Are these preferences conflicting?
            %s
            
            Consider: context, time, conditions.
            Return: {"conflicting": true/false, "reason": "..."}
            """,
            observations.stream()
                .map(ObservationEntity::getContent)
                .collect(Collectors.joining("\n"))
        );
        
        Map<String, Object> result = llm.chatCompletion(prompt);
        return (Boolean) result.get("conflicting");
    }
}
```

---

## Scenario 5: Extraction Trigger Timing

### Question: When should extraction run?

```java
// Option A: After each conversation
@PostMapping("/api/ingest/observation")
public void onNewObservation(ObservationRequest request) {
    observationRepository.save(request);
    
    // Check if extraction is needed
    if (shouldExtract(request)) {
        extractionService.runExtraction(request.getProjectPath());
    }
}

// Option B: Scheduled batch (more efficient)
@Scheduled(cron = "0 0 2 * * ?")  // 2 AM daily
public void dailyExtraction() {
    for (String activeProject : getActiveProjects()) {
        extractionService.runExtraction(activeProject);
    }
}

// Option C: On-demand
@PostMapping("/api/extraction/trigger")
public void triggerExtraction(String projectPath, String templateName) {
    extractionService.runExtraction(projectPath, templateName);
}
```

### Decision Logic

```java
private boolean shouldExtract(ObservationRequest request) {
    // Only extract if:
    // 1. Observation is from user (not system)
    if (!isUserSource(request.getSource())) return false;
    
    // 2. Conversation has ended or reached milestone
    if (!hasConversationEnded(request)) return false;
    
    // 3. Template triggers are satisfied
    for (ExtractionTemplate template : templates) {
        if (template.getTriggerKeywords().stream()
                .anyMatch(kw -> request.getContent().contains(kw))) {
            return true;
        }
    }
    return false;
}
```

---

## Scenario 6: Privacy and Access Control

### Situation
```
Family "Wang":
- Dad can see all family preferences
- Mom can see all family preferences
- Teenager can see family schedule, but NOT financial preferences
```

### Access Control in Extraction

```java
public class ExtractionAccessControl {
    
    public boolean canExtract(String requesterId, String targetScope) {
        // User can extract their own preferences
        if (isOwnData(requesterId, targetScope)) return true;
        
        // Family member can extract family-scoped data
        if (isFamilyMember(requesterId, targetScope) 
            && !isRestricted(targetScope, requesterId)) {
            return true;
        }
        
        return false;
    }
    
    private boolean isRestricted(String targetScope, String requesterId) {
        // Financial preferences are restricted to parents
        if (targetScope.contains("financial") 
            && !isParent(requesterId)) {
            return true;
        }
        return false;
    }
}
```

### Template with Access Control

```yaml
templates:
  - name: "family_schedule"
    access-level: "family"      # All family members
    restricted-to: []           # No restrictions
    
  - name: "financial_preference"
    access-level: "family"      # Family-scoped
    restricted-to: ["father", "mother"]  # Only parents
```

---

## Scenario 7: Zero-Shot Extraction (No Prior Data)

### Situation
```
First conversation with new user:
- No history
- No preferences known
- How to bootstrap?
```

### Solution: Ask and Confirm

```java
public class BootstrapExtraction {
    
    // When no preference exists, generate a question
    public String generatePreferenceQuestion(String category) {
        return String.format("""
            To personalize your experience, please answer:
            For %s, what is your preference? (optional)
            """, category);
    }
    
    // Store user's response as first preference data point
    public void bootstrapFromResponse(String userId, String category, 
                                       String response) {
        ObservationEntity firstData = new ObservationEntity();
        firstData.setContentSessionId("cortex:user:" + userId + ":preferences");
        firstData.setType("user_statement:" + category);
        firstData.setSource("user:bootstrap");
        firstData.setExtractedData(Map.of(
            "category", category,
            "value", response,
            "confidence", 1.0,  // User explicitly stated
            "bootstrap", true
        ));
        observationRepository.save(firstData);
    }
}
```

---

## Summary: All Scenarios Covered

| Scenario | Key Challenge | Solution |
|----------|--------------|----------|
| Family Assistant | Who is who? | Two-step: entity first, then attributes |
| Multi-session | Scope control | Template's `scope` field + `user-scoped` flag |
| Temporal Evolution | Track changes | Evolution history in extractedData |
| Conflict Detection | Context matters | LLM-based semantic comparison |
| Trigger Timing | When to extract? | Keyword triggers + scheduled batch |
| Privacy | Access control | Template's `access-level` + `restricted-to` |
| Zero-shot | No prior data | Bootstrap from explicit user input |

---

## Design Extensions Needed

Adding to the template design:

```java
public class ExtractionTemplateConfig {
    // Existing fields
    String name;
    boolean userScoped;
    
    // NEW fields for scenarios
    String scope;                    // "session_group:work", "user_global", "family:{familyId}"
    List<String> accessLevel;        // ["owner", "family", "public"]
    List<String> restrictedTo;       // ["father", "mother"]
    boolean bootstrapEnabled;         // Can bootstrap from explicit input
    
    // Two-step extraction support
    boolean entityExtractionFirst;    // Extract entity before attributes
    String entityTemplate;            // Reference to entity template
}
```

**The architecture is extensible to handle ALL these scenarios.**
