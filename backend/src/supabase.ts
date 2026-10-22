/** Supabase client for the backend (secret key → bypasses RLS). Never ship SUPABASE_SECRET_KEY in the Android app. */
import { createClient, SupabaseClient } from '@supabase/supabase-js';

export const SUPABASE_URL = () => (process.env.SUPABASE_URL || '').replace(/\/$/, '');
export const STORAGE_BUCKET = () => process.env.SUPABASE_BUCKET || 'sand-files';

let client: SupabaseClient | null = null;
export function supabase(): SupabaseClient {
  if (client) return client;
  const url = SUPABASE_URL(), key = process.env.SUPABASE_SECRET_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY || '';
  if (!url || !key) {
    console.error('\n✗ SUPABASE_URL و SUPABASE_SECRET_KEY غير موجودين في backend/.env\n  Supabase Dashboard → Project Settings → API Keys\n');
    process.exit(1);
  }
  client = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } });
  return client;
}
