/*
* Copyright (c) 2026 John L. Caron
* See LICENSE for license information.
*/
package org.cryptobiotic.rlauxe.belgium
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.beans.BeanProperties
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.betting.payoff
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.dhondt.AllSeats
import org.cryptobiotic.rlauxe.dhondt.AltContest
import org.cryptobiotic.rlauxe.dhondt.DhondtAssorter
import org.cryptobiotic.rlauxe.dhondt.PartyRange
import org.cryptobiotic.rlauxe.dhondt.RelaxedAssertionsIF
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.viewer.ViewerMain
import org.cryptobiotic.rlauxe.viewer.ViewerPanelIF
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Rectangle
import javax.swing.*

private val logger = KotlinLogging.logger("BelgiumContests")

class BelgiumAltContestTable(
    val prefs: PreferencesExt, 
    infoTA: TextHistoryPane, 
    infoWindow: IndependentWindow,
    fontSize: Float,
    val headerLabel: JLabel,
) : JPanel(), ViewerPanelIF {

    // var auditData: AuditData
    var allSeats: AllSeats? = null
    var seatTotals: PartyBean? = null
    var partyNames = emptyMap<Int, String>()
    val tables = mutableListOf<BeanTable<out Any>>()

    private val contestTable: BeanTable<AltContestBean>
    private val assertionTable: BeanTable<AltAssertionBean>
    private val partyTable: BeanTable<PartyBean>

    private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var auditRecord: CompositeAuditRecord? = null
    private var config: Config? = null
    private var electionName: String = ""
    private var lastAuditRound: AuditRoundIF? = null // may be null
    var relax: RelaxedAssertionsIF? = null

    private val assertTA = TextHistoryPane()
    private val assertWindow =
        IndependentWindow("AltAssertion", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(assertTA))

    init {
        val bounds = prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(50, 50, 1000, 700)) as Rectangle
        this.assertWindow.setBounds(bounds)

        // auditData = AuditData(statusButton) // so each panel gets its own AuditDataOld, but for all audit records.

        contestTable =
            BeanTable(AltContestBean::class.java, prefs.node("contestTable") as PreferencesExt, false, "Alt Contests", "Relaxed Assertion Alternative Contests", null)
        contestTable.addListSelectionListener {
            val contest = contestTable.getSelectedBean()
            if (contest != null) {
                setSelectedContest(contest)
            }
        }
        contestTable.addPopupOption(
            "Show Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: AltContestBean -> showContest(bean) }
        )
        contestTable.addPopupOption(
            "Print Contests",
            contestTable.makeShowAction(infoTA, infoWindow) {
                printTable(
                    contestTable,
                    BeanProperties.contests,
                    "BelgiumContests"
                )
            }
        )
        //contestTable.addPopupOption(
        //    "Save assertions to Json file for lean analyzer",
        //    contestTable.makeShowAction(infoTA, infoWindow) { bean: AltContestBean -> showAssertionsJson(bean) }
        //)
        tables.add(contestTable)

        assertionTable =
            BeanTable(AltAssertionBean::class.java, prefs.node("assertionTable") as PreferencesExt, false, "Alt Contest Assertions", "Relaxed Assertion Alternative Contest Assertions", null)
        assertionTable.addPopupOption(
            "Show Assertion",
            assertionTable.makeShowAction(
                assertTA,
                assertWindow
            ) { bean: AltAssertionBean? -> if (bean != null) showAssertion(bean) else "" }
        )
        tables.add(assertionTable)

        partyTable =
            BeanTable(PartyBean::class.java, prefs.node("candidateTable") as PreferencesExt, false, "Party Ranges", "Parties", null)
        partyTable.addPopupOption(
            "Show Party",
            partyTable.makeShowAction(infoTA, infoWindow)
            { bean: PartyBean -> bean.show() }
        )
        tables.add(partyTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 400))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, partyTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 800))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug { "BelgiumAuditPanel init" }
    }

    fun getActions(container: JPanel) {
        /* val limitAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                applySampleLimits()
            }
        }
        BAMutil.setActionProperties(limitAction, "speedometer.png", "reread sample limits", false, 'L'.code, -1)
        BAMutil.addActionToContainer(container, limitAction) */
        /*
            AbstractAction saveAction = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    saveConfig();
                }
            };
            BAMutil.setActionProperties(saveAction, "saveConfig.png", "Save these limits", false, 'S', -1);
            BAMutil.addActionToContainer(container, saveAction); */
    }

    fun setAltContest(relax: RelaxedAssertionsIF, sampleLimit: Int) {
        this.relax = relax
        val beanList = mutableListOf<AltContestBean>()
        // not really correct, original doesnt have assortersForProof
        // beanList.add(AltContestBean(AltContest("original", relax.orgContest, relax.totalContestRange()), sampleLimit))

        relax.altContests().forEach { altContest ->
            val bean = AltContestBean(altContest, sampleLimit)
            beanList.add(bean)
        }
        contestTable.setBeans(beanList)
        assertionTable.setBeans(null)
        partyTable.setBeans(null)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(null)
        assertionTable.setBeans(null)
        partyTable.setBeans(null)

        return true
    }

    fun setSelectedContest(contestBean: AltContestBean) {
        assertionTable.setBeans(null)
        logger.debug { "select contest ${contestBean.id} assertions" }

        val beanList = mutableListOf<AltAssertionBean>()
        for (ar in contestBean.dcontest.assorters) {
            val bean = AltAssertionBean(contestBean, ar)
            beanList.add(bean)
        }
        logger.debug { "add ${beanList.size} assertions" }

        if (beanList.isEmpty()) return

        // sort assertions by noerror
        beanList.sortBy { it.noerror }
        assertionTable.setBeans(beanList)

        setParties(contestBean)
    }

    fun setParties(contestBean: AltContestBean) {
        val cr = contestBean.altContest.contestRange
        val partyMap = contestBean.altContest.altContest.parties.associateBy{ it.id }

        val candBeans = mutableListOf<PartyBean>()
        cr.partyRanges().forEach { partyRange ->
            val bean = PartyBean(partyRange, partyMap[partyRange.partyId]?.nCandidates ?: -1) { updateCandidateTotal() }
            candBeans.add(bean)
        }
        seatTotals = makeSeatTotals(candBeans)
        candBeans.add(seatTotals!!)

        candBeans.sortByDescending { it.reportedSeats }
        partyTable.setBeans(candBeans)
    }

    fun makeSeatTotals(beans: MutableList<PartyBean>): PartyBean {
        val total = PartyRange(0, "--Total--")
        beans.forEach {
            val pr = it.partyRange
            total.minSeats += pr.minSeats
            total.reportedSeats += pr.reportedSeats
            total.maxSeats += pr.maxSeats
        }
        return PartyBean(total, -1) { }
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
        assertTA.setFontSize(size)
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }

        prefs.putBeanObject(ViewerMain.INFO_BOUNDS, assertWindow.getBounds())
        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    /** /////////////////////////////////////////////////////////////// */

    //// Actions 
    fun showInfo(county: String?) = buildString {
        if (auditRecord == null) append("no audit record")
        appendLine("Audit record at ${auditRecord!!.topdir}")
        appendLine("ElectionName = ${electionName}")
        if (county != null) config = auditRecord!!.configFor(county)
        appendLine(config!!.show())
        if (lastAuditRound != null) {
            append("AuditRounds")
            var totalExtra = 0
            for (round in auditRecord!!.rounds) {
                if (round.auditWasDone) {
                    val roundIdx = round.roundIdx
                    val nmvrs = round.samplePrns.size
                    appendLine("number of Mvrs in round $roundIdx = $nmvrs")
                    val extra = round.mvrsUnused
                    appendLine("  extraBallotsUsed = $extra")
                    totalExtra += extra
                }
            }
            appendLine("  total extraBallotsUsed = $totalExtra total Mvrs = ${lastAuditRound!!.nmvrs}")

            if (allSeats != null) {
                appendLine()
                appendLine("Party seat ranges based on contested assertions")
                append(allSeats!!.showAllPartySeats(partyNames))
            }
        }
    }

    @JvmOverloads
    fun updateCandidateTotal(
        beans: MutableList<PartyBean> = partyTable.beans,
        totalBean: PartyBean = seatTotals!!,
    ) {
        val candidates = mutableSetOf<Int>()
        for (bean in beans) {
            if (bean.includeBack && bean != totalBean) {
                candidates.add(bean.partyId)
            }
        }
        val coal = allSeats!!.calcCoalition(candidates, partyNames)
        val cand = PartyRange(0, "Total")
        cand.reportedSeats = coal.reportedSeats()
        cand.minSeats = coal.minSeats()
        cand.maxSeats = coal.maxSeats()
        //cand.failures.addAll(coal.all())

        totalBean.coal = coal
        totalBean.partyRange = cand
        partyTable.repaint()
    }

    fun showContest(bean: AltContestBean) = buildString {
        // appendLine(showContestWithDesc(bean, contestTable.tableModel, bean.dcontest))
        appendLine(bean.dcontest.show())
        if (relax != null) {
            appendLine("Relaxed Assertion Report (experimental)-------------------------------------")
            append(relax!!.show())
            appendLine()
        }
    }

    /*
    fun showAssertionsJson(bean: AltContestBean) = buildString {
        if (allSeats == null || lastAuditRound == null) return ""
        val org = writeAllContestsToJsonFile(
            lastAuditRound!!.contestRounds,
            "/home/stormy/rla/temp/assertions.json",
            .05, false, emptyMap()
        )
        append(org)
    } */

    //fun showAssertion(bean: AssertionRoundBean) = buildString { // TODO
    //    appendLine(showAssertionWithDesc(bean, assertionTable.tableModel, bean.contestUA, bean.assertion))
    //    append((bean.contestUA.contest as DhondtContest).showRelaxedAssertion(bean.contestRound, bean.cassertion!!))
    //}
    fun showAssertion(bean: AltAssertionBean) = buildString {
        // append(assertionTable.tableModel.showBean(bean, BeanProperties.assertions))
        append(bean.assorter)
    }

    /*
    inner class AuditData(val statusButton: JButton) {
        var useMvrs: Int = 0
        var contestedSeats: Int = 0
        var contestedAssertions: Int = 0
        var beans: MutableList<ContestBean>? = null

        fun updateStatus() {
            useMvrs = countMvrs()
            contestedSeats = countContestedSeats()
            contestedAssertions = countContestedAssertions()

            SwingUtilities.invokeLater {
                statusButton.setText("mvrs=$useMvrs failures=$contestedSeats")
                // statusButton.repaint()
            }
        }

        fun setNewBeans(beans: MutableList<ContestBean>) {
            this.beans = beans
            useMvrs = countMvrs()
            contestedSeats = countContestedSeats()
            SwingUtilities.invokeLater {
                statusButton.setText("mvrs=$useMvrs failures=$contestedSeats")
            }
        }

        fun countMvrs(): Int {
            var total = 0
            for (bean in beans!!) {
                total += bean.haveMvrs
            }
            return total
        }

        fun countContestedAssertions(): Int {
            var total = 0
            for (contestBean in beans!!) {
                var fail = 0
                for (ar in contestBean.contestRound.assertionRounds) {
                    val bean =
                        RlauxeAssertionBean(contestBean.contestUA, contestBean.contestRound, ar.assertion, ar)
                    if (bean.estRisk > ContestBean.alpha) fail++
                }
                contestBean.fail = fail
                total += fail
            }

            return total
        }

        fun countContestedSeats(): Int {
            var total = 0
            for (bean in beans!!) {
                val contest = bean.contestUA.contest
                if (contest is DhondtContest) {
                    val count = contest.countContestedSeats(bean.contestRound)
                    bean.failSeats = count
                    total += count
                }
            }
            return total
        }
    }
} */

    class AltContestBean(val altContest: AltContest, val sampleLimit: Int) {
        val dcontest = altContest.altContest

        val contest = altContest.altContest.name
        val name = altContest.name
        val id = dcontest.id
        val nseats = dcontest.nseats
        val nParties = dcontest.parties.size
        val winningParties = dcontest.parties.filter { it.lastSeatWon > 0 }.count()
        val losingParties = dcontest.parties.filter { it.firstSeatLost > 0 }.count()

        // val nBCand = dcontest.parties.filter{ !it.isBelowMin }.count()
        // val estAssort = nBCand * (nBCand-1) + nCand TODO
        val dhFail = altContest.dhFail
        val tFail = altContest.tFail
        /*
        fun getEstRisk(): Double {
        val minAssertion = dcontest.minClcaAssertion()
        if (minAssertion == null) return 1.0
        val noerror = minAssertion.noerror

        val haveMvrs = this.haveMvrs
        return estRiskStandardBet(dcontest.Npop, noerror, haveMvrs)
    }

        fun getEstMvrs(): Double {
        return 0.0
    }

    val margin: Double
        get() {
            val margin = dcontest.minMargin()
            return if (margin == null) 0.0 else margin
        }

    val noerror: String
        get() {
            val minAssertion = dcontest.minAssertion() ?: return "N/A"
            return dfn(minAssertion.assorter.noerror(dcontest.hasStyle), 5)
        }

    val payoff: String
        get() {
            val minAssertion = dcontest.minAssertion() ?: return "N/A"
            val noerror = minAssertion.assorter.noerror(dcontest.hasStyle)
            return dfn(payoff(2.0 / 1.03905, noerror), 6)
        }

    val phantoms = dcontest.Nphantoms()

    val recountMargin: Double
        get() {
            val min = dcontest.minRecountMargin()
            return if (min == null) 0.0 else min
        }

    val voteMargin: Int
        get() {
            val minAssertion = dcontest.minAssertion() ?: return 0
            return dcontest.contest.marginInVotes(minAssertion.assorter)
        }

     */


        val winners = dcontest.winnerSeatCount.toString()

        companion object {
            var alpha: Double = 0.0

            @JvmStatic
            fun editableProperties() = "mvrLimit"

            @JvmStatic
            fun hiddenProperties() = "altContest dcontest"
        }
    }

    class AltAssertionBean(val contestBean: AltContestBean, val assorter: AssorterIF) {
        val candidates: Map<Int, String>

        init {
            this.candidates = contestBean.dcontest.info().candidateIdToName
        }

        fun getEstRisk(): Double {
            val noerror = assorter.noerror(true)
            val haveMvrs = contestBean.sampleLimit
            return estRiskStandardBet(contestBean.dcontest.Nc, noerror, haveMvrs)
        }

        fun getEstMvrs(): Int {
            val noerror = assorter.noerror(true)
            val haveMvrs = contestBean.sampleLimit
            return estSampleSizeStandardBet(contestBean.dcontest.Nc, noerror, .05)
        }

        val type = assorter.javaClass.getSimpleName()

        fun getWinner(): String? {
            if (assorter is DhondtAssorter) {
                return assorter.winnerNameRound()
            }
            return candidates.get(assorter.winner())
        }

        fun getLoser(): String? {
            if (assorter is DhondtAssorter) {
                return assorter.loserNameRound()
            }
            return candidates.get(assorter.loser())
        }

        val desc = assorter.hashcodeDesc()
        // val difficulty = cua.contest.showAssertionDifficulty(assorter)

        //val estMvrs = assertionRound.estNewMvrs
        //val margin: Double = cassertion.cassorter.assorterMargin
        //val recountMargin = cua.contest.recountMargin(assorter)
        val noerror = dfn(assorter.noerror(true), 5)

        val payoff: String
            get() {
                val noerror = assorter.noerror(true)
                return dfn(payoff(2.0 / 1.03905, noerror), 6)
            }

        val upper = assorter.upperBound()

        companion object {
            val alpha = .05

            @JvmStatic
            fun hiddenProperties() = "contestBean, assorter, candidates"
        }
    }
}

   
