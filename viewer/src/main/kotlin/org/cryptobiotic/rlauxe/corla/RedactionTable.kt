/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.corlacvr.CorlaRawCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.RedactedGroup
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CountyCvrsTable")

class CountyRedactionTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val redactionTable: BeanTable<RedactionBean>
    private val cvrTable: BeanTable<CvrRowBean>

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

        cvrTable = BeanTable(
            CvrRowBean::class.java, prefs.node("cvrTable") as PreferencesExt, false,
            "Redacted Cvr Row", "CvrRow", null
        )
        cvrTable.addPopupOption (
            "Show Redacted Cvr",
            cvrTable.makeShowAction(infoTA, infoWindow) { bean: CvrRowBean -> showCvr(bean) }
        )
        tables.add(cvrTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, redactionTable, cvrTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyRedactionTable init" }
    }

    fun showRedaction(bean: RedactionBean) = buildString {
        append(showContestWithDesc(bean, redactionTable.tableModel, null))
        appendLine("Accumulation")
        bean.redactedGroup.candVotes.forEach {
            appendLine(it)
        }
    }

    fun showCvr(bean: CvrRowBean) = buildString {
        appendLine(bean.row)
        appendLine("B${bean.row.batchId}_T${bean.row.tabulatorNum}_R${bean.row.recordId}") // example B28_T30_R68
    }

    fun setCorlaCvrs(corlaCvrs: CorlaRawCvrsIF?) {
        if (corlaCvrs == null) return
        val redaction = corlaCvrs.redaction()
        val beanList = mutableListOf<RedactionBean>()
        redaction.groups().forEach {
            beanList.add(RedactionBean(it))
        }
        //if (corlaCvrs.redaction().redactedRows() != null)
        //    beanList.add(RedactionBean(corlaCvrs.redaction().redactedRows()!!))
        redactionTable.setBeans(beanList)

        val beanList2 = mutableListOf<CvrRowBean>()
        redaction.redactedRows().forEach { redactedRow ->
            beanList2.add(CvrRowBean(redactedRow))
        }
        cvrTable.setBeans(beanList2)
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
        // val contestVotes = redactedGroup.contestVotes // TOSO show I think
        val contests = redactedGroup.contests()

        companion object {
            @JvmStatic
            fun hiddenProperties() = "redaction redactedGroup"
        }
    }
}
