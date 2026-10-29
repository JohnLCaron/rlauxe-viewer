/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.corlacvr.SchemaColumnInfo
import org.cryptobiotic.rlauxe.corlacvr.SchemaContestInfo
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane
import kotlin.text.appendLine

private val logger = KotlinLogging.logger("CiCvrs")

// add another table for contests that use CountyCvrs
class CiSchema(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val contestTable: BeanTable<SchemaContestBean>
    private val choiceTable: BeanTable<SchemaChoiceBean>
    private val stylesTable: BeanTable<SchemaStyleBean>

    var currentCorlaCvrs: CorlaCountyCvrsIF? = null
    var currentStateInput: StatewideInput? = null
    var currentCountyInput: CountyInput? = null
    var currentCountyName: String? = null

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    private val split2: JSplitPane

    init {
        contestTable = BeanTable(
            SchemaContestBean::class.java, prefs.node("contestTable") as PreferencesExt, false,
            "Contest Schema", "SchemaContestInfo", null
        )
        contestTable.addListSelectionListener {
            val selected = contestTable.getSelectedBean()
            if (selected != null) setSelectedContest(selected)
        }
        contestTable.addPopupOption(
            "Show Canonical Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: SchemaContestBean -> showSchemaContest(bean) }
        )
        contestTable.addPopupOption(
            "Print Table",
            contestTable.makeShowAction(infoTA, infoWindow)
            { printTable(contestTable, SchemaContestBean.beanProperties,"CountyContests") }
        )
        tables.add(contestTable)

        choiceTable = BeanTable(
            SchemaChoiceBean::class.java, prefs.node("choiceTable") as PreferencesExt, false,
            "Choices", "SchemaColumnInfo", null
        )
        tables.add(choiceTable)

        stylesTable = BeanTable(
            SchemaStyleBean::class.java, prefs.node("stylesTable") as PreferencesExt, false,
            "CvrCardStyle", "CvrCardStyle", null
        )
        tables.add(stylesTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, choiceTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 600))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, stylesTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug { "CiSchema init" }
    }

    fun showSchemaContest(bean: SchemaContestBean) = buildString {
        append(showContestWithDesc(bean, contestTable.tableModel, null))
        appendLine(bean.scontest.show())
        appendLine("choices")
        currentCorlaCvrs!!.schema.columnsFor(bean.scontest).forEach { colInfo ->
            appendLine("  ${colInfo}")
        }
        appendLine()

        val input = currentStateInput
        if ((input != null) && bean.getMatch() == "FAIL") {
            val wantContestName = bean.scontest.contestName
            val canon = input.matchCanonicalContest(currentCountyName!!, wantContestName)

            val cleanedName = currentCountyInput!!.contestNameCleanup(wantContestName)
            bean.scontest.contestName
            appendLine()
            appendLine("Cvr contest '$wantContestName' cleaned='$cleanedName' canonical contest = $canon")
            appendLine()
            /*
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
            } */
        }
    }

    fun setCorlaInput(countyInput: CountyInput, stateInput: StatewideInput, corlaCvrs: CorlaCountyCvrsIF?) {
        if (corlaCvrs == null) return
        currentStateInput = stateInput
        currentCorlaCvrs = corlaCvrs
        currentCountyInput = countyInput
        currentCountyName = countyInput.countyName
        logger.debug { "set county $currentCountyName stateInput=${stateInput.name()} corlaCvrs=${corlaCvrs.electionName}" }

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

        if (stateInput == Statewide2026PwithCvrs() && currentCountyName == "La Plata") {
            val failing = "Secretary of State"
            val canon = stateInput.matchCanonicalContest(currentCountyName!!, failing)
            logger.debug{"$failing -> $canon"}
        }
    }

    fun setSelectedContest(bean: SchemaContestBean) {
        val beanList = mutableListOf<SchemaChoiceBean>()
        currentCorlaCvrs!!.schema.columnsFor(bean.scontest).forEach { colInfo ->
            if (!isWriteIn(colInfo.choiceName)) {
                beanList.add(SchemaChoiceBean(this, bean,colInfo))
            }
        }
        choiceTable.setBeans(beanList)
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }
        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
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
            cc.choices.any { it == schoice.choice }
        }
    }

    fun hasMunge(scontest: SchemaContestBean, schoice: SchemaChoiceBean): Boolean {
        val cc = currentStateInput!!.matchCanonicalContest(currentCountyName!!, scontest.contestName)
        return if (cc == null) false else {
            currentStateInput!!.matchCanonicalCandidate(currentCountyName!!, cc, schoice.choice) != null
        }
    }

    fun canonicalContest(scontest: SchemaContestInfo): String {
        val cc = currentStateInput!!.matchCanonicalContest(currentCountyName!!, scontest.contestName.trim())
        return cc?.contestName ?: ""
    }

    fun canonicalChoice(scontest: SchemaContestInfo, choice: String): String {
        val cc = currentStateInput!!.matchCanonicalContest(currentCountyName!!, scontest.contestName)
        if (cc == null) return ""
        return currentStateInput!!.matchCanonicalCandidate(currentCountyName!!, cc, choice) ?: ""
    }

    fun hasMatch(scontest: SchemaContestInfo): Boolean {
        val cleanedName = currentCountyInput!!.contestNameCleanup(scontest.contestName)
        val cc = currentStateInput!!.canonicalContests()[cleanedName]
        if (cc == null) return false
        currentCorlaCvrs!!.schema.columnsFor(scontest).forEach { colInfo ->
            if (currentStateInput!!.matchCanonicalCandidate(currentCountyName!!, cc, colInfo.choiceName) == null) return false
        }
        return true
    }

    // data class SchemaContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    //    val isIRV: Boolean
    //    val nchoices: Int
    //    val voteForN: Int
    class SchemaContestBean(val schemaTable: CiSchema, val scontest: SchemaContestInfo) {
        val contestIdx = scontest.contestIdx
        val contestName = scontest.contestName
        val canonName = schemaTable.canonicalContest(scontest)

        fun getMatch() = if (canonName == "") "FAIL" else if (canonName != contestName) "*" else ""

        companion object {
            @JvmStatic
            fun hiddenProperties() = "schemaTable scontest"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("contestIdx", "index of contest in county schema"),
                TableBeanProperty("contestName", "contest name"),
            )

        }
    }

    class SchemaChoiceBean(
        val schemaTable: CiSchema,
        val sbean: SchemaContestBean,
        val colInfo: SchemaColumnInfo
    ) {
        val contestIdx = colInfo.contestIdx
        val choice = colInfo.choiceName
        val party = colInfo.partyName
        val colno = colInfo.colIdx
        val canon = schemaTable.canonicalChoice(sbean.scontest, choice)

        fun getMatch() = if (canon == "") "FAIL" else if (canon != choice) "*" else ""

        companion object {
            @JvmStatic
            fun hiddenProperties() = "schemaTable sbean colInfo"
        }
    }
}

////////////////////////////////////////////////////////////////

class SchemaStyleBean(val cvrStyle: CvrCardStyle) {
    val cardType = cvrStyle.cardStyle
    val contestIds = cvrStyle.contestIds
    val ncards = cvrStyle.ncards

    companion object {
        @JvmStatic
        fun hiddenProperties() = "cvrStyle"
    }
}
