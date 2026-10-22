import nodemailer from 'nodemailer';
import { env } from '../env';

const transport = env.smtp.host
  ? nodemailer.createTransport({ host: env.smtp.host, port: env.smtp.port, auth: env.smtp.user ? { user: env.smtp.user, pass: env.smtp.pass } : undefined })
  : null;
export async function sendMail(to: string, subject: string, text: string) {
  if (!transport) { console.log(`[mail:dev] to=${to} subject=${subject}\n${text}`); return; }
  await transport.sendMail({ from: env.smtp.from, to, subject, text });
}
