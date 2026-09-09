/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInputWithCvrs
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaInput.StrataInfo
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

private val logger: Logger = LoggerFactory.getLogger(ColoradoCounties::class.java)

class ColoradoCounties(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
    val setCountyInput: (CorlaCountyInput) -> Unit,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val countyTable: BeanTable<CountyTabBean>
    private val countyContestTable: BeanTable<CountyContestVotesBean>

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        countyTable = BeanTable(
            CountyTabBean::class.java, prefs.node("countyTable") as PreferencesExt, false,
            "countyTabsAllContests", "CountyTab", null
        )
        countyTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent ->
            val selected = countyTable.getSelectedBean()
            if (selected != null) {
                setSelectedCounty(selected)
            }
        })
        countyTable.addPopupOption(
            "Show CountyCvrs",
            countyTable.makeActionOnCurrentBean { bean: CountyTabBean? ->
                if (bean?.corlaCountyInput != null) {
                    setCountyInput(bean.corlaCountyInput)
                }
                return@makeActionOnCurrentBean ((bean?.corlaCountyInput != null))
            }
        )
        tables.add(countyTable)

        countyContestTable = BeanTable(
            CountyContestVotesBean::class.java, prefs.node("contestTable") as PreferencesExt, false,
            "CountyContestVotes", "ContestVotes in selected county", null
        )
        /* countyContestTable.addPopupOption(
            "Show Canonical Contest",
            countyContestTable.makeShowAction(infoTA, infoWindow) { bean: CountyContestVotesBean -> showCanonicalContest(bean) }
        ) */
        tables.add(countyContestTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, countyContestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug("CountyPoolTable init")
    }

    fun setColoradoInput(input: ColoradoInput) {
        countyTable.setBeans(emptyList())
        val strataMap: Map<String, StrataInfo> = input.strataMap

        val requireCountyInput = (input is ColoradoInputWithCvrs)
        val inputWithCvrs = if (input is ColoradoInputWithCvrs) input as ColoradoInputWithCvrs else null

        val countyContests = mutableListOf<CountyTabBean>()
        input.countyTabsAllContests().forEach {
            val corlaCountyInput = inputWithCvrs?.corlaCountyInput(it.key)
            if (!requireCountyInput || (corlaCountyInput != null))
                countyContests.add(CountyTabBean(it.key, it.value, strataMap[it.key], corlaCountyInput))
        }
        countyTable.setBeans(countyContests)
    }

    fun setSelectedCounty(bean: CountyTabBean) {
        countyContestTable.setBeans(emptyList())

        val countyContests = mutableListOf<CountyContestVotesBean>()
        bean.countyTab.contests.forEach {
            countyContests.add(CountyContestVotesBean(it.value))
        }
        countyContestTable.setBeans(countyContests)
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

    class CountyTabBean(val county: String, val countyTab: CountyTabAllContests, val strata: StrataInfo?, val corlaCountyInput: CorlaCountyInput?) {
        val ncontests = countyTab.contests.size
        val nmvrs = strata?.nmvrs ?: 0
        val population = strata?.ballotCardCount ?: 0
        val hasCountyCvrs = (corlaCountyInput != null)

        fun show() = buildString {
            appendLine(countyTab.toString())
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "countyTab strata corlaCountyInput"

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

    // all the contests in this county
    class CountyContestVotesBean(val countyContest: CountyContestVotes) {
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
}
