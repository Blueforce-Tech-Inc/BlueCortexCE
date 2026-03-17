# Release Process - CortexCE (Claude-Mem Java)

## Version

**Current Version**: `0.1.0-beta`

## Release Checklist

### Pre-Release

- [ ] Update version in `VERSION` file
- [ ] Update version in `pom.xml` (if applicable)
- [ ] Run all tests
- [ ] Update CHANGELOG.md
- [ ] Create release branch (e.g., `release/v0.1.0-beta`)

### Build

- [ ] Run Maven build with tests
  ```bash
  cd java/backend
  ./mvnw clean package -DskipTests
  ```
- [ ] Verify JAR file generated in `target/`
- [ ] Build Docker image (optional)
  ```bash
  docker build -t ghcr.io/wubuku/claude-mem-java:0.1.0-beta -f java/Dockerfile .
  ```

### Testing

- [ ] Health check
  ```bash
  curl http://localhost:37777/actuator/health
  ```
- [ ] Run regression tests
  ```bash
  ./java/scripts/regression-test.sh
  ```
- [ ] Run WebUI integration tests
  ```bash
  ./java/scripts/webui-integration-test.sh
  ```

### Release Steps

1. **Tag the release**
   ```bash
   git tag -a v0.1.0-beta -m "Release v0.1.0-beta"
   git push origin v0.1.0-beta
   ```

2. **Create GitHub Release**
   - Use tag `v0.1.0-beta`
   - See template below

3. **Push Docker image** (if built)
   ```bash
   docker push ghcr.io/wubuku/claude-mem-java:0.1.0-beta
   ```

### Post-Release

- [ ] Merge release branch to main
- [ ] Update VERSION file to next version (e.g., `0.1.1-beta` or `0.2.0-beta`)
- [ ] Announce release

---

## GitHub Release Template

```
## What's New

<!-- Describe new features and improvements -->

## Bug Fixes

<!-- List bug fixes -->

## Breaking Changes

<!-- List any breaking changes -->

## Upgrading

<!-- Provide upgrade instructions if applicable -->

## Known Issues

<!-- List known issues and workarounds -->

## Docker

```bash
# Pull the release
docker pull ghcr.io/wubuku/claude-mem-java:0.1.0-beta

# Run the container
docker run -d \
  --name claude-mem \
  -p 37777:37777 \
  -e DB_PASSWORD=your_password \
  ghcr.io/wubuku/claude-mem-java:0.1.0-beta
```

## Full Changelog

<!-- Link to compare changes -->
```

---

## Versioning Strategy

| Version | Type | Description |
|---------|------|-------------|
| 0.1.0-beta | Beta | Initial beta release |
| 0.2.0-beta | Beta | Feature additions |
| 1.0.0 | Release | Stable release |

### Version Format

`MAJOR.MINOR.PATCH[-SUFFIX]`

- **MAJOR**: Incompatible API changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes (backward compatible)
- **SUFFIX**: Pre-release suffix (e.g., `-beta`, `-alpha`, `-rc1`)
