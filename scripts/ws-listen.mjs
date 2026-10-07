// Tiny WebSocket listener used by smoke-flow.sh (no dependencies; needs Node 22+ for the built-in WebSocket).
// Usage: node ws-listen.mjs <ws-url> <access-token-or-empty> <out-file> [seconds]
// Writes one line per event: OPEN, MSG <json>, ERROR, CLOSE <code>.
import { writeFileSync, appendFileSync } from 'node:fs';

const [, , url, token, out, secs = '45'] = process.argv;
writeFileSync(out, '');
const log = (line) => appendFileSync(out, line + '\n');

const headers = token ? { Cookie: `access_token=${token}` } : {};
const ws = new WebSocket(url, { headers });
ws.addEventListener('open', () => log('OPEN'));
ws.addEventListener('message', (e) => log('MSG ' + e.data));
ws.addEventListener('error', () => log('ERROR'));
ws.addEventListener('close', (e) => { log('CLOSE ' + e.code); process.exit(0); });
setTimeout(() => process.exit(0), Number(secs) * 1000);
