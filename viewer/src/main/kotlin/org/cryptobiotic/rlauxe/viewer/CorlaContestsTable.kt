package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.betting.payoff
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.prefs.BeanTable
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.util.*
import java.util.function.Function
import javax.swing.AbstractAction
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.collections.associateBy
import kotlin.text.split

// used for both Contests and Sampling tabs
class CorlaContestsTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane,
    infoWindow: IndependentWindow,
    fontSize: Float,
    sampler: Boolean,
) : JPanel(), ViewerPanelIF {
    var countyTotal: CountyBean? = null

    private val contestTable: BeanTable<CorlaContestBean>
    private val contestCountyTable: BeanTable<ContestCountyBean>

    // private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAuditRecord? = null
    private var config: Config? = null
    private var lastAuditRound: AuditRoundIF? = null // may not be null
    private var infos: Map<Int, ContestInfo> = emptyMap()
    private var countyPools: List<CountyPools> = emptyList()
    private var countyCvrPools: Map<String, CountyPools> = emptyMap()

    private var auditRiskLimit: Double = 0.0
    private var samplingChanged = false
    private var onlyShowInprogressContests = false

    init {
        contestTable =
            BeanTable<CorlaContestBean>(
                CorlaContestBean::class.java,
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
            contestTable.makeShowAction(infoTA, infoWindow) { bean: Any? -> showContest(bean as CorlaContestBean) }
        )
        contestTable.addPopupOption(
            "Print Contests", contestTable.makeShowAction(
                infoTA, infoWindow,
                Function { bean: Any? -> printContests() })
        )

        contestCountyTable = BeanTable<ContestCountyBean>(
            ContestCountyBean::class.java,
            prefs.node("contestCountyPoolBean") as PreferencesExt?,
            false,
            "County subtotals for selected Contest",
            "CountyContests",
            null
        )
        contestCountyTable.addPopupOption(
            "Show Contest",
            contestCountyTable.makeShowAction(infoTA, infoWindow) { bean: Any? -> showCountyContest(bean as ContestCountyBean) }
        )

        // countyTable.addPopupOption("Show County", countyTable.makeShowAction(infoTA, infoWindow, bean -> ((CountyBean) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, contestCountyTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 800))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("CorlaContestsTable init")
    }

    override fun setFontSize(size: Float) {
        contestTable.setFontSize(size)
        contestCountyTable.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean( "onlyInProgress", false)

        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(mutableListOf<CorlaContestBean?>())

        logger.debug("setAuditRecord " + auditRecordLocation)

        try {
            this.auditRecordLocation = auditRecordLocation
            val record = AuditRecord.read(auditRecordLocation)
            if (record == null) return false
            if (record.rounds.isEmpty()) {
                logger.info("first round was not started") // TODO plan B
                return false
            }
            if (record !is CountyAuditRecord) {
                logger.info("must be CountyAuditRecord")
                return false
            }
            this.countyAudit = record
            this.lastAuditRound = countyAudit!!.rounds.last()
            this.config = countyAudit!!.config
            this.auditRiskLimit = config!!.riskLimit
            CorlaContestBean.auditRiskLimit = config!!.riskLimit

            val mvrManager = PersistedMvrManager(this.countyAudit!!, false)
            val countyPools = mvrManager.countyPools()
            if (countyPools == null) return false
            this.countyPools = countyPools
            val countyCvrPools = mvrManager.countyCvrPools()
            if (countyCvrPools != null)
                this.countyCvrPools = countyCvrPools.associateBy { it.countyName }

            this.infos = countyAudit!!.contests.associate { it.contest.info().id to it.contest.info() }

            loadAuditRecord()

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("setAuditRecord failed", e)
        }

        return true
    }

    fun loadAuditRecord() {
        val auditRecord = this.countyAudit!!
        this.lastAuditRound = auditRecord.rounds.last()
        this.config = auditRecord.config
        this.auditRiskLimit = config!!.riskLimit

        val contestRoundMap: MutableMap<Int?, ContestRound?> = HashMap<Int?, ContestRound?>()
        for (contestRound in lastAuditRound!!.contestRounds) {
            contestRoundMap.put(contestRound.id, contestRound)
        }

        val contestList = mutableListOf<CorlaContestBean>()
        auditRecord.contests.filter { !onlyShowInprogressContests || it.preAuditStatus == TestH0Status.InProgress }.forEach { cwa ->
            val cr = contestRoundMap.get(cwa.id)
            val bean = CorlaContestBean(cwa, cr) { b -> samplingChanged = b }
            contestList.add(bean)
        }
        // sort contests by payoff
        contestList.sortBy { it.getPayoff() }
        contestTable.setBeans(contestList)
    }

    fun setSelectedContest(contestBean: CorlaContestBean) {
        val beans = mutableListOf<ContestCountyBean>()
        this.countyPools.forEach { countyPool : CountyPools ->
            val countyContestTab = countyPool.contestTabs[contestBean.getId()]
            if (countyContestTab != null) {
                val info = this.infos[contestBean.getId()]!!
                val auditcenterBean = ContestCountyBean(countyPool, countyContestTab, info, false)
                beans.add(auditcenterBean)
                val cvrPool = countyCvrPools[countyPool.countyName]
                val cvrTab = cvrPool?.contestTabs[contestBean.getId()]
                if (cvrTab != null) {
                    val mvrBean = ContestCountyBean(cvrPool,cvrTab, info, true)
                    mvrBean.acBean = auditcenterBean
                    beans.add(mvrBean)
                }
            }
        }
        contestCountyTable.setBeans(beans)
    }

    override fun saveState() {
        contestTable.saveState(false)
        contestCountyTable.saveState(false)

        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Actions


    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {
        //  * Example for Toggle Action
        // *
        // * <pre>
        // * AbstractAction dsAction =  new AbstractAction() {
        //    public void actionPerformed(ActionEvent e) {
        //     Boolean state = (Boolean) getValue( BAMutil.STATE);
        //     addCoords = state.booleanValue();
        //     String tooltip = addCoords ? "add Coordinates in ON" : "add Coordinates is OFF";
        //     dsButt.setToolTipText(tooltip);
        //    }
        //   };
        //   BAMutil.setActionProperties( dsAction, "Dataset", "add Coordinates is OFF", true, 'D', -1);
        //   addCoords = prefs.getBoolean( "dsState", false);
        //   dsAction.putValue(BAMutil.STATE, new Boolean(addCoords));
        //   AbstractButton dsButt = BAMutil.addActionToContainer(buttPanel, dsAction);
        //
        //   ...
        //   prefs.putBoolean("dsState", dsButt.getModel().isSelected());
        val onlyProcessAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val state = getValue( BAMutil.STATE);
                val onlyProcess = state as Boolean
                onlyProcess(onlyProcess)
                prefs.putBoolean( "onlyInProgress", onlyProcess)
            }
        }
        val savedState = prefs.getBoolean( "onlyInProgress", false)
        onlyProcessAction.putValue(BAMutil.STATE, savedState);
        BAMutil.setActionProperties(onlyProcessAction, "sunrise-icon.png", "Only show Contests InProgress", true, 'S'.code, -1)
        BAMutil.addActionToContainer(container, onlyProcessAction)

        logger.debug("CorlaAuditTable.getActions")
    }

    // all include or exclude
    fun onlyProcess(onlyInProgress: Boolean) {
        this.onlyShowInprogressContests = onlyInProgress
        loadAuditRecord()
    }

    fun showInfo(f: Formatter) {
        if (this.countyAudit == null) return

        f.format("Audit record at %s%n%n", countyAudit!!.topdir)
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

        f.format("%ntotal contests = %d %n", this.countyAudit!!.contests.size)
        f.format("auditable contests = %d %n", this.countyAudit!!.rounds.first().contestRounds.size)
        f.format("total cards = %d %n", this.config!!.election.totalCardCount)

        if (countyTotal != null) {
            f.format("%nmvrs for corla sampling = %d %n", countyTotal!!.corlaSampling)
            f.format("%nmvrs for rlauxe sampling = %d %n", countyTotal!!.rlauxeSampling)
        }
    }

    fun showContest(bean: CorlaContestBean) = buildString {
        append(BeanProperties.showContestG(bean, contestTable.tableModel, bean.contestUA))
        val votes: Map<Int, Int> = bean.contestUA.contest.votes()!!
        val sortedVotes = votes.toList().sortedBy { it.first }.toMap()
        appendLine("sortedVotes   = $sortedVotes")

        val showSums = buildString {
            var acNvotes = 0
            var cvrNvotes = 0
            var acNu = 0
            var cvrNu = 0
            val acSum = ContestTabulation(bean.contestUA.contest.info())
            val cvrSum = ContestTabulation(bean.contestUA.contest.info())
            contestCountyTable.getBeans().forEach { bean ->
                if (bean.isCvrs) {
                    cvrSum.sum(bean.contestTab)
                    cvrNvotes += bean.nvotes
                    cvrNu += bean.undervotes
                } else {
                    acSum.sum(bean.contestTab)
                    acNvotes += bean.nvotes
                    acNu += bean.undervotes
                }
            }
            appendLine("ac   = $acSum")
            appendLine("cvrs = $cvrSum")
            appendLine("acNvotes = $acNvotes, cvrNvotes = $cvrNvotes diff = ${acNvotes - cvrNvotes}")
            appendLine("acNu = $acNu, cvrNu = $cvrNu diff = ${acNu - cvrNu}")
        }
        append(showSums)
    }

    fun printContests(): String {
        // TODO make general
        val indexBeans = contestTable.getIndexedBeans()
        val sortedIndexBeans = indexBeans.sortedBy { it.viewIndex() }
        val sortedBeans =   sortedIndexBeans.map { it.bean }
        return BeanProperties.printTableG(sortedBeans, contestTable.tableModel, BeanProperties.contests, "contests")
    }

    fun showCountyContest(bean: ContestCountyBean) = buildString {
        append(BeanProperties.showContestG(bean, contestCountyTable.tableModel, null))
        // append(bean.showSums(contestCountyTable.getBeans()))
    }

    // used also in SamplingTable
    class CorlaContestBean(var contestUA: ContestWithAssertions, var contestRound: ContestRound?, val sampleChanged: (Boolean) -> Any) {
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
            return if (risk != null) risk else auditRiskLimit
        }

        // TODO editable properties have to be primitive
        fun setMaxRisk(risk: Double) {
            if (contestRound == null) return
            contestRound!!.auditorWantRisk = risk
            contestRound!!.estMvrs = this.calcEstMvrs(risk)
            // this.setInclude(true)
            sampleChanged(true)
        }

        fun getName() = contestUA.name

        fun getId() = contestUA.id

        fun getRisk(): Double {
            val minAssertion = contestUA.minClcaAssertion()
            if (minAssertion == null) return 0.0
            val noerror = minAssertion.noerror

            val haveMvrs = this.getHaveMvrs()
            return estRiskStandardBet(contestUA.population(), noerror, haveMvrs)
        }

        fun getEstMvrs(): Int {
            return calcEstMvrs(this.getMaxRisk())
        }

        fun calcEstMvrs(maxRisk: Double): Int {
            val minAssertion = contestUA.minClcaAssertion()
            if (minAssertion == null) return 0
            val noerror = minAssertion.noerror

            return estSampleSizeStandardBet(contestUA.population(), noerror, maxRisk)
        }

        fun getHaveMvrs() = if (contestRound == null) 0 else contestRound!!.haveSampleSize

        fun samplePct() : Double{
            val pop = this.getPopulation()
            return if (pop == 0) 0.0 else this.getEstMvrs() / (1.0 * pop)
        }

        fun getNCounties(): String {
            val counties = counties()
            if (counties == null) return "N/A"
            if (counties.size == 1) return counties[0]
            return String.format("%02d", counties.size)
        }

        fun counties(): List<String>? {
            val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
            if (CORLAcounties == null) return null
            val stripped = CORLAcounties.drop(1).dropLast(1)
            return stripped.split(",".toRegex()).dropLastWhile { it.isEmpty() }
        }

        fun getCorlaEstMvrs(): Int {
            if (!targeted()) return -1
            if (contestUA == null) return -1
            val CORLAsample = contestUA!!.contest.info().metadata.get("CORLAsample")
            if (CORLAsample == null) return -1
            return CORLAsample.toInt()
        }

        fun getCorlaHaveMvrs(): Int {
            if (contestUA == null) return -1
            val haveMvrss = contestUA!!.contest.info().metadata.get("CORLAhaveMvrs")
            if (haveMvrss == null) return -1
            return haveMvrss.toInt()
        }

        fun getCorlaStrata(): Int {
            if (contestUA == null) return -1
            val haveMvrss = contestUA!!.contest.info().metadata.get("CORLAstrataNcards")
            if (haveMvrss == null) return -1
            return haveMvrss.toInt()
        }

        // same as corlaSampling plot
        fun getCorlaVoteDiff(): Int {
            if (contestUA == null) return -1
            val cua = contestUA!!
            val contest = cua.contest
            val minMargin: String? = contest.info().metadata.get("CORLAmarginInVotes")
            if (minMargin == null) return -1
            return minMargin.toInt()
        }

        // same as corlaSampling plot
        fun getCorlaRisk(): Double {
            if (contestUA == null) return 1.0
            val cua = contestUA!!
            val contest = cua.contest
            val haveMvrss: String? = contest.info().metadata.get("CORLAhaveMvrs")
            if (haveMvrss == null) return 1.0
            val minAssertion: Assertion? = cua.minAssertion()
            if (minAssertion == null) return 1.0

            // fun estRiskStandardBet(voteDiff: Int, Npop: Int, upper: Double, nsamples: Int, ): Double {
            return estRiskStandardBet(getCorlaVoteDiff(), getCorlaStrata(), minAssertion.upper, haveMvrss.toInt())
        }

        fun getTarget() = if (targeted()) "YES" else ""

        fun targeted(): Boolean {
            val reason = contestUA.contest.info().metadata.get("CORLAauditReason")
            if (reason == null) return false
            // return reason == "county_wide_contest"
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

        fun getCvrNcards() : Int {
            val CvrNcards = contestUA.contest.info().metadata.get("CvrNcards")
            if (CvrNcards == null) return 0
            return CvrNcards.toInt()
        }

        fun getDiffNcards() : Int {
            val CvrNcards = contestUA.contest.info().metadata.get("CvrNcards")
            if (CvrNcards == null) return 0
            val ncvrs = CvrNcards.toInt()
            return contestUA.Nc - ncvrs
        }

        fun getCvrNvotes() : Int {
            val CvrNvotes = contestUA.contest.info().metadata.get("CvrNvotes")
            if (CvrNvotes == null) return 0
            val nvotes = CvrNvotes.toInt()
            return nvotes
        }

        fun getCvrNundervotes() : Int {
            val CvrNvotes = contestUA.contest.info().metadata.get("CvrNundervotes")
            if (CvrNvotes == null) return 0
            val nvotes = CvrNvotes.toInt()
            return nvotes
        }

        fun getNvotes() : Int {
            return contestUA.contest.nvotes()
        }

        fun getDiffNvotes() : Int {
            val CvrNvotes = contestUA.contest.info().metadata.get("CvrNvotes")
            if (CvrNvotes == null) return 0
            val nvotes = CvrNvotes.toInt()
            return contestUA.contest.nvotes() - nvotes
        }

         // Nc - cvrs.ncards / Nc = % phantoms ?
         fun getPctMissing() : Double {
             return getDiffNcards() / contestUA.Nc.toDouble()
         }

        fun getPopulation() = contestUA.population()

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
            var auditRiskLimit: Double = 0.0

            @JvmStatic
            fun editableProperties() = "include maxRisk"

            @JvmStatic
            fun hiddenProperties() = "contestRound contestUA sampleChanged"
        }
    }

    class AssertionBean(val contestBean: CorlaContestBean, val cassertion: ClcaAssertion) {
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

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CorlaContestsTable::class.java)
    }

    // all the counties for this contest
    class ContestCountyBean(val pool: CountyPools, val contestTab: ContestTabulation, val info: ContestInfo, val isCvrs: Boolean) {
        val countyName: String
        val contestId: Int
        val vunderTab: Vunder
        var acBean: ContestCountyBean? = null

        init {
            countyName = pool.countyName
            contestId = contestTab.contestId
            vunderTab = contestTab.votesAndUndervotes(pool.countyPoolId, contestTab.ncards(), true)
        }

        val undervotes = vunderTab.undervotes  // vunder properly calculates when voteForN > 1
        // val uvPct = undervotes / (contestTab.voteForN * contestTab.ncards()).toDouble()
        val missing = vunderTab.missing
        val votes = vunderTab.cands().toString()

        val nvotes = vunderTab.nvotes
        fun getCompareNvotes() : Int {
            return if (acBean == null) -1 else (acBean!!.nvotes - nvotes)
        }
        fun getPctCompareNvotes() : String {
            return if (acBean == null) "" else dfn((acBean!!.nvotes - nvotes) / acBean!!.nvotes.toDouble(), 4)
        }

        val cvrNcards = contestTab.ncards()
        val source = if (isCvrs) "cvrs" else "auditcenter"

        fun show() = buildString {
            append("${nfn(contestId, 3)}, ${trunc(countyName, 12)},    ${nfn(contestTab.ncards(), 6)}, ") // ${trunc(votes, 25)}, ")
            append("    ${nfn(nvotes, 6)},   ${nfn(undervotes, 6)},    ${getCompareNvotes()}")
        }

        companion object {
            val header = " id,    countyName, tabNCards, styleNCards, diffNCards, nvotes, undervotes, diffVotes"

            @JvmStatic
            fun hiddenProperties() = "pool contestTab vunderTab info acBean cvrs"
        }
    }
}