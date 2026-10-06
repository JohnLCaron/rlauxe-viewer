/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.corla.CountySchemaTable.SchemaContestBean
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.corlacvr.CorlaRawCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.corlacvr.CvrRow
import org.cryptobiotic.rlauxe.corlacvr.CvrSchema
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent

private val logger = KotlinLogging.logger("CountyCvrsTable")

// countyInput.readCorlaCvrs()
class CountyCvrsTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()
    val cvrTable: BeanTable<CvrRowBean>
    val precinctStyleTable: BeanTable<PrecinctStyleBean>
    val stylesTable: BeanTable<SchemaStyleBean>

    private val split1: JSplitPane
    private val split2: JSplitPane

    var corlaCvrs: CorlaRawCvrsIF? = null
    var cardStyleMap = emptyMap<Set<Int>, CvrCardStyle>()

    init {
        cvrTable = BeanTable(
            CvrRowBean::class.java, prefs.node("cardTable") as PreferencesExt, false,
            "CVRs from County", "CvrRow", null
        )
        cvrTable.addPopupOption(
            "Show Cvr",
            cvrTable.makeShowAction(infoTA, infoWindow) { bean: CvrRowBean -> showSelectedRow(bean) }
        )
        tables.add(cvrTable)

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
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, cvrTable, precinctStyleTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 600))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, stylesTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug { "CountyCvrsTable init" }
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }
        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showSelectedRow(bean: CvrRowBean) = buildString {
        append(bean.show(corlaCvrs!!.schema))
    }

    fun setCountyInput(countyInput: CorlaCountyInput) {
        val maxRead = 11111

        try {
            corlaCvrs = countyInput.readCorlaCvrs()
            if (corlaCvrs != null) setStyles(corlaCvrs!!)

            val beanList = mutableListOf<CvrRowBean>()
            var count = 0
            corlaCvrs!!.cvrs().forEach {
                beanList.add(CvrRowBean(it))
                if (count++ > maxRead) return@forEach
            }
            cvrTable.setBeans(beanList)
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "setCountyInput failed" }
        }
    }

    class UniqueContests() {
        val contests = mutableMapOf<Set<Int>, Int>() // count unique contests within the precinct
        var ncards = 0
        var styleMap = emptyMap<String, Int>()

        fun convert(cardStyleMap: Map<Set<Int>, CvrCardStyle>) {
            styleMap = contests.mapKeys { cardStyleMap[it.key]?.name ?: "unknown" }
        }
    }

    fun setStyles(cvrs: CorlaRawCvrsIF) {
        corlaCvrs = cvrs
        this.cardStyleMap = cvrs.cardStyleMap()

        val styleCounters = mutableMapOf<Pair<String, String>, UniqueContests>()
        corlaCvrs!!.cvrs().forEach { cvr ->
            val id = Pair(cvr.ballotType, cvr.precinctPortion ?: "none")
            val unique = styleCounters.getOrPut(id) { UniqueContests() }
            val count = unique.contests.getOrDefault(cvr.contests(), 0)
            unique.contests[cvr.contests()] = count + 1
            unique.ncards++
        }
        styleCounters.values.forEach { it.convert(this.cardStyleMap) }

        val beanList = mutableListOf<PrecinctStyleBean>()
        styleCounters.forEach {
            beanList.add(PrecinctStyleBean(it.key.first, it.key.second, it.value))
        }
        precinctStyleTable.setBeans(beanList)

        val styleList = mutableListOf<SchemaStyleBean>()
        cvrs.cardStyles().forEach {
            styleList.add(SchemaStyleBean(it))
        }
        stylesTable.setBeans(styleList)
    }
}

class CvrRowBean(val row: CvrRow) {
    val cvrNumber = row.cvrNumber
    val tabulatorNum = row.tabulatorNum
    val batchId = row.batchId
    val recordId = row.recordId
    val imprintedId = row.imprintedId
    val ballotType = row.ballotType
    val precinctPortion = row.precinctPortion

    val votes = buildString {
        row.contestVotes.forEach {
            append("${it.contestId}: ${it.candVotes()}, ")
        }
    }

    fun show(schema: CvrSchema) = buildString {
        appendLine(row.toString())
        row.contestVotes.forEach {
            val contest = schema.contests.get(it.contestId)
            append("  contest: ${sfn(contest.contestName, 60)} (${nfn(it.contestId, 3)}), ")
            appendLine(" candidate votes: ${it.candVotes()}")
        }
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "row"
    }
}

class PrecinctStyleBean(val ballotType: String, val precinctPortion: String, val unique: CountyCvrsTable.UniqueContests) {
    val uniqueContestSets = unique.contests.size
    val styleCounts = unique.styleMap
    val ncards = unique.ncards

    companion object {
        @JvmStatic
        fun hiddenProperties() = "uniqueContest"
    }
}


