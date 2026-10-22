/**
 * سند (Sand) API — Node/Express. Data: Supabase Postgres (lg_nodes), files: Supabase Storage.
 * Runs on any Node 22 host (Render, cPanel "Setup Node.js App", a VPS) or on your PC: npm run dev.
 */
import { env } from './env';
import { app } from './app';
import * as library from './services/library';

app.listen(env.port, () => {
  console.log(`[api] http://localhost:${env.port}/api/v1  —  admin: http://localhost:${env.port}/admin/`);
  library.ready().catch((e) => console.error('[library] load failed', e.message));
});
