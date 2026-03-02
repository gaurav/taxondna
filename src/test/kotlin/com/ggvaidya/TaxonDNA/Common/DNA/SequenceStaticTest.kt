@file:Suppress("ktlint:standard:package-name")

package com.ggvaidya.TaxonDNA.Common.DNA

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SequenceStaticTest :
    FunSpec({
        context("isValid") {
            test("standard bases are valid") {
                for (ch in listOf('A', 'C', 'T', 'G')) {
                    Sequence.isValid(ch) shouldBe true
                }
            }

            test("ambiguity codes are valid") {
                for (ch in listOf('R', 'Y', 'K', 'M', 'S', 'N', 'B', 'D', 'H', 'V', 'W')) {
                    Sequence.isValid(ch) shouldBe true
                }
            }

            test("gap and missing characters are valid") {
                Sequence.isValid('-') shouldBe true
                Sequence.isValid('?') shouldBe true
                Sequence.isValid('_') shouldBe true
            }

            test("uppercase interior letters are valid via case conversion") {
                // Lowercase letters between 'a' and 'z' (exclusive) get uppercased.
                // 'b' -> 'B' which is valid; 'y' -> 'Y' which is valid.
                Sequence.isValid('b') shouldBe true
                Sequence.isValid('y') shouldBe true
            }

            test("'a' and 'z' are NOT converted due to strict inequality bug") {
                // The code uses (ch > 'a' && ch < 'z'), so 'a' and 'z' are excluded
                // from uppercase conversion. 'a' (lowercase) doesn't match any switch case.
                Sequence.isValid('a') shouldBe false
                Sequence.isValid('z') shouldBe false
            }

            test("invalid characters return false") {
                Sequence.isValid('X') shouldBe false
                Sequence.isValid('1') shouldBe false
                Sequence.isValid(' ') shouldBe false
            }
        }

        context("isAmbiguous") {
            test("standard bases are not ambiguous") {
                for (ch in listOf('A', 'C', 'T', 'G')) {
                    Sequence.isAmbiguous(ch) shouldBe false
                }
            }

            test("IUPAC ambiguity codes are ambiguous") {
                for (ch in listOf('R', 'Y', 'K', 'M', 'S', 'N', 'B', 'D', 'H', 'V', 'W')) {
                    Sequence.isAmbiguous(ch) shouldBe true
                }
            }

            test("gap and missing are not ambiguous") {
                Sequence.isAmbiguous('-') shouldBe false
                Sequence.isAmbiguous('?') shouldBe false
                Sequence.isAmbiguous('_') shouldBe false
            }

            test("unrecognized characters are not ambiguous") {
                Sequence.isAmbiguous('X') shouldBe false
            }
        }

        context("isPurine") {
            test("A and G are purines") {
                Sequence.isPurine('A') shouldBe true
                Sequence.isPurine('G') shouldBe true
            }

            test("R (A/G) is a purine") {
                Sequence.isPurine('R') shouldBe true
            }

            test("C and T are not purines") {
                Sequence.isPurine('C') shouldBe false
                Sequence.isPurine('T') shouldBe false
            }
        }

        context("isPyrimidine") {
            test("C and T are pyrimidines") {
                Sequence.isPyrimidine('C') shouldBe true
                Sequence.isPyrimidine('T') shouldBe true
            }

            test("Y (C/T) is a pyrimidine") {
                Sequence.isPyrimidine('Y') shouldBe true
            }

            test("A and G are not pyrimidines") {
                Sequence.isPyrimidine('A') shouldBe false
                Sequence.isPyrimidine('G') shouldBe false
            }
        }

        context("isMissing") {
            test("'?' is missing") {
                Sequence.isMissing('?') shouldBe true
            }

            test("other characters are not missing") {
                Sequence.isMissing('A') shouldBe false
                Sequence.isMissing('-') shouldBe false
                Sequence.isMissing('N') shouldBe false
            }
        }

        context("isGap") {
            test("'-' is a gap") {
                Sequence.isGap('-') shouldBe true
            }

            test("'_' (external gap) is a gap") {
                Sequence.isGap('_') shouldBe true
            }

            test("other characters are not gaps") {
                Sequence.isGap('A') shouldBe false
                Sequence.isGap('?') shouldBe false
                Sequence.isGap('N') shouldBe false
            }
        }

        context("complement") {
            test("standard base complements") {
                Sequence.complement('A') shouldBe 'T'
                Sequence.complement('T') shouldBe 'A'
                Sequence.complement('C') shouldBe 'G'
                Sequence.complement('G') shouldBe 'C'
            }

            test("ambiguity code complements") {
                Sequence.complement('R') shouldBe 'Y'
                Sequence.complement('Y') shouldBe 'R'
                Sequence.complement('K') shouldBe 'M'
                Sequence.complement('M') shouldBe 'K'
                Sequence.complement('S') shouldBe 'W'
                Sequence.complement('W') shouldBe 'S'
                Sequence.complement('N') shouldBe 'N'
            }

            test("gap and missing are unchanged") {
                Sequence.complement('-') shouldBe '-'
                Sequence.complement('_') shouldBe '_'
                Sequence.complement('?') shouldBe '?'
            }

            test("unrecognized character returns 'x'") {
                Sequence.complement('X') shouldBe 'x'
            }
        }

        context("consensus") {
            test("missing data propagates") {
                Sequence.consensus('A', '?') shouldBe '?'
                Sequence.consensus('?', 'G') shouldBe '?'
                Sequence.consensus('?', '?') shouldBe '?'
            }

            test("external gap combinations") {
                Sequence.consensus('_', '_') shouldBe '_'
                Sequence.consensus('_', '-') shouldBe '-'
                Sequence.consensus('-', '_') shouldBe '-'
                Sequence.consensus('_', 'A') shouldBe 'A'
                Sequence.consensus('A', '_') shouldBe 'A'
            }

            test("internal gap yields the other base") {
                Sequence.consensus('-', 'A') shouldBe 'A'
                Sequence.consensus('T', '-') shouldBe 'T'
            }

            test("same base returns same base") {
                Sequence.consensus('A', 'A') shouldBe 'A'
                Sequence.consensus('T', 'T') shouldBe 'T'
            }

            test("A + T = W") {
                Sequence.consensus('A', 'T') shouldBe 'W'
            }

            test("A + C = M") {
                Sequence.consensus('A', 'C') shouldBe 'M'
            }

            test("all four bases = N") {
                // A + T = W, C + G = S, W + S -> all bits -> N
                val at = Sequence.consensus('A', 'T') // W
                val cg = Sequence.consensus('C', 'G') // S
                Sequence.consensus(at, cg) shouldBe 'N'
            }
        }
    })
