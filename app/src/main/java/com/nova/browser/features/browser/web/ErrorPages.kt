package com.nova.browser.features.browser.web

import com.nova.browser.core.utils.HtmlUtils

/** Custom NOVA-branded error / info pages rendered inside the WebView. */
object ErrorPages {

    private fun page(icon: String, title: String, message: String, detail: String?, accent: String = "#4A9EFF"): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>$title</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
    background: radial-gradient(1200px 600px at 50% -10%, #16203a 0%, #0A0E1A 60%);
    color: #F2F4F9; font-family: -apple-system, "Segoe UI", Roboto, sans-serif; padding: 24px;
  }
  .card {
    max-width: 460px; width: 100%; background: rgba(255,255,255,0.05);
    border: 1px solid rgba(255,255,255,0.10); border-radius: 24px; padding: 32px 26px;
    backdrop-filter: blur(20px); text-align: center;
    box-shadow: 0 10px 40px rgba(0,0,0,0.45);
  }
  .icon {
    width: 68px; height: 68px; margin: 0 auto 20px; border-radius: 50%;
    display: flex; align-items: center; justify-content: center; font-size: 30px;
    background: ${accent}22; border: 1px solid ${accent}55;
  }
  h1 { font-size: 21px; margin: 0 0 10px; font-weight: 600; }
  p { font-size: 15px; line-height: 1.6; color: rgba(255,255,255,0.72); margin: 0 0 18px; }
  code {
    display: block; font-size: 12px; color: rgba(255,255,255,0.5); word-break: break-all;
    background: rgba(0,0,0,0.3); padding: 10px 12px; border-radius: 10px; margin-bottom: 20px;
  }
  button {
    font: inherit; font-size: 15px; font-weight: 500; color: $accent; cursor: pointer;
    background: ${accent}22; border: 1px solid ${accent}66; border-radius: 12px;
    padding: 13px 26px; min-height: 48px; width: 100%;
  }
  button:active { transform: scale(0.98); }
  .brand { margin-top: 22px; font-size: 12px; letter-spacing: 2px; color: rgba(255,255,255,0.28); }
</style>
</head>
<body>
  <div class="card">
    <div class="icon">$icon</div>
    <h1>${HtmlUtils.escape(title)}</h1>
    <p>${HtmlUtils.escape(message)}</p>
    ${if (detail != null) "<code>${HtmlUtils.escape(detail)}</code>" else ""}
    <button onclick="location.reload()">Try again</button>
    <div class="brand">NOVA AI BROWSER</div>
  </div>
</body>
</html>
"""

    fun network(url: String, description: String): String = page(
        icon = "⚡",
        title = "Can't reach this page",
        message = "NOVA couldn't load this site. Check your connection, or the address may be wrong.",
        detail = "$url\n$description"
    )

    fun notFound(url: String): String = page(
        icon = "🔍",
        title = "Page not found (404)",
        message = "The page you requested doesn't exist on this server. It may have been moved or deleted.",
        detail = url,
        accent = "#FFD740"
    )

    fun offline(url: String): String = page(
        icon = "📡",
        title = "You're offline",
        message = "NOVA can't load this page because there's no internet connection. Reconnect and try again.",
        detail = url,
        accent = "#FFD740"
    )

    fun sslWarning(url: String, reason: String): String = page(
        icon = "🔒",
        title = "Your connection isn't private",
        message = "$reason Attackers might be trying to steal information from this site.",
        detail = url,
        accent = "#FF5252"
    )

    fun blocked(url: String): String = page(
        icon = "🛡️",
        title = "Blocked by NOVA",
        message = "This page was blocked because it matched a tracker or malware list.",
        detail = url,
        accent = "#FF5252"
    )
}
