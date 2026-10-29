/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlacvr.CardStyleId
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.corlacvr.CvrRow
import org.cryptobiotic.rlauxe.corlacvr.CvrSchema
import org.cryptobiotic.rlauxe.corlacvr.cleanCsvString
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CiCvrs")

// countyInput.readCorlaCvrs()
class CiCvrs(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()
    val cvrTable: BeanTable<CvrRowBean>
    val countyContestCvrTable: BeanTable<CountyContestCvrBean>

    private val split1: JSplitPane
    // private val split2: JSplitPane

    var corlaCvrs: CorlaCountyCvrsIF? = null
    var cardStyleMap = emptyMap<CardStyleId, CvrCardStyle>()

    init {
        cvrTable = BeanTable(
            CvrRowBean::class.java, prefs.node("cardTable") as PreferencesExt, false,
            "CVRs from County", "CvrRow", null
        )
        cvrTable.addPopupOption(
            "Show Cvr",
            cvrTable.makeShowAction(infoTA, infoWindow) { bean: CvrRowBean -> showSelectedRow(bean) }
        )
        tables.add(cvrTable)

        countyContestCvrTable = BeanTable(
            CountyContestCvrBean::class.java, prefs.node("precinctStyleTable") as PreferencesExt, false,
            "Precinct Styles", "CvrRow", null
        )
        tables.add(countyContestCvrTable)

        //cardTable.addPopupOption("Show Population", cardTable.makeShowAction(localInfo,
        //    bean -> ((cardTable) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, cvrTable, countyContestCvrTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 800))
        //split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, stylesTable)
        //split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CiCvrs init" }
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }
        prefs.putInt("splitPos1", split1.getDividerLocation())
        // prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showSelectedRow(bean: CvrRowBean) = buildString {
        append(bean.show(corlaCvrs!!.schema))
    }

    // TODO CountyInput knows its StateInput
    fun setCountyInput(countyInput: CountyInput) {
        val maxRead = 11111

        try {
            // TODO better to run it through CountyCardsFromManifest?
            corlaCvrs = countyInput.readCorlaCvrs()
            // if (corlaCvrs != null) setStyles(corlaCvrs!!)

            val beans = mutableListOf<CvrRowBean>()
            var count = 0
            corlaCvrs!!.cvrs().forEach {
                beans.add(CvrRowBean(it))
                if (count++ > maxRead) return@forEach
            }

            val sortedBeans = beans.sortedWith(
                compareBy<CvrRowBean> { it.precinctPortion ?: "" }.thenBy { it.ballotType }
            )
            cvrTable.setBeans(sortedBeans)
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "setCountyInput failed" }
        }
    }
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
            append("${it.contestId}:${it.votedFor}, ")
        }
    }

    fun show(schema: CvrSchema) = buildString {
        appendLine(row.toString())
        row.contestVotes.forEach {
            val contest = schema.contests.get(it.contestId)
            append("  contest: ${sfn(contest.contestName, 60)} (${nfn(it.contestId, 3)}), ")
            appendLine(" candidate votes: ${it.candVotes()}")
        }
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "row"
    }
}

// tabulation from the Cvr file
class CountyContestCvrBean(val countyContest: CountyContestVotes) {
    val county = countyContest.countyName
    val contest = countyContest.contestName
    val choices = countyContest.choices
    val totalVotes = countyContest.contestVotes()

    companion object {
        val header = " id,                                     name, tabNCards, styleNCards, diffNCards, nvotes, undervotes, uvPct"

        @JvmStatic
        fun hiddenProperties() = "countyContest"

        @JvmStatic
        val beanProperties = listOf(
            TableBeanProperty("contestId", "contest Id"),
            TableBeanProperty("contestName", "contest name"),
            TableBeanProperty("diffNVotes", "auditcenter.votes - cvr.votes"),
            TableBeanProperty("pctDiffNVotes", "(auditcenter.votes - cvr.votes)/auditcenter.votes"),
        )

    }
}


