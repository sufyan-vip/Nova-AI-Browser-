package com.nova.browser.features.browser.web

/**
 * The nova://home start page. Rendered inside the WebView so it scrolls and
 * behaves exactly like a real page; interactive Compose chrome sits above it.
 */
object HomePage {

    fun html(greeting: String = "", tip: String = ""): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val resolvedGreeting = greeting.ifBlank {
            when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Working late"
            }
        }
        val resolvedTip = tip.ifBlank { TIPS.random() }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>NOVA</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0; min-height: 100vh; padding: 18vh 20px 140px;
    background:
      radial-gradient(900px 500px at 15% -5%, rgba(74,158,255,0.18), transparent 60%),
      radial-gradient(700px 500px at 90% 10%, rgba(179,136,255,0.14), transparent 60%),
      #0A0E1A;
    color: #F2F4F9; font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
  }
  .wrap { max-width: 620px; margin: 0 auto; }
  .logo {
    display: flex; align-items: center; gap: 12px; margin-bottom: 26px;
  }
  .mark {
    width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center;
    background: linear-gradient(135deg, rgba(74,158,255,0.35), rgba(0,229,255,0.18));
    border: 1px solid rgba(74,158,255,0.5); font-weight: 700; font-size: 20px; color: #9CC7FF;
  }
  .name { font-size: 15px; letter-spacing: 4px; color: rgba(255,255,255,0.45); font-weight: 600; }
  h1 { font-size: 30px; line-height: 1.25; margin: 0 0 10px; font-weight: 650; }
  h1 span { background: linear-gradient(90deg, #4A9EFF, #00E5FF, #B388FF);
            -webkit-background-clip: text; background-clip: text; color: transparent; }
  .sub { color: rgba(255,255,255,0.6); font-size: 15px; line-height: 1.6; margin: 0 0 30px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
  .card {
    background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.09);
    border-radius: 18px; padding: 16px; min-height: 96px; display: flex; flex-direction: column;
    justify-content: space-between; transition: transform .18s ease, background .18s ease;
  }
  .card:active { transform: scale(0.975); background: rgba(255,255,255,0.09); }
  .card .ico { font-size: 20px; margin-bottom: 10px; }
  .card .t { font-size: 14px; font-weight: 600; }
  .card .d { font-size: 12px; color: rgba(255,255,255,0.45); margin-top: 3px; }
  .tip {
    margin-top: 26px; padding: 14px 16px; border-radius: 16px; font-size: 13px; line-height: 1.6;
    background: rgba(105,240,174,0.08); border: 1px solid rgba(105,240,174,0.22); color: #A8F5CE;
  }
  .tip b { color: #69F0AE; }
  .foot { margin-top: 30px; font-size: 11px; letter-spacing: 2px; color: rgba(255,255,255,0.22); text-align: center; }
  @media (min-height: 800px) { body { padding-top: 22vh; } }
</style>
</head>
<body>
  <div class="wrap">
    <div class="logo">
      <div class="mark">N</div>
      <div class="name">NOVA AI BROWSER</div>
    </div>
    <h1>$resolvedGreeting.<br><span>What should we explore?</span></h1>
    <p class="sub">Type a question, a URL, or a task in the address bar. NOVA can read the page, research across sources, automate steps, and write for you.</p>
    <div class="grid">
      <div class="card"><div class="ico">🔎</div><div><div class="t">Ask anything</div><div class="d">AI answers with sources</div></div></div>
      <div class="card"><div class="ico">📄</div><div><div class="t">Summarize a page</div><div class="d">TL;DR in one tap</div></div></div>
      <div class="card"><div class="ico">🤖</div><div><div class="t">Run an agent</div><div class="d">Multi-step automation</div></div></div>
      <div class="card"><div class="ico">🛡️</div><div><div class="t">Private by default</div><div class="d">Trackers blocked</div></div></div>
    </div>
    <div class="tip"><b>Tip:</b> $resolvedTip</div>
    <div class="foot">LOCAL-FIRST · ENCRYPTED · YOURS</div>
  </div>
</body>
</html>
"""
    }

    private val TIPS = listOf(
        "Tap the glowing orb to open the AI sidebar on any page.",
        "Long-press the tab counter to open the vertical tab list.",
        "Ask the agent to \"find the cheapest flight and summarize the options\".",
        "Turn on Reader mode from the menu for distraction-free articles.",
        "Your passwords are encrypted with the Android Keystore and never sent to any AI.",
        "Use Research mode to compare several sources into one cited report.",
        "DevTools includes a console, network inspector and an AI debugger.",
        "Set a daily token budget in Settings → AI to keep costs predictable."
    )
}
