package com.nfcmanager.app.data.nfc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VCardParserTest {

    @Test fun `parses minimal vcard with FN TEL and EMAIL`() {
        val raw = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            TEL;TYPE=CELL:+15551234
            EMAIL:ada@example.com
            ORG:Analytical Engine
            END:VCARD
        """.trimIndent()

        val result = VCardParser.parse(raw)

        assertThat(result.displayName).isEqualTo("Ada Lovelace")
        assertThat(result.phones).containsExactly("+15551234")
        assertThat(result.emails).containsExactly("ada@example.com")
        assertThat(result.organization).isEqualTo("Analytical Engine")
    }

    @Test fun `falls back to structured N when FN absent`() {
        val raw = """
            BEGIN:VCARD
            VERSION:3.0
            N:Turing;Alan;;;
            END:VCARD
        """.trimIndent()

        val result = VCardParser.parse(raw)
        assertThat(result.displayName).isEqualTo("Alan Turing")
    }

    @Test fun `tolerates CRLF endings and blank lines`() {
        val raw = "BEGIN:VCARD\r\n\r\nFN:Grace Hopper\r\nEND:VCARD\r\n"
        val result = VCardParser.parse(raw)
        assertThat(result.displayName).isEqualTo("Grace Hopper")
    }

    @Test fun `unfolds continuation lines per RFC 2425`() {
        val raw = "BEGIN:VCARD\r\nFN:John \r\n Smith\r\nEND:VCARD"
        val result = VCardParser.parse(raw)
        assertThat(result.displayName).isEqualTo("John Smith")
    }

    @Test fun `empty input returns all nulls and empty lists`() {
        val result = VCardParser.parse("")
        assertThat(result.displayName).isNull()
        assertThat(result.phones).isEmpty()
        assertThat(result.emails).isEmpty()
        assertThat(result.organization).isNull()
    }

    @Test fun `malformed lines are skipped`() {
        val raw = """
            GIBBERISH
            BEGIN:VCARD
            FN:Linus Torvalds
            not a key value pair
            END:VCARD
        """.trimIndent()
        val result = VCardParser.parse(raw)
        assertThat(result.displayName).isEqualTo("Linus Torvalds")
    }

    @Test fun `multiple phones are all collected`() {
        val raw = """
            BEGIN:VCARD
            FN:Name
            TEL:111
            TEL;TYPE=HOME:222
            END:VCARD
        """.trimIndent()
        val result = VCardParser.parse(raw)
        assertThat(result.phones).containsExactly("111", "222")
    }
}
