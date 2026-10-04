/*
* Copyright (c) 2026 John L. Caron
* See LICENSE for license information.
*/
package org.cryptobiotic.rlauxe.belgium
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.beans.BeanProperties
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.payoff
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.dhondt.*
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.json.writeRelaxedAssertionProofs
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.viewer.RlauxeAssertionBean
import org.cryptobiotic.rlauxe.viewer.ViewerMain
import org.cryptobiotic.rlauxe.viewer.ViewerPanelIF
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*

private val logger = KotlinLogging.logger("BelgiumContests")

class BelgiumContestTable(
    val prefs: PreferencesExt, 
    infoTA: TextHistoryPane, 
    infoWindow: IndependentWindow,
    fontSize: Float,
    val headerLabel: JLabel,
    statusButton: JButton,
    val setCounty: (String) -> Any,
    val setAltContest: (relax: RelaxedAssertionsIF, sampleLimit: Int) -> Any,
) : JPanel(), ViewerPanelIF {

    private val assertTA = TextHistoryPane()
    private val assertWindow =
        IndependentWindow("Assertion", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(assertTA))

    private val contestTable: BeanTable<ContestBean>
    private val assertionTable: BeanTable<AssertionBean>
    private val partyTable: BeanTable<PartyBean>

    private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var auditRecord: CompositeAuditRecord? = null
    private var config: Config? = null
    private var electionName: String = ""
    private var lastAuditRound: AuditRoundIF? = null

    var sampleLimits: Map<Int, Int>? = null
    var auditData: AuditData
    var allSeats: AllSeats? = null
    var coalitionTotal: PartyBean? = null
    var partyNames = emptyMap<Int, String>()
    val tables = mutableListOf<BeanTable<out Any>>()

    init {
        val bounds = prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(50, 50, 1000, 700)) as Rectangle
        this.assertWindow.setBounds(bounds)

        auditData = AuditData(statusButton) // so each panel gets its own AuditDataOld, but for all audit records.
        /* statusButton.addActionListener(ActionListener { e: ActionEvent? ->
            val f = Formatter()
            showCoalitionReport(f)
            infoTA.setFont(infoTA.getFont().deriveFont(fontSize))
            infoTA.setText(f.toString())
            infoWindow.show()
        }) */

        contestTable =
            BeanTable(ContestBean::class.java, prefs.node("contestTable") as PreferencesExt, false, "Contests", "Contests", null)
        contestTable.addListSelectionListener {
            val contest = contestTable.getSelectedBean()
            if (contest != null) {
                setSelectedContest(contest)
            }
        }
        contestTable.addPopupOption(
            "Show Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: ContestBean -> showContest(bean) }
        )
        contestTable.addPopupOption(
            "Show Logs for Contest",
            contestTable.makeActionOnCurrentBean { bean: ContestBean? ->
                if (bean != null) setCounty(bean.name)
                return@makeActionOnCurrentBean (bean != null)
            }
        )
        contestTable.addPopupOption(
            "Print Contests",
            contestTable.makeShowAction(infoTA, infoWindow) { printTable(contestTable, BeanProperties.contests, "BelgiumContests") }
        )
        contestTable.addPopupOption(
            "Save assertions to Json file for lean prover",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: ContestBean -> writeAssertionFile(bean) }
        )
        contestTable.addPopupOption(
            "Show Alt Contests",
            contestTable.makeActionOnCurrentBean { bean: ContestBean? ->
                if (bean != null) setAltContest(bean)
                return@makeActionOnCurrentBean (bean != null)
            }
        )
        /* contestTable.addPopupOption(
            "Use TooRelaxed Alt Contest",
            contestTable.makeActionOnCurrentBean { bean: ContestBean? ->
                if (bean != null) setAltContest(bean, "tooRelaxed")
                return@makeActionOnCurrentBean (bean != null)
            }
        ) */
        tables.add(contestTable)

        assertionTable =
            BeanTable(
                AssertionBean::class.java,
                prefs.node("assertionTable") as PreferencesExt,
                false,
                "Assertions",
                "Assertions",
                null
            )
        assertionTable.addPopupOption(
            "Show Assertion",
            assertionTable.makeShowAction(
                assertTA,
                assertWindow
            ) { bean: Any? -> showAssertion(bean as AssertionBean) }
        )
        tables.add(assertionTable)

        partyTable =
            BeanTable(
                PartyBean::class.java,
                prefs.node("candidateTable") as PreferencesExt,
                false,
                "Party Coalition",
                "Parties",
                null
            )
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
        val limitAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                applySampleLimits()
            }
        }
        BAMutil.setActionProperties(limitAction, "speedometer.png", "reread sample limits", false, 'L'.code, -1)
        BAMutil.addActionToContainer(container, limitAction)
        /*
            AbstractAction saveAction = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    saveConfig();
                }
            };
            BAMutil.setActionProperties(saveAction, "saveConfig.png", "Save these limits", false, 'S', -1);
            BAMutil.addActionToContainer(container, saveAction); */
    }

    fun applySampleLimits() {
        val limits = auditRecord!!.readSampleLimits() // should this be global ?
        for (bean in contestTable.beans) {
            var foundit = false
            for (limit in limits) {
                if (bean.id == limit.id) {
                    bean.mvrLimitBack = limit.limit
                    bean.contestRound.haveSampleSize = limit.limit
                    foundit = true
                    logger.debug { "read contest limit $limit" }
                }
            }
            if (!foundit) {
                bean.contestRound.haveSampleSize = bean.orgSampleSize
            }
        }
        auditData.updateStatus()
        repaint()
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(null)

        logger.debug { "setAuditRecord $auditRecordLocation" }

        try {
            this.auditRecordLocation = auditRecordLocation
            val record = read(auditRecordLocation)
            if (record == null) return false
            if (record.rounds.isEmpty()) {
                logger.info { "$auditRecordLocation first round was not started" } // TODO plan B
                return false
            }
            if (record !is CompositeAuditRecord) {
                logger.info { "$auditRecordLocation must be CompositeAuditRecord" }
                return false
            }
            this.auditRecord = record
            this.lastAuditRound = auditRecord!!.rounds.last()

            this.config = auditRecord!!.config
            this.electionName = auditRecord!!.name()
            headerLabel.setText(electionName)

            ContestBean.alpha = config!!.riskLimit
            val beanList = mutableListOf<ContestBean>()
            for (contestRound in lastAuditRound!!.contestRounds) {
                if (contestRound.contestUA.preAuditStatus == TestH0Status.InProgress) {
                    val bean = ContestBean(contestRound, auditData)
                    beanList.add(bean)
                }
            }
            beanList.sortBy { it.payoff }
            contestTable.setBeans(beanList)

            auditData.setNewBeans(beanList)
            applySampleLimits() // read in sample limits and apply them

            // parties
            partyNames = auditRecord!!.readPartyNames()
            sampleLimits = auditRecord!!.readSampleLimits().associate { it.id to it.limit }
            allSeats = makeAllSeatsFromRound(this.lastAuditRound!!, sampleLimits!!, .05)
            val beans = mutableListOf<PartyBean>()
            for (partySum in allSeats!!.partySums) {
                if (partySum.maxSeats > 0) {
                    val name = partyNames[partySum.partyId] ?: "-- coalition --"
                    val bean = PartyBean(partySum, -1) { updateCandidateTotal() }
                    beans.add(bean)
                }
            }
            coalitionTotal = makeCandidatesTotal(beans)
            beans.add(coalitionTotal!!)

            beans.sortByDescending { it.reportedSeats }
            partyTable.setBeans(beans)

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "setAuditRecord failed" }
        }

        return true
    }

    fun setAltContest(bean: ContestBean, version: String? = null) {
        val relax: RelaxedAssertionsIF = bean.contest.getRelaxedAssertion(bean.contestRound, .05, version = version)
        setAltContest(relax, bean.haveMvrs)
    }

    fun setSelectedContest(contestBean: ContestBean) {
        assertionTable.setBeans(null)
        logger.debug { "select contest ${contestBean.id} assertions" }

        val beanList = mutableListOf<AssertionBean>()
        for (ar in contestBean.contestRound.assertionRounds) {
            val bean = AssertionBean(contestBean, ar)
            beanList.add(bean)
        }
        logger.debug { "add ${beanList.size} assertions" }

        if (beanList.isEmpty()) return

        // sort assertions by noerror
        beanList.sortBy { it.noerror }
        assertionTable.setBeans(beanList)
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
        else {
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
    }

    fun makeCandidatesTotal(beans: MutableList<PartyBean>): PartyBean {
        val candidates = mutableSetOf<Int>()
        for (bean in beans) {
            candidates.add(bean.partyId)
        }
        val allcoal = allSeats!!.calcCoalition(candidates, partyNames)

        val cand = PartyRange(0, "-- coalition --")
        cand.reportedSeats = allcoal.reportedSeats()
        cand.minSeats = allcoal.minSeats()
        cand.maxSeats = allcoal.maxSeats()
        //cand.failures.addAll(allcoal.all())

        val totalBean = PartyBean(cand, -1) { }
        totalBean.isTotal = true
        totalBean.includeBack = false
        totalBean.coal = allcoal

        return totalBean
    }

    @JvmOverloads
    fun updateCandidateTotal(
        beans: MutableList<PartyBean> = partyTable.beans,
        totalBean: PartyBean = coalitionTotal!!,
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

    fun showContest(bean: ContestBean) = buildString {
        appendLine(showContestWithDesc(bean, contestTable.tableModel, bean.contestUA))
        appendLine()
        //appendLine("Relaxed Assertion Report (experimental)-------------------------------------")
        //append((bean.contestUA.contest as DhondtContest).showRelaxedAssertionReport(bean.contestRound))
        appendLine("Relaxed Assertions (experimental)------------------------------------------")
        append((bean.contestUA.contest as DhondtContest).showRelaxedAssertion(bean.contestRound, config!!.riskLimit))
        appendLine()
    }

    fun writeAssertionFile(bean: ContestBean) = buildString {
        if (allSeats == null || lastAuditRound == null || sampleLimits == null) return ""
        val filename = "$auditRecordLocation/assertions.v5.json"

        val org = writeRelaxedAssertionProofs(
            filename,
            lastAuditRound!!.contestRounds,
            .05, sampleLimits!!
        )
        appendLine("write to $filename")
        append(org)
    }

    //fun showAssertion(bean: AssertionRoundBean) = buildString { // TODO
    //    appendLine(showAssertionWithDesc(bean, assertionTable.tableModel, bean.contestUA, bean.assertion))
    //    append((bean.contestUA.contest as DhondtContest).showRelaxedAssertion(bean.contestRound, bean.cassertion!!))
    //}
    fun showAssertion(bean: AssertionBean) = buildString {
        append(assertionTable.tableModel.showBean(bean, BeanProperties.assertions))
    }

    inner class AuditData(val statusButton: JButton) {
        var useMvrs: Int = 0
        // var contestedSeats: Int = 0
        var contestedAssertions: Int = 0
        var beans: MutableList<ContestBean>? = null

        fun updateStatus() {
            useMvrs = countMvrs()
            // contestedSeats = countContestedSeats()
            contestedAssertions = countContestedAssertions()

            SwingUtilities.invokeLater {
                statusButton.setText("mvrs=$useMvrs failures=$contestedAssertions")
                // statusButton.repaint()
            }
        }

        fun setNewBeans(beans: MutableList<ContestBean>) {
            this.beans = beans
            useMvrs = countMvrs()
            // contestedSeats = countContestedSeats()
            SwingUtilities.invokeLater {
                statusButton.setText("mvrs=$useMvrs failures=$contestedAssertions")
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
                contestBean.failAssertions = fail
                total += fail
            }

            return total
        }

        /* fun countContestedSeats(): Int {
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
        } */
    }
}

class ContestBean(val contestRound: ContestRound, val auditData: BelgiumContestTable.AuditData) {
    var mvrLimitBack: Int = -1
    var contestUA: ContestWithAssertions
    var orgSampleSize: Int
    // var failSeats: Int = 0
    var failAssertions: Int = 0
    val contest: DhondtContest

    init {
        this.contestUA = contestRound.contestUA
        this.contest = contestUA.contest as DhondtContest
        orgSampleSize = this.contestRound.haveSampleSize
    }

    fun getMvrLimit() = mvrLimitBack

    fun setMvrLimit(limit: Int) {
        this.mvrLimitBack = limit
        if (limit < 0) contestRound.haveSampleSize = orgSampleSize else contestRound.haveSampleSize = limit
        auditData.updateStatus() // also update this row I hope
    }

    fun getEstRisk(): Double {
        val minAssertion = contestUA.minClcaAssertion()
        if (minAssertion == null) return 1.0
        val noerror = minAssertion.noerror

        val haveMvrs = this.haveMvrs
        return estRiskStandardBet(contestUA.Npop, noerror, haveMvrs)
    }

    val name = contestUA.name
    val id = contestUA.id
    val estMvrs = contestRound.estNewMvrs

    val haveMvrs: Int
        get() {
            if (mvrLimitBack >= 0) return mvrLimitBack
            return contestRound.haveSampleSize
        }

    val margin: Double
        get() {
            val margin = contestUA.minMargin()
            return if (margin == null) 0.0 else margin
        }

    val mvrsExtra = this.haveMvrs - this.estMvrs
    val mvrsUsed = contestRound.maxSamplesUsed()
    val nc = contestUA.Nc
    val nseats = contest.nseats
    val nParties = contest.parties.size
    val winningParties = contest.parties.filter { it.lastSeatWon > 0 }.count()
    val losingParties = contest.parties.filter { it.firstSeatLost > 0 }.count()

    val atParties = contest.parties.filter{ !it.isBelowMin }.count() // above threshold
    val estAssort = atParties * (atParties-1) + nParties

    val noerror: String
        get() {
            val minAssertion = contestUA.minAssertion() ?: return "N/A"
            return dfn(minAssertion.assorter.noerror(contestUA.hasStyle), 5)
        }

    val payoff: String
        get() {
            val minAssertion = contestUA.minAssertion() ?: return "N/A"
            val noerror = minAssertion.assorter.noerror(contestUA.hasStyle)
            return dfn(payoff(2.0 / 1.03905, noerror), 6)
        }

    val npop = contestUA.Npop
    val phantoms = contestUA.Nphantoms

    val recountMargin: Double
        get() {
            val min = contestUA.minRecountMargin()
            return if (min == null) 0.0 else min
        }

    val status = Naming.status(contestRound.status)
    val type = contestUA.choiceFunction.toString()
    val undervotes = contestUA.contest.Nundervotes()
    val uvPct = contestUA.contest.undervotePct()

    val votes: String
        get() {
            val votes = contestUA.contest.votes()
            if (votes != null) return votes.toString()
            return "N/A"
        }

    val voteMargin: Int
        get() {
            val minAssertion = contestUA.minAssertion() ?: return 0
            return contestUA.contest.marginInVotes(minAssertion.assorter)
        }

    val winners = contestUA.contest.winners().toString()

    companion object {
        var alpha: Double = 0.0

        @JvmStatic
        fun editableProperties() = "mvrLimit"
        @JvmStatic
        fun hiddenProperties() = "contestRound contest auditData contestUA orgSampleSize mvrLimitBack"
    }
}

// TODO why use rounds? serialization glitch ??
class AssertionBean(val contestBean: ContestBean, val assertionRound: AssertionRound) {
    val cua: ContestWithAssertions
    val cassertion: ClcaAssertion
    val assorter: AssorterIF
    val candidates: Map<Int, String>

    init {
        this.cua = contestBean.contestUA
        this.cassertion = assertionRound.assertion as ClcaAssertion
        this.assorter = cassertion.assorter
        this.candidates = cua.contest.info().candidateIdToName
    }

    fun getEstRisk(): Double {
        val noerror = cassertion.noerror
        val haveMvrs = contestBean.haveMvrs
        return estRiskStandardBet(cua.Npop, noerror, haveMvrs)
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
        return candidates.get( assorter.loser())
    }

    val desc = assorter.hashcodeDesc()
    val difficulty = cua.contest.showAssertionDifficulty(assorter)

    val estMvrs = assertionRound.estNewMvrs
    val margin: Double = cassertion.cassorter.assorterMargin
    val recountMargin = cua.contest.recountMargin(assorter)
    val noerror = dfn(assorter.noerror(cua.hasStyle), 5)

    val payoff: String
        get() {
            val noerror = assorter.noerror(cua.hasStyle)
            return dfn(payoff(2.0 / 1.03905, noerror), 6)
        }

    val upper = assorter.upperBound()

    /*
    val scoreDiff = contestBean.contestUA.contest.marginInVotes(assorter)

    // TODO an attempt to define a range that might contain all the disputed assertions
    fun getScoreRange() : Int {
        return if (assorter is DhondtAssorter) assorter.scoreRange(cua.Npop, contestBean.haveMvrs, alpha)
        else -1
    } */

    companion object {
        val alpha = .05

        @JvmStatic
        fun hiddenProperties() = "contestBean assertionRound cassertion cua assorter candidates"
    }
}

class PartyBean(var partyRange: PartyRange, val ncandidates: Int, val sampleChanged: (Boolean) -> Any) {
    var includeBack: Boolean = true
    var isTotal: Boolean = false
    var coal: Coalition? = null

    fun getInclude() = includeBack
    fun setInclude(include: Boolean) {
        this.includeBack = include
        sampleChanged(true)
    }

    val partyName = partyRange.partyName
    val partyId = partyRange.partyId
    val minSeats = partyRange.minSeats
    val reportedSeats = partyRange.reportedSeats
    val maxSeats = partyRange.maxSeats
    // val nCandidates = ncandidate // TODO change to nseats default

    val inCoalition = if (includeBack && !isTotal && this.maxSeats > 0) "YES" else ""

    fun show(): String {
        // only the coalition total bean has a coalition attached
        if (coal != null) return coal.toString()
        else return partyRange.toString()
    }

    companion object {
        @JvmStatic
        fun editableProperties() = "include"

        @JvmStatic
        fun hiddenProperties() = "candidateSeats sampleChanged total coal includeBack"
    }
}

   
