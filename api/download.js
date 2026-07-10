const { Readable } = require('node:stream');

const ALLOWED_FORMATS = new Set(['mp3', 'flac', 'm4a']);
const DEFAULT_TIMEOUT_MS = 180000;
const DEFAULT_BACKEND_URL = 'http://34.28.240.33:8000';
const CONVERT_PATH = '/api/convert';

function sendJson(response, statusCode, payload) {
  response.statusCode = statusCode;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.end(JSON.stringify(payload));
}

async function readJsonBody(request) {
  if (request.body && typeof request.body === 'object' && !Buffer.isBuffer(request.body)) {
    return request.body;
  }

  if (typeof request.body === 'string') {
    return JSON.parse(request.body);
  }

  if (Buffer.isBuffer(request.body)) {
    return JSON.parse(request.body.toString('utf8'));
  }

  const chunks = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }

  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function getFilename(upstreamResponse) {
  return upstreamResponse.headers.get('content-disposition') || 'attachment; filename="FridaMusic-conversion.mp3"';
}

function getErrorDetail(payload, fallback) {
  if (!payload || typeof payload !== 'object') {
    return fallback;
  }

  const detail = payload.detail;

  if (typeof detail === 'string' && detail.trim()) {
    return detail;
  }

  if (Array.isArray(detail)) {
    const messages = detail
      .map((item) => {
        if (typeof item === 'string') {
          return item;
        }

        if (item && typeof item === 'object' && typeof item.msg === 'string') {
          return item.msg;
        }

        return '';
      })
      .filter(Boolean);

    if (messages.length) {
      return messages.join(' ');
    }
  }

  if (typeof payload.message === 'string' && payload.message.trim()) {
    return payload.message;
  }

  return fallback;
}

function getHeader(request, name) {
  const value = request.headers?.[name.toLowerCase()] || request.headers?.[name];
  return Array.isArray(value) ? value[0] : value;
}

function getForwardedFor(request) {
  const forwardedFor = getHeader(request, 'x-forwarded-for');
  const socketAddress = request.socket?.remoteAddress || request.connection?.remoteAddress || '';
  return [forwardedFor, socketAddress].filter(Boolean).join(', ');
}

module.exports = async function handler(request, response) {
  if (request.method !== 'POST') {
    response.setHeader('Allow', 'POST');
    return sendJson(response, 405, { detail: 'Metodo no permitido.' });
  }

  const apiBaseUrl = (
    process.env.FRIDAMUSIC_API_URL
    || process.env.NEXT_PUBLIC_FRIDAMUSIC_API_URL
    || DEFAULT_BACKEND_URL
  ).replace(/\/+$/, '');

  if (!apiBaseUrl) {
    return sendJson(response, 503, { detail: 'El servicio de conversion no esta configurado.' });
  }

  let payload;
  try {
    payload = await readJsonBody(request);
  } catch (error) {
    return sendJson(response, 400, { detail: 'Envia JSON valido con url y format.' });
  }

  const url = String(payload.url || '').trim();
  const format = String(payload.format || '').trim().toLowerCase();

  try {
    const parsedUrl = new URL(url);
    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      throw new Error('invalid protocol');
    }
  } catch (error) {
    return sendJson(response, 400, { detail: 'Ingresa una URL valida.' });
  }

  if (!ALLOWED_FORMATS.has(format)) {
    return sendJson(response, 400, { detail: 'Selecciona mp3, flac o m4a.' });
  }

  const timeoutMs = Number(process.env.FRIDAMUSIC_PROXY_TIMEOUT_MS || DEFAULT_TIMEOUT_MS);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number.isFinite(timeoutMs) ? timeoutMs : DEFAULT_TIMEOUT_MS);
  const userAgent = getHeader(request, 'user-agent') || 'FridaMusic-Web';
  const forwardedFor = getForwardedFor(request);

  try {
    const upstreamResponse = await fetch(`${apiBaseUrl}${CONVERT_PATH}`, {
      method: 'POST',
      headers: {
        Accept: 'application/octet-stream, application/json',
        'Content-Type': 'application/json',
        'User-Agent': userAgent,
        ...(forwardedFor ? { 'X-Forwarded-For': forwardedFor } : {}),
      },
      body: JSON.stringify({ url, formato: format }),
      signal: controller.signal,
    });

    if (!upstreamResponse.ok) {
      const text = await upstreamResponse.text();
      let detail = 'No se pudo completar la conversion.';

      try {
        detail = getErrorDetail(JSON.parse(text), detail);
      } catch (error) {
        if (text) {
          detail = text.slice(0, 240);
        }
      }

      return sendJson(response, upstreamResponse.status, { detail });
    }

    response.statusCode = upstreamResponse.status;
    response.setHeader('Content-Type', upstreamResponse.headers.get('content-type') || 'application/octet-stream');
    response.setHeader('Content-Disposition', getFilename(upstreamResponse));

    const contentLength = upstreamResponse.headers.get('content-length');
    if (contentLength) {
      response.setHeader('Content-Length', contentLength);
    }

    if (!upstreamResponse.body) {
      const buffer = Buffer.from(await upstreamResponse.arrayBuffer());
      return response.end(buffer);
    }

    return Readable.fromWeb(upstreamResponse.body).pipe(response);
  } catch (error) {
    const detail = error.name === 'AbortError'
      ? 'La conversion tardo demasiado. Intenta con otro enlace o formato.'
      : 'No se pudo conectar con el servicio de conversion.';

    return sendJson(response, 502, { detail });
  } finally {
    clearTimeout(timeout);
  }
};
