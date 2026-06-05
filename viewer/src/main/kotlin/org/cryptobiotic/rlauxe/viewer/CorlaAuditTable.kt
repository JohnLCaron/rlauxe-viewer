package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.resampleAndSaveResults
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.betting.payoff
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.CountyData
import org.cryptobiotic.rlauxe.util.dfn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.prefs.BeanTable
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.util.ArrayList
import java.util.Formatter
import java.util.HashMap
import java.util.function.Function
import javax.swing.AbstractAction
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class CorlaAuditTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane?,
    infoWindow: IndependentWindow?,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {
    var countyTotal: CountyBean? = null
    var countyMap = mutableMapOf<String, CountyBean>()

    private val contestTable: BeanTable<ContestBean>
    private val assertionTable: BeanTable<AssertionBean>
    private val countyTable: BeanTable<CountyBean>

    private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAudit? = null
    private var config: Config? = null
    private var lastAuditRound: AuditRoundIF? = null // may not be null
    private var auditRiskLimit: Double = .03
    private var samplingChanged = false

    init {
        /* auditData = new AuditData(statusButton); // so each panel gets its own AuditData, but for all audit records.
       statusButton.addActionListener(e -> {
           Formatter f = new Formatter();
           showCoalitionReport(f);
           infoTA.setFont(infoTA.getFont().deriveFont(fontSize));
           infoTA.setText(f.toString());
           infoWindow.show();
       }); */
        contestTable =
            BeanTable<ContestBean>(
                ContestBean::class.java,
                prefs.node("contestTable") as PreferencesExt?,
                false,
                "Contests",
                "Contests",
                null
            )
        contestTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent? ->
            val contest = contestTable.getSelectedBean()
            if (contest != null) {
                setSelectedContest(contest)
            }
        })
        contestTable.addPopupOption(
            "Show Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: Any? -> showContest(bean as ContestBean) }
        )
        contestTable.addPopupOption(
            "Print Contests", contestTable.makeShowAction(
                infoTA, infoWindow,
                Function { bean: Any? -> printContests() })
        )

        assertionTable = BeanTable<AssertionBean>(
            AssertionBean::class.java,
            prefs.node("assertionTable") as PreferencesExt?,
            false,
            "Assertions",
            "Assertions",
            null
        )
        assertionTable.addPopupOption(
            "Show Assertion",
            assertionTable.makeShowAction(infoTA, infoWindow) { bean: Any? -> showAssertion(bean as AssertionBean) }
        )

        countyTable = BeanTable<CountyBean>(
            CountyBean::class.java,
            prefs.node("countyTable") as PreferencesExt?,
            false,
            "Counties",
            "County",
            null
        )

        // countyTable.addPopupOption("Show County", countyTable.makeShowAction(infoTA, infoWindow, bean -> ((CountyBean) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 400))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, countyTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 800))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("CorlaAuditTable init")
    }

    override fun setFontSize(size: Float) {
        contestTable.setFontSize(size)
        assertionTable.setFontSize(size)
        countyTable.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(mutableListOf<ContestBean?>())

        logger.debug("setAuditRecord " + auditRecordLocation)

        try {
            this.auditRecordLocation = auditRecordLocation
            val record = AuditRecord.read(auditRecordLocation)
            if (record == null) return false
            if (record.rounds.isEmpty()) {
                logger.info("first round was not started") // TODO plan B
                return false
            }
            if (record !is CountyAudit) {
                logger.info("must be CountyAudit")
                return false
            }
            this.countyAudit = record
            this.lastAuditRound = countyAudit!!.rounds.last()

            this.config = countyAudit!!.config
            this.auditRiskLimit = config!!.riskLimit

            val contestRoundMap: MutableMap<Int?, ContestRound?> = HashMap<Int?, ContestRound?>()
            for (contestRound in lastAuditRound!!.contestRounds) {
                contestRoundMap.put(contestRound.id, contestRound)
            }

            val beanList = mutableListOf<ContestBean>()
            for (cwa in countyAudit!!.contests) {
                val cr = contestRoundMap.get(cwa.id)
                val bean = ContestBean(cwa, cr) { b -> samplingChanged = b }
                beanList.add(bean)
            }
            // sort contests by payoff
            beanList.sortBy { it.getPayoff() }
            contestTable.setBeans(beanList)

            /**////////////////// */
            var countUniformMvrs = 0
            var statewide: CountyBean? = null
            val countyList = mutableListOf<CountyBean>()
            val _countyMap = mutableMapOf<String, CountyBean>()
            for (countyData in countyAudit!!.countyData) {
                val bean = CountyBean(countyData)
                if (bean.name == "Statewide") statewide = bean
                else countUniformMvrs += bean.nmvrsUniform // statewide now included in counties, so dont count twice

                countyList.add(bean)
                _countyMap.put(bean.name, bean)
            }
            countyMap = _countyMap

            val npop = (if (statewide == null) 0 else statewide.npop)
            countyTotal = CountyBean(CountyData("=Total", countUniformMvrs, npop))
            countMvrsByCounty()
            countyList.add(countyTotal!!)

            // sort counties by nmvrs
            countyList.sortByDescending { it.nmvrsConsistent }
            countyTable.setBeans(countyList)
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("setAuditRecord failed", e)
        }

        return true
    }

    fun setSelectedContest(contestBean: ContestBean) {
        val beanList = mutableListOf<AssertionBean>()
        for (cassert in contestBean.contestUA.clcaAssertions) {
            beanList.add(AssertionBean(contestBean, cassert))
        }

        if (beanList.isEmpty()) return

        // sort assertions by payoff
        beanList.sortBy{ it.getPayoff() }
        assertionTable.setBeans(beanList)
    }

    override fun saveState() {
        contestTable.saveState(false)
        assertionTable.saveState(false)
        countyTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun getActions(container: JPanel?) {
        val startAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                resample()
            }
        }
        BAMutil.setActionProperties(startAction, "ambition.png", "Resample", false, 'S'.code, -1)
        BAMutil.addActionToContainer(container, startAction)

        /* TODO put into separate thread
        AbstractAction runAuditRoundAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                callRunRound();
                contestsPanel.resetAuditRecord();
            }
        };
        BAMutil.setActionProperties(runAuditRoundAction, "hamster.png", "Run Audit Round", false, 'R', -1);
        BAMutil.addActionToContainer(container, runAuditRoundAction); */
        val targetAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                includeTargetedOnly()
            }
        }
        BAMutil.setActionProperties(targetAction, "goal.png", "Include Targets Only", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, targetAction)

        val targetPlusAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                includeTargetedPlus()
            }
        }
        BAMutil.setActionProperties(targetPlusAction, "goal.png", "Include Targets plus", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, targetPlusAction)

        val targetLessThanAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                includeTargetedPlusContestsLessThan(100) // TODO allow user to set maxMvrs
            }
        }
        BAMutil.setActionProperties(
            targetLessThanAction,
            "goal.png",
            "Include Targets and LessThan 100",
            false,
            'T'.code,
            -1
        )
        BAMutil.addActionToContainer(container, targetLessThanAction)

        val includeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(true)
            }
        }
        BAMutil.setActionProperties(includeAllAction, "add.png", "Include All Contests", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, includeAllAction)

        val excludeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(false)
            }
        }
        BAMutil.setActionProperties(excludeAllAction, "exemption.png", "Exclude All Contests", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, excludeAllAction)
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Actions

    fun resample() {
        try {
            val cuas: MutableList<ContestWithAssertions?> = ArrayList<ContestWithAssertions?>()
            for (cr in this.lastAuditRound!!.contestRounds) {
                cuas.add(cr.contestUA)
            }

            val nrounds = countyAudit!!.rounds.size
            if (nrounds == 0) return

            logger.info(String.format("call resampleAndSaveResults"))

            resampleAndSaveResults(countyAudit!!, (lastAuditRound as AuditRound?)!!)
            countMvrsByCounty()
            contestTable.refresh()

            samplingChanged = false // perhaps not needed
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("AuditRoundsTable.resample failed", e)
        }
    }

    // all include or exclude
    fun setInclude(include: Boolean) {
        for (contestRound in lastAuditRound!!.contestRounds) {
            contestRound.included = include
        }
        samplingChanged = true
        contestTable.refresh()
    }

    // set targeted to be included
    fun includeTargetedOnly() {
        for (bean in contestTable.getBeans()) {
            if (bean.contestRound != null) {
                bean.contestRound!!.included = bean.targeted()
            }
        }
        samplingChanged = true
        contestTable.refresh()
    }

    // targeted plus some experimental algorithm using include and maxRisk
    fun includeTargetedPlus() {
        for (bean in contestTable.getBeans()) {
            if (bean.getStatus() != TestH0Status.InProgress.name) continue
            if (bean.contestRound == null) continue

            bean.setMaxRisk( auditRiskLimit) // also sets estMvrs
            if (bean.targeted()) {
                bean.setInclude(true)
            } else {
                if (bean.getEstMvrs() >= 500) bean.setInclude(false)
                else if (bean.getEstMvrs()  >= 150) bean.setMaxRisk(.10)
                else if (bean.getEstMvrs()  >= 50) bean.setMaxRisk(.05)
            }
        }

        samplingChanged = true
        contestTable.refresh()
    }

    // targeted and contests with estMvrs < needMvrs
    fun includeTargetedPlusContestsLessThan(maxMvrs: Int): Boolean {
        includeTargetedOnly()

        for (bean in contestTable.getBeans()) {
            if (bean.contestRound == null) continue
            if (bean.getEstMvrs() <= maxMvrs && bean.getStatus() == TestH0Status.InProgress.name)
                bean.contestRound!!.included = true
        }

        samplingChanged = true
        contestTable.refresh()
        return true
    }

    fun countMvrsByCounty() {
        var countMvrs = 0
        val mvrCounts = countyAudit!!.countMvrsByCounty()
        for (countyData in mvrCounts.values) {
            val countyBean = countyMap.get(countyData.countyName)
            if (countyBean != null) {
                countyBean.nmvrsConsistent = countyData.nmvrs
                countMvrs += countyData.nmvrs
            } else {
                logger.warn("cant find countyName '" + countyData.countyName + "'")
            }
        }
        if (countyTotal != null) {
            countyTotal!!.nmvrsConsistent = countMvrs
        }
        countyTable.refresh()
    }

    fun showInfo(f: Formatter) {
        if (this.countyAudit == null) return

        f.format("Audit record at %s%n%n", countyAudit!!.location)
        f.format("%s%n", this.config)
        if (this.lastAuditRound == null) return

        f.format("AuditRounds")
        var totalExtra = 0
        for (round in countyAudit!!.rounds) {
            if (round.auditWasDone) {
                val roundIdx = round.roundIdx
                val nmvrs = round.samplePrns.size
                f.format("%n  number of Mvrs in round %d = %d %n", roundIdx, nmvrs)
                val extra = round.mvrsUnused
                f.format("  extraBallotsUsed = %d %n", extra)
                totalExtra += extra
            }
        }
        f.format("%n  total extraBallotsUsed = %d %n", totalExtra)
        f.format("  total Mvrs = %d%n", this.lastAuditRound!!.nmvrs)
    }

    fun showContest(bean: ContestBean): String {
        return BeanProperties.showContestG(
            bean,
            contestTable.tableModel,
            bean.contestUA,
            bean.contestRound
        )
    }

    fun printContests(): String {
        return BeanProperties.printContestsG(contestTable.getBeans(), contestTable.tableModel)
    }

    fun showAssertion(bean: AssertionBean): String {
        return BeanProperties.showAssertionG(
            bean,
            assertionTable.tableModel,
            bean.cua,
            bean.cassertion
        )
    }


    class ContestBean(var contestUA: ContestWithAssertions, var contestRound: ContestRound?, val sampleChanged: (Boolean) -> Any) {
        var mvrLimit: Int = -1

        fun canedit(): Boolean {
            return true
        }

        fun isInclude() = contestRound != null && contestRound!!.included

        fun setInclude(include: Boolean) {
            if (contestRound == null) return
            val oldState = contestRound!!.included
            if (oldState != include) {
                contestRound!!.included = include
                sampleChanged(true)
            }
        }

        fun getMaxRisk(): Double {
            if (contestRound == null) return 1.0
            val risk = contestRound!!.auditorWantRisk
            return (if (risk != null) risk else auditRiskLimit)
        }

        // TODO  editable properties have to be primitive
        fun setMaxRisk(risk: Double) {
            if (contestRound == null) return
            contestRound!!.auditorWantRisk = risk
            val estMvrs = this.getEstMvrs()
            contestRound!!.estMvrs = estMvrs
            this.setInclude(true)
            sampleChanged(true)
        }

        fun getName() = contestUA.name

        fun getId() = contestUA.id

        fun getRisk(): Double {
            val minAssertion = contestUA.minClcaAssertion()
            if (minAssertion == null) return 1.0
            val noerror = minAssertion.noerror

            val haveMvrs = this.getHaveMvrs()
            return estRiskStandardBet(contestUA.population(), noerror, haveMvrs)
        }

        fun getEstMvrs(): Int {
            val minAssertion = contestUA.minClcaAssertion()
            if (minAssertion == null) return 0
            val noerror = minAssertion.noerror

            return estSampleSizeStandardBet(contestUA.population(), noerror, this.getMaxRisk())
        }

        fun getHaveMvrs() = if (contestRound == null) 0 else contestRound!!.haveSampleSize

        fun samplePct() : Double{
            val pop = this.getPopSize()
            return if (pop == 0) 0.0 else this.getEstMvrs() / (1.0 * pop)
        }

        fun getNCounties(): String {
            val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
            if (CORLAcounties == null) return "N/A"
            val toks: List<String> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
            if (toks.size == 1) return toks[0]
            return String.format("%02d", toks.size)
        }

        fun getTarget() = if (targeted()) "YES" else ""

        fun targeted(): Boolean {
            val reason = contestUA.contest.info().metadata.get("CORLAauditReason")
            if (reason == null) return false
            return reason == "state_wide_contest" || reason == "county_wide_contest"
        }

        fun statewide(): Boolean {
            val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
            if (CORLAcounties == null) return false
            val toks: Array<String?> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return (toks.size > 60)
        }

        fun getPayoff(): String {
            val minAssertion = contestUA.minAssertion()
            if (minAssertion == null) return "N/A"
            val noerror = minAssertion.assorter.noerror(contestUA.hasStyle)
            return dfn(payoff(2.0 / 1.03905, noerror), 6)
        }

        fun getMargin(): Double{
            val margin = contestUA.minMargin()
            return if (margin == null) 0.0 else margin
        }

        fun getMvrsExtra() = this.getHaveMvrs() - this.getEstMvrs()

        fun getNCand() = contestUA.ncandidates

        fun getPopSize() = contestUA.population()

        fun getPhantoms() = contestUA.Nphantoms

        fun getRecountMargin(): Double {
                val min = contestUA.minRecountMargin()
                return if (min == null) 0.0 else min
            }

        fun getStatus() = Naming.status(contestUA.preAuditStatus)

        fun getType() = contestUA.choiceFunction.toString()

        fun getUndervotes() = contestUA.contest.Nundervotes()

        fun getUvPct() = contestUA.contest.undervotePct()

        fun getVotes(): String {
                val votes = contestUA.contest.votes()
                if (votes != null) return votes.toString()
                return "N/A"
            }

        fun getVoteDiff(): Int {
                val minAssertion = contestUA.minAssertion()
                if (minAssertion == null) return 0
                return contestUA.contest.marginInVotes(minAssertion.assorter)
            }

        fun getWinners() = contestUA.contest.winners().toString()

        companion object {
            val auditRiskLimit: Double = .03

            @JvmStatic
            fun editableProperties() = "include maxRisk"

            @JvmStatic
            fun hiddenProperties() = "contestRound contestUA sampleChanged"
        }
    }

    class AssertionBean(val contestBean: ContestBean, val cassertion: ClcaAssertion) {
        val cua: ContestWithAssertions
        val assorter: AssorterIF
        val candidates: Map<Int, String>

        // constructor()

        init {
            this.cua = contestBean.contestUA
            this.assorter = cassertion.assorter
            this.candidates = cua.contest.info().candidateIdToName
        }

        fun getType() = assorter.javaClass.getSimpleName()

        fun getWinner(): String {
            if (assorter is DHondtAssorter) {
                return assorter.winnerNameRound()
            }
            val winner = assorter.winner()
            return candidates.get(winner)!!
        }

        fun getLoser(): String {
            if (assorter is DHondtAssorter) {
                return assorter.loserNameRound()
            }
            val loser = assorter.loser()
            return candidates.get(loser)!!
        }

        fun getDesc() = assorter.hashcodeDesc()

        fun getEstRisk(): Double {
                val noerror = cassertion.noerror
                val haveMvrs = contestBean.getHaveMvrs()
                return estRiskStandardBet(cua.population(), noerror, haveMvrs)
            }

        fun getEstMvrs(): Int {
                val noerror = cassertion.noerror
                return estSampleSizeStandardBet(contestBean.contestUA.population(), noerror, contestBean.getMaxRisk())
            }

        fun getMargin() = cassertion.cassorter.assorterMargin

        fun getDifficulty() = cua.contest.showAssertionDifficulty(assorter)

        fun getRecountMargin() = cua.contest.recountMargin(assorter)

        fun getMean() = assorter.dilutedMean()

        fun getNoerror() = dfn(assorter.noerror(cua.hasStyle), 5)

        fun getPayoff(): String {
                val noerror = assorter.noerror(cua.hasStyle)
                return dfn(payoff(2.0 / 1.03905, noerror), 6)
            }

        fun getUpper() = assorter.upperBound()

        companion object {
            @JvmStatic
            fun hiddenProperties() = "contestBean cassertion cua assorter candidates"
        }
    }

    /*
    inner class CountyBean {
        var name: String = null
        var npop: Int? = null
        var nmvrsUniform: Int? = null
        var nmvrsConsistent: Int = 0

        constructor()

        internal constructor(countyData: CountyData) {
            this.name = countyData.countyName
            this.npop = countyData.npop
            this.nmvrsUniform = countyData.nmvrs
        }

        fun getNpop(): Int {
            return npop!!
        }

        fun getNmvrsUniform(): Int {
            return nmvrsUniform!!
        }

        val diff: Int
            get() = nmvrsUniform!! - nmvrsConsistent
        val uniformInvRate: Int
            get() = if (nmvrsUniform == 0) 0 else roundUp(getNpop().toDouble() / nmvrsUniform!!)
        val consistentInvRate: Int
            get() = if (nmvrsConsistent == 0) 0 else roundUp(getNpop().toDouble() / nmvrsConsistent)
    }

    inner class CountyContestBean {
        var countyContestData: CountyContestData? = null
        var npop: Int? = null
        var nmvrsUniform: Int? = null
        var nmvrsConsistent: Int = 0

        constructor()

        internal constructor(countyContestData: CountyContestData) {
            this.countyContestData = countyContestData
        }

        val contestName: String
            get() = countyContestData!!.contestName
        val voteDiff: Int
            get() = countyContestData!!.voteDiff
        val votes: String?
            get() = countyContestData!!.votes.toString()
    } */

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CorlaAuditTable::class.java)
    }
}