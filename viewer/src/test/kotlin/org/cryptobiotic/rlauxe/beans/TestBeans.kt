package org.cryptobiotic.rlauxe.beans

import org.cryptobiotic.rlauxe.viewer.AssertionRoundBean
import org.cryptobiotic.rlauxe.viewer.ContestBean
import org.cryptobiotic.rlauxe.viewer.ContestRoundBean
import org.cryptobiotic.rlauxe.viewer.CorlaContestsTable
import org.cryptobiotic.rlauxe.viewer.CountyTable
import org.cryptobiotic.rlauxe.viewer.RlauxeContestBean
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
            Bean("RlauxeContest", RlauxeContestBean::class.java),
            Bean("ContestRound", ContestRoundBean::class.java),
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
            Bean("Belgium", AssertionRoundBean::class.java),
            Bean("AssertionRound", AssertionRoundBean::class.java),
        )
        val b = Beans(beans, BeanProperties.assertions)
        println(b.show())
    }
}