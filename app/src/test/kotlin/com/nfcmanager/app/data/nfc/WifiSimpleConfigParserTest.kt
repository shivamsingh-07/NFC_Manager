package com.nfcmanager.app.data.nfc

import com.google.common.truth.Truth.assertThat
import com.nfcmanager.app.domain.model.TagPayload
import java.nio.ByteBuffer
import org.junit.Test

class WifiSimpleConfigParserTest {

    @Test fun `parses WIFI URI form WPA with password`() {
        val raw = "WIFI:T:WPA;S:MyNet;P:s3cret;H:false;;"
        val result = WifiSimpleConfigParser.parseUriForm(raw)
        assertThat(result).isNotNull()
        assertThat(result!!.ssid).isEqualTo("MyNet")
        assertThat(result.password).isEqualTo("s3cret")
        assertThat(result.security).isEqualTo(TagPayload.WiFi.Security.WPA_WPA2_PSK)
        assertThat(result.hidden).isFalse()
    }

    @Test fun `parses open WIFI URI without password`() {
        val raw = "WIFI:T:nopass;S:Guest;;"
        val result = WifiSimpleConfigParser.parseUriForm(raw)!!
        assertThat(result.ssid).isEqualTo("Guest")
        assertThat(result.password).isNull()
        assertThat(result.security).isEqualTo(TagPayload.WiFi.Security.OPEN)
    }

    @Test fun `handles escaped special characters`() {
        val raw = "WIFI:T:WPA;S:Net\\;with\\:colons;P:pa\\;ss;;"
        val result = WifiSimpleConfigParser.parseUriForm(raw)!!
        assertThat(result.ssid).isEqualTo("Net;with:colons")
        assertThat(result.password).isEqualTo("pa;ss")
    }

    @Test fun `returns null for wrong scheme`() {
        assertThat(WifiSimpleConfigParser.parseUriForm("https://example.com")).isNull()
    }

    @Test fun `returns null when SSID is missing`() {
        assertThat(WifiSimpleConfigParser.parseUriForm("WIFI:T:WPA;P:x;;")).isNull()
    }

    @Test fun `parses WPA3 SAE`() {
        val raw = "WIFI:T:SAE;S:Modern;P:xyz;;"
        val result = WifiSimpleConfigParser.parseUriForm(raw)!!
        assertThat(result.security).isEqualTo(TagPayload.WiFi.Security.WPA3_SAE)
    }

    @Test fun `parses WSC TLV credential bytes`() {
        // Build a minimal WSC payload: Credential(0x100E) containing
        // SSID(0x1045) "Net", NetworkKey(0x1027) "pass", Auth(0x1003) WPA2_PSK.
        val ssid = "Net".toByteArray(Charsets.UTF_8)
        val key = "pass".toByteArray(Charsets.UTF_8)
        val inner = ByteBuffer.allocate(256).apply {
            putShort(0x1045.toShort()); putShort(ssid.size.toShort()); put(ssid)
            putShort(0x1027.toShort()); putShort(key.size.toShort()); put(key)
            putShort(0x1003.toShort()); putShort(2.toShort()); put(byteArrayOf(0x00, 0x20))
        }
        val innerBytes = inner.array().copyOf(inner.position())
        val outer = ByteBuffer.allocate(innerBytes.size + 4).apply {
            putShort(0x100E.toShort()); putShort(innerBytes.size.toShort()); put(innerBytes)
        }
        val bytes = outer.array().copyOf(outer.position())

        val result = WifiSimpleConfigParser.parse(bytes)!!
        assertThat(result.ssid).isEqualTo("Net")
        assertThat(result.password).isEqualTo("pass")
        assertThat(result.security).isEqualTo(TagPayload.WiFi.Security.WPA_WPA2_PSK)
    }

    @Test fun `returns null when credential is missing`() {
        assertThat(WifiSimpleConfigParser.parse(ByteArray(0))).isNull()
    }
}
