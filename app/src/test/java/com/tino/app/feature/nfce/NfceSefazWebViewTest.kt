package com.tino.app.feature.nfce

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfceSefazWebViewTest {
    @Test
    fun allowsOnlyOfficialHttpsSefazHosts() {
        assertTrue(NfceSefazOfficial.allowsTopNavigation("https://portal.sefaz.pi.gov.br/nfce"))
        assertTrue(NfceSefazOfficial.allowsTopNavigation("https://webas.sefaz.pi.gov.br/consulta"))
        assertFalse(NfceSefazOfficial.allowsTopNavigation("http://portal.sefaz.pi.gov.br/nfce"))
        assertFalse(NfceSefazOfficial.allowsTopNavigation("https://example.com/nfce"))
        assertTrue(NfceSefazOfficial.allowsWebViewNavigation(false, "https://hcaptcha.com/challenge"))
        assertFalse(NfceSefazOfficial.allowsWebViewNavigation(true, "https://hcaptcha.com/challenge"))
    }

    @Test
    fun autofillOnlyFillsTheAccessKeyAndDoesNotSubmit() {
        val script = autofillAccessKeyScript("22260831838128000748650120002104021782591975")

        assertTrue(script.contains("input[name=\"chave\"]"))
        assertTrue(script.contains("dispatchEvent(new Event('input'"))
        assertTrue(script.contains("dispatchEvent(new Event('change'"))
        assertFalse(script.contains(".submit()"))
        assertFalse(script.contains("form.submit"))
    }

    @Test
    fun submitMonitoringRequiresHumanPageActionAndResultSendsOnlyDomHtml() {
        assertTrue(monitorNfceQuerySubmitScript.contains("document.addEventListener('submit'"))
        assertTrue(monitorNfceQuerySubmitScript.contains("consultar"))
        assertFalse(monitorNfceQuerySubmitScript.contains("click()"))
        assertFalse(monitorNfceQuerySubmitScript.contains("submit()"))

        assertTrue(detectNfceResultScript.contains("document.documentElement.outerHTML"))
        assertTrue(detectNfceResultScript.contains("#tbLeiauteDANFENFCe"))
        assertFalse(detectNfceResultScript.contains("document.cookie"))
        assertFalse(detectNfceResultScript.contains("localStorage"))
    }
}
