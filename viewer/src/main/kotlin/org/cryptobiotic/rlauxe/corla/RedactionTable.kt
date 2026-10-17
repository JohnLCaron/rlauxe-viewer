/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.corla.MvrComparisonTable.MvrComparisonBean
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.RedactedGroup
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CountyRedactionTable")

class CountyRedactionTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val redactionTable: BeanTable<RedactionBean>
    private val rowTable: BeanTable<CvrRowBean>

    val localInfo = TextHistoryPane()
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        redactionTable = BeanTable(
            RedactionBean::class.java, prefs.node("redactionTable") as PreferencesExt, false,
            "Redaction Aggregations", "RedactionGroup", null)
        //redactionTable.addListSelectionListener { e: ListSelectionEvent? ->
        //    val bean = redactionTable.getSelectedBean()
        //    if (bean != null) setSelectedRow(bean) }
        redactionTable.addPopupOption(
            "Show Redaction",
            redactionTable.makeShowAction(infoTA, infoWindow) { bean: RedactionBean -> showRedaction(bean) }
        )
        tables.add(redactionTable)

        rowTable = BeanTable(
            CvrRowBean::class.java, prefs.node("cvrTable") as PreferencesExt, false,
            "Redacted Cvr Row", "CvrRow", null
        )
        rowTable.addPopupOption (
            "Show Redacted Cvr",
            rowTable.makeShowAction(infoTA, infoWindow) { bean: CvrRowBean -> showCvr(bean) }
        )
        tables.add(rowTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, redactionTable, rowTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyRedactionTable init" }
    }

    fun showRedaction(bean: RedactionBean) = buildString {
        appendLine(redactionTable.tableModel.showBean(bean, RedactionBean.beanProperties))
        appendLine("Accumulation")
        bean.redactedGroup.candVotes.toSortedMap().forEach {
            appendLine("  $it")
        }
    }

    fun showCvr(bean: CvrRowBean) = buildString {
        appendLine(bean.row)
        appendLine("B${bean.row.batchId}_T${bean.row.tabulatorNum}_R${bean.row.recordId}") // example B28_T30_R68
    }

    fun setCorlaCvrs(corlaCvrs: CorlaCountyCvrsIF?) {
        if (corlaCvrs == null) return
        logger.debug { "CountyRedactionTable setCorlaCvrs" }
        redactionTable.setBeans(null)

        val redaction = corlaCvrs.redaction()
        if (redaction.groups().isNotEmpty()) {
            val first = redaction.groups().first()
            val totalGroup = RedactedGroup("---Totals---", first.firstCsv, first.schema)

            val beanList = mutableListOf<RedactionBean>()
            redaction.groups().forEach {
                beanList.add(RedactionBean(it))
                totalGroup.nlines += it.nlines
                totalGroup.setNcards += it.ncards()
                totalGroup.merge(it)
            }
            beanList.add(RedactionBean(totalGroup))

            redactionTable.setBeans(beanList)
        }

        val beanList2 = mutableListOf<CvrRowBean>()
        redaction.redactedRows().forEach { redactedRow ->
            beanList2.add(CvrRowBean(redactedRow))
        }
        rowTable.setBeans(beanList2)

        logger.debug { "CountyRedactionTable setCorlaCvrs groups=${redaction.groups().size} rows=${redaction.redactedRows().size}" }
    }

    /*
    fun setSelectedRow(bean: RedactionBean) {
        val beanList = mutableListOf<CvrRowBean>()
        bean.redactedGroup.redactedRows().forEach { redactedRow ->
            beanList.add(CvrRowBean(redactedRow))
        }
        cvrTable.setBeans(beanList)
    } */

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }
        prefs.putInt("splitPos1", split1.getDividerLocation())
    }

    ////////////////////////////////////////////////////////////////

    // data class SchemaContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    //    val isIRV: Boolean
    //    val nchoices: Int
    //    val voteForN: Int
    class RedactionBean(val redactedGroup: RedactedGroup) {
        val groupName = redactedGroup.groupName
        val nlines = redactedGroup.nlines
        val fixedNcards = redactedGroup.fixedNcards
        val ncards = redactedGroup.ncards()
        val minVotes = redactedGroup.minCards()
        val singleCards = redactedGroup.singleCards
        val totalVotes = redactedGroup.totalVotes()
        val contests = redactedGroup.contests().sorted()
        val ncontests = redactedGroup.contests().size

        companion object {
            @JvmStatic
            fun hiddenProperties() = "redaction redactedGroup"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("groupName", "name, may be the ballot type or style"),
                TableBeanProperty("nlines", "number of CVR rows"),
                TableBeanProperty("fixedNcards", "if ncards was given in the CVR file"),
                TableBeanProperty("ncards", "estimated number of cards"),
                TableBeanProperty("minVotes", "min number of cards needed for the vote subtotal"),
                TableBeanProperty("singleCards", "from a single row"),
                TableBeanProperty("totalVotes", "total number of votes"),
                TableBeanProperty("contests", "contest ids in the subtotal"),
            )

        }
    }
}
