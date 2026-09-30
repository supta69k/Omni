#!/usr/bin/env node
// Minimal Figma Dev Mode MCP client over streamable HTTP.
// Usage: node fig-mcp.js <method> '<paramsJson>'
//   node fig-mcp.js tools/list
//   node fig-mcp.js tools/call '{"name":"get_metadata","arguments":{"nodeId":"177-17","clientLanguages":"kotlin","clientFrameworks":"jetpack-compose"}}'
const BASE = 'http://127.0.0.1:3845/mcp';

function post(body, sessionId) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const u = new URL(BASE);
    const req = require('http').request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json, text/event-stream',
          'Content-Length': Buffer.byteLength(data),
          ...(sessionId ? { 'mcp-session-id': sessionId } : {}),
        },
      },
      (res) => {
        let raw = '';
        res.on('data', (c) => (raw += c));
        res.on('end', () => resolve({ sid: res.headers['mcp-session-id'], raw, status: res.statusCode }));
      }
    );
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

// Pull the last JSON object out of an SSE stream (event: message\ndata: {...}).
function parseSSE(raw) {
  const results = [];
  for (const line of raw.split(/\r?\n/)) {
    const t = line.trim();
    if (!t.startsWith('data:')) continue;
    const payload = t.slice(5).trim();
    try {
      results.push(JSON.parse(payload));
    } catch (_) {}
  }
  return results;
}

(async () => {
  const method = process.argv[2];
  const params = process.argv[3] ? JSON.parse(process.argv[3]) : {};

  // 1. initialize
  const init = await post({
    jsonrpc: '2.0',
    id: 1,
    method: 'initialize',
    params: {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'omni-cli', version: '1.0.0' },
    },
  });
  const sid = init.sid;
  // 2. initialized notification
  await post({ jsonrpc: '2.0', method: 'notifications/initialized' }, sid);
  // 3. the actual call
  const out = await post({ jsonrpc: '2.0', id: 2, method, params }, sid);
  const msgs = parseSSE(out.raw);
  const result = msgs.find((m) => m.id === 2) || msgs[msgs.length - 1] || { status: out.status, raw: out.raw };
  console.log(JSON.stringify(result, null, 2));
})().catch((e) => {
  console.error('ERR', e.message);
  process.exit(1);
});
