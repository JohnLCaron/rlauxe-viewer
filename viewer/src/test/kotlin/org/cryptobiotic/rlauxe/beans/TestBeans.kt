package org.cryptobiotic.rlauxe.beans

import org.cryptobiotic.rlauxe.viewer.AssertionBean
import org.cryptobiotic.rlauxe.viewer.AuditRoundsTable
import org.cryptobiotic.rlauxe.viewer.BelgiumContestsTable
import org.cryptobiotic.rlauxe.viewer.ContestBean
import org.cryptobiotic.rlauxe.viewer.ContestsPanel
import org.cryptobiotic.rlauxe.viewer.CorlaContestsTable
import org.cryptobiotic.rlauxe.viewer.CountyTable
import kotlin.test.Test

class TestBeans {

    @Test
    fun testPropertyCol() {
        Bean("PropertyCol", PropertyCol::class.java)
    }

    @Test
    fun testContest() {
        Bean("CountyPool", CountyTable.CountyPoolsBean::class.java)
    }

    @Test
    fun testContestBeans() {
        // used by AuditRoundsTable.contestRoundTable
        // ContestsPanel.contestTable
        // BelgiumContestsTable.contestTable
        // CorlaContestsTable.contestTable and SamplingTable.contestTable (same CorlaContestBean)
        val beans = listOf(
            Bean("Contests", ContestsPanel.ContestBean::class.java),
            Bean("ContestRound", AuditRoundsTable.ContestRoundBean::class.java),
            Bean("BelgiumContest", ContestBean::class.java),
            Bean("CorlaContest", CorlaContestsTable.CorlaContestBean::class.java),
            Bean("CountyContest", CountyTable.CountyContestBean::class.java),
            Bean("ContestCounty", CorlaContestsTable.ContestCountyBean::class.java),
        )
        val b = Beans(beans, BeanProperties.contests)
        println(b.show())
    }

    @Test
    fun testAssertionBeans() {
        val beans = listOf(
            Bean("Belgium", AssertionBean::class.java),
            Bean("AssertionRound", AuditRoundsTable.AssertionBean::class.java),
        )
        val b = Beans(beans, BeanProperties.assertions)
        println(b.show())
    }
}