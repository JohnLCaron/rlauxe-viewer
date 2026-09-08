/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2022Primary
import org.cryptobiotic.rlauxe.auditcenter.Colorado2024General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PMerged
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026Primary
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.MergedContestInfo
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corla.ColoradoInputTable.CanonicalContestBean
import org.cryptobiotic.rlauxe.corla.CountyCvrsTable.CvrRowBean
import org.cryptobiotic.rlauxe.corlaCounty.CorlaCountyInput
import org.cryptobiotic.rlauxe.cvr.CvrSchema
import org.cryptobiotic.rlauxe.cvr.SchemaColumnInfo
import org.cryptobiotic.rlauxe.cvr.SchemaContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.strata.Strata
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.text.split

private val logger: Logger = LoggerFactory.getLogger(CountySchemaTable::class.java)

class CountySchemaTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val contestTable: BeanTable<SchemaContestBean>
    private val choiceTable: BeanTable<SchemaChoiceBean>
    var currentSchema : CvrSchema? = null

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        contestTable = BeanTable(
            SchemaContestBean::class.java, prefs.node("contestTable") as PreferencesExt, false,
            "Cvr Schema Contests", "Cvr Schema Contest", null)
        contestTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent ->
            val selected = contestTable.getSelectedBean()
            if (selected != null) setSelectedContest(selected) })
        contestTable.addPopupOption(
            "Show Canonical Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: SchemaContestBean -> showSchemaContest(bean) }
        )
        tables.add(contestTable)

        choiceTable = BeanTable(
            SchemaChoiceBean::class.java, prefs.node("choiceTable") as PreferencesExt, false,
            "Choices", "Cvr Schema Choices", null
        )
        tables.add(choiceTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, choiceTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug("CountySchemaTable init")
    }

    fun showSchemaContest(bean: SchemaContestBean) = buildString {
        append(showContestWithDesc(bean, contestTable.tableModel, null))
        appendLine(bean.scontest.toString())
    }

    fun setCvrSchema(schema: CvrSchema?) {
        if (schema == null) return
        currentSchema = schema

        val beanList = mutableListOf<SchemaContestBean>()
        schema.contests.forEach {
            beanList.add(SchemaContestBean(it))
        }
        contestTable.setBeans(beanList)
    }

    fun setSelectedContest(bean: SchemaContestBean) {
        val beanList = mutableListOf<SchemaChoiceBean>()
        var start = bean.scontest.startCol
        repeat(bean.scontest.ncols) {
            beanList.add(SchemaChoiceBean(currentSchema!!.columns[start+it]))
        }
        choiceTable.setBeans(beanList)
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
    class SchemaContestBean(val scontest: SchemaContestInfo) {
        val contestIdx = scontest.contestIdx
        val contestName = scontest.contestName
        val startCol = scontest.startCol
        val ncols = scontest.ncols
        val isIRV = scontest.isIRV
        val nchoices = scontest.nchoices
        val voteForN = scontest.voteForN

        companion object {
            @JvmStatic
            fun hiddenProperties() = "scontest"
        }
    }

    class SchemaChoiceBean(val colInfo: SchemaColumnInfo) {
        val contest = colInfo.contestIdx
        val choice = colInfo.choice
        val party = colInfo.headerName
        val colno = colInfo.colno

        companion object {
            @JvmStatic
            fun hiddenProperties() = "colInfo"
        }
    }

    //////////////////////////////////////////////////////

    class CountyPoolsBean(val countyPool: CountyPools, val countyData: Strata) {
        val countyName = countyPool.countyName
        val countyPoolId = countyPool.countyPoolId
        val totalCards = countyPool.cardCount

        // val nmvrs = countyData.nmvrs
        val population = countyData.population
        val diffCards = population - totalCards
        val diffCardsPct = (population - totalCards) / population.toDouble()

        var cvrTabs: Map<Int, ContestTabulation> = emptyMap()

        val acNvotes = countyPool.contestTabs.values.sumOf { it.nvotes() }
        fun getCvrNvotes() = cvrTabs.values.sumOf { it.nvotes() }

        fun getDiffNvotes() : Int {
            return (acNvotes - getCvrNvotes())
        }
        fun getPctDiffNvotes() : Double {
            return (acNvotes - getCvrNvotes()) / acNvotes.toDouble()
        }

        fun show() = buildString {
            appendLine(countyData.toString())
            val totalCards = countyPool.styles.sumOf { it.ncards() }
            appendLine("sum of countyPool.styles.ncards() = ${totalCards} ")
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "countyPool countyData cvrTabs"

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
    class CountyContestBean(val countyBean: CountyPoolsBean, val info: ContestInfo, val contestTab: ContestTabulation, val isMvrs: Boolean) {
        val contestId: Int
        val contestName: String
        val countyPoolId = countyBean.countyPoolId
        val vunderTab: Vunder
        var acBean: CountyContestBean? = null

        init {
            contestId = contestTab.contestId
            contestName = info.name
            vunderTab = contestTab.votesAndUndervotes(countyPoolId, contestTab.ncards(), true)
        }

        val undervotes = vunderTab.undervotes  // vunder properly calculates when voteForN > 1
        val uvPct = undervotes / (contestTab.voteForN * contestTab.ncards()).toDouble()
        val voteForN = contestTab.voteForN
        // val missing = vunderTab.missing
        val votes = vunderTab.cands().toString()

        val nvotes = vunderTab.nvotes
        fun getDiffNvotes() : Int {
            return if (acBean == null) -1 else (acBean!!.nvotes - vunderTab.nvotes)
        }
        fun getPctDiffNvotes() : String {
            return if (acBean == null) "" else dfn((acBean!!.nvotes - vunderTab.nvotes) / acBean!!.nvotes.toDouble(), 4)
        }

        val estNcards = contestTab.ncards()
        val source = if (isMvrs) "cvrs" else "auditcenter"

        fun getNCounties(): String {
            val CORLAcounties = info.metadata.get("CORLAcounties")
            if (CORLAcounties == null) return "N/A"
            val toks: List<String> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
            if (toks.size == 1) return toks[0]
            return String.format("%02d", toks.size)
        }

        fun show() = buildString {
            append("${nfn(contestId, 3)}, ${trunc(contestName, 40)},    ${nfn(contestTab.ncards(), 6)}, ") // ${trunc(votes, 25)}, ")
            append("    ${nfn(nvotes, 6)},   ${nfn(undervotes, 6)},    ${dfn(uvPct, 2)}")
        }

        companion object {
            val header = " id,                                     name, tabNCards, styleNCards, diffNCards, nvotes, undervotes, uvPct"

            @JvmStatic
            fun hiddenProperties() = "countyBean contestTab vunderTab info acBean mvrs"

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
