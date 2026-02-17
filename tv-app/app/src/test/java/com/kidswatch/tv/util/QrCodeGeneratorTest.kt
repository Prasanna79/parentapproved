package com.kidswatch.tv.util

import org.junit.Assert.*
import org.junit.Test

class QrCodeGeneratorTest {

    // Note: These tests use ZXing's QRCodeWriter which works in JVM tests,
    // but Bitmap creation requires Android. We test the URL builder instead.

    @Test
    fun buildConnectUrl_formatsCorrectly() {
        val url = NetworkUtils.buildConnectUrl("192.168.1.100", 8080)
        assertEquals("http://192.168.1.100:8080", url)
    }

    @Test
    fun buildConnectUrl_differentPort() {
        val url = NetworkUtils.buildConnectUrl("10.0.0.1", 9090)
        assertEquals("http://10.0.0.1:9090", url)
    }

    @Test
    fun buildConnectUrl_defaultPort() {
        val url = NetworkUtils.buildConnectUrl("172.16.0.5")
        assertEquals("http://172.16.0.5:8080", url)
    }
}
