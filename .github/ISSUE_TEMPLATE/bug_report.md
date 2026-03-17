---
name: Bug report
about: Report a bug to help us improve Claude-Mem Java
title: '[BUG] '
labels: bug, needs-triage
assignees: ''
---

## Bug Description
A clear and concise description of what the bug is.

## Environment Information

### System
- **OS**: [e.g., macOS 14.3, Ubuntu 22.04, Windows 11]
- **Java Version**: [e.g., OpenJDK 21.0.2]
- **Maven Version**: [e.g., 3.9.6]

### Application
- **Claude-Mem Java Version**: [e.g., 1.0.0]
- **PostgreSQL Version**: [e.g., 16.2]
- **pgvector Extension Version**: [e.g., 0.7.0]

### Configuration
- **Embedding Model**: [e.g., BAAI/bge-m3, text-embedding-3-small]
- **LLM Provider**: [e.g., DeepSeek, OpenAI, Anthropic]
- **Deployment Mode**: [e.g., Docker, bare metal, Kubernetes]

## Steps to Reproduce
Please provide detailed steps to reproduce the behavior:

1. Start the application with '...'
2. Make a request to '...'
3. Call the endpoint '...'
4. See error

**Minimal Reproduction Code** (if applicable):
```java
// Your code here
```

**cURL Request** (if applicable):
```bash
curl -X POST http://localhost:8080/api/endpoint \
  -H "Content-Type: application/json" \
  -d '{"key": "value"}'
```

## Expected Behavior
A clear and concise description of what you expected to happen.

## Actual Behavior
A clear and concise description of what actually happened.

## Error Messages and Logs

### Application Logs
```
Paste relevant application logs here
```

### Stack Trace
```
Paste full stack trace if available
```

### Database Logs (if relevant)
```
Paste PostgreSQL logs if relevant
```

## Additional Context

### Configuration Files
<details>
<summary>application.yml (redact sensitive info)</summary>

```yaml
server:
  port: 8080
# ... your configuration
```
</details>

<details>
<summary>.env (redact sensitive info)</summary>

```bash
LLM_PROVIDER=deepseek
# ... your environment variables
```
</details>

### Screenshots
If applicable, add screenshots to help explain your problem.

### Frequency
- [ ] Always happens
- [ ] Sometimes happens (describe frequency: X% of the time)
- [ ] Only happened once

### Regression
- [ ] Yes, this worked in version: [specify version]
- [ ] No, this is a new issue
- [ ] Not sure

## Possible Solution
If you have suggestions on how to fix the bug, please describe them here.

## Related Issues
List any related issues or PRs:
- #

## Additional Information
Add any other context about the problem here.
