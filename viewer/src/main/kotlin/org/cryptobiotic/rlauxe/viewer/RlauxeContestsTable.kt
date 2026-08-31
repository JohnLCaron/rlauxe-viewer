package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.beans.BeanProperties.getContestBeanProperties
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.beans.showAssertionWithDesc
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.payoff
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.AuditRecordIF
import org.cryptobiotic.rlauxe.util.dfn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.AbstractAction
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

private val logger: Logger = LoggerFactory.getLogger(RlauxeContestsTable::class.java)

class RlauxeContestsTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane,
    infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {
    private val contestTable: BeanTable<RlauxeContestBean>
    private val assertionTable: BeanTable<RlauxeAssertionBean>

    private val split2: JSplitPane
    private var onlyShowInprogressContests = false

    private var auditRecordLocation = "none"
    private var auditRecord: AuditRecordIF? = null
    private var config: Config? = null
    private var oneshotMvrs: Map<Int, Int>? = null
    private var alpha: Double = .05
    private var lastAuditRound: AuditRoundIF? = null // may be null

    init {
        contestTable =
            BeanTable(
                RlauxeContestBean::class.java,
                (prefs.node("contestTable") as PreferencesExt?)!!,
                false,
                "Contests",
                "ContestRound",
                null
            )

        contestTable.addListSelectionListener { e: ListSelectionEvent? ->
            val contest = contestTable.getSelectedBean()
            if (contest != null) {
                setSelectedContest(contest)
            }
        }
        contestTable.addPopupOption(
            "Show Contest", contestTable.makeShowAction(
                infoTA, infoWindow
            ) { bean: RlauxeContestBean -> showContest(bean) })
        contestTable.addPopupOption(
            "Print Contests", contestTable.makeShowAction(
                infoTA, infoWindow
            ) { bean: RlauxeContestBean? -> printContests() })

        assertionTable =
            BeanTable(
                RlauxeAssertionBean::class.java,
                (prefs.node("assertionTable") as PreferencesExt?)!!,
                false,
                "Assertion",
                "Assertion",
                null
            )

        assertionTable.addPopupOption(
            "Show Assertion", assertionTable.makeShowAction(
                infoTA, infoWindow
            ) { bean: RlauxeAssertionBean -> showAssertion(bean) })

        setFontSize(fontSize)

        // layout of tables
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 200))

        // auditRoundTable not used for now
        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("RlauxeContestsTable init")
    }

    fun getActions(container: JPanel) {
        val onlyProcessAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val state = getValue( BAMutil.STATE)
                val onlyProcess = state as Boolean
                onlyProcess(onlyProcess)
                prefs.putBoolean( "onlyInProgress", onlyProcess)
            }
        }
        val savedState = prefs.getBoolean( "onlyInProgress", false)
        onlyProcessAction.putValue(BAMutil.STATE, savedState)
        BAMutil.setActionProperties(onlyProcessAction, "sunrise-icon.png", "Only show Contests InProgress", true, 'S'.code, -1)
        BAMutil.addActionToContainer(container, onlyProcessAction)
    }
    // only show active contests
    fun onlyProcess(onlyInProgress: Boolean) {
        this.onlyShowInprogressContests = onlyInProgress
        loadAuditRecord()
    }

    override fun setFontSize(size: Float) {
        contestTable.setFontSize(size)
        assertionTable.setFontSize(size)
    }

    fun resetAuditRecord() {
        setAuditRecord(auditRecordLocation)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean("onlyInProgress", false)
        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(null)

        logger.debug("RlauxeContestsTable setAuditRecord " + auditRecordLocation)

        this.auditRecordLocation = auditRecordLocation
        this.auditRecord = read(auditRecordLocation)
        if (this.auditRecord == null) return false
        this.config = auditRecord!!.config
        this.alpha = config!!.riskLimit

        return loadAuditRecord()
    }

    fun loadAuditRecord(): Boolean {
        if (auditRecord!!.rounds.isEmpty()) {
            logger.info("{} first round was not started", auditRecordLocation)

            val beanList: MutableList<RlauxeContestBean> = ArrayList<RlauxeContestBean>()

            auditRecord!!.contests.filter { !onlyShowInprogressContests || it.preAuditStatus == TestH0Status.InProgress }.forEach { cwa ->
                val bean = RlauxeContestBean(cwa, null)
                beanList.add(bean)
            }
            contestTable.setBeans(beanList)

        } else {
            this.lastAuditRound = auditRecord!!.rounds.last()

            try {
                val contestMap = mutableMapOf<Int, RlauxeContestBean>()
                val beanList = mutableListOf<RlauxeContestBean>()

                val contestRoundMap: MutableMap<Int?, ContestRound?> = HashMap<Int?, ContestRound?>()
                for (contestRound in lastAuditRound!!.contestRounds) {
                    contestRoundMap.put(contestRound.id, contestRound)
                }

                auditRecord!!.contests.filter { !onlyShowInprogressContests || it.preAuditStatus == TestH0Status.InProgress }.forEach { cwa ->
                    val cr = contestRoundMap.get(cwa.id)
                    val bean: RlauxeContestBean = RlauxeContestBean(cwa, cr)
                    beanList.add(bean)
                    contestMap.put(cwa.id, bean)
                }
                contestTable.setBeans(beanList)

                // sort contests by payoff
                beanList.sortBy { it.payoff }
                contestTable.setBeans(beanList)

                val minBean = beanList.minByOrNull { it.margin }
                if (minBean != null) contestTable.setSelectedBean(minBean)
                oneshotMvrs = auditRecord!!.readOneShotMvrs()

            } catch (e: Exception) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(null, e.message)
                logger.error("RlauxeContestsTable setAuditRecord failed", e)
            }
        }

        return true
    }

    fun setSelectedContest(contestBean: RlauxeContestBean) {
        val beanList: MutableList<RlauxeAssertionBean> = ArrayList<RlauxeAssertionBean>()

        if (contestBean.contestRound != null) {
            for (ar in contestBean.contestRound.assertionRounds) {
                val bean = RlauxeAssertionBean(contestBean, ar.assertion, ar)
                beanList.add(bean)
            }
        } else {
            for (ar in contestBean.contestUA.assertions) {
                val bean: RlauxeAssertionBean = RlauxeAssertionBean(contestBean, ar, null)
                beanList.add(bean)
            }
        }
        assertionTable.setBeans(beanList)

        if (beanList.isEmpty()) return

        // select assertion with smallest noerror
        val minBean = beanList.minByOrNull { it.noerror }
        if (minBean != null) assertionTable.setSelectedBean(minBean)
    }

    override fun saveState() {
        contestTable.saveState(false)
        assertionTable.saveState(false)

        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showInfo(f: Formatter) {
        if (this.auditRecord == null) return

        f.format("Audit record at %s%n%n", auditRecord!!.topdir)
        f.format("%s%n", this.config!!.show())
        f.format("  auditable contests = %d %n", this.auditRecord!!.rounds.first().contestRounds.size)

        if (this.lastAuditRound == null) return

        f.format("AuditRounds")
        var totalExtra = 0
        var mvrsUsed = 0
        for (round in auditRecord!!.rounds) {
            if (round.auditWasDone) {
                val roundIdx = round.roundIdx
                val nmvrs = round.samplePrns.size
                f.format("%n  number of Mvrs in round %d = %d %n", roundIdx, nmvrs)
                f.format("  mvrsUsed = %d %n", round.mvrsUsed)
                f.format("  extraMvrs = %d %n", round.mvrsUnused)
                totalExtra += round.mvrsUnused
                mvrsUsed = round.mvrsUsed
            }
        }
        f.format("%n  total mvrs used = %d %n", mvrsUsed)
        f.format("  total extraMvrs = %d %n", totalExtra)
        f.format("  total mvrs sampled = %d%n", this.lastAuditRound!!.nmvrs)
    }

    fun showContest(bean: RlauxeContestBean): String {
        return showContestWithDesc(bean, contestTable.tableModel, bean.contestUA)
    }

    fun printContests(): String {
        return printTable(contestTable, getContestBeanProperties(), "contests")
    }

    fun showAssertion(bean: RlauxeAssertionBean): String {
        val assn = if (bean.cassertion != null) bean.cassertion else bean.assertion
        return showAssertionWithDesc(bean, assertionTable.tableModel, bean.cua, assn)
    }
}

class RlauxeContestBean(val contestUA: ContestWithAssertions, val contestRound: ContestRound?) {
    var orgSampleSize = if (contestRound != null) contestRound.haveSampleSize else 0

    val name: String
        get() = contestUA.name

    val id: Int
        get() = contestUA.id

    val candidates: String
        get() = contestUA.contest.info().candidateNames.map { it.key }.joinToString(", ")

    val estRisk: Double
        get() {
            val minAssertion = contestUA.minClcaAssertion()
            if (minAssertion == null) return 1.0
            val noerror = minAssertion.noerror

            val haveMvrs = this.haveMvrs
            return estRiskStandardBet(contestUA.Npop, noerror, haveMvrs)
        }

    val estMvrs: Int
        get() = if (contestRound == null) 0 else contestRound.estMvrs

    val haveMvrs: Int
        get() = if (contestRound == null) 0 else contestRound.haveSampleSize

    val noerror: String
        get() {
            val minAssertion = contestUA.minAssertion()
            if (minAssertion == null) return "N/A"
            return dfn(minAssertion.assorter.noerror(contestUA.hasStyle), 5)
        }

    val payoff: String
        get() {
            val minAssertion = contestUA.minAssertion()
            if (minAssertion == null) return "N/A"
            val noerror = minAssertion.assorter.noerror(contestUA.hasStyle)
            return dfn(payoff(2.0 / 1.03905, noerror), 6)
        }

    val margin: Double
        get() {
            val margin = contestUA.minMargin()
            return if (margin == null) 0.0 else margin
        }

    val mvrsExtra: Int
        get() = this.haveMvrs - this.estMvrs

    val mvrsUsed: Int
        get() = if (contestRound == null) 0 else contestRound.maxSamplesUsed()

    val nc: Int
        get() = contestUA.Nc

    val nCand: Int
        get() = contestUA.ncandidates

    val npop: Int
        get() = contestUA.Npop

    val phantoms: Int
        get() = contestUA.Nphantoms

    val nvotes = contestUA.contest.nvotes()

    val recountMargin: Double
        get() {
            val min = contestUA.minRecountMargin()
            return if (min == null) 0.0 else min
        }

    val status: String?
        // TODO maybe not needed
        get() = if (contestRound == null || contestUA.preAuditStatus != TestH0Status.InProgress)
            Naming.status(contestUA.preAuditStatus)
        else
            Naming.status(contestRound.status)

    val type: String?
        get() = contestUA.choiceFunction.toString()

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
            val minAssertion = contestUA.minAssertion()
            return if (minAssertion == null) 0 else contestUA.contest.marginInVotes(minAssertion.assorter)
        }

    val winners: String
        get() = contestUA.contest.winners().toString()

    fun getNCounties(): String {
        val counties = counties()
        if (counties == null) return "N/A"
        if (counties.size == 1) return counties[0]
        return String.format("%02d", counties.size)
    }

    fun counties(): List<String>? {
        val counties = contestUA.contest.info().metadata.get("Counties")
        if (counties == null) return null
        //val stripped = counties.drop(1).dropLast(1)
        return counties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
    }

    fun getPoolPct(): Int {
        val poolPct = contestUA.contest.info().metadata.get("PoolPct") ?: return -1
        return poolPct.toInt()
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "contestUA contestRound orgSampleSize"
    }
}

class RlauxeAssertionBean(val contestBean: RlauxeContestBean, val assertion: Assertion, val assertionRound: AssertionRound?) {
    val cua: ContestWithAssertions = contestBean.contestUA
    val candidates = cua.contest.info().candidateIdToName
    val cassertion: ClcaAssertion?
    var oaAssorter: OneAuditClcaAssorter? = null

    init {
        if (assertion is ClcaAssertion) {
            this.cassertion = assertion
            if (assertion.cassorter is OneAuditClcaAssorter) {
                this.oaAssorter = assertion.cassorter as OneAuditClcaAssorter
            }
        } else {
            this.cassertion = null
            this.oaAssorter = null
        }
    }

    val type: String
        get() = assertion.assorter.javaClass.getSimpleName()

    val winner: String
        get() {
            if (assertion.assorter is DHondtAssorter) {
                return (assertion.assorter as DHondtAssorter).winnerNameRound()
            }
            val winner = assertion.assorter.winner()
            return candidates[winner]!!
        }

    val loser: String
        get() {
            if (assertion.assorter is DHondtAssorter) {
                return (assertion.assorter as DHondtAssorter).loserNameRound()
            }
            val loser = assertion.assorter.loser()
            return candidates[loser]!!
        }

    val desc: String
        get() = assertion.assorter.hashcodeDesc()

    val estRisk: Double
        get() {
            if (cassertion == null) return 0.0
            val noerror = cassertion.noerror
            val haveMvrs = contestBean.haveMvrs
            return estRiskStandardBet(cua.Npop, noerror, haveMvrs)
        }

    val estMvrs: Int
        get() = assertionRound?.estNewMvrs ?: 0

    val margin: Double
        get() = if (cassertion != null) cassertion.cassorter.assorterMargin else assertion.assorter.dilutedMargin()

    val difficulty: String
        get() = cua.contest.showAssertionDifficulty(assertion.assorter)

    val recountMargin: Double
        get() = cua.contest.recountMargin(assertion.assorter)

    val mean: Double
        get() = assertion.assorter.dilutedMean()

    val noerror: String
        // could use payoff
        get() = dfn(assertion.assorter.noerror(cua.hasStyle), 5)

    val payoff: String
        get() {
            val noerror = assertion.assorter.noerror(cua.hasStyle)
            return dfn(payoff(2.0 / 1.03905, noerror), 6)
        }

    val upper: Double
        get() = assertion.assorter.upperBound()

    companion object {
        @JvmStatic
        fun hiddenProperties() = "contestBean assertion assertionRound cua candidates cassertion oaAssorter"
    }
}

