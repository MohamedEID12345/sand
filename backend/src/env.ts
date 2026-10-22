import fs from 'node:fs';
import path from 'node:path';
import dotenv from 'dotenv';

// backend/.env (+ .env.local on a PC, overrides). On Render/cPanel the same keys can be set as environment variables instead.
// /etc/secrets/.env = Render "Secret File"; .env.local overrides on a PC
for (const p of ['/etc/secrets/.env', path.resolve(__dirname, '..', '.env'), path.resolve(__dirname, '..', '.env.local')])
  if (fs.existsSync(p)) dotenv.config({ path: p, override: p.endsWith('.env.local') });

const v = (k: string, def: string) => process.env[k] ?? def;
export const env = {
  port: Number(process.env.PORT || process.env.LOCAL_PORT || 4000),
  nodeEnv: v('NODE_ENV', 'development'),
  jwtSecret: v('JWT_SECRET', 'dev-only-change-me'),
  accessTtlSec: Number(process.env.ACCESS_TOKEN_TTL_SEC || 3600),
  refreshTtlDays: Number(process.env.REFRESH_TOKEN_TTL_DAYS || 30),
  geminiKey: v('GEMINI_API_KEY', ''),
  fawry: {
    baseUrl: v('FAWRY_BASE_URL', 'https://atfawry.fawrystaging.com'),
    merchantCode: v('FAWRY_MERCHANT_CODE', ''), secureKey: v('FAWRY_SECURE_KEY', ''),
    returnUrl: v('FAWRY_RETURN_URL', 'legalguide://payment/return'),
  },
  smtp: { host: process.env.SMTP_HOST, port: Number(process.env.SMTP_PORT || 587), user: process.env.SMTP_USER, pass: process.env.SMTP_PASS,
    from: v('SMTP_FROM', 'no-reply@legalguide.app') },
};
if (env.nodeEnv === 'production' && env.jwtSecret.startsWith('dev-only')) throw new Error('JWT_SECRET must be set in production');
