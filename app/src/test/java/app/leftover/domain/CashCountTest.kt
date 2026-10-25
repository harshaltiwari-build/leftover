package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CashCountTest {
    @Test fun `equal amounts match`() = assertEquals(CashCount.Matches, CashCount.compare(50_000, 50_000))
    @Test fun `less cash than expected is untracked spending`() = assertEquals(CashCount.Missing(12_000), CashCount.compare(50_000, 38_000))
    @Test fun `more cash than expected is untracked money in`() = assertEquals(CashCount.Extra(2_500), CashCount.compare(50_000, 52_500))
    @Test fun `empty wallet counted at zero`() = assertEquals(CashCount.Missing(50_000), CashCount.compare(50_000, 0))
}
