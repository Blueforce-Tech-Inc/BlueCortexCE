import { readFileSync, writeFileSync, existsSync } from 'fs';
import axios from 'axios';
import { join, resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const javaUrl = 'http://127.0.0.1:37777';
const cwd = '/tmp/claude-mem-test';

const CONFIG = {
  javaUrl: javaUrl,
  timeout: 10000,
  tagPattern: /<claude-mem-context>([\s\S]*?)<\/claude-mem-context>/,
};

async function callJavaApi(endpoint, data) {
  try {
    const response = await axios({
      method: 'POST',
      url: `${CONFIG.javaUrl}${endpoint}`,
      data,
      timeout: CONFIG.timeout,
    });
    return response.data;
  } catch (error) {
    console.error('API error:', error.message);
    return null;
  }
}

function findClaudeMd(startDir) {
  let current = resolve(startDir);
  while (true) {
    const claudeMdPath = join(current, 'CLAUDE.md');
    const gitPath = join(current, '.git');
    if (existsSync(claudeMdPath)) {
      return claudeMdPath;
    }
    if (existsSync(gitPath)) return null;
    const parent = dirname(current);
    if (parent === current) break;
    current = parent;
  }
  return null;
}

function updateClaudeMd(filePath, newContent) {
  if (!existsSync(filePath)) {
    console.log('Creating:', filePath);
    writeFileSync(filePath, newContent, 'utf8');
    return;
  }
  const existing = readFileSync(filePath, 'utf8');
  if (CONFIG.tagPattern.test(existing)) {
    const updated = existing.replace(CONFIG.tagPattern, `<claude-mem-context>\n${newContent}\n</claude-mem-context>`);
    writeFileSync(filePath, updated, 'utf8');
    console.log('Updated:', filePath);
  } else {
    const separator = existing.endsWith('\n') ? '' : '\n';
    writeFileSync(filePath, existing + separator + `<claude-mem-context>\n${newContent}\n</claude-mem-context>\n`, 'utf8');
    console.log('Appended:', filePath);
  }
}

async function main() {
  const sessionId = 'full-test-' + Date.now();

  console.log('=== Testing Full Flow ===');
  console.log('Session ID:', sessionId);
  console.log('Working directory:', cwd);

  const data = await callJavaApi('/api/ingest/session-start', {
    session_id: sessionId,
    project_path: cwd,
    debug: true
  });

  if (!data) {
    console.log('ERROR: No data from API');
    return;
  }

  console.log('\nAPI Response received');
  console.log('Has context:', !!data.context);
  console.log('Has updateFiles:', !!data.updateFiles);

  if (data.context) {
    console.log('\n=== CONTEXT OUTPUT (stdout for Claude) ===');
    console.log(data.context);
  }

  if (data.updateFiles) {
    console.log('\n=== Processing updateFiles ===');
    for (const file of data.updateFiles) {
      console.log('File:', file.path);
      const claudeMdPath = findClaudeMd(cwd);
      if (claudeMdPath) {
        console.log('Found CLAUDE.md at:', claudeMdPath);
        updateClaudeMd(claudeMdPath, file.content);
      } else {
        const targetPath = join(cwd, 'CLAUDE.md');
        console.log('Creating CLAUDE.md at:', targetPath);
        updateClaudeMd(targetPath, file.content);
      }
    }
  }

  console.log('\n=== Test Complete ===');
}

main();
