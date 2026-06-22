/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.CountyData
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.viewer.BeanProperties
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.prefs.BeanTable
import ucar.ui.prefs.BeanTable.TableBeanProperty
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.util.function.Function
import javax.swing.AbstractAction
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

private val logger: Logger = LoggerFactory.getLogger(ContestPoolsTable::class.java)

class CountyPoolsTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane?,
    infoWindow: IndependentWindow?,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {
    private val countyTable: BeanTable<CountyPoolsBean>
    private val styleTable: BeanTable<StyleTable.StyleBean>
    private val contestTable: BeanTable<CountyPoolContestBean>

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyRecord: CountyAudit? = null
    var mvrManager: PersistedMvrManager? = null
    var infos: Map<Int, ContestInfo> = emptyMap()
    var countyCvrMap: Map<String, CountyPools> = emptyMap()

    init {
        countyTable = BeanTable(
            CountyPoolsBean::class.java, prefs.node("countyCardPool") as PreferencesExt?, false,
            "CountyPools", "CountyPools", null
        )
        countyTable.addPopupOption(
            "Show CountyPool",
            countyTable.makeShowAction(infoTA, infoWindow, Function { bean: Any? -> showCountyPool(bean as CountyPoolsBean) })
        )
        // TODO print in sorted order
        countyTable.addPopupOption(
            "Print Table", countyTable.makeShowAction(infoTA, infoWindow,
                Function { bean: Any? ->
                    val indexBeans : List<BeanTable.IndexedBean<CountyPoolsBean>> = countyTable.getIndexedBeans()
                    val sortedIndexBeans : List<BeanTable.IndexedBean<CountyPoolsBean>> = indexBeans.sortedBy { it.viewIndex() }
                    val sortedBeans : List<CountyPoolsBean> =   sortedIndexBeans.map { it.bean }
                    BeanProperties.printTableG(sortedBeans, countyTable.tableModel, CountyPoolsBean.beanProperties,"countyPool")
                }
            )
        )
        countyTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent? ->
            val selected = countyTable.getSelectedBean()
            if (selected != null) {
                setSelectedCounty(selected)
            }
        })

        styleTable = BeanTable(
            StyleTable.StyleBean::class.java, prefs.node("styleTable") as PreferencesExt?, false,
            "Style", "Style", null
        )
        styleTable.addPopupOption(
            "Show use in Contests", styleTable.makeShowAction(infoTA, infoWindow,
            Function { bean: Any? -> showCountyStyle(bean as StyleTable.StyleBean) })
        )

        contestTable = BeanTable(
            CountyPoolContestBean::class.java, prefs.node("contestTable") as PreferencesExt?, false,
            "Contest subtotal in selected County", "ContestTabulation", null
        )
        contestTable.addPopupOption(
            "Show County Contest", contestTable.makeShowAction(infoTA, infoWindow,
                Function { bean: Any? -> showCountyContest(bean as CountyPoolContestBean) })
        )
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, contestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("CountyPoolTable init")
    }

    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {
        val readMvrAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val selected = countyTable.getSelectedBean()
                if (selected != null) {
                    // readMvrTabulation(selected)
                }
            }
        }
        BAMutil.setActionProperties(readMvrAction, "sunrise-icon.png", "Read Mvr Tabulation", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, readMvrAction)
    }

    override fun setFontSize(size: Float) {
        countyTable.setFontSize(size)
        contestTable.setFontSize(size)
        styleTable.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        logger.debug("ContestPoolsTable setAuditRecord " + auditRecordLocation)
        countyTable.setBeans(emptyList())
        contestTable.setBeans(emptyList())
        styleTable.setBeans(emptyList())

        this.auditRecordLocation = auditRecordLocation
        val auditRecord = read(auditRecordLocation)
        if (auditRecord == null) {
            logger.info("ContestPoolsTable failed on readFrom " + auditRecordLocation)
            return false
        }

        if (auditRecord !is CountyAudit) return false
        this.countyRecord = auditRecord as CountyAudit
        this.mvrManager = PersistedMvrManager(this.countyRecord!!, false)

        val countyPools = mvrManager!!.countyPools()
        if (countyPools == null) return false

        val countyCvrs = mvrManager!!.countyCvrPools() //.associateBy { it.countyName }
        this.countyCvrMap = if (countyCvrs == null) emptyMap() else countyCvrs.associateBy { it.countyName }
        logger.info("read countyCvrPools=" + countyCvrMap.size)

        this.infos = auditRecord.contests.associate { it.contest.info().id to it.contest.info() }
        val countyData = countyRecord!!.countyData.associateBy { it.countyName }

        val beanList = mutableListOf<CountyPoolsBean>()
        countyPools.forEach {
            val bean = CountyPoolsBean(it, countyData[it.countyName]!!)
            val cvrTabs = this.countyCvrMap[ it.countyName ]
            if (cvrTabs != null)
                bean.cvrTabs = cvrTabs.contestTabs.associateBy { it.contestId }
            beanList.add(bean)
        }
        countyTable.setBeans(beanList)

        return true
    }

    fun setSelectedCounty(countyBean: CountyPoolsBean) {
        contestTable.setBeans(emptyList())
        styleTable.setBeans(emptyList())

        val styleNCards = mutableMapOf<Int, Int>()

        val sbeanList = mutableListOf<StyleTable.StyleBean>()
        countyBean.countyPool.styles.forEach { style ->
            sbeanList.add( StyleTable.StyleBean(style))

            style.possibleContests().forEach { contestId ->
                val contestNcard: Int = styleNCards.getOrPut(contestId) { 0 }
                styleNCards[contestId] = contestNcard + style.ncards()
            }
        }
        styleTable.setBeans(sbeanList)

        val beanList = mutableListOf<CountyPoolContestBean>()
        countyBean.countyPool.contestTabs.forEach { tab ->
            val info = this.infos[tab.contestId]!!
            val auditcenterBean = CountyPoolContestBean(countyBean, info, tab, false)
            beanList.add( auditcenterBean )
            val cvrTab = countyBean.cvrTabs[tab.contestId]
            if (cvrTab != null) {
                val mvrBean = CountyPoolContestBean(countyBean, info, cvrTab, true)
                mvrBean.acBean = auditcenterBean
                beanList.add(mvrBean)
            }
        }
        contestTable.setBeans(beanList)
    }

    override fun saveState() {
        countyTable.saveState(false)
        styleTable.saveState(false)
        contestTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showCountyContest(bean: CountyPoolContestBean) = buildString {
        appendLine(contestTable.tableModel.showBean(this, CountyPoolContestBean.beanProperties))
        appendLine(bean.contestTab)
        appendLine(bean.vunderTab)
        appendLine("Used in Styles:")
        styleTable.getBeans().forEach { styleBean ->
            if (styleBean.style.hasContest(bean.contestId)) {
                appendLine("  ${styleBean.style}")
            }
        }
    }

    fun showCountyStyle(bean: StyleTable.StyleBean) = buildString {
        appendLine(bean.style)
        appendLine("Used in Contests:")
        appendLine(CountyPoolContestBean.header)

        contestTable.getBeans().forEach { contestBean ->
            if (bean.style.hasContest(contestBean.contestId)) {
                appendLine(contestBean.show())
            }
        }
    }

    fun showCountyPool(bean: CountyPoolsBean) = buildString {
        appendLine(countyTable.tableModel.showBean(bean, CountyPoolsBean.beanProperties))
        append(bean.show())
    }
    ////////////////////////////////////////////////////////////////
    // data class CountyPoolMultipleStyles (
    //    val countyName: String,
    //    val countyPoolId: Int,
    //    val contestTabs: List<ContestTabulation>,  // contestId -> ContestTabulation
    //    val totalCards: Int,
    //    val cardStyles: List<StyleIF>,
    //    // val cardStylesCount: List<Int>, // or CardStyleWithNCards ??
    //)
    class CountyPoolsBean(val countyPool: CountyPools, val countyData: CountyData) {
        val countyName = countyPool.countyName
        val countyPoolId = countyPool.countyPoolId
        val totalCards = countyPool.cardCount

        // val nmvrs = countyData.nmvrs
        val population = countyData.npop
        val diffCards = population - totalCards
        val diffCardsPct = (population - totalCards) / population.toDouble()

        var cvrTabs: Map<Int, ContestTabulation> = emptyMap()

        val acNvotes = countyPool.contestTabs.sumOf { it.nvotes() }
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
    class CountyPoolContestBean(val countyBean: CountyPoolsBean, val info: ContestInfo, val contestTab: ContestTabulation, val isMvrs: Boolean) {
        val contestId: Int
        val contestName: String
        val countyPoolId = countyBean.countyPoolId
        val vunderTab: Vunder
        var acBean: CountyPoolContestBean? = null

        init {
            contestId = contestTab.contestId
            contestName = info.name
            vunderTab = contestTab.votesAndUndervotes(countyPoolId, contestTab.ncards(), true)
        }

        val undervotes = vunderTab.undervotes  // vunder properly calculates when voteForN > 1
        val uvPct = undervotes / (contestTab.voteForN * contestTab.ncards()).toDouble()
        val voteForN = contestTab.voteForN
        val missing = vunderTab.missing
        val votes = vunderTab.cands().toString()

        val nvotes = vunderTab.nvotes
        fun getDiffNvotes() : Int {
            return if (acBean == null) -1 else (acBean!!.nvotes - vunderTab.nvotes)
        }
        fun getPctDiffNvotes() : String {
            return if (acBean == null) "" else dfn((acBean!!.nvotes - vunderTab.nvotes) / acBean!!.nvotes.toDouble(), 4)
        }

        val ncards = contestTab.ncards()
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
            fun hiddenProperties() = "countyBean contestTab vunderTab info acBean"

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
