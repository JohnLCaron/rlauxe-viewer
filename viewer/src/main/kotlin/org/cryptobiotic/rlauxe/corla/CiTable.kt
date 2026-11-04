/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.auditcenter.ContestTabAllCounties
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.MergedContestInfo
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
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

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        inputTable = BeanTable(
            ColoradoInputBean::class.java, prefs.node("ColoradoInputData") as PreferencesExt, false,
            "Available Colorado Data", "ColoradoInput", null)
        inputTable.addListSelectionListener {
            val selected = inputTable.getSelectedBean()
            if (selected != null) setSelectedInput(selected)
        }
        tables.add(inputTable)

        contestTable = BeanTable(
            CanonicalContestBean::class.java, prefs.node("CanonicalContests") as PreferencesExt, false,
            "Canonical Contests", "Merged Canonical Contest Information", null)
        contestTable.addPopupOption(
            "Show Row",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: CanonicalContestBean -> showCanonicalContest(bean) }
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

        logger.debug { "CountyPoolTable init" }
    }

    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {
        val readMvrAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val selected = contestTable.getSelectedBean()
                if (selected != null) {
                    // readMvrTabulation(selected)
                }
            }
        }
        BAMutil.setActionProperties(readMvrAction, "sunrise-icon.png", "Read Mvr Tabulation", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, readMvrAction)
    }

    fun setSelectedInput(inputBean: ColoradoInputBean) {
        contestTable.setBeans(emptyList())

        val contestTabs: Map<String, ContestTabAllCounties> = inputBean.input.contestTabsAllCounties()

        // using mergedContestMap instead of canonical contests
        val contestBeans = mutableListOf<CanonicalContestBean>()
        inputBean.input.mergedContestMap.forEach {
            contestBeans.add(CanonicalContestBean(it.key, it.value, contestTabs[it.key]))
        }
        contestTable.setBeans(contestBeans)

        setInput(inputBean.input)
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

    ////////////////////////////////////////////////////////////////
    fun setInputBeans() {
        val inputBeans = mutableListOf<ColoradoInputBean>()
        statewideInputs.forEach { inputBeans.add(ColoradoInputBean(it)) }
        inputTable.setBeans(inputBeans)
    }

    class ColoradoInputBean(val input: StatewideInput) {
        val name = input.name()
        val ncontests = input.ncontests()
        val uncontested = input.nuncontested()
        val contested = ncontests - uncontested

        companion object {
            @JvmStatic
            fun hiddenProperties() = "input"
        }
    }

    ////////////////////////////////////////////////////////////////

    class CanonicalContestBean(val name: String, val mcontest: MergedContestInfo, val contestTab: ContestTabAllCounties?) {
        val contest = mcontest.canonicalContest

        // contest round
        val auditReason = mcontest.auditReason
        val corlaPop = mcontest.corlaPop       // ballot_card_count
        val ncast = mcontest.nc         // contest_ballot_card_count
        val voteForN = mcontest.voteForN  // winners_allowed
        val estSamples  = mcontest.estSamples // Corla estimated_samples_to_audit
        val marginInVotes = mcontest.marginInVotes // min_margin
        val riskLimit  = mcontest.riskLimit // risk_limit

        // mvr file
        val countyMvrs = mcontest.countyMvrs
        val statewideMvrs = mcontest.statewideMvrs

        fun getNCounties(): String {
            val counties = counties()
            if (counties.size == 1) return counties[0]
            return String.format("%02d", counties.size)
        }

        fun counties(): List<String> {
            return contest.counties.toList()
        }

        fun getTarget(): String? {
            return when (mcontest.auditReason) {
                AuditReason.county_wide_contest -> "county"
                AuditReason.state_wide_contest -> "state"
                else -> null
            }
        }

        fun statewide(): Boolean {
            return (counties().size > 60)
        }

        fun getNCand() = contest.choices.size

        fun getChoices() = contest.choices.toString()
        
        companion object {
            @JvmStatic
            fun hiddenProperties() = "mcontest contest contestTab"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("auditReason", "from round"),
                TableBeanProperty("countyPoolId", "county name"),
                TableBeanProperty("marginInVotes", "from round.min_margin"),
                TableBeanProperty("nsamples", "from round.optimistic_samples_to_audit"),
                TableBeanProperty("nc", "contest population from round.contestBallotCardCount"),
                TableBeanProperty("npop", "county population from round.ballotCardCount"),
                TableBeanProperty("riskLimit", "from round.risk_limit"),
                TableBeanProperty("voteForN", "from round.winners_allowed"),

                TableBeanProperty("name", "contest name"),
                TableBeanProperty("countyMvrs", "count of county mvrs over all counties with this contest"),
                TableBeanProperty("statewideMvrs", "count of statewide mvrs over all counties with this contest"),
                TableBeanProperty("NCounties", "number of counties, or county name if only one"),
                TableBeanProperty("counties", "list of counties from tabulateCountyFile"),
                TableBeanProperty("choices", "list of choices/candidates"),
                TableBeanProperty("targeted", "if targeted for audit"),
                TableBeanProperty("NCand", "number of choices / candidates"),
            )
        }
    }
}
