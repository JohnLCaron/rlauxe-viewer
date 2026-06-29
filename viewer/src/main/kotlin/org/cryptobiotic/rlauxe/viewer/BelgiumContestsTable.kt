/*
* Copyright (c) 2026 John L. Caron
* See LICENSE for license information.
*/
package org.cryptobiotic.rlauxe.viewer
import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.beans.BeanProperties
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.beans.showAssertionWithDesc
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
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.viewer.ViewerMain.ViewerProfile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent

class BelgiumContestsTable(
    val prefs: PreferencesExt, 
    infoTA: TextHistoryPane, 
    infoWindow: IndependentWindow, 
    fontSize: Float,
    statusButton: JButton, 
    val profile: ViewerProfile,
) : JPanel(), ViewerPanelIF {
    var auditData: AuditData
    var allSeats: AllSeats? = null
    var coalitionTotal: PartyBean? = null
    var partyNames = emptyMap<Int, String>()

    private val contestTable: BeanTable<ContestBean>
    private val assertionTable: BeanTable<AssertionBean>
    private val partyTable: BeanTable<PartyBean>

    private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var auditRecord: CompositeAuditRecord? = null
    private var config: Config? = null
    private var lastAuditRound: AuditRoundIF? = null // may be null

    private val assertTA = TextHistoryPane()
    private val assertWindow  = IndependentWindow("Assertion", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(assertTA))

    init {
        val bounds = prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(50, 50, 1000, 700)) as Rectangle
        this.assertWindow.setBounds(bounds)

        auditData = AuditData(statusButton) // so each panel gets its own AuditData, but for all audit records.
        /* statusButton.addActionListener(ActionListener { e: ActionEvent? ->
            val f = Formatter()
            showCoalitionReport(f)
            infoTA.setFont(infoTA.getFont().deriveFont(fontSize))
            infoTA.setText(f.toString())
            infoWindow.show()
        }) */

        contestTable =
            BeanTable(
                ContestBean::class.java,
                prefs.node("contestTable") as PreferencesExt,
                false,
                "Contests",
                "Contests",
                null
            )
        contestTable.addListSelectionListener { e: ListSelectionEvent? ->
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
            "Print Contests", 
            contestTable.makeShowAction(infoTA, infoWindow)
                { printTable(contestTable, BeanProperties.contests,"BelgiumContests") }
        )

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
            assertionTable.makeShowAction(assertTA, assertWindow) { bean: Any? -> showAssertion(bean as AssertionBean) }
        )

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

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 400))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, partyTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 800))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("BelgiumAuditPanel init")
    }

    fun getActions(container: JPanel?) {
        if (profile.isBelgium()) {
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
    }

    /*
    public void saveConfig() {
        try {
            if (this.lastAuditRound == null) {
                JOptionPane.showMessageDialog(null, "There is no audit round to save");
                return;
            }

            saveAuditRound(auditRecord, lastAuditRound);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("AuditRoundsTable.resample failed", e);
        }
    } */
    fun applySampleLimits() {
        val limits = auditRecord!!.readSampleLimits() // should this be global ?
        for (bean in contestTable.beans) {
            var foundit = false
            for (limit in limits) {
                if (bean.id == limit.id) {
                    bean.mvrLimitBack = limit.limit
                    bean.contestRound.haveSampleSize = limit.limit
                    foundit = true
                    logger.debug("read contest limit {}", limit)
                }
            }
            if (!foundit) {
                bean.contestRound.haveSampleSize = bean.orgSampleSize
            }
        }
        auditData.updateStatus()
        repaint()
    }

    override fun setFontSize(size: Float) {
        assertTA.setFontSize(size)
        contestTable.setFontSize(size)
        assertionTable.setFontSize(size)
        partyTable.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(null)

        logger.debug("setAuditRecord " + auditRecordLocation + " with profile " + profile)

        try {
            this.auditRecordLocation = auditRecordLocation
            val record = read(auditRecordLocation)
            if (record == null) return false
            if (record.rounds.isEmpty()) {
                logger.info("{} first round was not started", auditRecordLocation) // TODO plan B
                return false
            }
            if (record !is CompositeAuditRecord) {
                logger.info("{} must be CompositeAuditRecord", auditRecordLocation)
                return false
            }
            this.auditRecord = record
            this.lastAuditRound = auditRecord!!.rounds.last()

            this.config = auditRecord!!.config
            ContestBean.alpha = config!!.riskLimit

            val beanList: MutableList<ContestBean> = ArrayList<ContestBean>()
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
            val sampleLimits = auditRecord!!.readSampleLimits()
            allSeats = makeAllSeats(this.lastAuditRound!!, sampleLimits)
            val candBeans: MutableList<PartyBean> = ArrayList<PartyBean>()
            for (candidateSeat in allSeats!!.candidateSums) {
                if (candidateSeat.maxSeats > 0) {
                    val bean = PartyBean(candidateSeat)  { updateCandidateTotal() }
                    candBeans.add(bean)
                }
            }
            coalitionTotal = makeCandidatesTotal(candBeans)
            candBeans.add(coalitionTotal!!)

            candBeans.sortByDescending { it.reportedSeats }
            partyTable.setBeans(candBeans)

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("setAuditRecord failed", e)
        }

        return true
    }

    fun setSelectedContest(contestBean: ContestBean) {
        assertionTable.setBeans(null)
        logger.debug("select contest ${contestBean.id} assertions")

        val beanList = mutableListOf<AssertionBean>()
        contestBean.contestRound.assertionRounds.forEach { ar ->
            val bean = AssertionBean(contestBean, ar)
            beanList.add(bean)
        }
        logger.debug("add ${beanList.size} assertions")

        if (beanList.isEmpty()) return

        // sort assertions by payoff
        beanList.sortBy { it.payoff }
        assertionTable.setBeans(beanList)
    }

    override fun saveState() {
        prefs.putBeanObject(ViewerMain.INFO_BOUNDS, assertWindow.getBounds())

        contestTable.saveState(false)
        assertionTable.saveState(false)
        partyTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    /** /////////////////////////////////////////////////////////////// */

    //// Actions 
    fun showInfo(f: Formatter) {
        if (this.auditRecord == null) return

        val result = buildString {

            appendLine("Audit record at ${auditRecord!!.topdir}")
            appendLine(config)
            if (lastAuditRound == null) return

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
                append(allSeats!!.showAllPartySeats())
            }
        }

        f.format("%s", result)
    }


    fun makeCandidatesTotal(beans: MutableList<PartyBean>): PartyBean {
        val candidates = mutableSetOf<Int>()
        for (bean in beans) {
            candidates.add(bean.partyId)
        }
        val allcoal = allSeats!!.calcCoalition(candidates, partyNames)

        val cand = CandidateSeats(0, "-- coalition --")
        cand.reportedSeats = allcoal.reportedSeats()
        cand.minSeats = allcoal.minSeats()
        cand.maxSeats = allcoal.maxSeats()
        cand.failures.addAll(allcoal.all())

        val totalBean = PartyBean(cand) { }
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
        val cand = CandidateSeats(0, "-- coalition --")
        cand.reportedSeats = coal.reportedSeats()
        cand.minSeats = coal.minSeats()
        cand.maxSeats = coal.maxSeats()
        cand.failures.addAll(coal.all())

        totalBean.coal = coal
        totalBean.candidateSeats = cand
        partyTable.repaint()
    }

    fun showContest(bean: ContestBean) = buildString {
        append(showContestWithDesc(bean, contestTable.tableModel, bean.contestUA))

        if (bean.contestUA.contest is DHondtContest) {
            append((bean.contestUA.contest as DHondtContest).showRelaxedAssertions(bean.contestRound))
        }
    }

    fun showAssertion(bean: AssertionBean) = buildString {
        appendLine(showAssertionWithDesc(bean, assertionTable.tableModel, bean.cua, bean.cassertion))
        append((bean.cua.contest as DHondtContest).showRelaxedAssertion(bean.contestBean.contestRound, bean.cassertion))
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BelgiumContestsTable::class.java)
    }
}

class AuditData (val statusButton: JButton) {
    var useMvrs: Int = 0
    var contestedSeats: Int = 0
    var beans: MutableList<ContestBean>? = null

    fun updateStatus() {
        useMvrs = countMvrs()
        contestedSeats = countContestsSeats()

        SwingUtilities.invokeLater {
            statusButton.setText(String.format("mvrs=%d failures=%d", useMvrs, contestedSeats))
            statusButton.repaint()
        }
    }

    fun setNewBeans(beans: MutableList<ContestBean>) {
        this.beans = beans
        useMvrs = countMvrs()
        contestedSeats = countContestsSeats()
        SwingUtilities.invokeLater {
            statusButton.setText(String.format("mvrs=%d failures=%d", useMvrs, contestedSeats))
        }
    }

    fun countMvrs(): Int {
        var total = 0
        for (bean in beans!!) {
            total += bean.haveMvrs
        }
        return total
    }

    fun countContestsSeats(): Int {
        var total = 0
        for (bean in beans!!) {
            val contest = bean.contestUA.contest
            if (contest is DHondtContest) {
                val count = contest.countContestedSeats(bean.contestRound)
                bean.nFailures = count
                total += count
            }
        }
        return total
    }
}

// TODO can this share a ContestBean ?
class ContestBean(val contestRound: ContestRound, val auditData: AuditData) {
    var mvrLimitBack: Int = -1
    var contestUA: ContestWithAssertions
    var orgSampleSize: Int
    var nFailures: Int = 0
    val contest: DHondtContest

    init {
        this.contestUA = contestRound.contestUA
        this.contest = contestUA.contest as DHondtContest
        orgSampleSize = this.contestRound.haveSampleSize
    }

    fun getMvrLimit(): Int {
        return mvrLimitBack
    }

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

    val mvrsExtra: Int
        get() = this.haveMvrs - this.estMvrs

    val mvrsUsed: Int
        get() = contestRound.maxSamplesUsed()

    val nc: Int
        get() = contestUA.Nc

    val nseats = contest.nseats

    val nCand: Int
        get() = contestUA.ncandidates

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

    val npop: Int
        get() = contestUA.Npop

    val phantoms: Int
        get() = contestUA.Nphantoms

    val recountMargin: Double
        get() {
            val min = contestUA.minRecountMargin()
            return if (min == null) 0.0 else min
        }

    val status: String?
        // TODO maybe not needed
        get() = if (contestRound == null) Naming.status(contestUA.preAuditStatus) else Naming.status(contestRound.status)

    val type = contestUA.choiceFunction.toString()

    val undervotes: Int
        get() = contestUA.contest.Nundervotes()

    val uvPct: Int
        get() = contestUA.contest.undervotePct()

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

    val winners: String
        get() = contestUA.contest.winners().toString()

    companion object {
        var alpha: Double = 0.0

        @JvmStatic
        fun editableProperties() = "mvrLimit"
        @JvmStatic
        fun hiddenProperties() = "contestRound contest auditData contestUA orgSampleSize mvrLimitBack"
    }
}

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

    val winner: String?
        get() {
            if (assorter is DHondtAssorter) {
                return assorter.winnerNameRound()
            }
            return candidates.get(assorter.winner())
        }

    val loser: String?
        get() {
            if (assorter is DHondtAssorter) {
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

    val scoreDiff = contestBean.contestUA.contest.marginInVotes(assorter)

    // an attempt to define a range that might contain all the disputed assertions
    fun getScoreRange() : Int {
        return if (assorter is DHondtAssorter) assorter.scoreRange(cua.Npop, contestBean.haveMvrs, alpha)
        else -1
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "contestBean assertionRound cassertion cua assorter candidates"
    }
}

class PartyBean(var candidateSeats: CandidateSeats, val sampleChanged: (Boolean) -> Any) {
    var includeBack: Boolean = true
    var isTotal: Boolean = false
    var coal: Coalition? = null

    fun getInclude() = includeBack

    fun setInclude(include: Boolean) {
        this.includeBack = include
        sampleChanged(true)
    }

    val partyName: String
        get() = candidateSeats.candName
    val partyId: Int
        get() = candidateSeats.candId
    val minSeats: Int
        get() = candidateSeats.minSeats
    val reportedSeats: Int
        get() = candidateSeats.reportedSeats
    val maxSeats: Int
        get() = candidateSeats.maxSeats
    val nFailures: Int
        get() = candidateSeats.failures.size

    val inCoalition: String
        get() = if (includeBack && !isTotal && this.maxSeats > 0) "YES" else ""

    fun show(): String {
        // only the coalition total bean has a coalition attached
        if (coal != null) return coal.toString()
        else return candidateSeats.toString()
    }

    companion object {
        @JvmStatic
        fun editableProperties() = "include"

        @JvmStatic
        fun hiddenProperties() = "candidateSeats sampleChanged total coal includeBack"
    }
}

   
