/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.SortedManifest
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent

private val logger = KotlinLogging.logger("MvrsTable")

class MvrsTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()
    val cardTable: BeanTable<CardBean>
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
            "MvrSamples", "AuditableCard", null
        )
        cardTable.addListSelectionListener { e: ListSelectionEvent? ->
            val cardBean = cardTable.getSelectedBean()
            if (cardBean != null) {
                setSelectedCard(cardBean)
            }
        }
        tables.add(cardTable)

        //cardTable.addPopupOption("Show Population", cardTable.makeShowAction(localInfo,
        //    bean -> ((cardTable) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, cardTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "cardTable init" }
    }

    override fun setFontSize(size: Float) {
        for (vpanel in tables) {
            vpanel.setFontSize(size)
        }
        localInfo.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        logger.debug{"CardTable setAuditRecord $auditRecordLocation"}
        cardTable.setBeans(null)

        this.auditRecordLocation = auditRecordLocation
        val auditRecord = read(auditRecordLocation)
        if (auditRecord == null) {
            logger.info{"CardTable failed on readFrom $auditRecordLocation"}
            return false
        }
        if (auditRecord is CompositeAuditRecord) return false
        this.auditRecord = auditRecord as AuditRecord
        this.mvrManager = PersistedMvrManager(this.auditRecord!!, false)

        needsReading = true
        readCards(1)

        return true
    }

    fun readCards(roundIdx: Int): Boolean {
        if (auditRecord == null) return false
        val config = auditRecord!!.config
        // val cutoff = config.round.sampling.contestSampleCutoff
        // val ncardsToRead = if (cutoff == null || cutoff < 11111) 11111 else cutoff
        val styles = mvrManager!!.styles()

        try {
            val publisher = Publisher(auditRecordLocation!!)
            val mvrFile = publisher.sampleMvrsFile(roundIdx)
            val mvrCardIter = readCardsCsvIterator(mvrFile, styles=styles)

            val styles = this.mvrManager!!.styles()

            val beanList = mutableListOf<CardBean>()
            var index = 1
            mvrCardIter.use { iter ->
                while (iter.hasNext()) {
                    val card = iter.next()
                    beanList.add(CardBean(card))
                    index++
                }
            }
            cardTable.setBeans(beanList)
            logger.debug { "readCards " + index + " cards from " + auditRecordLocation }
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e){"setAuditRecord failed"}
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
        for (vpanel in tables) {
            vpanel.saveState(false)
        }

        prefs.putInt("splitPos1", split1.getDividerLocation())
    }
}