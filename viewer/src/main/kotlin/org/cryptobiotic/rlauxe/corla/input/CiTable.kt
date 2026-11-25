/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla.input

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.co.auditcenter.ContestSelection
import org.cryptobiotic.rlauxe.co.auditcenter.TabulateContest
import org.cryptobiotic.rlauxe.co.corla.pValueApproximation
import org.cryptobiotic.rlauxe.co.input.ContestMergedInfo
import org.cryptobiotic.rlauxe.corlaInput.statewideInputs
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JPanel
import javax.swing.JSplitPane
import kotlin.String

private val logger = KotlinLogging.logger("ColoradoInputTable")


class ColoradoInputTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
    val setInput: (StatewideInput) -> Unit,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val inputTable: BeanTable<ColoradoInputBean>
    private val contestTable: BeanTable<CanonicalContestBean>
    private var selectedInputBean: ColoradoInputBean? = null
    private var onlyContested: Boolean = prefs.getBoolean("onlyContested", false)

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    private var isInit = false

    init {
        inputTable = BeanTable(
            ColoradoInputBean::class.java, prefs.node("ColoradoInputData") as PreferencesExt, false,
            "Available Colorado Data", "ColoradoInput", null)
        inputTable.addListSelectionListener {
            val selected = inputTable.getSelectedBean()
            if (isInit && selected != null) setSelectedInput(selected)
        }
        tables.add(inputTable)

        contestTable = BeanTable(
            CanonicalContestBean::class.java, prefs.node("CanonicalContests") as PreferencesExt, false,
            "Contest Merged Info", "Merged Canonical Contest Information", null)
        contestTable.addPopupOption(
            "Show Row",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: CanonicalContestBean -> showCanonicalContest(bean) }
        )
        contestTable.addPopupOption(
            "Print Contests", contestTable.makeShowAction(infoTA, infoWindow)
                { printTable(contestTable, CanonicalContestBean.beanProperties,"CanonicalContests") }
        )
        tables.add(contestTable)

        setFontSize(fontSize)

        setInputBeans()

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, inputTable, contestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CiTable init" }
        isInit = true
    }

    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {
        val onlyContestedAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val state = getValue( BAMutil.STATE)
                onlyContested = state as Boolean
                if (selectedInputBean != null) {
                    if (onlyContested) filterContested() else allContests(selectedInputBean!!)
                }
                prefs.putBoolean( "onlyContested", onlyContested)
            }
        }
        val savedState = prefs.getBoolean("onlyContested", false)
        onlyContestedAction.putValue(BAMutil.STATE, savedState)
        BAMutil.setActionProperties(onlyContestedAction, "sunrise-icon.png", "Contested Contests only", true, 'T'.code, -1)
        BAMutil.addActionToContainer(container, onlyContestedAction)
    }

    ////////////////////////////////////////////////////////////////
    fun setInputBeans() {
        val inputBeans = mutableListOf<ColoradoInputBean>()
        statewideInputs.forEach { inputBeans.add(ColoradoInputBean(it)) }
        inputTable.setBeans(inputBeans)
    }

    fun setSelectedInput(bean: ColoradoInputBean) {
        this.selectedInputBean = bean
        if (onlyContested) filterContested() else allContests(bean)
        setInput(bean.input)
    }

    fun allContests(inputBean: ColoradoInputBean) {
        this.selectedInputBean = inputBean
        contestTable.setBeans(emptyList())

        val contestTabs = inputBean.input.contestTabsAllCounties()
        val contestBeans = mutableListOf<CanonicalContestBean>()
        inputBean.input.mergedContestMap.forEach {
            contestBeans.add(CanonicalContestBean(it.key, it.value, contestTabs[it.key]))
        }
        contestTable.setBeans(contestBeans)
    }

    fun filterContested() {
        val filtered = contestTable.beans.filter { it.contested() }
        contestTable.setBeans(filtered)
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }

        prefs.putInt("splitPos1", split1.getDividerLocation())
        // prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showCanonicalContest(bean: CanonicalContestBean) = buildString {
        appendLine(contestTable.tableModel.showBean(bean, CanonicalContestBean.beanProperties))
        appendLine(bean.contest.toString())
        if (bean.contestTab != null) {
            bean.contestTab.choices.forEach { (key, value) ->
                 appendLine("  ${sfn(key,20)} : $value")
            }
        }
    }

    class ColoradoInputBean(val input: StatewideInput) {
        val name = input.name()
        val ncontests = input.ncontests()
        val uncontested = input.nuncontested()
        val contested = ncontests - uncontested
        val targetedContests: Int

        val countyMvrs: Int
        val stateMvrs: Int
        val totalMvrs: Int
        val totalCards: Int

        init {
            totalCards = input.countyStrataMap.values.sumOf{ it.population }
            countyMvrs = input.countyStrataMap.values.sumOf{ it.countyMvrs }
            stateMvrs = input.countyStrataMap.values.sumOf{ it.stateMvrs }
            totalMvrs = countyMvrs + stateMvrs

            val selected: Map<String, ContestSelection> = ContestSelection.readFromFile(input.contestSelectionFile)
            targetedContests = selected.size
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "input"
        }
    }

    ////////////////////////////////////////////////////////////////
    // use ContestMergedInfo ?

    class CanonicalContestBean(val name: String, val mcontest: ContestMergedInfo, val contestTab: TabulateContest?) {
        val contest = mcontest.canonicalContest

        // contest round
        val auditReason = mcontest.corlaReason
        val corlaPop = mcontest.corlaPop       // ballot_card_count
        val corlaNc = mcontest.corlaNc         // contest_ballot_card_count
        val corlaNmvrs  = mcontest.corlaEstSamples // Corla estimated_samples_to_audit
        val estNmvrs  = mcontest.rlauxeEstSamples // Corla estimated_samples_to_audit
        val marginInVotes = mcontest.marginInVotes // min_margin
        val margin = mcontest.marginInVotes / mcontest.corlaPop.toDouble()

        val p2o = if (mcontest.errors.isEmpty()) 0 else mcontest.errors[0]
        val p1o = if (mcontest.errors.isEmpty()) 0 else mcontest.errors[1]
        val p1u = if (mcontest.errors.isEmpty()) 0 else mcontest.errors[2]
        val p2u = if (mcontest.errors.isEmpty()) 0 else mcontest.errors[3]

        val riskLimit  = mcontest.riskLimit // risk_limit
        val voteForN = mcontest.voteForN  // winners_allowed

        // mvr file
        val countyMvrs = mcontest.strata!!.countyMvrs
        val statewideMvrs = mcontest.strata!!.stateMvrs
        val extraMvrs =  countyMvrs + statewideMvrs - mcontest.corlaEstSamples

        fun getRecount(): Double {
            if (contestTab == null) return Double.NaN
            val winnerVote = contestTab.choices.maxOf { it.value }
            return mcontest.marginInVotes / winnerVote.toDouble()
        }

        fun getNCounties(): String {
            val counties = counties()
            if (counties.size == 1) return counties[0]
            return String.format("%02d", counties.size)
        }

        fun counties(): List<String> {
            return contest.counties.toList()
        }

        fun getNCand() = contest.choices.size

        fun getChoices() = contest.choices.toString()

        fun contested() = getNCand() > voteForN

        fun getCorlaRisk(): Double {
            var pe = mcontest.errors
            if (pe.isEmpty()) pe = listOf(0,0,0,0)
            return pValueApproximation(
                    n = mcontest.strata!!.nmvrs, // n = auditedBallots the number of ballots audited for this strata
                    dilutedMargin = margin, // probably minimum margin of the contest's assertions,
                    gamma = 1.03905,  // “inflator” γ > 1; same purpose as MaxRisk I think. Corla uses 1.03905.
                    pe[0], pe[1], pe[2], pe[3],
                )
        }

        fun getExtraRisk(): Double {
            val noerrs =  pValueApproximation(
                n = mcontest.strata!!.nmvrs, // n = auditedBallots the number of ballots audited for this strata
                dilutedMargin = margin, // probably minimum margin of the contest's assertions,
                gamma = 1.03905,  // “inflator” γ > 1; same purpose as MaxRisk I think. Corla uses 1.03905.
                0, 0, 0, 0,
            )
            return getCorlaRisk() - noerrs
        }

        fun getTarget(): String? {
            return when (mcontest.corlaReason) {
                AuditReason.county_wide_contest -> "county"
                AuditReason.state_wide_contest -> "state"
                AuditReason.single_county -> "single"
                else -> null
            }
        }

        fun statewide(): Boolean {
            return (counties().size > 60)
        }

        fun getStatus(): String {
            return when {
                !contested() -> "noloser"
                else -> ""
            }
        }
        
        companion object {
            @JvmStatic
            fun hiddenProperties() = "mcontest contest contestTab strata"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("auditReason", "from round.audit_reason"),
                TableBeanProperty("choices", "list of choices/candidates"),
                TableBeanProperty("corlaPop", "round.ballot_card_count"),
                TableBeanProperty("corlaNc", "from round.contest_ballot_card_count"),
                TableBeanProperty("corlaRisk", "risk achieved"),
                TableBeanProperty("countyMvrs", "count of mvrs in this county"),
                TableBeanProperty("estSamples", "from round.estimated_samples_to_audit"),
                TableBeanProperty("extraMvrs", "countyMvrs + statewideMvrs - estSamples"),
                TableBeanProperty("margin", "vote margin / corlaPop"),
                TableBeanProperty("marginInVotes", "from round.min_margin"),
                TableBeanProperty("name", "contest name"),
                TableBeanProperty("NCand", "number of choices (candidates)"),
                TableBeanProperty("NCounties", "number of counties, or county name if only one"),
                TableBeanProperty("recount", "vote margin / winner's votes"),
                TableBeanProperty("p2o", "two count overstatement"),
                TableBeanProperty("p1o", "one count overstatement"),
                TableBeanProperty("p1u", "one count understatement"),
                TableBeanProperty("p2u", "two count understatement"),
                TableBeanProperty("riskLimit", "from round.risk_limit"),
                TableBeanProperty("statewideMvrs", "count of statewide mvrs over all this countest's counties"),
                TableBeanProperty("target", "if targeted for audit, from round.audit_reason"),
                TableBeanProperty("voteForN", "from round.winners_allowed"),
            )
        }
    }
}
