package com.leanbitlab.leantype.gif.net

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UrlFilterTest {
    @Test
    fun testValidKlipyUrls() {
        assertNotNull(UrlFilter.validateUrl("https://api.klipy.com/api/v1/samplekey/gifs/search"))
        assertNotNull(UrlFilter.validateUrl("https://static.klipy.com/ii/123/45/67/sample.gif"))
        assertNotNull(UrlFilter.validateUrl("https://static1.klipy.com/ii/123/45/67/sample.gif"))
        assertNotNull(UrlFilter.validateUrl("https://static2.klipy.com/ii/123/45/67/sample.webp"))
        assertNotNull(UrlFilter.validateUrl("https://klipy.co/sample.gif"))
    }

    @Test
    fun testValidGiphyUrls() {
        assertNotNull(UrlFilter.validateUrl("https://api.giphy.com/v1/gifs/search"))
        assertNotNull(UrlFilter.validateUrl("https://media.giphy.com/media/xyz/giphy.gif"))
    }

    @Test
    fun testRejectInvalidUrls() {
        assertNull(UrlFilter.validateUrl("http://api.klipy.com/v1")) // non-https
        assertNull(UrlFilter.validateUrl("https://malicious.com/hack.gif"))
        assertNull(UrlFilter.validateUrl("https://klipy.com:8080/sample.gif")) // non-443
    }
}
