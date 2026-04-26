package com.nfcmanager.app.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlValidatorTest {

    private val validator = UrlValidator()

    @Test fun `https urls with host pass through`() {
        assertThat(validator.sanitize("https://example.com/path?q=1"))
            .isEqualTo("https://example.com/path?q=1")
    }

    @Test fun `http urls with host pass through`() {
        assertThat(validator.sanitize("http://example.com"))
            .isEqualTo("http://example.com")
    }

    @Test fun `javascript scheme is rejected`() {
        assertThat(validator.sanitize("javascript:alert(1)")).isNull()
    }

    @Test fun `intent scheme is rejected`() {
        assertThat(validator.sanitize("intent://scan/#Intent;package=com.x;end")).isNull()
    }

    @Test fun `file scheme is rejected`() {
        assertThat(validator.sanitize("file:///etc/hosts")).isNull()
    }

    @Test fun `data scheme is rejected`() {
        assertThat(validator.sanitize("data:text/html,<script>alert(1)</script>")).isNull()
    }

    @Test fun `http with no host is rejected`() {
        assertThat(validator.sanitize("http://")).isNull()
    }

    @Test fun `whitespace in input is rejected`() {
        assertThat(validator.sanitize("https://exam ple.com")).isNull()
    }

    @Test fun `tel mailto sms and geo schemes are allowed`() {
        assertThat(validator.sanitize("tel:+15551234")).isEqualTo("tel:+15551234")
        assertThat(validator.sanitize("mailto:a@b.com")).isEqualTo("mailto:a@b.com")
        assertThat(validator.sanitize("sms:+15551234")).isEqualTo("sms:+15551234")
        assertThat(validator.sanitize("geo:37.7,-122.4")).isEqualTo("geo:37.7,-122.4")
    }

    @Test fun `very long urls are rejected`() {
        val url = "https://example.com/" + "a".repeat(4096)
        assertThat(validator.sanitize(url)).isNull()
    }

    @Test fun `control characters are rejected`() {
        assertThat(validator.sanitize("https://example.com/\u0000")).isNull()
    }

    @Test fun `domainOf returns host`() {
        assertThat(validator.domainOf("https://sub.example.com/path"))
            .isEqualTo("sub.example.com")
    }

    // --- normalizeForUserInput ---

    @Test fun `normalize bare domain prepends https`() {
        assertThat(validator.normalizeForUserInput("google.com"))
            .isEqualTo("https://google.com")
    }

    @Test fun `normalize www-prefixed domain prepends https`() {
        assertThat(validator.normalizeForUserInput("www.github.com"))
            .isEqualTo("https://www.github.com")
    }

    @Test fun `normalize preserves existing http scheme`() {
        assertThat(validator.normalizeForUserInput("http://example.org"))
            .isEqualTo("http://example.org")
    }

    @Test fun `normalize preserves existing https scheme`() {
        assertThat(validator.normalizeForUserInput("https://google.com"))
            .isEqualTo("https://google.com")
    }

    @Test fun `normalize keeps path and query`() {
        assertThat(validator.normalizeForUserInput("example.com/foo?x=1"))
            .isEqualTo("https://example.com/foo?x=1")
    }

    @Test fun `normalize trims surrounding whitespace`() {
        assertThat(validator.normalizeForUserInput("   google.com   "))
            .isEqualTo("https://google.com")
    }

    @Test fun `normalize rejects random text without dot`() {
        assertThat(validator.normalizeForUserInput("random text")).isNull()
        assertThat(validator.normalizeForUserInput("randomtext")).isNull()
    }

    @Test fun `normalize rejects malicious schemes`() {
        assertThat(validator.normalizeForUserInput("javascript:alert(1)")).isNull()
        assertThat(validator.normalizeForUserInput("file:///etc/passwd")).isNull()
        assertThat(validator.normalizeForUserInput("data:text/html,hi")).isNull()
    }

    @Test fun `normalize rejects host-shaped garbage`() {
        assertThat(validator.normalizeForUserInput("...")).isNull()
        assertThat(validator.normalizeForUserInput(".com")).isNull()
        assertThat(validator.normalizeForUserInput("-bad.com")).isNull()
    }

    @Test fun `normalize rejects empty input`() {
        assertThat(validator.normalizeForUserInput("")).isNull()
        assertThat(validator.normalizeForUserInput("   ")).isNull()
    }
}
