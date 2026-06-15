/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.SortedManifest
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.prefs.BeanTable
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class CardTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {
    
    private val cardTable: BeanTable<CardBean>
    var localInfo: TextHistoryPane = TextHistoryPane()

    private val split1: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var auditRecord: AuditRecord? = null
    private var mvrManager: PersistedMvrManager? = null
    private var needsReading = true

    private var cardManifest: SortedManifest? = null
    var poolMap: MutableMap<String, StyleIF> = mutableMapOf<String, StyleIF>()

    init {
        cardTable = BeanTable(
            CardBean::class.java, prefs.node("cardTable") as PreferencesExt, false,
            "CardManifest", "AuditableCard", null
        )
        cardTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent? ->
            val cardBean = cardTable.getSelectedBean()
            if (cardBean != null) {
                setSelectedCard(cardBean)
            }
        })

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

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        logger.info("CardTable setAuditRecord " + auditRecordLocation)
        cardTable.setBeans(mutableListOf<CardBean?>())

        this.auditRecordLocation = auditRecordLocation
        val auditRecord = read(auditRecordLocation)
        if (auditRecord == null) {
            logger.info("CardTable failed on readFrom " + auditRecordLocation)
            return false
        }
        if (auditRecord is CompositeAuditRecord) return false
        this.auditRecord = auditRecord as AuditRecord
        this.mvrManager = PersistedMvrManager(this.auditRecord!!, false)

        needsReading = true

        return true
    }

    fun setSelectedTab() {
        if (needsReading) {
            if (readCards()) needsReading = false
        }
    }

    fun readCards(): Boolean {
        logger.info("start readCards for " + auditRecordLocation)

        val config = auditRecord!!.config
        val cutoff = config.round.sampling.contestSampleCutoff
        val ncardsToRead = if (cutoff == null || cutoff < 11111) 11111 else cutoff

        try {
            this.cardManifest = this.mvrManager!!.sortedManifest()

            val styles = this.mvrManager!!.styles()
            if (styles != null) {
                val pools = mutableMapOf<String, StyleIF>() // sorted
                for (pool in styles) {
                    val cardStyle = "P" + pool.id()
                    pools.put(cardStyle, pool)
                }
                this.poolMap = pools
            } else {
                val cardPools = this.mvrManager!!.pools()
                if (cardPools != null) {
                    val pools= mutableMapOf<String, StyleIF>() // sorted
                    for (pool in cardPools) {
                        val cardStyle = "P" + pool.id()
                        pools.put(cardStyle, pool)
                    }
                    this.poolMap = pools
                }
            }

            val beanList = mutableListOf<CardBean>()
            var index = 1
            cardManifest!!.cards.iterator().use { iter ->
                while (iter.hasNext() && index < ncardsToRead) {
                    val card = iter.next()
                    beanList.add(CardBean(card, index))
                    index++
                }
            }
            cardTable.setBeans(beanList)
            logger.info("end readCards " + index + " cards from " + auditRecordLocation)
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("setAuditRecord failed", e)
            return false
        }

        return true
    }

    fun findPool(cardStyle: String?): StyleIF? {
        return poolMap.get(cardStyle)
    }

    fun setSelectedCard(bean: CardBean) {
        localInfo.setText(bean.show())
        localInfo.gotoTop()
    }

    override fun saveState() {
        cardTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
    }

    class CardBean(val card: AuditableCardIF, val sortedIndex: Int) {

        val id: String
            get() = card.id()

        val location: String
            get() = card.location()

        val manifestIndex: Int
            get() = card.index()

        val prn: Long
            get() = card.prn()

        val phantom: Boolean
            get() = card.phantom()

        val contests: String = card.possibleContests().contentToString()

        val poolId: Int?
            get() = card.poolId()

        val cardStyle: String
            get() = card.styleName()

        val possibleContests: String = card.style()?.possibleContests().contentToString() ?: ""

        val votes = buildString {
            if (card.votes() != null) {
                card.votes()!!.forEach { append("${it.key}:${it.value.contentToString()}, ") }
            }
        }

        fun show()= buildString {
            appendLine(card.toString())
            appendLine(card.style().toString())
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "card";
        }

    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CardPanel::class.java)
    }

}