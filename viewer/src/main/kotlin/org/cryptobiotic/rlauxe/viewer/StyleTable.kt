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
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent

class StyleTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane?,
    infoWindow: IndependentWindow?,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {

    private val styleTable: BeanTable<StyleBean>
    var localInfo: TextHistoryPane = TextHistoryPane()

    private val split1: JSplitPane

    private var auditRecord: AuditRecord? = null
    var mvrManager: PersistedMvrManager? = null

    init {
        // class BeanTable<T>(
        //    val beanClass: Class<T>,
        //    val store: PreferencesExt,
        //    val canAddDelete: Boolean,
        //    val header: String,
        //    val tooltip: String,
        //    val innerbean: T,

        styleTable = BeanTable<StyleBean>(
            StyleBean::class.java, prefs.node("styleTable") as PreferencesExt, false,
            "CardStyles", "CardStyle"
        )
        styleTable.addListSelectionListener { e: ListSelectionEvent ->
            val poolBean = styleTable.getSelectedBean()
            if (poolBean != null) {
                setSelectedPool(poolBean)
            }
        }

        // poolTable.addPopupOption("Show Population", poolTable.makeShowAction(localInfo,
        //    bean -> ((PoolBean) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, styleTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "StyleTable init" }
    }

    override fun setFontSize(size: Float) {
        styleTable.setFontSize(size)
        localInfo.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        logger.debug { "StyleTable setAuditRecord $auditRecordLocation" }
        styleTable.setBeans(null)

        val auditRecord = read(auditRecordLocation)
        if (auditRecord == null) {
            logger.info{"StyleTable failed on readFrom $auditRecordLocation"}
            return false
        }
        if (auditRecord is CompositeAuditRecord) return false
        this.auditRecord = auditRecord as AuditRecord
        this.mvrManager = PersistedMvrManager(this.auditRecord!!, false)

        try {
            val beanList = mutableListOf<StyleBean>()
            val styles = mvrManager!!.styles()
            if (styles != null) {
                for (pop in styles) {
                    beanList.add(StyleBean(pop))
                }
            }
            styleTable.setBeans(beanList)
            logger.debug{"setAuditRecord bean count=${beanList.size}" }

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.debug(e) {"setAuditRecord failed"}
        }

        return true
    }

    fun setCounty(wantCounty: String) {
        val beanList = mutableListOf<StyleBean>()
        for (bean in styleTable.beans) {
            if (bean.getCounty() == wantCounty)
                beanList.add(bean)
        }
        styleTable.setBeans(beanList)
    }

    fun setSelectedPool(bean: StyleBean) {
        localInfo.setText(bean.show())
        localInfo.gotoTop()
    }

    override fun saveState() {
        styleTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
    }


    class StyleBean(val style: StyleIF) {
        val styleName = style.name()
        val id = style.id()
        val ncards= style.ncards()
        val exactContests= style.hasExactContests()
        val ncontests= style.possibleContests().size
        val contests: String
            get() {
                val ids = style.possibleContests().toList().sorted()
                return ids.toString()
            }

        fun getCounty(): String {
            return if (styleName.indexOf(":") > 0) styleName.substring(0, styleName.indexOf(":"))
            else if (styleName.indexOf("-") > 0) styleName.substring(0, styleName.indexOf("-"))
            else "N/A"
        }
        fun show() = style.toString()

        companion object {
            @JvmStatic
            fun hiddenProperties() = "style"
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("StyleTable")
    }

}