/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlacvr.CardStyleId
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.corlacvr.cleanCsvString
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CiCvrs")

// countyInput.readCorlaCvrs()
class CiStyles(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()
    val precinctStyleTable: BeanTable<PrecinctStyleBean>
    val stylesTable: BeanTable<SchemaStyleBean>

    // private val split1: JSplitPane
    private val split2: JSplitPane

    var corlaCvrs: CorlaCountyCvrsIF? = null
    var cardStyleMap = emptyMap<CardStyleId, CvrCardStyle>()

    init {

        precinctStyleTable = BeanTable(
            PrecinctStyleBean::class.java, prefs.node("precinctStyleTable") as PreferencesExt, false,
            "Precinct Styles", "CvrRow", null
        )
        tables.add(precinctStyleTable)

        stylesTable = BeanTable(
            SchemaStyleBean::class.java, prefs.node("stylesTable") as PreferencesExt, false,
            "Styles", "CvrCardStyle", null
        )
        tables.add(stylesTable)

        //cardTable.addPopupOption("Show Population", cardTable.makeShowAction(localInfo,
        //    bean -> ((cardTable) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        //split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, cvrTable, precinctStyleTable)
        //split1.setDividerLocation(prefs.getInt("splitPos1", 600))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, precinctStyleTable, stylesTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug { "CiCvrs init" }
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }
        // prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    // TODO CountyInput knows its StateInput
    fun setCountyInput(countyInput: CountyInput) {
        val maxRead = 11111

        try {
            // TODO better to run it through CountyCardsFromManifest?
            corlaCvrs = countyInput.readCorlaCvrs()
            if (corlaCvrs != null) setStyles(corlaCvrs!!)

            /* val beans = mutableListOf<CvrRowBean>()
            var count = 0
            corlaCvrs!!.cvrs().forEach {
                beans.add(CvrRowBean(it))
                if (count++ > maxRead) return@forEach
            }

            val sortedBeans = beans.sortedWith(
                compareBy<CvrRowBean> { it.precinctPortion ?: "" }.thenBy { it.ballotType }
            )
            cvrTable.setBeans(sortedBeans) */
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "setCountyInput failed" }
        }
    }

    fun setStyles(corlaCvrs: CorlaCountyCvrsIF) {
        this.cardStyleMap = corlaCvrs.cardStyleMap()

        val precinctStyles = mutableMapOf<PrecinctCardStyle, PrecinctCardStyle>()
        corlaCvrs.cvrs().forEach { cvr ->
            val cleanBallotType = cleanCsvString(cvr.ballotType) // no commas
            val cvrStyleId = CardStyleId(cleanBallotType, cvr.contests())
            val cardStyle = cardStyleMap[cvrStyleId]!!
            val key = PrecinctCardStyle(cardStyle, cvr.precinctPortion ?: "")
            val precinctStyle = precinctStyles.getOrPut(key) { key }
            precinctStyle.ncards++
        }

        val beanList = mutableListOf<PrecinctStyleBean>()
        precinctStyles.values.forEach {
            beanList.add(PrecinctStyleBean(it))
        }
        precinctStyleTable.setBeans(beanList)

        val styleList = mutableListOf<SchemaStyleBean>()
        corlaCvrs.cardStyles().forEach {
            styleList.add(SchemaStyleBean(it))
        }
        stylesTable.setBeans(styleList)
    }

    fun showPrecinctStyleInfo() = buildString {
        val cardCount = precinctStyleTable.beans.sumOf { it.ncards }

        data class PrecinctStyle(val precinct: String, val style: Set<Int>)

        val uniquePrecinctStyles = mutableMapOf<PrecinctStyle, Int>()
        corlaCvrs!!.cvrs().forEach { row ->
            val ps = PrecinctStyle(row.precinctPortion?: "", row.contests())
            val accum = uniquePrecinctStyles.getOrDefault(ps, 0)
            uniquePrecinctStyles[ps] = accum + 1
        }
        var countRarePrecinctStyles = 0
        var countRarePrecinctStyleCards = 0
        uniquePrecinctStyles.forEach { (style, ncards) ->
            if (ncards < 10) {
                countRarePrecinctStyles++
                countRarePrecinctStyleCards += ncards
            }
        }

        var countRareCvrCardStyle = 0
        var countRareCvrCards = 0
        stylesTable.beans.forEach {
            if (it.ncards < 10) {
                countRareCvrCardStyle++
                countRareCvrCards += it.ncards
            }
        }

        val uniqueStyles = mutableMapOf<Set<Int>, Int>()
        corlaCvrs!!.cvrs().forEach { row ->
            val accum = uniqueStyles.getOrDefault(row.contests(), 0)
            uniqueStyles[row.contests()] = accum + 1
        }
        var countRareStyles = 0
        var countRareStyleCards = 0
        uniqueStyles.forEach { (style, ncards) ->
            if (ncards < 10) {
                countRareStyles++
                countRareStyleCards += ncards
                appendLine("rare style = ${style} ncards=$ncards")
            }
        }

        appendLine("cardCount = $cardCount")
        appendLine("unique precinct/styles = ${precinctStyleTable.beans.size}")
        appendLine("count rare precinct/styles = ${countRarePrecinctStyles} out of ${uniquePrecinctStyles.size} = ${countRarePrecinctStyleCards} cards")
        appendLine()
        appendLine("unique ballotType/styles = ${stylesTable.beans.size}")
        appendLine("count rare ballotType/style = ${countRareCvrCardStyle} out of ${stylesTable.beans.size} = ${countRareCvrCards} cards")
        appendLine()
        appendLine("unique styles = ${uniqueStyles.size}")
        appendLine("count rare styles = ${countRareStyles} out of ${uniqueStyles.size} = ${countRareStyleCards} cards ")
    }
}

/*     fun showPrecinctStyleInfo() = buildString {
        val cardCount = precinctStyleTable.beans.sumOf { it.ncards }

        data class PrecinctStyle(val precinct: String, val style: Set<Int>)

        val uniquePrecinctStyles = mutableMapOf<PrecinctStyle, Int>()
        corlaCvrs!!.cvrs().forEach { row ->
            val ps = PrecinctStyle(row.precinctPortion?: "", row.contests())
            val accum = uniquePrecinctStyles.getOrDefault(ps, 0)
            uniquePrecinctStyles[ps] = accum + 1
        }
        var countRarePrecinctStyles = 0
        var countRarePrecinctStyleCards = 0
        uniquePrecinctStyles.forEach { (style, ncards) ->
            if (ncards < 10) {
                countRarePrecinctStyles++
                countRarePrecinctStyleCards += ncards
            }
        }

        var countRareCvrCardStyle = 0
        var countRareCvrCards = 0
        stylesTable.beans.forEach {
            if (it.ncards < 10) {
                countRareCvrCardStyle++
                countRareCvrCards += it.ncards
            }
        }

        val uniqueStyles = mutableMapOf<Set<Int>, Int>()
        corlaCvrs!!.cvrs().forEach { row ->
            val accum = uniqueStyles.getOrDefault(row.contests(), 0)
            uniqueStyles[row.contests()] = accum + 1
        }
        var countRareStyles = 0
        var countRareStyleCards = 0
        uniqueStyles.forEach { (style, ncards) ->
            if (ncards < 10) {
                countRareStyles++
                countRareStyleCards += ncards
                appendLine("rare style = ${style} ncards=$ncards")
            }
        }

        appendLine("cardCount = $cardCount")
        appendLine("unique precinct/styles = ${precinctStyleTable.beans.size}")
        appendLine("count rare precinct/styles = ${countRarePrecinctStyles} out of ${uniquePrecinctStyles.size} = ${countRarePrecinctStyleCards} cards")
        appendLine()
        appendLine("unique ballotType/styles = ${stylesTable.beans.size}")
        appendLine("count rare ballotType/style = ${countRareCvrCardStyle} out of ${stylesTable.beans.size} = ${countRareCvrCards} cards")
        appendLine()
        appendLine("unique styles = ${uniqueStyles.size}")
        appendLine("count rare styles = ${countRareStyles} out of ${uniqueStyles.size} = ${countRareStyleCards} cards ")
    } */

data class PrecinctCardStyle(val cardStyle: CvrCardStyle, val precinct: String) {
    var ncards = 0
}

class PrecinctStyleBean(precinctStyle: PrecinctCardStyle) {
    val cardType = precinctStyle.cardStyle.cardStyle
    val contestIds = precinctStyle.cardStyle.contestIds
    val ncards = precinctStyle.ncards
    val precinctPortion = precinctStyle.precinct

    companion object {
        @JvmStatic
        fun hiddenProperties() = "unique"
    }
}


