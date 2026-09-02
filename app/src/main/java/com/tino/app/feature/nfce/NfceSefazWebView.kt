package com.tino.app.feature.nfce

import java.net.URI

object NfceSefazOfficial {
    const val URL = "https://portal.sefaz.pi.gov.br/nfce"
    private val hosts = setOf(
        "portal.sefaz.pi.gov.br",
        "www.sefaz.pi.gov.br",
        "sefaz.pi.gov.br",
        "webas.sefaz.pi.gov.br",
    )

    fun allowsTopNavigation(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && hosts.contains(uri.host?.lowercase())
    }.getOrDefault(false)

    fun allowsWebViewNavigation(isForMainFrame: Boolean, value: String): Boolean =
        !isForMainFrame || allowsTopNavigation(value)
}

fun autofillAccessKeyScript(accessKey: String): String {
    val encoded = accessKey.toJavascriptStringLiteral()
    return """
        (function() {
          const value = $encoded;
          const input = document.querySelector('input[name="chave"]')
            || document.querySelector('input[placeholder*="Chave de acesso" i]');
          if (!input) {
            window.TinoNfceBridge?.postMessage(JSON.stringify({type:'autofill',status:'failed'}));
            return;
          }
          const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
          setter?.call(input, value);
          input.dispatchEvent(new Event('input', {bubbles:true}));
          input.dispatchEvent(new Event('change', {bubbles:true}));
          window.TinoNfceBridge?.postMessage(JSON.stringify({type:'autofill',status:'success'}));
        })();
        true;
    """.trimIndent()
}

private fun String.toJavascriptStringLiteral(): String = buildString(length + 2) {
    append('\'')
    for (character in this@toJavascriptStringLiteral) {
        when (character) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> append(character)
        }
    }
    append('\'')
}

val monitorNfceQuerySubmitScript: String = """
    (function() {
      if (window.__tinoNfceSubmitMonitor) return;
      window.__tinoNfceSubmitMonitor = true;
      document.addEventListener('submit', function() {
        window.TinoNfceBridge?.postMessage(JSON.stringify({type:'query-submit'}));
      }, true);
      document.addEventListener('click', function(event) {
        const target = event.target?.closest?.('button,input[type="button"],input[type="submit"]');
        const label = target?.innerText || target?.value || '';
        if (/consultar/i.test(label)) {
          window.TinoNfceBridge?.postMessage(JSON.stringify({type:'query-submit'}));
        }
      }, true);
    })();
    true;
""".trimIndent()

val detectNfceResultScript: String = """
    (function() {
      if (window.__tinoNfceResultWatcher) return;
      window.__tinoNfceResultWatcher = true;
      let captured = false;
      const capture = function() {
        if (captured) return;
        const text = document.body?.innerText || '';
        const danfe = document.querySelector('#tbLeiauteDANFENFCe');
        const items = document.querySelectorAll('tr[id^="Item +"]');
        const hasInvoice = /DANFE NFC-e|CHAVE DE ACESSO|Qtd\. Total de Itens/i.test(text);
        const isCaptcha = /Resolva o problema|I am human/i.test(text) && !hasInvoice;
        if (danfe && items.length > 0 && hasInvoice && !isCaptcha) {
          captured = true;
          observer.disconnect();
          window.clearInterval(interval);
          window.TinoNfceBridge?.postMessage(JSON.stringify({
            type:'result-dom',
            url:window.location.href,
            title:document.title,
            html:document.documentElement.outerHTML
          }));
        }
      };
      const observer = new MutationObserver(capture);
      observer.observe(document.documentElement, {childList:true,subtree:true});
      const interval = window.setInterval(capture, 500);
      capture();
    })();
    true;
""".trimIndent()
