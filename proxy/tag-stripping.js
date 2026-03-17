/**
 * Tag Stripping Utilities (Java Proxy Port)
 *
 * Implements the dual-tag system for meta-observation control:
 * 1. <claude-mem-context> - System-level tag for auto-injected observations
 *    (prevents recursive storage when context injection is active)
 * 2. <private> - User-level tag for manual privacy control
 *    (allows users to mark content they don't want persisted)
 *
 * EDGE PROCESSING PATTERN: Filter at proxy layer before sending to Java backend.
 * This keeps the backend service simple and follows one-way data stream.
 *
 * Ported from: src/utils/tag-stripping.ts
 */

/**
 * Maximum number of tags allowed in a single content block
 * This protects against ReDoS (Regular Expression Denial of Service) attacks
 * where malicious input with many nested/unclosed tags could cause catastrophic backtracking
 */
const MAX_TAG_COUNT = 100;

/**
 * Count total number of opening tags in content
 * Used for ReDoS protection before regex processing
 *
 * @param {string} content - Content to check
 * @returns {number} Number of opening tags found
 */
function countTags(content) {
  if (!content || typeof content !== 'string') {
    return 0;
  }

  const privateCount = (content.match(/<private>/g) || []).length;
  const contextCount = (content.match(/<claude-mem-context>/g) || []).length;
  return privateCount + contextCount;
}

/**
 * Internal function to strip memory tags from content
 * Shared logic extracted from both JSON and prompt stripping functions
 *
 * @param {string} content - Content to process
 * @returns {string} Content with tags removed
 */
function stripTagsInternal(content) {
  if (!content || typeof content !== 'string') {
    return content || '';
  }

  // ReDoS protection: limit tag count before regex processing
  const tagCount = countTags(content);
  if (tagCount > MAX_TAG_COUNT) {
    console.error(`[claude-mem] WARNING: tag count (${tagCount}) exceeds limit (${MAX_TAG_COUNT}), content length: ${content.length}`);
    // Still process but log the anomaly
  }

  return content
    .replace(/<claude-mem-context>[\s\S]*?<\/claude-mem-context>/g, '')
    .replace(/<private>[\s\S]*?<\/private>/g, '')
    .trim();
}

/**
 * Strip memory tags from JSON-serialized content (tool inputs/responses)
 *
 * @param {string} content - Stringified JSON content from tool_input or tool_response
 * @returns {string} Cleaned content with tags removed
 */
function stripMemoryTagsFromJson(content) {
  return stripTagsInternal(content);
}

/**
 * Strip memory tags from user prompt content
 *
 * @param {string} content - Raw user prompt text
 * @returns {string} Cleaned content with tags removed
 */
function stripMemoryTagsFromPrompt(content) {
  return stripTagsInternal(content);
}

/**
 * Check if content is entirely private (strips to empty string)
 * Used to skip processing when user prompt is completely private
 *
 * @param {string} content - Content to check
 * @returns {boolean} True if content is entirely private
 */
function isEntirelyPrivate(content) {
  if (!content || typeof content !== 'string') {
    return false;
  }

  const stripped = stripTagsInternal(content);
  return stripped.length === 0 && content.length > 0;
}

export {
  stripMemoryTagsFromJson,
  stripMemoryTagsFromPrompt,
  isEntirelyPrivate,
  countTags,
  MAX_TAG_COUNT
};
