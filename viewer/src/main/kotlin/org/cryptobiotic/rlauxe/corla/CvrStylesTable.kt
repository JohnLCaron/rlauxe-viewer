/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.CvrCardStyle
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CvrStylesTable")

class CvrStylesTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val stylesTable: BeanTable<StyleBean>

    val localInfo = TextHistoryPane()
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        stylesTable = BeanTable(
            StyleBean::class.java, prefs.node("stylesTable") as PreferencesExt, false,
            "Cvr Redactions", "Cvr Redactions", null)
        tables.add(stylesTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, stylesTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountySchemaTable init" }
    }


    fun setCorlaCvrs(corlaCvrs: CorlaCvrsIF?) {
        if (corlaCvrs == null) return
        val beanList = mutableListOf<StyleBean>()
        corlaCvrs.cardStyles().forEach {
            beanList.add(StyleBean(it))
        }
        stylesTable.setBeans(beanList)
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

    class StyleBean(val cvrStyle: CvrCardStyle) {
        val name = cvrStyle.name
        val contestIds = cvrStyle.contestIds
        val countCards = cvrStyle.countCards

        companion object {
            @JvmStatic
            fun hiddenProperties() = "cvrStyle"
        }
    }
}
