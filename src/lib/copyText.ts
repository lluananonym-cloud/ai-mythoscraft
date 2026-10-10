/** Text in die Zwischenablage kopieren, auch auf Handys.
 *  navigator.clipboard fehlt oder scheitert in manchen mobilen Browsern und In-App-Ansichten (WebView, iOS ohne Fokus);
 *  dann kopieren wir über ein unsichtbares Textfeld. */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch { /* weiter mit dem Ausweg */ }
  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    // Schriftgröße 16px verhindert das Hineinzoomen auf iOS.
    ta.style.cssText = "position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;font-size:16px;";
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    ta.setSelectionRange(0, text.length);
    const ok = document.execCommand("copy");
    ta.remove();
    return ok;
  } catch {
    return false;
  }
}
