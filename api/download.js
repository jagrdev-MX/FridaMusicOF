const { Readable } = require('node:stream');

const ALLOWED_FORMATS = new Set(['mp3', 'flac', 'm4a']);
const DEFAULT_TIMEOUT_MS = 180000;
const BACKEND_URL = "http://34.29.190.146:8000";

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

module.exports = async function handler(request, response) {
  if (request.method !== 'POST') {
    response.setHeader('Allow', 'POST');
    return sendJson(response, 405, { detail: 'Metodo no permitido.' });
  }

  const apiBaseUrl = 'http://34.29.190.146:8000';

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

  try {
    const upstreamResponse = await fetch(`${apiBaseUrl}/download`, {
      method: 'POST',
      headers: {
        Accept: 'application/octet-stream, application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ url, format }),
      signal: controller.signal,
    });

    if (!upstreamResponse.ok) {
      const text = await upstreamResponse.text();
      let detail = 'No se pudo completar la conversion.';

      try {
        detail = JSON.parse(text).detail || detail;
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
