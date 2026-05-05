## 4. UserProfile Design (Deferred)

**Current Solution is Sufficient**: Session-based isolation

Extractions of type "user_profile" can be stored with source="profile_update".

---


## 5. Implementation Roadmap

| Phase | Content | Changes |
|-------|---------|---------|
| **Phase 3.1** | StructuredExtractionService + templates + user_id | Generic extraction engine with append-only extraction |
| **Phase 3.2** | Template configurations + keyword trigger | YAML configs, trigger enhancement |
| **Phase 3.3** | DeepRefine integration | Run extraction during deep refine |
| **Phase 3.4** | Manual review (optional) | API + UI for reviewing extraction history |

---


## 6. Key Design Principles

| Principle | Application |
|-----------|-------------|
| **Prompt-driven** | What to extract = prompt template (configuration) |
| **Generic core** | `StructuredExtractionService` works for any extraction type |
| **Specific wrappers** | Templates configure specific extraction tasks |
| **Configuration over code** | Add new extraction type = add YAML config |

---
