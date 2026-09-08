package com.nova.browser.features.browser.web

import com.nova.browser.core.utils.HtmlUtils

/**
 * All JavaScript injected into pages. Every script is wrapped in an IIFE with
 * try/catch so a hostile or broken page can never break the caller, and each
 * returns a JSON string (evaluateJavascript gives us a JSON-encoded value).
 */
object JsScripts {

    /** Readability-style main-content + metadata extraction (spec 10). */
    const val EXTRACT_CONTENT = """
(function() {
  try {
    function clean(text) {
      return (text || '').replace(/\s+/g, ' ').trim();
    }
    function visible(el) {
      if (!el) return false;
      var style = window.getComputedStyle(el);
      if (!style) return true;
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
      var rect = el.getBoundingClientRect();
      return rect.width > 0 || rect.height > 0;
    }
    var NOISE = 'nav,header,footer,aside,script,style,noscript,iframe,svg,form,button,[role=navigation],[role=banner],[role=contentinfo],[aria-hidden=true],.nav,.navbar,.menu,.sidebar,.footer,.header,.ad,.ads,.advert,.advertisement,.cookie,.popup,.modal,.newsletter,.social,.share,.comments,.related,.promo';

    function scoreOf(el) {
      if (!el || !visible(el)) return -1;
      var text = clean(el.innerText || '');
      if (text.length < 140) return -1;
      var paragraphs = el.querySelectorAll('p').length;
      var links = el.querySelectorAll('a').length;
      var linkText = 0;
      el.querySelectorAll('a').forEach(function(a) { linkText += clean(a.innerText).length; });
      var density = text.length > 0 ? linkText / text.length : 1;
      var score = text.length + paragraphs * 90 - links * 12;
      if (density > 0.55) score = score * 0.25;
      var tag = el.tagName.toLowerCase();
      if (tag === 'article' || tag === 'main') score += 1400;
      var idClass = ((el.id || '') + ' ' + (el.className || '')).toString().toLowerCase();
      if (/(article|content|post|story|entry|main|body|markdown|readme)/.test(idClass)) score += 700;
      if (/(comment|footer|nav|sidebar|promo|advert|related|share|menu)/.test(idClass)) score -= 900;
      return score;
    }

    var best = null, bestScore = 0;
    var candidates = document.querySelectorAll('article,main,[role=main],section,div,td');
    for (var i = 0; i < candidates.length && i < 3000; i++) {
      var s = scoreOf(candidates[i]);
      if (s > bestScore) { bestScore = s; best = candidates[i]; }
    }
    var root = best || document.body;

    // Work on a clone so we never mutate the live page.
    var clone = root.cloneNode(true);
    clone.querySelectorAll(NOISE).forEach(function(n) { n.remove(); });
    var mainText = clean(clone.innerText || '');
    if (mainText.length < 200) {
      var fallback = document.body ? document.body.cloneNode(true) : null;
      if (fallback) {
        fallback.querySelectorAll(NOISE).forEach(function(n) { n.remove(); });
        mainText = clean(fallback.innerText || '');
      }
    }

    var headings = [];
    document.querySelectorAll('h1,h2,h3,h4,h5,h6').forEach(function(h) {
      if (headings.length >= 60) return;
      var t = clean(h.innerText);
      if (t) headings.push({ level: parseInt(h.tagName.substring(1), 10), text: t.substring(0, 200) });
    });

    var links = [];
    document.querySelectorAll('a[href]').forEach(function(a) {
      if (links.length >= 150) return;
      var t = clean(a.innerText);
      var href = a.href;
      if (t && href && href.indexOf('javascript:') !== 0) {
        links.push({ text: t.substring(0, 120), url: href });
      }
    });

    var images = [];
    document.querySelectorAll('img').forEach(function(img) {
      if (images.length >= 60) return;
      if (!img.src) return;
      images.push({ src: img.src, alt: clean(img.alt).substring(0, 160), width: img.naturalWidth || 0, height: img.naturalHeight || 0 });
    });

    var tables = [];
    document.querySelectorAll('table').forEach(function(table) {
      if (tables.length >= 8) return;
      var rows = [];
      table.querySelectorAll('tr').forEach(function(tr) {
        if (rows.length >= 40) return;
        var cells = [];
        tr.querySelectorAll('th,td').forEach(function(cell) {
          if (cells.length < 12) cells.push(clean(cell.innerText).substring(0, 120));
        });
        if (cells.length) rows.push(cells);
      });
      if (rows.length) tables.push(rows);
    });

    var codeBlocks = [];
    document.querySelectorAll('pre,code').forEach(function(c) {
      if (codeBlocks.length >= 15) return;
      var t = (c.innerText || '').trim();
      if (t.length > 40) codeBlocks.push(t.substring(0, 3000));
    });

    var videos = [];
    document.querySelectorAll('video,iframe[src*="youtube"],iframe[src*="vimeo"]').forEach(function(v) {
      if (videos.length >= 10) return;
      videos.push({ src: v.src || (v.currentSrc || ''), title: clean(v.title || '') });
    });

    function meta(name) {
      var el = document.querySelector('meta[name="' + name + '"]') || document.querySelector('meta[property="' + name + '"]');
      return el ? clean(el.getAttribute('content')) : '';
    }

    var type = 'page';
    var url = location.href;
    var bodyText = mainText.toLowerCase();
    if (document.querySelector('article') || meta('og:type') === 'article') type = 'article';
    if (/\/(search|results)\b/.test(location.pathname) || location.search.indexOf('q=') >= 0) type = 'search';
    if (document.querySelector('[itemtype*="Product"], .price, #priceblock_ourprice, [data-price]')) type = 'product';
    if (codeBlocks.length > 2) type = 'code';
    if (videos.length > 0 && bodyText.length < 2000) type = 'video';
    if (/(forum|thread|topic|comments)/.test(location.href)) type = 'forum';

    return JSON.stringify({
      ok: true,
      url: url,
      title: document.title || '',
      description: meta('description') || meta('og:description'),
      siteName: meta('og:site_name'),
      author: meta('author') || meta('article:author'),
      published: meta('article:published_time') || meta('date'),
      lang: document.documentElement.lang || '',
      contentType: type,
      text: mainText.substring(0, 120000),
      wordCount: mainText.split(/\s+/).filter(Boolean).length,
      headings: headings,
      links: links,
      images: images,
      tables: tables,
      codeBlocks: codeBlocks,
      videos: videos
    });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e), url: location.href, title: document.title || '' });
  }
})();
"""

    /** Interactive elements the agent may act on (spec 09). */
    const val EXTRACT_INTERACTIVE = """
(function() {
  try {
    function clean(t) { return (t || '').replace(/\s+/g, ' ').trim().substring(0, 140); }
    function selectorFor(el) {
      if (el.id) return '#' + CSS.escape(el.id);
      var path = [];
      var node = el;
      while (node && node.nodeType === 1 && path.length < 6) {
        var part = node.tagName.toLowerCase();
        if (node.className && typeof node.className === 'string') {
          var cls = node.className.trim().split(/\s+/).slice(0, 2).filter(function(c) { return c && !/\d{4,}/.test(c); });
          if (cls.length) part += '.' + cls.map(function(c) { return CSS.escape(c); }).join('.');
        }
        var parent = node.parentElement;
        if (parent) {
          var siblings = Array.prototype.filter.call(parent.children, function(c) { return c.tagName === node.tagName; });
          if (siblings.length > 1) part += ':nth-of-type(' + (siblings.indexOf(node) + 1) + ')';
        }
        path.unshift(part);
        node = node.parentElement;
        if (node && node.id) { path.unshift('#' + CSS.escape(node.id)); break; }
      }
      return path.join(' > ');
    }
    function visible(el) {
      var r = el.getBoundingClientRect();
      var s = window.getComputedStyle(el);
      return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
    }
    var out = { ok: true, buttons: [], links: [], inputs: [], selects: [], checkboxes: [] };
    document.querySelectorAll('button,[role=button],input[type=submit],input[type=button]').forEach(function(b) {
      if (out.buttons.length >= 60 || !visible(b)) return;
      out.buttons.push({ text: clean(b.innerText || b.value || b.getAttribute('aria-label')), selector: selectorFor(b) });
    });
    document.querySelectorAll('a[href]').forEach(function(a) {
      if (out.links.length >= 80 || !visible(a)) return;
      var t = clean(a.innerText || a.getAttribute('aria-label'));
      if (t) out.links.push({ text: t, url: a.href, selector: selectorFor(a) });
    });
    document.querySelectorAll('input,textarea').forEach(function(input) {
      if (out.inputs.length >= 50 || !visible(input)) return;
      var type = (input.type || 'text').toLowerCase();
      if (type === 'hidden' || type === 'submit' || type === 'button') return;
      if (type === 'checkbox' || type === 'radio') {
        out.checkboxes.push({ type: type, name: input.name || '', checked: !!input.checked, label: clean(input.getAttribute('aria-label') || input.name), selector: selectorFor(input) });
        return;
      }
      var label = '';
      if (input.id) {
        var lab = document.querySelector('label[for="' + CSS.escape(input.id) + '"]');
        if (lab) label = clean(lab.innerText);
      }
      if (!label) label = clean(input.getAttribute('aria-label') || input.placeholder || input.name);
      out.inputs.push({
        label: label, name: input.name || '', type: type,
        placeholder: clean(input.placeholder), value: type === 'password' ? '' : clean(input.value),
        selector: selectorFor(input)
      });
    });
    document.querySelectorAll('select').forEach(function(sel) {
      if (out.selects.length >= 25 || !visible(sel)) return;
      var options = [];
      Array.prototype.forEach.call(sel.options, function(o) { if (options.length < 30) options.push(clean(o.text)); });
      out.selects.push({ name: sel.name || '', label: clean(sel.getAttribute('aria-label') || sel.name), options: options, selector: selectorFor(sel) });
    });
    return JSON.stringify(out);
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Login form detection for the password manager (spec 18). */
    const val DETECT_LOGIN_FORM = """
(function() {
  try {
    function sel(el) {
      if (el.id) return '#' + CSS.escape(el.id);
      if (el.name) return el.tagName.toLowerCase() + '[name="' + CSS.escape(el.name) + '"]';
      return el.tagName.toLowerCase();
    }
    var password = document.querySelector('input[type=password]');
    if (!password) return JSON.stringify({ ok: true, found: false });
    var form = password.form;
    var scope = form || document;
    var candidates = scope.querySelectorAll('input[type=text],input[type=email],input[type=tel],input:not([type])');
    var username = null;
    for (var i = 0; i < candidates.length; i++) {
      var c = candidates[i];
      var hint = ((c.name || '') + ' ' + (c.id || '') + ' ' + (c.autocomplete || '') + ' ' + (c.placeholder || '')).toLowerCase();
      if (/user|email|login|account|phone|mail/.test(hint)) { username = c; break; }
      if (!username) username = c;
    }
    return JSON.stringify({
      ok: true, found: true,
      usernameSelector: username ? sel(username) : null,
      passwordSelector: sel(password),
      usernameValue: username ? (username.value || '') : '',
      origin: location.origin,
      host: location.host,
      hasSubmit: !!(form && form.querySelector('button,input[type=submit]'))
    });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Selected text plus a little surrounding context. */
    const val GET_SELECTION = """
(function() {
  try {
    var sel = window.getSelection();
    var text = sel ? sel.toString() : '';
    var context = '';
    if (sel && sel.rangeCount > 0) {
      var node = sel.getRangeAt(0).commonAncestorContainer;
      var el = node.nodeType === 1 ? node : node.parentElement;
      if (el) context = (el.innerText || '').replace(/\s+/g, ' ').trim().substring(0, 1200);
    }
    return JSON.stringify({ ok: true, text: text.trim(), context: context });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e), text: '' });
  }
})();
"""

    /** Page HTML source. */
    const val GET_SOURCE = """
(function() {
  try {
    return JSON.stringify({ ok: true, html: document.documentElement.outerHTML.substring(0, 500000) });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** DOM tree for the inspector (depth-limited). */
    const val GET_DOM_TREE = """
(function() {
  try {
    function describe(el, depth) {
      if (depth > 12) return null;
      var attrs = {};
      for (var i = 0; i < el.attributes.length && i < 12; i++) {
        attrs[el.attributes[i].name] = String(el.attributes[i].value).substring(0, 120);
      }
      var rect = el.getBoundingClientRect();
      var node = {
        tag: el.tagName.toLowerCase(),
        id: el.id || '',
        classes: (typeof el.className === 'string' ? el.className : '').split(/\s+/).filter(Boolean).slice(0, 6),
        attrs: attrs,
        text: (el.children.length === 0 ? (el.innerText || '') : '').replace(/\s+/g, ' ').trim().substring(0, 100),
        width: Math.round(rect.width), height: Math.round(rect.height),
        children: []
      };
      for (var j = 0; j < el.children.length && j < 40; j++) {
        var child = describe(el.children[j], depth + 1);
        if (child) node.children.push(child);
      }
      return node;
    }
    return JSON.stringify({ ok: true, root: describe(document.documentElement, 0) });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Local/session storage + cookies for the storage inspector. */
    const val GET_STORAGE = """
(function() {
  try {
    function dump(store) {
      var out = [];
      try {
        for (var i = 0; i < store.length && i < 200; i++) {
          var k = store.key(i);
          out.push({ key: k, value: String(store.getItem(k)).substring(0, 2000) });
        }
      } catch (e) { }
      return out;
    }
    var cookies = [];
    try {
      document.cookie.split(';').forEach(function(c) {
        var idx = c.indexOf('=');
        if (idx > 0) cookies.push({ key: c.substring(0, idx).trim(), value: c.substring(idx + 1).trim().substring(0, 500) });
      });
    } catch (e) { }
    return JSON.stringify({
      ok: true,
      localStorage: dump(window.localStorage),
      sessionStorage: dump(window.sessionStorage),
      cookies: cookies
    });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Performance metrics for the AI debugger. */
    const val GET_PERFORMANCE = """
(function() {
  try {
    var nav = performance.getEntriesByType('navigation')[0] || {};
    var resources = performance.getEntriesByType('resource') || [];
    var slow = [];
    resources.slice(0, 300).forEach(function(r) {
      if (r.duration > 300) slow.push({ name: String(r.name).substring(0, 200), duration: Math.round(r.duration), type: r.initiatorType, size: r.transferSize || 0 });
    });
    slow.sort(function(a, b) { return b.duration - a.duration; });
    return JSON.stringify({
      ok: true,
      domContentLoaded: Math.round(nav.domContentLoadedEventEnd || 0),
      loadComplete: Math.round(nav.loadEventEnd || 0),
      ttfb: Math.round(nav.responseStart || 0),
      transferSize: nav.transferSize || 0,
      resourceCount: resources.length,
      slowest: slow.slice(0, 15),
      domNodes: document.getElementsByTagName('*').length
    });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Accessibility audit used by the AI inspector. */
    const val ACCESSIBILITY_AUDIT = """
(function() {
  try {
    var issues = [];
    var imgs = document.querySelectorAll('img:not([alt])');
    if (imgs.length) issues.push({ severity: 'medium', issue: imgs.length + ' image(s) missing alt text' });
    var inputs = document.querySelectorAll('input:not([aria-label]):not([id])');
    if (inputs.length) issues.push({ severity: 'high', issue: inputs.length + ' form field(s) without labels' });
    var buttons = 0;
    document.querySelectorAll('button').forEach(function(b) {
      if (!(b.innerText || '').trim() && !b.getAttribute('aria-label')) buttons++;
    });
    if (buttons) issues.push({ severity: 'high', issue: buttons + ' button(s) without accessible names' });
    if (!document.documentElement.lang) issues.push({ severity: 'low', issue: 'Missing <html lang> attribute' });
    if (!document.querySelector('h1')) issues.push({ severity: 'medium', issue: 'No H1 heading on the page' });
    var small = 0;
    document.querySelectorAll('a,button').forEach(function(el) {
      var r = el.getBoundingClientRect();
      if (r.width > 0 && (r.width < 44 || r.height < 44)) small++;
    });
    if (small) issues.push({ severity: 'medium', issue: small + ' touch target(s) smaller than 44px' });
    return JSON.stringify({ ok: true, issues: issues, score: Math.max(0, 100 - issues.length * 12) });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Canvas/WebGL/audio/font fingerprint defences (spec 17). */
    const val FINGERPRINT_PROTECTION = """
(function() {
  try {
    if (window.__novaFpGuard) return JSON.stringify({ ok: true, already: true });
    window.__novaFpGuard = true;
    var noise = function(v, amount) { return v + (Math.random() - 0.5) * amount; };

    var toDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function() {
      try {
        var ctx = this.getContext('2d');
        if (ctx && this.width > 0 && this.height > 0) {
          var data = ctx.getImageData(0, 0, Math.min(this.width, 50), Math.min(this.height, 50));
          for (var i = 0; i < data.data.length; i += 997) {
            data.data[i] = (data.data[i] + 1) % 256;
          }
          ctx.putImageData(data, 0, 0);
        }
      } catch (e) { }
      return toDataURL.apply(this, arguments);
    };

    var getImageData = CanvasRenderingContext2D.prototype.getImageData;
    CanvasRenderingContext2D.prototype.getImageData = function() {
      var result = getImageData.apply(this, arguments);
      try {
        for (var i = 0; i < result.data.length; i += 1499) {
          result.data[i] = (result.data[i] + 1) % 256;
        }
      } catch (e) { }
      return result;
    };

    if (window.WebGLRenderingContext) {
      var getParameter = WebGLRenderingContext.prototype.getParameter;
      WebGLRenderingContext.prototype.getParameter = function(p) {
        if (p === 37445) return 'Generic Renderer';
        if (p === 37446) return 'Generic GPU';
        return getParameter.apply(this, arguments);
      };
    }

    if (window.AnalyserNode) {
      var getFloatFrequencyData = AnalyserNode.prototype.getFloatFrequencyData;
      AnalyserNode.prototype.getFloatFrequencyData = function(array) {
        getFloatFrequencyData.apply(this, arguments);
        for (var i = 0; i < array.length; i += 89) array[i] = noise(array[i], 0.0002);
      };
    }

    try {
      Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 4; } });
      Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 4; } });
    } catch (e) { }
    return JSON.stringify({ ok: true });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Cosmetic ad hiding (spec 17 → cosmetic blocking). */
    const val COSMETIC_AD_BLOCK = """
(function() {
  try {
    var selectors = ['[id*="google_ads"]','[id^="ad-"]','[id$="-ad"]','[class*="advert"]','[class*="-ads"]','.ad-container','.ad-banner','.ads','.adsbygoogle','ins.adsbygoogle','[data-ad-slot]','[aria-label="advertisement" i]','iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]','iframe[src*="adservice"]','.sponsored-content','.promoted-content'];
    var hidden = 0;
    selectors.forEach(function(s) {
      try {
        document.querySelectorAll(s).forEach(function(el) {
          if (el && el.style && el.getAttribute('data-nova-hidden') !== '1') {
            el.style.setProperty('display', 'none', 'important');
            el.setAttribute('data-nova-hidden', '1');
            hidden++;
          }
        });
      } catch (e) { }
    });
    return JSON.stringify({ ok: true, hidden: hidden });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e), hidden: 0 });
  }
})();
"""

    /** Detects third-party tracker scripts present in the DOM. */
    const val DETECT_TRACKERS = """
(function() {
  try {
    var found = [];
    var host = location.hostname;
    document.querySelectorAll('script[src],img[src],iframe[src]').forEach(function(el) {
      try {
        var u = new URL(el.src, location.href);
        if (u.hostname && u.hostname !== host && found.indexOf(u.hostname) < 0 && found.length < 80) {
          found.push(u.hostname);
        }
      } catch (e) { }
    });
    return JSON.stringify({ ok: true, thirdParty: found, cookieCount: document.cookie ? document.cookie.split(';').length : 0 });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Reader-mode transformation applied in-page. */
    const val READER_MODE = """
(function() {
  try {
    if (window.__novaReader) {
      document.documentElement.innerHTML = window.__novaReaderOriginal;
      window.__novaReader = false;
      return JSON.stringify({ ok: true, enabled: false });
    }
    window.__novaReaderOriginal = document.documentElement.innerHTML;
    var article = document.querySelector('article') || document.querySelector('main') || document.body;
    var clone = article.cloneNode(true);
    clone.querySelectorAll('nav,header,footer,aside,script,style,iframe,form,.ad,.ads,.sidebar,.related,.comments,.share,.social').forEach(function(n) { n.remove(); });
    var title = document.title || '';
    document.body.innerHTML = '<div id="nova-reader"><h1>' + title.replace(/</g, '&lt;') + '</h1>' + clone.innerHTML + '</div>';
    var style = document.createElement('style');
    style.textContent = 'html,body{background:#0A0E1A !important;color:#F0F2F7 !important;} #nova-reader{max-width:44rem;margin:0 auto;padding:24px 18px 80px;font-size:18px;line-height:1.75;font-family:-apple-system,Roboto,sans-serif;} #nova-reader h1{font-size:28px;line-height:1.25;margin-bottom:18px;color:#fff;} #nova-reader h2,#nova-reader h3{color:#9CC7FF;margin-top:28px;} #nova-reader img{max-width:100%;height:auto;border-radius:12px;} #nova-reader a{color:#4A9EFF;} #nova-reader pre,#nova-reader code{background:#141929;padding:8px;border-radius:8px;overflow-x:auto;display:block;} #nova-reader blockquote{border-left:3px solid #4A9EFF;padding-left:14px;color:#B9C2D6;margin-left:0;}';
    document.head.appendChild(style);
    window.__novaReader = true;
    return JSON.stringify({ ok: true, enabled: true });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Captures the console for DevTools by proxying the console object. */
    const val CONSOLE_HOOK = """
(function() {
  try {
    if (window.__novaConsoleHooked) return JSON.stringify({ ok: true, already: true });
    window.__novaConsoleHooked = true;
    ['log', 'info', 'warn', 'error', 'debug'].forEach(function(level) {
      var original = console[level];
      console[level] = function() {
        try {
          var parts = [];
          for (var i = 0; i < arguments.length; i++) {
            var a = arguments[i];
            if (typeof a === 'object') {
              try { parts.push(JSON.stringify(a)); } catch (e) { parts.push(String(a)); }
            } else parts.push(String(a));
          }
          if (window.NovaBridge && window.NovaBridge.onConsole) {
            window.NovaBridge.onConsole(level, parts.join(' ').substring(0, 4000));
          }
        } catch (e) { }
        return original.apply(console, arguments);
      };
    });
    window.addEventListener('error', function(ev) {
      try {
        if (window.NovaBridge && window.NovaBridge.onConsole) {
          window.NovaBridge.onConsole('error', (ev.message || 'Error') + ' @ ' + (ev.filename || '') + ':' + (ev.lineno || 0));
        }
      } catch (e) { }
    });
    window.addEventListener('unhandledrejection', function(ev) {
      try {
        if (window.NovaBridge && window.NovaBridge.onConsole) {
          window.NovaBridge.onConsole('error', 'Unhandled promise rejection: ' + String(ev.reason).substring(0, 500));
        }
      } catch (e) { }
    });
    return JSON.stringify({ ok: true });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /* ------------------------- Parameterised actions ------------------------- */

    /** Clicks an element by CSS selector. */
    fun click(selector: String): String = """
(function() {
  try {
    var el = document.querySelector('${HtmlUtils.escapeJsString(selector)}');
    if (!el) return JSON.stringify({ ok: false, error: 'Element not found' });
    el.scrollIntoView({ block: 'center' });
    el.click();
    return JSON.stringify({ ok: true, text: (el.innerText || el.value || '').substring(0, 120) });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Clicks the first visible element whose text matches. */
    fun clickByText(text: String): String = """
(function() {
  try {
    var needle = '${HtmlUtils.escapeJsString(text)}'.toLowerCase();
    var nodes = document.querySelectorAll('a,button,[role=button],input[type=submit],input[type=button],li,span,div');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var label = ((el.innerText || el.value || el.getAttribute('aria-label') || '')).trim().toLowerCase();
      if (!label) continue;
      var r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) continue;
      if (label === needle || label.indexOf(needle) >= 0) {
        el.scrollIntoView({ block: 'center' });
        el.click();
        return JSON.stringify({ ok: true, matched: label.substring(0, 120) });
      }
    }
    return JSON.stringify({ ok: false, error: 'No clickable element matching "' + needle + '"' });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Types text into a field, dispatching the events frameworks expect. */
    fun typeText(selector: String, text: String): String = """
(function() {
  try {
    var el = document.querySelector('${HtmlUtils.escapeJsString(selector)}');
    if (!el) return JSON.stringify({ ok: false, error: 'Field not found' });
    el.focus();
    var value = '${HtmlUtils.escapeJsString(text)}';
    var setter = Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value');
    if (setter && setter.set) setter.set.call(el, value); else el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    return JSON.stringify({ ok: true });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    /** Fills a field located by its label/placeholder/name. */
    fun typeByLabel(label: String, text: String): String = """
(function() {
  try {
    var needle = '${HtmlUtils.escapeJsString(label)}'.toLowerCase();
    var fields = document.querySelectorAll('input,textarea');
    for (var i = 0; i < fields.length; i++) {
      var f = fields[i];
      var hint = ((f.name || '') + ' ' + (f.id || '') + ' ' + (f.placeholder || '') + ' ' + (f.getAttribute('aria-label') || '')).toLowerCase();
      if (f.id) {
        var lab = document.querySelector('label[for="' + CSS.escape(f.id) + '"]');
        if (lab) hint += ' ' + (lab.innerText || '').toLowerCase();
      }
      if (hint.indexOf(needle) >= 0) {
        f.focus();
        var value = '${HtmlUtils.escapeJsString(text)}';
        var setter = Object.getOwnPropertyDescriptor(f.constructor.prototype, 'value');
        if (setter && setter.set) setter.set.call(f, value); else f.value = value;
        f.dispatchEvent(new Event('input', { bubbles: true }));
        f.dispatchEvent(new Event('change', { bubbles: true }));
        return JSON.stringify({ ok: true });
      }
    }
    return JSON.stringify({ ok: false, error: 'No field matching "' + needle + '"' });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun selectOption(selector: String, option: String): String = """
(function() {
  try {
    var el = document.querySelector('${HtmlUtils.escapeJsString(selector)}');
    if (!el || el.tagName.toLowerCase() !== 'select') return JSON.stringify({ ok: false, error: 'Dropdown not found' });
    var needle = '${HtmlUtils.escapeJsString(option)}'.toLowerCase();
    for (var i = 0; i < el.options.length; i++) {
      var text = (el.options[i].text || '').toLowerCase();
      if (text === needle || text.indexOf(needle) >= 0) {
        el.selectedIndex = i;
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return JSON.stringify({ ok: true, selected: el.options[i].text });
      }
    }
    return JSON.stringify({ ok: false, error: 'Option not found' });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun setCheckbox(selector: String, checked: Boolean): String = """
(function() {
  try {
    var el = document.querySelector('${HtmlUtils.escapeJsString(selector)}');
    if (!el) return JSON.stringify({ ok: false, error: 'Checkbox not found' });
    if (el.checked !== $checked) el.click();
    return JSON.stringify({ ok: true, checked: el.checked });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun scrollBy(amount: Int): String = """
(function() {
  try {
    window.scrollBy({ top: $amount, behavior: 'smooth' });
    return JSON.stringify({ ok: true, scrollY: window.scrollY, max: document.body.scrollHeight });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun scrollToElement(selector: String): String = """
(function() {
  try {
    var el = document.querySelector('${HtmlUtils.escapeJsString(selector)}');
    if (!el) return JSON.stringify({ ok: false, error: 'Element not found' });
    el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    return JSON.stringify({ ok: true });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun elementExists(selector: String): String = """
(function() {
  try {
    var el = document.querySelector('${HtmlUtils.escapeJsString(selector)}');
    return JSON.stringify({ ok: true, exists: !!el, text: el ? (el.innerText || '').substring(0, 200) : '' });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun extractText(selector: String): String = """
(function() {
  try {
    var els = document.querySelectorAll('${HtmlUtils.escapeJsString(selector)}');
    if (!els.length) return JSON.stringify({ ok: false, error: 'No matching elements' });
    var out = [];
    for (var i = 0; i < els.length && i < 50; i++) {
      out.push((els[i].innerText || els[i].value || '').replace(/\s+/g, ' ').trim());
    }
    return JSON.stringify({ ok: true, values: out });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun findInPageHighlight(query: String): String = """
(function() {
  try {
    var q = '${HtmlUtils.escapeJsString(query)}';
    if (!q) return JSON.stringify({ ok: true, count: 0 });
    var count = (document.body.innerText.match(new RegExp(q.replace(/[.*+?^$'{}()|[\]\\]/g, '\\$&'), 'gi')) || []).length;
    return JSON.stringify({ ok: true, count: count });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e), count: 0 });
  }
})();
"""

    fun fillLogin(usernameSelector: String, username: String, passwordSelector: String, password: String): String = """
(function() {
  try {
    function setValue(sel, value) {
      var el = document.querySelector(sel);
      if (!el) return false;
      el.focus();
      var setter = Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value');
      if (setter && setter.set) setter.set.call(el, value); else el.value = value;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    }
    var u = setValue('${HtmlUtils.escapeJsString(usernameSelector)}', '${HtmlUtils.escapeJsString(username)}');
    var p = setValue('${HtmlUtils.escapeJsString(passwordSelector)}', '${HtmlUtils.escapeJsString(password)}');
    return JSON.stringify({ ok: u && p, username: u, password: p });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""

    fun setTextZoom(percent: Int): String = """
(function() {
  try {
    document.documentElement.style.setProperty('zoom', '${percent}%');
    return JSON.stringify({ ok: true });
  } catch (e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
"""
}
