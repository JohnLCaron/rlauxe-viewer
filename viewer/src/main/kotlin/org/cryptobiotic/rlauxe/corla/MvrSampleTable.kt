/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.ComparisonMvr
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.co.auditcenter.ContestMergedInfos
import org.cryptobiotic.rlauxe.co.auditcenter.MvrComparison
import org.cryptobiotic.rlauxe.co.auditcenter.MvrMergedInfo
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("MvrSampleTable")

class MvrSampleTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()
    // private val mvrSampleTable: BeanTable<MvrSampleBean>
    private val mvrInfoTable: BeanTable<MvrInfoBean>
    private val comparisonLineTable: BeanTable<ComparisionLineBean>
    // val localInfo = TextHistoryPane()
    private val split1: JSplitPane
    // private val split2: JSplitPane

    var contestMergedInfos: ContestMergedInfos? = null

    init {
        /* mvrSampleTable = BeanTable(
            MvrSampleBean::class.java, prefs.node("mvrSampleTable") as PreferencesExt, false,
            "All Mvrs", "Mvr", null
        )
        mvrSampleTable.addListSelectionListener {
            val selected = mvrSampleTable.getSelectedBean()
            if (selected != null) setSelectedSample(selected)
        }
        tables.add(mvrSampleTable) */

        mvrInfoTable = BeanTable(
            MvrInfoBean::class.java, prefs.node("MvrInfoBean") as PreferencesExt, false,
            "MvrInfo", "Mvr", null
        )
        mvrInfoTable.addListSelectionListener {
            val selected = mvrInfoTable.getSelectedBean()
            if (selected != null) setSelectedSample(selected)
        }
        tables.add(mvrInfoTable)

        comparisonLineTable = BeanTable(
            ComparisionLineBean::class.java, prefs.node("comparisonLineTable") as PreferencesExt, false,
            "Comparison Line", "Mvr", null
        )
        tables.add(comparisonLineTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, mvrInfoTable, comparisonLineTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        //split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, comparisonLineTable)
        //split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "MvrSampleTable init" }
    }

    fun setColoradoInput(stateInput: StatewideInput, county: String?) {
        logger.debug { "MvrSampleTable setColoradoInput ${stateInput.name()} $county" }
        contestMergedInfos = ContestMergedInfos(stateInput)

        /* if (county != null) {
            this.corlaPrngCounty = CorlaPrngCounty(stateInput, county)
            this.corlaPrngStatewide = CorlaPrngStatewide(stateInput)
        } */

        mvrInfoTable.setBeans(null)
        // mvrSampleTable.setBeans(null)
        comparisonLineTable.setBeans(null)

        if (county != null) {
            val mvrInfos = contestMergedInfos!!.getMvrsForCounty(county)
            logger.debug { "mvrInfoTable ${mvrInfos.size} mvrs" }
            val beans = mutableListOf<MvrInfoBean>()
            mvrInfos.forEach { mvr ->
                beans.add(MvrInfoBean(mvr))

                //if (county == null || mvr.county() == county) {
                /* val sampled = if (corlaPrngCounty?.claim("${mvr.county()}: ${mvr.imprintedId()}") ?: false) "county" else {
                    if (corlaPrngStatewide?.claim("${mvr.county()}: ${mvr.imprintedId()}") ?: false) "statewide" else "unknown"
                } */
                //beans.add(MvrSampleBean(mvr, ""))
                //}
            }
            mvrInfoTable.setBeans(beans)
            logger.debug { "mvrInfoTable setColoradoInput added ${beans.size} mvrs" }
        }

        /* val (contestMvrs, countyMvrs, mvrs, stylesByCounty) = stateInput.cardComparison()
        logger.debug { "MvrSampleTable ${mvrs.size} mvrs" }
        val beanss = mutableListOf<MvrSampleBean>()
        mvrs.forEach { mvr ->
            if (county == null || mvr.county() == county) {
                /* val sampled = if (corlaPrngCounty?.claim("${mvr.county()}: ${mvr.imprintedId()}") ?: false) "county" else {
                    if (corlaPrngStatewide?.claim("${mvr.county()}: ${mvr.imprintedId()}") ?: false) "statewide" else "unknown"
                } */
                beanss.add(MvrSampleBean(mvr, ""))
            }
        }
        mvrSampleTable.setBeans(beanss)
        logger.debug { "MvrSampleBean setColoradoInput added ${beanss.size} mvrs" } */
    }

    fun setSelectedSample(bean: MvrInfoBean) {
        if (bean.mvr.lines != null)
            comparisonLineTable.setBeans(bean.mvr.lines!!.map { ComparisionLineBean(it) })
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }

        prefs.putInt("splitPos1", split1.getDividerLocation())
        //prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    /* fun showCanonicalContest(bean: CanonicalContestBean) = buildString {
        append(showContestWithDesc(bean, contestTable.tableModel, null))

        appendLine(bean.contest.toString())
    } */

    //////////////////////////////////////////////////////

    class MvrInfoBean(val mvr: MvrMergedInfo) {
        val imprintedId = ImprintedId(mvr.imprintedId)
        val county = mvr.county
        val dbid = mvr.dbId
        val prn = mvr.prn
        val seq = mvr.seq
        val duplicate = mvr.mult - 1
        val counties = mvr.counties
        val auditReason = mvr.auditReason
        val disagree = if (mvr.lines == null) 0 else mvr.lines!!.filter { it.choice_per_voting_computer != it.audit_board_selection }.count()

        companion object {
            @JvmStatic
            fun hiddenProperties() = "mvr"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("county", "county name"),
                TableBeanProperty("ballotType", "mvr ballotType"),
                TableBeanProperty("imprintedId", "mvr imprintedId"),
                TableBeanProperty("dbid", "CORLA internal database id"),
                TableBeanProperty("auditReason", "drawn for statewide sample, else county sample"),
                TableBeanProperty("disagree", "number of choices that disagreed with CVR"),
                TableBeanProperty("prn", "psuedo random number"),
                TableBeanProperty("seq", "sequence in county or statewide draw"),
                TableBeanProperty("mult", "number of duplicate times the card was drawn"),
            )
        }
    }

    class MvrSampleBean(val mvr: ComparisonMvr, val sampled: String) {
        val countyName = mvr.county()
        val ballotType = mvr.ballotType()
        val imprintedId = ImprintedId(mvr.imprintedId())
        val dbid = mvr.dbId
        val disagree = mvr.lines.filter { it.mvrChoice != it.cvrChoice }.count()

        companion object {
            @JvmStatic
            fun hiddenProperties() = "mvr"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("countyName", "county name"),
                TableBeanProperty("ballotType", "mvr ballotType"),
                TableBeanProperty("imprintedId", "mvr imprintedId"),
                TableBeanProperty("dbid", "CORLA internal database id"),
                TableBeanProperty("statewide", "drawn for statewide sample, else county sample"),
                TableBeanProperty("disagree", "number of choices that disagreed with CVR"),
                TableBeanProperty("prn", "psuedo random number"),
            )
        }
    }

    // data class ComparisonLine(
    //    val countyName: String,
    //    val contestName: String,
    //    val imprintedId: String,
    //    val ballotType: String,
    //    val cvrChoice: String,
    //    val mvrChoice: String,
    //    val dbId: Int, // apparently the opaque "db creation id"
    //    val statewide: Boolean,
    //)
    class ComparisionLineBean(line: MvrComparison) {
        val countyName = line.county_name
        val contest = line.contest_name
        val imprintedId = line.imprinted_id
        val ballotType = line.ballot_type
        val cvrChoice = line.choice_per_voting_computer
        val mvrChoice = line.audit_board_selection
        val comment = line.audit_board_comment
        val disagree = line.choice_per_voting_computer != line.audit_board_selection
    }
}

/*
class CorlaPrngCounty(stateInput: StatewideInput, val county: String, verbose: Boolean = false) {
    val prnIds: List<String>
    val prnIdSet: Set<String>
    val countyMvrs: Int

    init {
        println("-------------- $county")
        val strata = stateInput.countyStrataMap[county]!!
        val countyManifest = stateInput.countyManifest(county)!!
        // modulus max - min + 1 = 1_444_047
        val prng = CorlaPrng(stateInput.seed, 1, strata.ballotCardCount) // without replacement (!)
        if (verbose) println("  county population=${strata.ballotCardCount} nmvrs =${strata.countyMvrs}") // 2020 doesnt know which are statewide
        countyMvrs = strata.countyMvrs

        val prns = mutableListOf<Int>()
        repeat(strata.nmvrs) { prns.add(prng.next()) }
        if (verbose) println("  prns size=${prns.size} unique=${prns.toSet().size}")

        prnIds = prns.map { prn -> countyManifest.idFromIndex(prn, 0) }
        prnIdSet = prnIds.toSet()
        if (verbose) println("  ids size=${prnIds.size} unique=${prnIdSet.size}")
    }

    fun claim(id: String): Boolean {
        return prnIdSet.contains(id)
    }
}

class CorlaPrngStatewide(val stateInput: StatewideInput, val population: Int = 4_114_582, val statewideNmvrs: Int = 953, val verbose: Boolean = false) {
    val prnIds: List<String>
    val prnIdSet: Set<String>

    init {
        val prng = CorlaPrng(stateInput.seed, 1, population) // without replacement (!)

        val prns = mutableListOf<Int>()
        repeat(statewideNmvrs) { prns.add(prng.next()) }
        if (verbose) println("  prns size=${prns.size} unique=${prns.toSet().size}")

        val manifest = stateInput.statewideManifest()
        println("  manifest.totalCards=${manifest.totalCards}")

        prnIds = prns.map { prn -> manifest.idFromIndex(prn) }
        prnIdSet = prnIds.toSet()
        if (verbose) println("  ids size=${prnIds.size} unique=${prnIdSet.size}")
    }

    fun claim(id: String): Boolean {
        return prnIdSet.contains(id)
    }
} */

class ImprintedId(val id: String): Comparable<ImprintedId> {
    val token: List<Int?>

    init {
        token = id.split("-").map{ it.toIntOrNull() }
    }

    override fun compareTo(other: ImprintedId) = COMPARATOR.compare(this, other)
    override fun toString() = id

    companion object {
        private val COMPARATOR = compareBy<ImprintedId> { it.token[0] }
            .thenBy { it.token[1] }
            .thenBy { it.token[2] }
    }
}