/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.auditcenter.ComparisonMvr
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInputWithManifests
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.io.File
import java.nio.charset.Charset
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

    val localInfo = TextHistoryPane()
    private val split1: JSplitPane
    // private val split2: JSplitPane

    val currentInput: ColoradoInput? = null

    init {
        // problem is it does a static parsing of the beans....we need a new table each time
        mvrSampleTable = BeanTable(
            MvrSampleBean::class.java, prefs.node("countyTable") as PreferencesExt, false,
            "All Mvrs", "Mvr", null
        )
        tables.add(mvrSampleTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, mvrSampleTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyPoolTable init" }
    }

    fun setColoradoInput(stateInput: ColoradoInput, county: String?) {
        mvrSampleTable.setBeans(emptyList())
        val ccmvrs = stateInput.cardComparison().mvrs
        val mvrComparisions = mutableListOf<MvrSampleBean>()
        ccmvrs.forEach {
            if (county == null || it.countyName == county)
                mvrComparisions.add(MvrSampleBean(it))
        }
        mvrSampleTable.setBeans(mvrComparisions)
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

    // TODO change Bean introspectipon to get dynamic fields
    class MvrSampleBean(val mvr: ComparisonMvr) {
        val countyName = mvr.countyName
        val imprintedId = mvr.imprintedId
        val statewide = mvr.statewide
        val cvrId = mvr.dbid

        companion object {
            @JvmStatic
            fun hiddenProperties() = "mvr"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("countyName", "county name"),
                TableBeanProperty("countyPoolId", "county name"),
                TableBeanProperty("population", "county population from round.ballotCardCount"),
                TableBeanProperty("totalCards", "number of cvrs in the pool"),
                TableBeanProperty("diffCards", "population - totalCards"),
                TableBeanProperty("diffCardsPct", "(population - totalCards)/population"),
                TableBeanProperty("acNvotes", "auditcenter.votes"),
                TableBeanProperty("cvrNvotes", "cvr.votes"),
                TableBeanProperty("diffNvotes", "auditcenter.votes - cvr.votes"),
                TableBeanProperty("pctDiffNvotes", "(auditcenter.votes - cvr.votes)/auditcenter.votes"),
            )
        }
    }
}