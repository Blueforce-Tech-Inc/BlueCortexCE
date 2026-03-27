#!/usr/bin/env node
/**
 * Claude-Mem Thin Proxy
 *
 * Thin proxy layer between Claude Code hooks and Java backend.
 * Responsibilities:
 * 1. HTTP calls to Java API (200ms timeout)
 * 2. Handle updateFiles array from Java
 * 3. Write/update CLAUDE.md files with incremental tag replacement
 *
 * Usage:
 *   node proxy.js [--port PORT] [--java-url URL]
 *
 * Environment variables:
 *   JAVA_API_URL - Java API URL (default: http://127.0.0.1:37777)
 *   PROXY_PORT   - Proxy listening port (default: 37778)
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync, statSync, readdirSync, lstatSync } from 'fs';
import { join, resolve, dirname, relative } from 'path';
import { fileURLToPath } from 'url';
import http from 'http';
import axios from 'axios';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Configuration
const CONFIG = {
  javaUrl: process.env.JAVA_API_URL || 'http://127.0.0.1:37777',
  port: parseInt(process.env.PROXY_PORT || '37778', 10),
  timeout: 200, // 200ms timeout for Java API calls
  tagPattern: /<claude-mem-context>([\s\S]*?)<\/claude-mem-context>/,
  excludeDirs: ['.git', 'node_modules', 'build', 'dist', '__pycache__', '.venv', 'venv'],
  rootMarker: '.git', // Stop recursing if this file/dir found (project root)
};

/**
 * Find CLAUDE.md file starting from startDir, searching up to project root
 * @param {string} startDir - Starting directory
 * @returns {string|null} - Path to CLAUDE.md or null
 */
function findClaudeMd(startDir) {
  let current = resolve(startDir);
  let foundPath = null;
  let isProjectRoot = false;

  while (true) {
    const claudeMdPath = join(current, 'CLAUDE.md');
    const gitPath = join(current, '.git');

    // Check if this directory has CLAUDE.md
    if (existsSync(claudeMdPath) && statSync(claudeMdPath).isFile()) {
      foundPath = claudeMdPath;
      // If this is a project root (has .git), stop here
      if (existsSync(gitPath)) {
        isProjectRoot = true;
        break;
      }
    }

    // If this is a project root, stop searching
    if (existsSync(gitPath)) {
      break;
    }

    // Move to parent
    const parent = dirname(current);
    if (parent === current) {
      // Reached filesystem root
      break;
    }
    current = parent;
  }

  return foundPath;
}

/**
 * Find CLAUDE.md in a directory tree (recursive search)
 * Stops at project root (.git found)
 * @param {string} dir - Starting directory
 * @returns {string[]} - Array of CLAUDE.md paths
 */
function findAllClaudeMd(dir) {
  const results = [];

  function search(currentDir, isProjectRoot) {
    // Check if this directory should be excluded
    const baseName = require('path').basename(currentDir);
    if (CONFIG.excludeDirs.includes(baseName)) {
      return;
    }

    const claudeMdPath = join(currentDir, 'CLAUDE.md');
    if (existsSync(claudeMdPath) && statSync(claudeMdPath).isFile()) {
      results.push(claudeMdPath);
    }

    // Check if this is a project root
    if (existsSync(join(currentDir, '.git'))) {
      return; // Stop recursion at project root
    }

    // Continue searching parent
    const parent = dirname(currentDir);
    if (parent !== currentDir) {
      search(parent, true);
    }
  }

  search(dir, false);
  return results;
}

/**
 * Update CLAUDE.md with incremental tag replacement
 * @param {string} filePath - Path to CLAUDE.md
 * @param {string} newContent - New content to inject
 * @returns {boolean} - Whether file was updated
 */
function updateClaudeMd(filePath, newContent) {
  try {
    if (!existsSync(filePath)) {
      console.log(`[proxy] Creating ${filePath}`);
      writeFileSync(filePath, newContent, 'utf8');
      return true;
    }

    const existing = readFileSync(filePath, 'utf8');

    if (CONFIG.tagPattern.test(existing)) {
      // Replace existing tag content
      const updated = existing.replace(
        CONFIG.tagPattern,
        `<claude-mem-context>\n${newContent}\n</claude-mem-context>`
      );

      if (updated !== existing) {
        console.log(`[proxy] Updated ${filePath}`);
        writeFileSync(filePath, updated, 'utf8');
        return true;
      }
    } else {
      // Append new tag at the end
      const separator = existing.endsWith('\n') ? '' : '\n';
      const updated = `${separator}<claude-mem-context>\n${newContent}\n</claude-mem-context>\n`;

      console.log(`[proxy] Appended to ${filePath}`);
      writeFileSync(filePath, existing + updated, 'utf8');
      return true;
    }

    return false;
  } catch (error) {
    console.error(`[proxy] Error updating ${filePath}:`, error.message);
    return false;
  }
}

/**
 * Process updateFiles array from Java response
 * @param {string} cwd - Working directory
 * @param {Array} updateFiles - Array of {path, content} objects
 * @returns {Object} - Result with success status and processed files
 */
function processUpdateFiles(cwd, updateFiles) {
  const processed = [];
  const errors = [];

  if (!updateFiles || !Array.isArray(updateFiles)) {
    return { processed, errors };
  }

  for (const file of updateFiles) {
    try {
      const targetPath = file.path.startsWith('/')
        ? file.path
        : join(cwd, file.path);

      const dir = dirname(targetPath);

      // Create directory if needed
      if (!existsSync(dir)) {
        mkdirSync(dir, { recursive: true });
      }

      // Handle CLAUDE.md specially
      if (file.path === 'CLAUDE.md' || file.path.endsWith('/CLAUDE.md')) {
        const claudeMdPath = findClaudeMd(cwd);
        if (claudeMdPath) {
          updateClaudeMd(claudeMdPath, file.content);
          processed.push({ path: relative(cwd, claudeMdPath), success: true });
        } else {
          // Create at current level
          const targetFilePath = join(cwd, 'CLAUDE.md');
          updateClaudeMd(targetFilePath, file.content);
          processed.push({ path: 'CLAUDE.md', success: true });
        }
      } else {
        // Regular file write
        writeFileSync(targetPath, file.content, 'utf8');
        processed.push({ path: file.path, success: true });
        console.log(`[proxy] Wrote ${file.path}`);
      }
    } catch (error) {
      errors.push({ path: file.path, error: error.message });
      console.error(`[proxy] Error writing ${file.path}:`, error.message);
    }
  }

  return { processed, errors };
}

/**
 * Forward request to Java API
 * @param {Object} req - HTTP request
 * @returns {Object} - Java API response
 */
async function forwardToJava(req) {
  const url = `${CONFIG.javaUrl}${req.url}`;

  try {
    const response = await axios({
      method: req.method,
      url,
      headers: {
        'Content-Type': 'application/json',
        'X-Forwarded-For': 'claude-mem-proxy',
      },
      data: req.body,
      timeout: CONFIG.timeout,
      validateStatus: () => true, // Accept all status codes
    });

    return {
      status: response.status,
      headers: response.headers,
      data: response.data,
    };
  } catch (error) {
    if (error.code === 'ECONNREFUSED') {
      console.error(`[proxy] Cannot connect to Java API at ${CONFIG.javaUrl}`);
      return {
        status: 503,
        data: { error: 'Java API unavailable', message: 'Connection refused' },
      };
    }

    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      console.error(`[proxy] Java API timeout (>${CONFIG.timeout}ms)`);
      return {
        status: 504,
        data: { error: 'Gateway timeout', message: 'Java API did not respond in time' },
      };
    }

    console.error(`[proxy] Java API error:`, error.message);
    return {
      status: 500,
      data: { error: 'Internal proxy error', message: error.message },
    };
  }
}

/**
 * Handle incoming request from Claude Code hooks
 * @param {Object} req - HTTP request
 * @param {Object} res - HTTP response
 */
async function handleRequest(req, res) {
  const startTime = Date.now();
  const requestId = Math.random().toString(36).substring(7);

  console.log(`[proxy:${requestId}] ${req.method} ${req.url}`);

  try {
    // Parse body for POST requests
    if (req.method === 'POST' && !req.body) {
      const buffers = [];
      for await (const chunk of req) {
        buffers.push(chunk);
      }
      req.body = JSON.parse(Buffer.concat(buffers).toString());
    }

    // Forward request to Java
    const javaResponse = await forwardToJava(req);

    // Process update_files if present
    if (javaResponse.data && javaResponse.data.update_files) {
      const cwd = req.body?.cwd || process.cwd();
      const result = processUpdateFiles(cwd, javaResponse.data.update_files);

      // Add file operation results to response
      javaResponse.data.fileOperations = result;

      // Log result
      console.log(`[proxy:${requestId}] Processed ${result.processed.length} files, ${result.errors.length} errors`);
    }

    // Send response
    res.writeHead(javaResponse.status, {
      'Content-Type': 'application/json',
      'X-Request-Id': requestId,
      'X-Response-Time': `${Date.now() - startTime}ms`,
    });
    res.end(JSON.stringify(javaResponse.data));

  } catch (error) {
    console.error(`[proxy:${requestId}] Error:`, error.message);

    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      error: 'Proxy error',
      message: error.message,
      requestId,
    }));
  }
}

/**
 * Start the proxy server
 */
function start() {
  const server = http.createServer(handleRequest);

  server.listen(CONFIG.port, () => {
    console.log(`
╔══════════════════════════════════════════════════════════╗
║  Claude-Mem Thin Proxy                                 ║
╠══════════════════════════════════════════════════════════╣
║  Listening: http://127.0.0.1:${CONFIG.port}                       ║
║  Java API:  ${CONFIG.javaUrl.padEnd(40)}║
║  Timeout:   ${CONFIG.timeout}ms                                        ║
╚══════════════════════════════════════════════════════════╝
`);
  });

  server.on('error', (error) => {
    if (error.code === 'EADDRINUSE') {
      console.error(`[proxy] Port ${CONFIG.port} is already in use`);
      process.exit(1);
    }
    throw error;
  });

  // Graceful shutdown
  process.on('SIGINT', () => {
    console.log('\n[proxy] Shutting down...');
    server.close(() => {
      process.exit(0);
    });
  });
}

// Export for testing
export {
  findClaudeMd,
  findAllClaudeMd,
  updateClaudeMd,
  processUpdateFiles,
  CONFIG,
};

// Start if run directly
start();
