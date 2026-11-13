/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.ComparisonLine
import org.cryptobiotic.rlauxe.auditcenter.ComparisonMvr
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.util.CorlaPrng
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CvrStylesTable")

class MvrSampleTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()
    private val mvrSampleTable: BeanTable<MvrSampleBean>
    private val comparisonLineTable: BeanTable<ComparisionLineBean>
    // val localInfo = TextHistoryPane()
    private val split1: JSplitPane
    // private val split2: JSplitPane

    var corlaPrngCounty: CorlaPrngCounty? = null
    var corlaPrngStatewide: CorlaPrngStatewide? = null

    init {
        // problem is it does a static parsing of the beans....we need a new table each time
        mvrSampleTable = BeanTable(
            MvrSampleBean::class.java, prefs.node("mvrSampleTable") as PreferencesExt, false,
            "All Mvrs", "Mvr", null
        )
        mvrSampleTable.addListSelectionListener {
            val selected = mvrSampleTable.getSelectedBean()
            if (selected != null) setSelectedSample(selected)
        }
        tables.add(mvrSampleTable)

        comparisonLineTable = BeanTable(
            ComparisionLineBean::class.java, prefs.node("comparisonLineTable") as PreferencesExt, false,
            "Comparison Line", "Mvr", null
        )
        tables.add(comparisonLineTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, mvrSampleTable, comparisonLineTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyPoolTable init" }
    }

    fun setColoradoInput(stateInput: StatewideInput, county: String?) {
        if (county != null) {
            this.corlaPrngCounty = CorlaPrngCounty(stateInput, county)
            this.corlaPrngStatewide = CorlaPrngStatewide(stateInput)
        }

        mvrSampleTable.setBeans(null)
        comparisonLineTable.setBeans(null)
        val (contestMvrs, countyMvrs, mvrs, stylesByCounty) = stateInput.cardComparison()
        val beans = mutableListOf<MvrSampleBean>()
        mvrs.forEach { mvr ->
            if (county == null || mvr.county() == county) {
                val sampled = if (corlaPrngCounty?.claim("${mvr.county()}: ${mvr.imprintedId()}") ?: false) "county" else {
                    if (corlaPrngStatewide?.claim("${mvr.county()}: ${mvr.imprintedId()}") ?: false) "statewide" else "unknown"
                }
                beans.add(MvrSampleBean(mvr, sampled))
            }
        }
        mvrSampleTable.setBeans(beans)
    }

    fun setSelectedSample(bean: MvrSampleBean) {
        comparisonLineTable.setBeans(bean.mvr.lines.map { ComparisionLineBean(it) })
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

    class MvrSampleBean(val mvr: ComparisonMvr, val sampled: String) {
        val countyName = mvr.county()
        val ballotType = mvr.ballotType()
        val imprintedId = mvr.imprintedId()
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
    class ComparisionLineBean(line: ComparisonLine) {
        val countyName = line.countyName
        val contest = line.contestName
        val imprintedId = line.imprintedId
        val ballotType = line.ballotType
        val cvrChoice = line.cvrChoice
        val mvrChoice = line.mvrChoice
        val statewide = line.statewide
        val disagree = line.cvrChoice != line.mvrChoice
    }
}

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
}