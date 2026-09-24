/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ContestTabulationIF
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
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

class PoolTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane,
    infoWindow: IndependentWindow,
    fontSize: Float
) : JPanel(), ViewerPanelIF {
    private val poolTable: BeanTable<PoolBean>
    private val contestTable: BeanTable<ContestTabBean>

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane

    private var auditRecordLocation: String = "none"
    private var auditRecord: AuditRecord? = null
    var mvrManager: PersistedMvrManager? = null
    var infos: Map<Int, ContestInfo> = emptyMap()

    init {
        poolTable = BeanTable<PoolBean>(
            PoolBean::class.java, prefs.node("poolTable") as PreferencesExt, false,
            "Pool", "CardPool", null
        )
        poolTable.addListSelectionListener { e: ListSelectionEvent ->
            val poolBean = poolTable.getSelectedBean()
            if (poolBean != null) {
                setSelectedPool(poolBean)
            }
        }

        contestTable = BeanTable<ContestTabBean>(
            ContestTabBean::class.java, prefs.node("contestTable") as PreferencesExt, false,
            "Contest", "Vunder", null
        )

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, poolTable, contestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug("poolTable init")
    }

    override fun setFontSize(size: Float) {
        poolTable.setFontSize(size)
        contestTable.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        logger.debug("PoolTable setAuditRecord " + auditRecordLocation)
        poolTable.setBeans(null)
        contestTable.setBeans(null)

        this.auditRecordLocation = auditRecordLocation
        val auditRecord = read(auditRecordLocation)
        if (auditRecord == null) {
            logger.info("PoolTable failed on read " + auditRecordLocation)
            return false
        }

        if (auditRecord is CompositeAuditRecord) return false
        this.auditRecord = auditRecord as AuditRecord
        this.mvrManager = PersistedMvrManager(this.auditRecord!!, false)
        this.infos = auditRecord.contests.associate { it.id to it.contest.info() }
        val pools = mvrManager!!.pools()
        if (pools == null) return false

        // data class CardPool(
        //    override val poolName: String,
        //    override val poolId: Int,
        //    val hasExactContests: Boolean,    // aka single style
        //    val infos: Map<Int, ContestInfo>, // do we really need this ??
        //    val contestTabs: Map<Int, ContestTabulation>,  // contestId -> ContestTabulation
        //    val totalCards: Int,
        val totalPoolTab = mutableMapOf<Int, ContestTabulation>()
        var totalPoolCards = 0
        val beanList = mutableListOf<PoolBean>()
        for (pool in pools) {
            beanList.add(PoolBean(pool))
            totalPoolTab.sumContestTabulations(pool.contestTabs)
            totalPoolCards += pool.totalCards
        }
        beanList.add(PoolBean(CardPool("Total", -1, false, this.infos.toMap(), totalPoolTab, totalPoolCards)))
        poolTable.setBeans(beanList)

        return true
    }

    fun setCounty(wantCounty: String) {
        val beanList = mutableListOf<PoolBean>()
        for (bean in poolTable.beans) {
            if (bean.getCounty() == wantCounty)
            beanList.add(bean)
        }
        poolTable.setBeans(beanList)
    }

    fun setSelectedPool(bean: PoolBean) {
        val beanList = mutableListOf<ContestTabBean>()
        for (tab in bean.pool.contestTabs.values) {
            if (this.infos[tab.contestId] != null)
                beanList.add(ContestTabBean(bean.pool, tab, this.infos[tab.contestId]!!))
        }
        contestTable.setBeans(beanList)
    }

    override fun saveState() {
        poolTable.saveState(false)
        contestTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
    }


    /**/////////////////////////////////////////////////////////////// */
    class PoolBean (val pool: CardPool) {
        val name = pool.name()
        val id = pool.id()
        val singleStyle = pool.hasExactContests()
        val ncards = pool.ncards()
        val ncontests = pool.possibleContests().size
        val contests = buildString {
                val ids = pool.possibleContests()
                for (id in ids) {
                    append("$id,")
                }
            }

        fun getCounty(): String {
            return if (name.indexOf(":") > 0) name.substring(0, name.indexOf(":"))
            else if (name.indexOf("-") > 0) name.substring(0, name.indexOf("-"))
            else "N/A"
        }

        fun show(): String {
            return pool.toString()
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "pool"
        }
    }

    class ContestTabBean(val pool: CardPool, val contestTab: ContestTabulationIF, val info: ContestInfo) {
        val vunder = contestTab.votesAndUndervotes(pool.poolId, pool.ncards(), pool.hasExactContests())

        val contestId = info.id
        val contestName = info.name

        val isIrv = (if (contestTab.isIrv) "yes" else "")
        val voteForN = vunder.voteForN

        val nCards = vunder.ncards
        val undervotes = vunder.undervotes
        val nVotes = vunder.nvotes
        val missing = vunder.missing
        val votes: String
            get() {
                if (!contestTab.isIrv) return vunder.cands().toString()
                else return "VC " + vunder.voteCounts.size + " unique rankings"
            }

        fun show(): String {
            return contestTab.toString() + "\n" + vunder.toString()
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "pool contestTab info vunder"
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PoolTable::class.java)
    }
}
