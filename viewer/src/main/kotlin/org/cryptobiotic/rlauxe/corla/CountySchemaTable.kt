/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlacvr.CorlaRawCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.corlacvr.SchemaColumnInfo
import org.cryptobiotic.rlauxe.corlacvr.SchemaContestInfo
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

private val logger = KotlinLogging.logger("CountyCvrsTable")

// add another table for contests that use CountyCvrs
class CountySchemaTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val contestTable: BeanTable<SchemaContestBean>
    private val choiceTable: BeanTable<SchemaChoiceBean>
    private val stylesTable: BeanTable<SchemaStyleBean>

    var currentCorlaCvrs : CorlaRawCvrsIF? = null
    var currentStateInput: ColoradoInput? = null
    var currentCountyName: String? = null

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    private val split2: JSplitPane

    init {
        contestTable = BeanTable(
            SchemaContestBean::class.java, prefs.node("contestTable") as PreferencesExt, false,
            "Contest Schema", "SchemaContestInfo", null)
        contestTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent ->
            val selected = contestTable.getSelectedBean()
            if (selected != null) setSelectedContest(selected) })
        contestTable.addPopupOption(
            "Show Canonical Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: SchemaContestBean -> showSchemaContest(bean) }
        )
        tables.add(contestTable)

        choiceTable = BeanTable(
            SchemaChoiceBean::class.java, prefs.node("choiceTable") as PreferencesExt, false,
            "Choices", "SchemaColumnInfo", null
        )
        tables.add(choiceTable)

        stylesTable = BeanTable(
            SchemaStyleBean::class.java, prefs.node("stylesTable") as PreferencesExt, false,
            "Styles", "CvrCardStyle", null)
        tables.add(stylesTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, choiceTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 600))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, stylesTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug { "CountySchemaTable init" }
    }

    fun showSchemaContest(bean: SchemaContestBean) = buildString {
        append(showContestWithDesc(bean, contestTable.tableModel, null))
        appendLine(bean.scontest.toString())
        if ((currentStateInput != null) && bean.getFailMatch() == "FAIL") {
            val wantContestName = bean.scontest.contestName
            appendLine()
            appendLine("Cvr contest '$wantContestName' does not match a canonical contest. Possible match(es) are:")
            appendLine()
            val input = currentStateInput!!
            val expectMunge = input.contestNameMunged(currentCountyName!!, wantContestName)
            var nmatch = 0
            var singleton: CanonicalContest? = null
            input.canonicalContestMungedNames.forEach { (mungeName, cc) ->
                // must use the county
                if (cc.counties.contains(currentCountyName)) {
                    if (expectMunge.startsWith(mungeName)) {
                        appendLine("  $expectMunge starts with $mungeName for '${cc.contestName}'")
                        nmatch++
                        singleton = cc
                    }
                    if (mungeName.contains(expectMunge)) {
                        appendLine("  $expectMunge is contained in $mungeName for '${cc.contestName}'")
                        nmatch++
                        singleton = cc
                    }
                }
                if (nmatch != 1) {
                    if (wantContestName.indexOf("-") > 0) {
                        val breakAt = wantContestName.lastIndexOf("-")
                        val lookfor = wantContestName.substring(breakAt + 1)
                        input.canonicalContests().forEach { (contestName, cc) ->
                            if (cc.counties.contains(currentCountyName)) { // must use this county
                                if (contestName.contains(lookfor)) {
                                    appendLine("  '$contestName' contains '$lookfor'")
                                    nmatch++
                                    singleton = cc
                                }
                            }
                        }
                    }
                }
            }
            if (nmatch == 1) {
                appendLine("\n*** Single candidate = '${singleton!!.contestName}'")
            }
        }
    }

    fun setCorlaInput(countyName: String, stateInput: ColoradoInput, corlaCvrs: CorlaRawCvrsIF?) {
        if (corlaCvrs == null) return
        currentStateInput = stateInput
        currentCorlaCvrs = corlaCvrs
        currentCountyName = countyName

        val beanList = mutableListOf<SchemaContestBean>()
        corlaCvrs.schema.contests.forEach {
            beanList.add(SchemaContestBean(this, it))
        }
        contestTable.setBeans(beanList)

        val styleList = mutableListOf<SchemaStyleBean>()
        corlaCvrs.cardStyles().forEach {
            styleList.add(SchemaStyleBean(it))
        }
        stylesTable.setBeans(styleList)
    }

    fun setSelectedContest(bean: SchemaContestBean) {
        val beanList = mutableListOf<SchemaChoiceBean>()
        val start = bean.scontest.startCol
        repeat(bean.scontest.ncols) {
            beanList.add(SchemaChoiceBean(this, bean, currentCorlaCvrs!!.schema.columns[start+it]))
        }
        choiceTable.setBeans(beanList)
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }

        prefs.putInt("splitPos1", split1.getDividerLocation())
        //prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun hasExact(scontest: SchemaContestBean): Boolean {
        return currentStateInput!!.canonicalContests()[scontest.contestName] != null
    }

    fun hasMunge(scontest: SchemaContestBean): Boolean {
        val cc = currentStateInput!!.matchCanonicalContest(currentCountyName!!, scontest.contestName)
        return cc != null
    }

    fun hasExact(scontest: SchemaContestBean, schoice: SchemaChoiceBean): Boolean {
        val cc = currentStateInput!!.matchCanonicalContest(currentCountyName!!, scontest.contestName)
        return if (cc == null) false else {
            cc.choices.any{ it == schoice.choice}
        }
    }

    fun hasMunge(scontest: SchemaContestBean, schoice: SchemaChoiceBean): Boolean {
        val cc = currentStateInput!!.matchCanonicalContest(currentCountyName!!, scontest.contestName)
        return if (cc == null) false else {
            currentStateInput!!.matchCanonicalCandidate(currentCountyName!!, cc, schoice.choice) != null
        }
    }

    // data class SchemaContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    //    val isIRV: Boolean
    //    val nchoices: Int
    //    val voteForN: Int
    class SchemaContestBean(val schemaTable: CountySchemaTable, val scontest: SchemaContestInfo) {
        val contestIdx = scontest.contestIdx
        val contestName = scontest.contestName
        val startCol = scontest.startCol
        val ncols = scontest.ncols
        val isIRV = scontest.isIRV
        val nchoices = scontest.nchoices
        val voteForN = scontest.voteForN

        fun getFailMatch() = if (schemaTable.hasMunge(this)) "" else "FAIL"

        companion object {
            @JvmStatic
            fun hiddenProperties() = "schemaTable scontest"
        }
    }

    class SchemaChoiceBean(val schemaTable: CountySchemaTable, val scontest: SchemaContestBean, colInfo: SchemaColumnInfo) {
        val contestIdx = colInfo.contestIdx
        val choice = colInfo.choiceName
        val party = colInfo.headerName
        val colno = colInfo.colno

        fun getFailMatch() = if (schemaTable.hasMunge(scontest, this)) "" else "FAIL"

        companion object {
            @JvmStatic
            fun hiddenProperties() = "schemaTable scontest colInfo"
        }
    }

    ////////////////////////////////////////////////////////////////

    class SchemaStyleBean(val cvrStyle: CvrCardStyle) {
        val name = cvrStyle.name
        val contestIdxs = cvrStyle.contestIds
        val countCards = cvrStyle.countCards

        companion object {
            @JvmStatic
            fun hiddenProperties() = "cvrStyle"
        }
    }

}
