/** The Express app (API + admin console + office portal). Served by Cloud Functions (index.ts → `api`) or locally (`npm run dev`). */
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import path from 'node:path';
import { errorHandler, wrap } from './http';
import { requireAuth } from './services/auth';
import { appConfig } from './services/config';
import * as library from './services/library';
import { serveFile } from './files';
import { SUPABASE_URL } from './supabase';
import auth from './routes/auth';
import me from './routes/me';
import lib from './routes/library';
import ai from './routes/ai';
import contracts from './routes/contracts';
import office from './routes/office';
import misc from './routes/misc';
import admin from './routes/admin';
import officeTemplates from './routes/officeTemplates';
import payments, { webhooks } from './routes/payments';

export const app = express();
app.set('trust proxy', 1);
app.use(helmet({ contentSecurityPolicy: { directives: { defaultSrc: ["'self'"], scriptSrc: ["'self'", "'unsafe-inline'"], styleSrc: ["'self'", "'unsafe-inline'"], imgSrc: ["'self'", 'data:'], connectSrc: ["'self'"], frameAncestors: ["'none'"] } } }));
app.use(cors());
app.use(express.json({ limit: '4mb' }));
app.use('/admin', express.static(path.join(__dirname, '..', 'public', 'admin')));
app.use('/office', express.static(path.join(__dirname, '..', 'public', 'office')));
app.get('/', (req, res) => res.redirect(req.originalUrl.replace(/\/?(\?.*)?$/, '/office/')));
app.get('/files/*', serveFile);

const api = express.Router();
api.get('/health', wrap(async () => { await library.ready().catch(() => undefined); return { ok: true, time: new Date().toISOString(), database: SUPABASE_URL() ? 'supabase' : 'memory', library: library.stats() }; }));
api.get('/app/config', wrap(async () => appConfig()));
api.use('/auth', auth);
api.use('/webhooks', webhooks);
api.use(requireAuth);
api.use('/admin', admin);
for (const r of [me, lib, ai, contracts, officeTemplates, office, misc, payments]) api.use(r);

app.use('/api/v1', api);
app.use((_req, res) => res.status(404).json({ error: { code: 'NOT_FOUND', message: 'المسار غير موجود' } }));
app.use(errorHandler);
