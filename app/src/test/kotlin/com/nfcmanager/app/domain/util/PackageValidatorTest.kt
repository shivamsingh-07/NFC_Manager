package com.nfcmanager.app.domain.util

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

/**
 * Focused tests for [PackageValidator.isValidFormat]. The installation + label
 * lookups require a real PackageManager so we cover those with integration/UI
 * tests rather than pure JVM unit tests.
 */
class PackageValidatorTest {

    private val validator = PackageValidator(mockk(relaxed = true))

    @Test fun `valid package names pass format`() {
        assertThat(validator.isValidFormat("com.example.app")).isTrue()
        assertThat(validator.isValidFormat("io.my_org.app")).isTrue()
        assertThat(validator.isValidFormat("a.b.c")).isTrue()
    }

    @Test fun `single-segment names are rejected`() {
        assertThat(validator.isValidFormat("example")).isFalse()
    }

    @Test fun `blank and empty names are rejected`() {
        assertThat(validator.isValidFormat("")).isFalse()
        assertThat(validator.isValidFormat("   ")).isFalse()
    }

    @Test fun `leading digit segments are rejected`() {
        assertThat(validator.isValidFormat("com.0example.app")).isFalse()
        assertThat(validator.isValidFormat("1com.example.app")).isFalse()
    }

    @Test fun `spaces and special chars are rejected`() {
        assertThat(validator.isValidFormat("com.exa mple.app")).isFalse()
        assertThat(validator.isValidFormat("com.example.app;rm -rf")).isFalse()
        assertThat(validator.isValidFormat("com.example/app")).isFalse()
    }

    @Test fun `trailing dot is rejected`() {
        assertThat(validator.isValidFormat("com.example.")).isFalse()
    }

    @Test fun `over-long names are rejected`() {
        val name = "com." + "a".repeat(260)
        assertThat(validator.isValidFormat(name)).isFalse()
    }
}
