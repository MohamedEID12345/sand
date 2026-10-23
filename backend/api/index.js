/**
 * Vercel entry — one serverless function serves the whole API (/api/v1/*).
 * Static admin console & office portal come from backend/public (Vercel CDN).
 * vercel.json rewrites /api/<rest> → /api/index?__p=api/<rest>; the original path is restored here.
 */
const { app } = require('../dist/app.js');

module.exports = (req, res) => {
  const u = new URL(req.url, 'http://localhost');
  const p = u.searchParams.get('__p');
  if (p !== null) {
    u.searchParams.delete('__p');
    const qs = u.searchParams.toString();
    req.url = '/' + p.replace(/^\/+/, '') + (qs ? '?' + qs : '');
  }
  return app(req, res);
};
