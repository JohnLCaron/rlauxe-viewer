/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.cvr.CvrSchema
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent

class CountyCvrsTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    private val cardTable: BeanTable<CvrRowBean>
    var localInfo: TextHistoryPane = TextHistoryPane()

    private val split1: JSplitPane

    var corlaCvrs: CorlaCvrsIF? = null
    // var currentSchema: CvrSchema? = null
    // var redactedGroups: List<RedactedGroup> = emptyList()
    var poolMap: MutableMap<String, StyleIF> = mutableMapOf<String, StyleIF>()

    init {
        cardTable = BeanTable(
            CvrRowBean::class.java, prefs.node("cardTable") as PreferencesExt, false,
            "CVRs from County", "AuditableCard", null
        )
        cardTable.addListSelectionListener { e: ListSelectionEvent? ->
            val cardBean = cardTable.getSelectedBean()
            if (cardBean != null) {
                setSelectedRow(cardBean)
            }
        }

        //cardTable.addPopupOption("Show Population", cardTable.makeShowAction(localInfo,
        //    bean -> ((cardTable) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, cardTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug("cardTable init")
    }

    override fun setFontSize(size: Float) {
        cardTable.setFontSize(size)
        localInfo.setFontSize(size)
    }

    fun setCountyInput(countyInput: CorlaCountyInput) {
        val maxRead = 11111

        try {
            corlaCvrs = countyInput.readCorlaCvrs()

            val beanList = mutableListOf<CvrRowBean>()
            var count = 0
            corlaCvrs!!.cvrs().forEach {
                beanList.add(CvrRowBean(it))
                if (count++ > maxRead) return@forEach
            }
            cardTable.setBeans(beanList)
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("setCountyInput failed", e)
        }
    }

    fun findPool(cardStyle: String?): StyleIF? {
        return poolMap.get(cardStyle)
    }

    fun setSelectedRow(bean: CvrRowBean) {
        localInfo.setText(bean.show(corlaCvrs!!.schema))
        localInfo.gotoTop()
    }

    override fun saveState() {
        cardTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CountyCvrsTable::class.java)
    }

    class CvrRowBean(val row: CvrRow) {

        val cvrNumber = row.cvrNumber
        val tabulatorNum = row.tabulatorNum
        val batchId = row.batchId
        val recordId = row.recordId
        val imprintedId = row.imprintedId
        val ballotType = row.ballotType
        val precinctPortion = row.precinctPortion

        val votes = buildString {
            row.contestVotes.forEach {
                append("${it.contestId}: ${it.candVotes}, ")
            }
        }

        fun show(schema: CvrSchema) = buildString {
            appendLine(row.toString())
            row.contestVotes.forEach {
                val contest = schema.contests.get(it.contestId)
                append("  contest: ${sfn(contest.contestName, 60)} (${nfn(it.contestId, 3)}), ")
                appendLine(" candidate votes: ${it.candVotes}")
            }
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "row";
        }
    }
}
