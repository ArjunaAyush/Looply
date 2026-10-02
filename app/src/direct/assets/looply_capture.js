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
    try {
      const msg = JSON.stringify(Object.assign({ type }, payload));
      if (typeof LooplyBridge !== 'undefined' && LooplyBridge && typeof LooplyBridge.postMessage === 'function') {
        LooplyBridge.postMessage(msg);
      } else if (typeof LooplyNative !== 'undefined' && LooplyNative && typeof LooplyNative.postMessage === 'function') {
        LooplyNative.postMessage(msg);
      }
    } catch (_) {}
  };
  const abs = (u) => { try { return new URL(u, location.href).href; } catch (_) { return String(u); } };
  const matches = (url, list) => list.some(p => p.test(url));

  const stringifyBody = (body) => {
    if (!body) return null;
    if (typeof body === 'string') return body;
    if (body instanceof URLSearchParams) return body.toString();
    try { return String(body); } catch (_) { return null; }
  };

  const getShortcodeFromUrl = (urlStr) => {
    try {
      const u = new URL(urlStr, location.href);
      const m = u.pathname.match(/\/(?:reels?|reel|p)\/([A-Za-z0-9_-]+)/);
      return m ? m[1] : null;
    } catch (_) {
      return null;
    }
  };

  const extractVideoFromDom = () => {
    const video = document.querySelector('video');
    if (!video) return null;
    const src = video.currentSrc || video.src || (video.querySelector('source') && video.querySelector('source').src);
    const poster = video.poster || null;
    let author = null;
    const authorEl = document.querySelector('header a') ||
      document.querySelector('a[role="link"][href^="/"] span') ||
      document.querySelector('a[href^="/"][tabindex="0"]');
    if (authorEl && authorEl.textContent) author = authorEl.textContent.trim().replace(/^@/, '');
    return {
      src: src || null,
      poster: poster,
      author: author
    };
  };

  let lastReportedShortcode = null;
  let lastReportedWithVideo = false;

  const checkCurrentReel = () => {
    const code = getShortcodeFromUrl(location.href);
    if (!code) return;
    const dom = extractVideoFromDom();
    const hasVideo = !!(dom && dom.src);
    if (code !== lastReportedShortcode || (hasVideo && !lastReportedWithVideo)) {
      lastReportedShortcode = code;
      lastReportedWithVideo = hasVideo;
      send('redirect', {
        shortcode: code,
        url: location.href,
        videoUrl: dom ? dom.src : null,
        thumbnailUrl: dom ? dom.poster : null,
        author: dom ? dom.author : null
      });
    }
  };

  // ---- URL change hooks (history push/replace) ----
  const origPushState = history.pushState.bind(history);
  history.pushState = function () {
    origPushState.apply(this, arguments);
    setTimeout(checkCurrentReel, 100);
    setTimeout(checkCurrentReel, 500);
  };
  const origReplaceState = history.replaceState.bind(history);
  history.replaceState = function () {
    origReplaceState.apply(this, arguments);
    setTimeout(checkCurrentReel, 100);
    setTimeout(checkCurrentReel, 500);
  };
  window.addEventListener('popstate', () => {
    setTimeout(checkCurrentReel, 100);
    setTimeout(checkCurrentReel, 500);
  });

  // ---- DOM Mutation Observer for video element ----
  const observeDom = () => {
    if (!document.body) return;
    const observer = new MutationObserver(() => {
      checkCurrentReel();
    });
    observer.observe(document.body, { childList: true, subtree: true });
  };

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
      res.clone().text().then(t => {
        if (t && t.length < MAX_BODY) send('payload', { url, body: t });
      }).catch(() => {});
      const method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
      const headers = init && init.headers ? Object.fromEntries(new Headers(init.headers)) : {};
      const body = stringifyBody(init && init.body);
      rememberTemplate(url, method, headers, body);
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
    const strBody = stringifyBody(body);
    if (l && matches(l.u, PATTERNS)) {
      this.addEventListener('load', () => {
        if (this.responseType === '' || this.responseType === 'text') {
          const t = this.responseText;
          if (t && t.length < MAX_BODY) send('payload', { url: l.u, body: t });
        }
      });
      rememberTemplate(l.u, l.m, l.h, strBody);
    }
    return XS.apply(this, arguments);
  };

  // ---- server-rendered first page & init ----
  const scanSsr = () => {
    document.querySelectorAll('script[type="application/json"]').forEach(s => {
      const t = s.textContent;
      if (t && t.length < MAX_BODY && t.indexOf('video_versions') !== -1) {
        send('payload', { url: 'ssr', body: t });
      }
    });
    document.querySelectorAll('script:not([type="application/json"])').forEach(s => {
      const t = s.textContent;
      if (t && t.length < MAX_BODY && t.indexOf('video_versions') !== -1) {
        const first = t.indexOf('{');
        const last = t.lastIndexOf('}');
        if (first !== -1 && last > first) {
          send('payload', { url: 'ssr_embedded', body: t.substring(first, last + 1) });
        }
      }
    });
  };

  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    scanSsr();
    observeDom();
    checkCurrentReel();
  } else {
    document.addEventListener('DOMContentLoaded', () => {
      scanSsr();
      observeDom();
      checkCurrentReel();
    });
  }

  // Periodic safety check for URL / Video
  setInterval(checkCurrentReel, 1000);

  // ---- cursor replacement ----
  function setDeep(obj, cursor) {
    if (!obj || typeof obj !== 'object') return false;
    for (const k of CURSOR_KEYS) if (k in obj) { obj[k] = cursor; return true; }
    for (const v of Object.values(obj)) if (setDeep(v, cursor)) return true;
    return false;
  }

  function looksPaginated(body) {
    if (typeof body !== 'string') return false;
    if (body.startsWith('{')) {
      try {
        const obj = JSON.parse(body);
        if (obj.variables) return true;
        return CURSOR_KEYS.some(k => k in obj) || 'page_size' in obj || 'container_module' in obj;
      } catch (_) {}
    }
    const p = new URLSearchParams(body);
    if (p.has('variables')) return true;
    return CURSOR_KEYS.some(k => p.has(k)) || p.has('page_size') || p.has('container_module');
  }

  function withCursor(body, cursor) {
    if (typeof body !== 'string') return body;
    if (body.startsWith('{')) {
      try {
        const obj = JSON.parse(body);
        if (obj.variables) {
          let v = typeof obj.variables === 'string' ? JSON.parse(obj.variables) : obj.variables;
          if (!setDeep(v, cursor)) v.after = cursor;
          obj.variables = typeof obj.variables === 'string' ? JSON.stringify(v) : v;
        } else {
          if (!setDeep(obj, cursor)) obj.max_id = cursor;
        }
        return JSON.stringify(obj);
      } catch (_) {}
    }
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
    loadFeed: () => {
      lastReportedShortcode = null;
      lastReportedWithVideo = false;
      window.location.href = "https://www.instagram.com/reels/";
    },
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
