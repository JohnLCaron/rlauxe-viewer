/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2022Primary
import org.cryptobiotic.rlauxe.corlaInput.Colorado2024General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PMerged
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026Primary
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.MergedContestInfo
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.core.ContestInfo
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
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.text.split

private val logger = KotlinLogging.logger("ColoradoInputTable")

class ColoradoInputTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
    val setInput: (ColoradoInput) -> Unit,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val inputTable: BeanTable<ColoradoInputBean>
    private val contestTable: BeanTable<CanonicalContestBean>

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        inputTable = BeanTable(
            ColoradoInputBean::class.java, prefs.node("inputBeans") as PreferencesExt, false,
            "Available Colorado Data", "ColoradoInput", null)
        inputTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent ->
            val selected = inputTable.getSelectedBean()
            if (selected != null) setSelectedInput(selected) })
        tables.add(inputTable)

        contestTable = BeanTable(
            CanonicalContestBean::class.java, prefs.node("contestTable") as PreferencesExt, false,
            "Canonical Contests", "Merged Canonical Contest Information", null)
        contestTable.addPopupOption(
            "Show Canonical Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: CanonicalContestBean -> showCanonicalContest(bean) }
        )
        tables.add(contestTable)

        /*
        countyTable = BeanTable(
            CountyPoolsBean::class.java, prefs.node("countyCardPool") as PreferencesExt, false,
            "CountyPools", "CountyPools", null
        )
        countyTable.addPopupOption(
            "Show CountyPool",
            countyTable.makeShowAction(infoTA, infoWindow,) { bean: CountyPoolsBean -> showCountyPool(bean) }
        )
        countyTable.addPopupOption(
            "Print Table", countyTable.makeShowAction(infoTA, infoWindow)
                { printTable(countyTable, CountyPoolsBean.beanProperties,"countyPool") }
            )
        countyTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent ->
            val selected = countyTable.getSelectedBean()
            if (selected != null) {
                setSelectedCounty(selected)
            }
        })

        styleTable = BeanTable(
            StyleTable.StyleBean::class.java, prefs.node("styleTable") as PreferencesExt, false,
            "Style", "Style", null
        )
        styleTable.addPopupOption(
            "Show use in Contests",
            styleTable.makeShowAction(infoTA, infoWindow) { bean: StyleTable.StyleBean -> showCountyStyle(bean) }
        ) */

        setFontSize(fontSize)

        setInputBeans()

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, inputTable, contestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyPoolTable init" }
    }

    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {
        val readMvrAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val selected = contestTable.getSelectedBean()
                if (selected != null) {
                    // readMvrTabulation(selected)
                }
            }
        }
        BAMutil.setActionProperties(readMvrAction, "sunrise-icon.png", "Read Mvr Tabulation", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, readMvrAction)
    }

    fun setSelectedInput(inputBean: ColoradoInputBean) {
        contestTable.setBeans(emptyList())

        val contestBeans = mutableListOf<CanonicalContestBean>()
        inputBean.coloradoInput.mergedContestMap.forEach {
            contestBeans.add(CanonicalContestBean(it.key, it.value))
        }
        contestTable.setBeans(contestBeans)

        setInput(inputBean.coloradoInput)
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }

        prefs.putInt("splitPos1", split1.getDividerLocation())
        //prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showCanonicalContest(bean: CanonicalContestBean) = buildString {
        append(showContestWithDesc(bean, contestTable.tableModel, null))
        appendLine(bean.contest.toString())
    }

    ////////////////////////////////////////////////////////////////
    fun setInputBeans() {
        val inputBeans = mutableListOf<ColoradoInputBean>()
        inputBeans.add(ColoradoInputBean(Colorado2020General()))
        inputBeans.add(ColoradoInputBean(Colorado2022Primary()))
        inputBeans.add(ColoradoInputBean(Colorado2024General()))
        inputBeans.add(ColoradoInputBean(Colorado2026Primary()))
        inputBeans.add(ColoradoInputBean(Colorado2026PMerged()))
        inputBeans.add(ColoradoInputBean(Colorado2026PwithCvrs()))
        inputTable.setBeans(inputBeans)
    }

    class ColoradoInputBean(val coloradoInput: ColoradoInput) {
        val name = coloradoInput.name
        val ncontests = coloradoInput.canonicalContests().size
        val nolosers = coloradoInput.canonicalContests().count() { it.value.choices.size < 2 }
        val auditableContests = ncontests - nolosers

        companion object {
            @JvmStatic
            fun hiddenProperties() = "coloradoInput"
        }
    }

    ////////////////////////////////////////////////////////////////

    class CanonicalContestBean(val name: String, val mcontest: MergedContestInfo) {
        val contest = mcontest.canonicalContest

        // contest round
        val auditReason = mcontest.auditReason
        val npop = mcontest.npop       // ballot_card_count
        val nc = mcontest.nc         // contest_ballot_card_count
        val voteForN = mcontest.voteForN  // winners_allowed
        val nsamples  = mcontest.nsamples // optimistic_samples_to_audit
        val marginInVotes = mcontest.marginInVotes // min_margin
        val riskLimit  = mcontest.riskLimit // risk_limit

        // mvr file
        val countyMvrs = mcontest.countyMvrs
        val statewideMvrs = mcontest.statewideMvrs

        fun getNCounties(): String {
            val counties = counties()
            if (counties.size == 1) return counties[0]
            return String.format("%02d", counties.size)
        }

        fun counties(): List<String> {
            return contest.counties.toList()
        }

        fun getTarget() = if (targeted()) "YES" else ""

        fun targeted(): Boolean {
            return false
        }

        fun statewide(): Boolean {
            return (counties().size > 60)
        }

        fun getNCand() = contest.choices.size

        fun getChoices() = contest.choices.toString()
        
        companion object {
            @JvmStatic
            fun hiddenProperties() = "mcontest contest"
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
