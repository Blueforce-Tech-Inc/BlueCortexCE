---
name: Pull Request
about: Submit changes to Claude-Mem Java
title: '[PR] '
labels: ''
assignees: ''
---

## Description

<!-- Briefly describe what this PR does -->

## Type of Change

What type of PR is this? (check all that apply):

- [ ] **Bug fix** (non-breaking change that fixes an issue)
- [ ] **New feature** (non-breaking change that adds functionality)
- [ ] **Breaking change** (fix or feature that would cause existing functionality to not work)
- [ ] **Documentation update**
- [ ] **Refactoring** (no functional changes)
- [ ] **Performance improvement**
- [ ] **Testing**

## Related Issues

- Fixes # (issue number)
- Related to # (issue number)

## Changes Made

### Files Changed

<!-- List the files that were modified -->

| File | Change Type | Description |
|------|-------------|-------------|
| `path/to/file.java` | Added/Modified/Deleted | Description of change |

### New Dependencies

<!-- List any new dependencies added -->

- Dependency 1: [version] - [reason]
- Dependency 2: [version] - [reason]

### Database Changes

Are there database migrations required?
- [ ] Yes
- [ ] No

If yes, list migration files:

- `V#__migration_name.sql` - [description]

## Testing

### Test Coverage

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

### Test Results

<!-- Describe the test results -->

```bash
# Test command and output
```

### Regression Testing

- [ ] Regression tests pass
- [ ] WebUI integration tests pass (11/11)
- [ ] Thin proxy tests pass

## Checklist

### Pre-submission

- [ ] Code follows project coding standards
- [ ] Code has been formatted (run `./mvnw spotless:apply` if available)
- [ ] No lint errors (run `./mvnw checkstyle:check` if available)
- [ ] No security vulnerabilities introduced
- [ ] Commit messages follow conventional commits format

### PR Description

- [ ] This PR has a clear title
- [ ] This PR describes the changes thoroughly
- [ ] This PR links to related issues

### Testing Checklist

- [ ] New code has unit tests (> 80% coverage recommended)
- [ ] Bug fixes include regression tests
- [ ] All tests pass locally
- [ ] Database migrations tested

### Documentation

- [ ] Code comments added where necessary
- [ ] API documentation updated (if applicable)
- [ ] README or configuration docs updated (if applicable)

## Screenshots (if applicable)

<!-- Add screenshots to demonstrate changes -->

## Additional Notes

<!-- Any additional context or information reviewers should know -->

### Breaking Changes

- [ ] This PR contains breaking changes
- [ ] This PR does NOT contain breaking changes

If breaking changes, describe the migration path:

### Performance Impact

<!-- Describe any performance impact -->

## Reviewer Suggestions

<!-- Suggestions for reviewers -->
- Areas of particular focus: [specify]
- Questions to address: [specify]
