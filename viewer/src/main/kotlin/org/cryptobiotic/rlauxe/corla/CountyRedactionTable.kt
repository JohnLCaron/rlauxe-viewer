/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.cvr.CvrSchema
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger: Logger = LoggerFactory.getLogger(CountySchemaTable::class.java)

class CountyRedactionTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val redactionTable: BeanTable<RedactionBean>

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        redactionTable = BeanTable(
            RedactionBean::class.java, prefs.node("redactionTable") as PreferencesExt, false,
            "Cvr Redactions", "Cvr Redactions", null)
        redactionTable.addPopupOption(
            "Show Redaction",
            redactionTable.makeShowAction(infoTA, infoWindow) { bean: RedactionBean -> showRedaction(bean) }
        )
        tables.add(redactionTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, redactionTable, infoTA)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug("CountySchemaTable init")
    }

    fun showRedaction(bean: RedactionBean) = buildString {
        append(showContestWithDesc(bean, redactionTable.tableModel, null))
        appendLine(bean.toString())
    }

    fun setRedactions(redactions: List<RedactedGroup>) {
        val beanList = mutableListOf<RedactionBean>()
        redactions.forEach {
            beanList.add(RedactionBean(it))
        }
        redactionTable.setBeans(beanList)
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }

        prefs.putInt("splitPos1", split1.getDividerLocation())
        //prefs.putInt("splitPos2", split2.getDividerLocation())
    }


    ////////////////////////////////////////////////////////////////

    // data class SchemaContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    //    val isIRV: Boolean
    //    val nchoices: Int
    //    val voteForN: Int
    class RedactionBean(val redaction: RedactedGroup) {
        val ballotType = redaction.ballotType
        val ncards = redaction.ncards()
        val singleCards = redaction.singleCards
        val totalVotes = redaction.totalVotes()
        val contestVotes = redaction.contestVotes

        companion object {
            @JvmStatic
            fun hiddenProperties() = "redaction"
        }
    }
}
