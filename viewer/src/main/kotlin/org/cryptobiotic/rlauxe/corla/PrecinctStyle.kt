/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.corlacvr.CorlaRawCvrsIF
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CountyCvrsTable")

// countyInput.readCorlaCvrs()
class PrecinctStyle(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
    ) : JPanel(), SubPanelIF {

    private val precinctStyleTable: BeanTable<PrecinctStyleBean>
    var localInfo: TextHistoryPane = TextHistoryPane()

    private val split1: JSplitPane

    var corlaCvrs: CorlaRawCvrsIF? = null

    init {
        precinctStyleTable = BeanTable(
            PrecinctStyleBean::class.java, prefs.node("precinctStyleTable") as PreferencesExt, false,
            "Precinct Styles", "CvrRow", null
        )
        /* precinctStyleTable.addListSelectionListener { e: ListSelectionEvent? ->
            val cardBean = precinctStyleTable.getSelectedBean()
            if (cardBean != null) setSelectedRow(cardBean) } */

        //cardTable.addPopupOption("Show Population", cardTable.makeShowAction(localInfo,
        //    bean -> ((cardTable) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, precinctStyleTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "cardTable init" }
    }

    override fun setFontSize(size: Float) {
        precinctStyleTable.setFontSize(size)
        localInfo.setFontSize(size)
    }

    fun setCvrs(cvrs: CorlaRawCvrsIF) {
        corlaCvrs = cvrs

        val styleCounters = mutableMapOf<Pair<String, String>, Int>()
        corlaCvrs!!.cvrs().forEach {
            val id = Pair(it.ballotType, it.precinctPortion ?: "none")
            val styleCounter = styleCounters.getOrDefault(id, 0)
            styleCounters[id] = styleCounter + 1
        }

        try {
            val beanList = mutableListOf<PrecinctStyleBean>()
            var count = 0
            styleCounters.forEach {
                beanList.add(PrecinctStyleBean(it.key.first, it.key.second, it.value))
            }
            precinctStyleTable.setBeans(beanList)
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "setCountyInput failed" }
        }
    }

    /* fun setSelectedRow(bean: PrecinctStyleBean) {
        localInfo.setText(bean.show(corlaCvrs!!.schema))
        localInfo.gotoTop()
    } */

    override fun saveState() {
        precinctStyleTable.saveState(false)
        prefs.putInt("splitPos1", split1.getDividerLocation())
    }


    class PrecinctStyleBean(val style: String, val precinct: String, val count: Int) {

        companion object {
            @JvmStatic
            fun hiddenProperties() = "unique"
        }
    }
}
