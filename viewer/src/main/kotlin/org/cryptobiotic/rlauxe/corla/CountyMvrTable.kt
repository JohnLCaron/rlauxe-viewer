/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.io.File
import java.nio.charset.Charset
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CvrStylesTable")

class CountyMvrTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val mvrComparisonTable: BeanTable<MvrComparisonBean>

    val localInfo = TextHistoryPane()
    private val split1: JSplitPane
    // private val split2: JSplitPane

    val currentInput: ColoradoInput? = null

    init {
        // problem is it does a static parsing of the beans....we need a new table each time
        mvrComparisonTable = BeanTable(
            MvrComparisonBean::class.java, prefs.node("countyTable") as PreferencesExt, false,
            "Mvrs for county", "Mvr", null
        )
        tables.add(mvrComparisonTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, mvrComparisonTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyPoolTable init" }
    }

    fun setColoradoInput(input: ColoradoInput, county: String) {
        mvrComparisonTable.setBeans(emptyList())
        val (header, lines) = readContestComparisonCsv(input.mvrComparisonFile, county)
        MvrComparisonBean.checkHeaders(input, header)

        val mvrComparisions = mutableListOf<MvrComparisonBean>()

        lines.forEach {
            mvrComparisions.add(MvrComparisonBean(header, it))
        }
        mvrComparisonTable.setBeans(mvrComparisions)
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
    class MvrComparisonBean(val headers: List<String>, val values: List<String>) {
        val valueMap = mutableMapOf<String, String>()

        init {
            headers.forEachIndexed { idx, header ->
                valueMap[header] = if (idx < values.size) values[idx] else ""
            }
            // are there any headers not in headerNames?
        }

        val county_name = valueMap["county_name"]
        val contest_name = valueMap["contest_name"]
        val imprinted_id = valueMap["imprinted_id"]
        val ballot_type = valueMap["ballot_type"]
        val choice_per_voting_computer = valueMap["choice_per_voting_computer"]
        val audit_board_selection = valueMap["audit_board_selection"]
        val consensus = valueMap["consensus"]
        val record_type = valueMap["record_type"]
        val audit_board_comment = valueMap["audit_board_comment"]
        val timestamp = valueMap["timestamp"]
        val cvr_id = valueMap["cvr_id"]
        val audit_reason = valueMap["audit_reason"]

        companion object {
            @JvmStatic
            fun hiddenProperties() = "headers values valueMap"

            val headerSet = "county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,"+
                    "audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason".split(",").toSet()

            fun checkHeaders(input: ColoradoInput, headers: List<String>) {
                headers.forEach {
                    if (!headerSet.contains(it))
                        logger.warn { "Header $it is not present in ${input.name}" }
                }
            }

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
