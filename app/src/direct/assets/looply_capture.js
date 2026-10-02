(() => {
  if (window.__looply) return;

  // Kotlin replaces the next line with the remote config JSON before injection.
  const CFG = /*__CONFIG__*/{};
  const PATTERNS = (CFG.capturePatterns || ['/api/v1/clips/', '/graphql', '/api/v1/feed/', '/api/v1/discover/'])
    .map(p => new RegExp(p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  const TEMPLATE_PATTERNS = (CFG.templatePatterns || ['/api/v1/clips/', '/graphql'])
    .map(p => new RegExp(p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  const CURSOR_KEYS = CFG.cursorKeys || ['max_id', 'after', 'cursor', 'end_cursor'];
  const MAX_BODY = 4000000;

  const send = (type, payload) => {
    try { LooplyBridge.postMessage(JSON.stringify(Object.assign({ type }, payload))); } catch (_) {}
  };
  const abs = (u) => { try { return new URL(u, location.href).href; } catch (_) { return String(u); } };
  const matches = (url, list) => list.some(p => p.test(url));

  let template = null;
  const rememberTemplate = (url, method, headers, body) => {
    if (template || method !== 'POST' || typeof body !== 'string') return;
    if (!matches(url, TEMPLATE_PATTERNS) || !looksPaginated(body)) return;
    template = { url, method, headers, body };
    send('template', { url });
  };

  // ---- fetch hook ----
  const origFetch = window.fetch.bind(window);
  window.fetch = async function (input, init) {
    const url = abs(typeof input === 'string' ? input : (input && input.url));
    const res = await origFetch(input, init);
    if (matches(url, PATTERNS)) {
      res.clone().text().then(t => { if (t && t.length < MAX_BODY) send('payload', { url, body: t }); }).catch(() => {});
      const method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
      const headers = init && init.headers ? Object.fromEntries(new Headers(init.headers)) : {};
      rememberTemplate(url, method, headers, init && init.body);
    }
    return res;
  };

  // ---- XHR hook ----
  const XO = XMLHttpRequest.prototype.open;
  const XH = XMLHttpRequest.prototype.setRequestHeader;
  const XS = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) {
    this.__l = { m: String(m).toUpperCase(), u: abs(u), h: {} };
    return XO.apply(this, arguments);
  };
  XMLHttpRequest.prototype.setRequestHeader = function (k, v) {
    if (this.__l) this.__l.h[k] = v;
    return XH.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function (body) {
    const l = this.__l;
    if (l && matches(l.u, PATTERNS)) {
      this.addEventListener('load', () => {
        if (this.responseType === '' || this.responseType === 'text') {
          const t = this.responseText;
          if (t && t.length < MAX_BODY) send('payload', { url: l.u, body: t });
        }
      });
      rememberTemplate(l.u, l.m, l.h, body);
    }
    return XS.apply(this, arguments);
  };

  // ---- server-rendered first page ----
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('script[type="application/json"]').forEach(s => {
      const t = s.textContent;
      if (t && t.length < MAX_BODY && t.indexOf('video_versions') !== -1) send('payload', { url: 'ssr', body: t });
    });
  });

  // ---- cursor replacement ----
  function setDeep(obj, cursor) {
    if (!obj || typeof obj !== 'object') return false;
    for (const k of CURSOR_KEYS) if (k in obj) { obj[k] = cursor; return true; }
    for (const v of Object.values(obj)) if (setDeep(v, cursor)) return true;
    return false;
  }
  function looksPaginated(body) {
    const p = new URLSearchParams(body);
    if (p.has('variables')) return true;
    return CURSOR_KEYS.some(k => p.has(k)) || p.has('page_size') || p.has('container_module');
  }
  function withCursor(body, cursor) {
    const p = new URLSearchParams(body);
    if (p.has('variables')) {
      const v = JSON.parse(p.get('variables'));
      if (!setDeep(v, cursor)) v.after = cursor;
      p.set('variables', JSON.stringify(v));
    } else {
      p.set(CURSOR_KEYS.find(k => p.has(k)) || 'max_id', cursor);
    }
    return p.toString();
  }

  // ---- API called from Kotlin ----
  window.__looply = {
    next: async (cursor, reqId) => {
      if (!template) { send('failure', { reqId, code: 'NO_TEMPLATE' }); return; }
      try {
        const r = await origFetch(template.url, {
          method: 'POST',
          headers: template.headers,
          body: withCursor(template.body, cursor),
          credentials: 'include'
        });
        const t = await r.text();
        send('page', { reqId, status: r.status, body: t.length < MAX_BODY ? t : '' });
      } catch (e) {
        send('failure', { reqId, code: 'FETCH_FAILED', message: String(e) });
      }
    }
  };
})();
