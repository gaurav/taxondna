@file:Suppress("ktlint:standard:package-name")

package com.ggvaidya.TaxonDNA.Common.DNA

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe

class FromToPairTest :
    FunSpec({
        context("constructor validation") {
            test("rejects from = 0") {
                shouldThrow<RuntimeException> { FromToPair(0, 5) }
            }

            test("rejects to = 0") {
                shouldThrow<RuntimeException> { FromToPair(1, 0) }
            }

            test("rejects negative from") {
                shouldThrow<RuntimeException> { FromToPair(-1, 5) }
            }

            test("rejects negative to") {
                shouldThrow<RuntimeException> { FromToPair(1, -3) }
            }

            test("rejects to < from") {
                shouldThrow<RuntimeException> { FromToPair(5, 3) }
            }

            test("accepts from == to") {
                val ftp = FromToPair(3, 3)
                ftp.from shouldBe 3
                ftp.to shouldBe 3
            }

            test("accepts valid range") {
                val ftp = FromToPair(1, 10)
                ftp.from shouldBe 1
                ftp.to shouldBe 10
            }
        }

        context("copy constructor") {
            test("copies from and to") {
                val original = FromToPair(3, 7)
                val copy = FromToPair(original)
                copy.from shouldBe 3
                copy.to shouldBe 7
            }

            test("copy is independent of original") {
                val original = FromToPair(3, 7)
                val copy = FromToPair(original)
                copy.from = 4
                original.from shouldBe 3
            }
        }

        context("compareTo") {
            test("returns negative when this.from < other.from") {
                val a = FromToPair(1, 5)
                val b = FromToPair(3, 5)
                a shouldBeLessThan b
            }

            test("returns positive when this.from > other.from") {
                val a = FromToPair(5, 10)
                val b = FromToPair(2, 10)
                a shouldBeGreaterThan b
            }

            test("returns zero when from values are equal") {
                val a = FromToPair(3, 5)
                val b = FromToPair(3, 10)
                a.compareTo(b) shouldBe 0
            }
        }

        context("toString") {
            test("single position shows just the number") {
                FromToPair(5, 5).toString() shouldBe "5"
            }

            test("range shows 'from to to'") {
                FromToPair(3, 7).toString() shouldBe "3 to 7"
            }
        }

        context("overlaps") {
            test("non-overlapping ranges return false") {
                val a = FromToPair(1, 3)
                val b = FromToPair(5, 7)
                a.overlaps(b) shouldBe false
            }

            test("adjacent ranges do not overlap") {
                val a = FromToPair(1, 3)
                val b = FromToPair(4, 6)
                a.overlaps(b) shouldBe false
            }

            test("touching endpoints overlap") {
                val a = FromToPair(1, 5)
                val b = FromToPair(5, 10)
                a.overlaps(b) shouldBe true
            }

            test("contained range overlaps") {
                val a = FromToPair(1, 10)
                val b = FromToPair(3, 7)
                a.overlaps(b) shouldBe true
            }

            test("partial overlap returns true") {
                val a = FromToPair(1, 5)
                val b = FromToPair(3, 8)
                a.overlaps(b) shouldBe true
            }

            test("identical ranges overlap") {
                val a = FromToPair(3, 7)
                val b = FromToPair(3, 7)
                a.overlaps(b) shouldBe true
            }
        }
    })
