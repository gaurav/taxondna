@file:Suppress("ktlint:standard:package-name")

package com.ggvaidya.TaxonDNA.Common.DNA

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class SettingsTest :
    FunSpec({
        // Save and restore the default accurateTo value around the entire spec,
        // since Settings uses static state.
        var savedAccurateTo: Double = 0.0

        beforeSpec {
            savedAccurateTo = Settings.getAccurateTo()
        }

        afterSpec {
            Settings.setAccurateTo(savedAccurateTo)
        }

        context("accurateTo") {
            test("setAccurateToDigits(5) gives 0.00001") {
                Settings.setAccurateToDigits(5)
                Settings.getAccurateTo() shouldBe (0.00001 plusOrMinus 1e-10)
            }

            test("setAccurateTo changes getAccurateTo") {
                Settings.setAccurateTo(0.001)
                Settings.getAccurateTo() shouldBe (0.001 plusOrMinus 1e-10)
            }

            test("setAccurateToDigits(4) gives 0.0001") {
                Settings.setAccurateToDigits(4)
                Settings.getAccurateTo() shouldBe (0.0001 plusOrMinus 1e-10)
            }
        }

        context("makeLongFromDouble and makeDoubleFromLong") {
            beforeTest {
                Settings.setAccurateToDigits(5) // accurateTo = 100000
            }

            test("round-trips a double") {
                val original = 0.12345
                val asLong = Settings.makeLongFromDouble(original)
                val back = Settings.makeDoubleFromLong(asLong)
                back shouldBe (original plusOrMinus 0.00001)
            }

            test("makeLongFromDouble scales by accurateTo") {
                Settings.setAccurateTo(0.01) // accurateTo = 100
                Settings.makeLongFromDouble(0.5) shouldBe 50L
            }

            test("makeDoubleFromLong reverses makeLongFromDouble") {
                Settings.setAccurateTo(0.01) // accurateTo = 100
                Settings.makeDoubleFromLong(50L) shouldBe (0.5 plusOrMinus 1e-10)
            }
        }

        context("percentage") {
            beforeTest {
                Settings.setAccurateToDigits(5) // accurateTo = 100000
            }

            test("50% of 100") {
                Settings.percentage(50.0, 100.0) shouldBe (50.0 plusOrMinus 0.01)
            }

            test("x% of 0 is 0") {
                Settings.percentage(5.0, 0.0) shouldBe 0.0
            }

            test("1 out of 3") {
                Settings.percentage(1.0, 3.0) shouldBe (33.33 plusOrMinus 0.01)
            }
        }

        context("roundOff") {
            beforeTest {
                Settings.setAccurateTo(0.01) // accurateTo = 100
            }

            test("rounds to accuracy level") {
                Settings.roundOff(0.126) shouldBe (0.12 plusOrMinus 0.001)
            }

            test("exact values are unchanged") {
                Settings.roundOff(0.25) shouldBe (0.25 plusOrMinus 1e-10)
            }
        }

        context("identical") {
            beforeTest {
                Settings.setAccurateTo(0.01) // accurateTo = 100
            }

            test("same values are identical") {
                Settings.identical(0.5, 0.5) shouldBe true
            }

            test("values within accuracy are identical") {
                Settings.identical(0.501, 0.509) shouldBe true
            }

            test("values differing beyond accuracy are not identical") {
                Settings.identical(0.50, 0.52) shouldBe false
            }
        }
    })
