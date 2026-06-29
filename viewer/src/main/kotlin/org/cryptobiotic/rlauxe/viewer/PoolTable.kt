/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.util.ContestTabulation
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

        val pools = mvrManager!!.pools()
        if (pools == null) return false

        val beanList = mutableListOf<PoolBean>()
        for (pool in pools) {
            beanList.add(PoolBean(pool))
        }
        poolTable.setBeans(beanList)

        return true
    }

    fun setSelectedPool(bean: PoolBean) {
        val beanList = mutableListOf<ContestTabBean>()
        for (tab in bean.pool.contestTabs.values) {
            beanList.add(ContestTabBean(bean.pool, tab))
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

        val name: String
            get() = pool.name()

        val id: Int
            get() = pool.id()

        val singleStyle: Boolean
            get() = pool.hasExactContests()

        val ncards: Int
            get() = pool.ncards()

        val contests = buildString {
                val ids = pool.possibleContests()
                for (id in ids) {
                    append("$id,")
                }
            }

        val ncontests: Int
            get() = pool.possibleContests().size

        val className: String
            get() = pool.javaClass.getSimpleName()

        fun show(): String {
            return pool.toString()
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "pool"
        }
    }

    class ContestTabBean(val pool: CardPool, val contestTab: ContestTabulation) {
        val vunder = contestTab.votesAndUndervotes(pool.poolId, pool.ncards(), pool.hasExactContests())

        val contestId: Int
            get() = vunder.contestId
        val isIrv: String
            get() = (if (contestTab.isIrv) "yes" else "")
        val voteForN: Int
            get() = vunder.voteForN

        val nCards: Int
            get() = vunder.ncards
        val undervotes: Int
            get() = vunder.undervotes
        val nVotes: Int
            get() = vunder.nvotes
        val missing: Int
            get() = vunder.missing
        val votes: String
            get() {
                if (!contestTab!!.isIrv) return vunder.cands().toString()
                else return "VC " + vunder.voteCounts.size + " unique rankings"
            }

        fun show(): String {
            return contestTab.toString() + "\n" + vunder.toString()
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "pool contestTab vunder"
        }
    }


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PoolTable::class.java)
    }
}
