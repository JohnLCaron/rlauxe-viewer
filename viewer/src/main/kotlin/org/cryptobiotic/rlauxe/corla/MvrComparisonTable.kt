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

class MvrComparisonTable(
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
            "All Mvrs", "Mvr", null
        )
        mvrComparisonTable.addPopupOption(
            "Show Row",
            mvrComparisonTable.makeShowAction(infoTA, infoWindow) { bean: MvrComparisonBean -> showMvr(bean) }
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

    fun showMvr(bean: MvrComparisonBean) = buildString {
        appendLine(mvrComparisonTable.tableModel.showBean(bean, MvrComparisonBean.beanProperties))
    }

    fun setColoradoInput(input: ColoradoInput, county: String? = null) {
        mvrComparisonTable.setBeans(emptyList())
        // we want the low level details
        val (header, lines) = readContestComparisonCsv(input.mvrComparisonFile, county=null)
        MvrComparisonBean.checkHeaders(input, header)

        val mvrComparisions = mutableListOf<MvrComparisonBean>()
        lines.forEach {
            val bean = MvrComparisonBean(header, it)
            if (county == null || bean.countyName == county)
                mvrComparisions.add(bean)
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

        val countyName = valueMap["county_name"]
        val contestName = valueMap["contest_name"]
        val imprintedId = valueMap["imprinted_id"]
        val ballotType = valueMap["ballot_type"]
        val cvrSelection = valueMap["choice_per_voting_computer"]
        val auditSelection = valueMap["audit_board_selection"]
        val consensus = valueMap["consensus"]
        val recordType = valueMap["record_type"]
        val auditBoardComment = valueMap["audit_board_comment"]
        val timestamp = valueMap["timestamp"]
        val cvrId = valueMap["cvr_id"]
        val auditReason = valueMap["audit_reason"]

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
                TableBeanProperty("contestName", "contest name"),
                TableBeanProperty("imprintedId", "imprinted id"),
                TableBeanProperty("ballotType", "ballot type"),
                TableBeanProperty("cvrSelection", "choice on the CVR (choice_per_voting_computer)"),
                TableBeanProperty("auditSelection", "choice from MVR (audit_board_selection)"),
                TableBeanProperty("consensus", "consensus"),
                TableBeanProperty("recordType", "record_type"),
                TableBeanProperty("auditBoardComment", "audit board comment"),
                TableBeanProperty("timestamp", "timestamp"),
                TableBeanProperty("cvrId", "internal database id (cvr_id)"),
                TableBeanProperty("auditReason", "audit reason"),
            )
        }
    }
}

// return header, list of lines
fun readContestComparisonCsv(filename: String, county: String?): Pair<List<String>, List<List<String>>> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT) // TODO
    val records = parser.iterator()

    val header = records.next()
    val lines = mutableListOf<List<String>>()
    var count = 0
    try {
        while (records.hasNext()) {
            val cols = records.next().toList()
            if (county == null || cols.first() == county) {
                lines.add(cols)
                count++
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return Pair(header.toList(), lines)
}
