/**
 * Basic usage example for CortexMemClient (JS SDK).
 *
 * Usage:
 *   npx tsx examples/basic.ts
 */

import { CortexMemClient } from '../src';

async function main() {
  // 1. Create client
  const client = new CortexMemClient({
    baseURL: 'http://localhost:37777',
    timeout: 10_000,
  });

  try {
    // 2. Health check
    const health = await client.healthCheck();
    console.log('Health:', health);

    // 3. Start session
    const session = await client.startSession({
      session_id: 'js-sdk-demo-' + Date.now(),
      project_path: '/tmp/js-sdk-demo',
    });
    console.log('Session started:', session.session_id);

    // 4. Record observation (fire-and-forget)
    await client.recordObservation({
      session_id: session.session_id,
      cwd: '/tmp/js-sdk-demo',
      tool_name: 'Read',
      tool_input: { file: 'main.go' },
      tool_response: { content: 'package main...' },
      source: 'demo',
    });
    console.log('Observation recorded');

    // 5. Retrieve experiences
    const experiences = await client.retrieveExperiences({
      task: 'How to parse JSON in Go?',
      project: '/tmp/js-sdk-demo',
      count: 3,
    });
    console.log('Found', experiences.length, 'experiences');

    // 6. Build ICL prompt
    const icl = await client.buildICLPrompt({
      task: 'How to parse JSON in Go?',
      project: '/tmp/js-sdk-demo',
      maxChars: 4000,
    });
    console.log('ICL prompt:', icl.prompt.length, 'chars');

    // 7. Search
    const search = await client.search({
      project: '/tmp/js-sdk-demo',
      query: 'JSON parsing',
      limit: 5,
    });
    console.log('Search results:', search.count);

    // 8. List observations
    const observations = await client.listObservations({
      project: '/tmp/js-sdk-demo',
      limit: 10,
    });
    console.log('Observations:', observations.items.length);

    // 9. Get version
    const version = await client.getVersion();
    console.log('Backend version:', version.version);

    // 10. Get projects
    const projects = await client.getProjects();
    console.log('Projects:', projects.projects);

    // 11. Get stats
    const stats = await client.getStats('/tmp/js-sdk-demo');
    console.log('Stats:', stats.database);

    // 12. Get modes
    const modes = await client.getModes();
    console.log('Modes:', modes.name);

    // 13. Get settings
    const settings = await client.getSettings();
    console.log('Settings keys:', Object.keys(settings));

    // 14. Get quality distribution
    const quality = await client.getQualityDistribution('/tmp/js-sdk-demo');
    console.log('Quality:', quality);

    // 15. Record user prompt (fire-and-forget)
    await client.recordUserPrompt({
      session_id: session.session_id,
      prompt_text: 'How to parse JSON in Go?',
      cwd: '/tmp/js-sdk-demo',
    });
    console.log('User prompt recorded');

    // 16. Record session end (fire-and-forget)
    await client.recordSessionEnd({
      session_id: session.session_id,
      cwd: '/tmp/js-sdk-demo',
      last_assistant_message: 'Use encoding/json package',
    });
    console.log('Session ended');

    // 17. Trigger refinement
    await client.triggerRefinement('/tmp/js-sdk-demo');
    console.log('Refinement triggered');

    // 18. Get quality distribution after refinement
    const qualityAfter = await client.getQualityDistribution('/tmp/js-sdk-demo');
    console.log('Quality after refinement:', qualityAfter);

    console.log('\n✅ All demo operations completed successfully!');
  } finally {
    client.close();
  }
}

main().catch(console.error);
