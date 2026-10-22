import { Router } from 'express';
import { listPage } from '../db';
import { notFound, wrap } from '../http';
import * as lib from '../services/library';

const r = Router();
r.get('/library/categories', wrap(async () => ({ items: await lib.categories(), nextCursor: null })));
r.get('/library/search', wrap(async (req) => lib.search(String(req.query.q || ''), req.query.type as string, req.query.categoryId as string, req.query.cursor as string)));
r.get('/library/source', wrap(async (req) => {
  const v = await lib.source(String(req.query.type || 'LAW'), String(req.query.id || ''), Number(req.query.page || 1));
  if (!v) throw notFound('المصدر');
  return v;
}));
r.get('/legal-updates', wrap(async (req) => listPage('legalUpdates', { cursor: req.query.cursor as string, sortKey: 'publishedAt' })));
export default r;
