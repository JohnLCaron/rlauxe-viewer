/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.CountyPoolMultipleStyles
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.prefs.BeanTable
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.collections.set

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
    private var auditRecord: AuditRecord? = null
    var mvrManager: PersistedMvrManager? = null
    var infos: Map<Int, ContestInfo> = emptyMap()

    init {
        countyTable = BeanTable(
            CountyPoolsBean::class.java, prefs.node("countyCardPool") as PreferencesExt?, false,
            "CountyPools", "CountyPools", null
        )
        countyTable.addPopupOption(
            "Show CountyCardPool",
            countyTable.makeShowAction(infoTA, infoWindow, Function { bean: Any? -> (bean as PartyBean).show() })
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
            "Show CountyStyle", styleTable.makeShowAction(infoTA, infoWindow,
            Function { bean: Any? -> showCountyStyle(bean as StyleTable.StyleBean) })
        )

        contestTable = BeanTable(
            CountyPoolContestBean::class.java, prefs.node("contestTable") as PreferencesExt?, false,
            "Contest subtotal in selected County", "ContestTabulation", null
        )
        contestTable.addPopupOption(
            "Show ContestPool", contestTable.makeShowAction(infoTA, infoWindow,
                Function { bean: Any? -> showCountyContest(bean as CountyPoolContestBean) })
        )
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, styleTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, contestTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("CountyPoolTable init")
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

        if (auditRecord is CompositeAuditRecord) return false
        this.auditRecord = auditRecord as AuditRecord
        this.mvrManager = PersistedMvrManager(this.auditRecord!!, false)

        val pools = mvrManager!!.countyPools()
        if (pools == null) return false

        this.infos = auditRecord.contests.associate { it.contest.info().id to it.contest.info() }

        val beanList = mutableListOf<CountyPoolsBean>()
        pools.forEach {
            beanList.add(CountyPoolsBean(it))
        }
        countyTable.setBeans(beanList)

        return true
    }

    fun setSelectedCounty(countyBean: CountyPoolsBean) {
        contestTable.setBeans(emptyList())
        styleTable.setBeans(emptyList())

        val contestsNcards = mutableMapOf<Int, Int>()

        val sbeanList = mutableListOf<StyleTable.StyleBean>()
        countyBean.countyPool.styles.forEach { style ->
            sbeanList.add( StyleTable.StyleBean(style))

            style.possibleContests().forEach { contestId ->
                val contestNcard: Int = contestsNcards.getOrPut(contestId) { 0 }
                contestsNcards[contestId] = contestNcard + style.ncards()
            }
        }
        styleTable.setBeans(sbeanList)

        val beanList = mutableListOf<CountyPoolContestBean>()
        countyBean.countyPool.contestTabs.forEach { tab ->
            val name = this.infos[tab.contestId]?.name ?: "unknown"
            beanList.add( CountyPoolContestBean(countyBean, name, tab, contestsNcards[tab.contestId] ?: 0 ))
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
        appendLine(bean.contestTab)
        appendLine(bean.vunder)
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

    ////////////////////////////////////////////////////////////////
    // data class CountyPoolMultipleStyles (
    //    val countyName: String,
    //    val countyPoolId: Int,
    //    val contestTabs: List<ContestTabulation>,  // contestId -> ContestTabulation
    //    val totalCards: Int,
    //    val cardStyles: List<StyleIF>,
    //    // val cardStylesCount: List<Int>, // or CardStyleWithNCards ??
    //)
    class CountyPoolsBean(val countyPool: CountyPoolMultipleStyles) {
        val countyName: String
        val countyPoolId: Int
        val totalCards: Int

        init {
            this.countyName = countyPool.countyName
            this.countyPoolId = countyPool.countyPoolId
            this.totalCards = countyPool.totalCards
        }

        fun show(): String {
            return countyPool.toString()
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "countyPool"
        }
    }

    class CountyPoolContestBean(val countyBean: CountyPoolsBean, val contestName: String, val contestTab: ContestTabulation, val poolCards: Int) {
        val contestId: Int
        val countyPoolId = countyBean.countyPoolId
        val vunder: Vunder

        init {
            contestId = contestTab.contestId
            vunder = contestTab.votesAndUndervotes(countyPoolId, poolCards, true)
        }

        val undervotes = poolCards - vunder.nvotes
        val uvPct = undervotes / poolCards.toDouble()

        val voteForN = contestTab.voteForN
        val nvotes = vunder.nvotes
        val missing = vunder.missing
        val votes = vunder.cands().toString()

        fun show() = buildString {
            append("${nfn(contestId, 3)}, ${trunc(contestName, 40)}, ") // ${trunc(votes, 25)}, ")
            append("   ${nfn(poolCards, 6)}, ${nfn(nvotes, 6)},   ${nfn(undervotes, 6)},    ${dfn(uvPct, 2)}")
        }

        companion object {
            val header = " id,                                     name, poolCards, nvotes, undervotes, uvPct"

            @JvmStatic
            fun hiddenProperties() = "countyBean contestTab vunder"
        }
    }
}
