package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.betting.payoff
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.CountyData
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
    private val contestCountyTable: BeanTable<ContestCountyPoolBean>

    private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAudit? = null
    private var config: Config? = null
    private var lastAuditRound: AuditRoundIF? = null // may not be null
    private var infos: Map<Int, ContestInfo> = emptyMap()
    private var countyPools: List<CountyPools> = emptyList()
    private var countyCvrPools: Map<String, CountyPools> = emptyMap()

    private var auditRiskLimit: Double = .03
    private var samplingChanged = false
    private var onlyShowInprogressContests = false

    init {
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

        contestCountyTable = BeanTable<ContestCountyPoolBean>(
            ContestCountyPoolBean::class.java,
            prefs.node("contestCountyPoolBean") as PreferencesExt?,
            false,
            "County subtotals for selected Contest",
            "CountyContests",
            null
        )

        // countyTable.addPopupOption("Show County", countyTable.makeShowAction(infoTA, infoWindow, bean -> ((CountyBean) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 400))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, contestCountyTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 800))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("CorlaAuditTable init")
    }

    override fun setFontSize(size: Float) {
        contestTable.setFontSize(size)
        assertionTable.setFontSize(size)
        countyTable.setFontSize(size)
        contestCountyTable.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean( "onlyInProgress", false)

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

        val beanList = mutableListOf<ContestBean>()
        auditRecord.contests.filter { !onlyShowInprogressContests || it.preAuditStatus == TestH0Status.InProgress }.forEach { cwa ->
            val cr = contestRoundMap.get(cwa.id)
            val bean = ContestBean(cwa, cr) { b -> samplingChanged = b }
            beanList.add(bean)
        }
        // sort contests by payoff
        beanList.sortBy { it.getPayoff() }
        contestTable.setBeans(beanList)

        var countUniformMvrs = 0
        var statewide: CountyBean? = null
        val countyList = mutableListOf<CountyBean>()
        val _countyMap = mutableMapOf<String, CountyBean>()
        for (countyData in countyAudit!!.countyData) {
            val bean = CountyBean(countyData)
            if (bean.name == "Statewide") statewide = bean
            else countUniformMvrs += bean.nmvrsUniformSampling // statewide now included in counties, so dont count twice

            countyList.add(bean)
            _countyMap.put(bean.name, bean)
        }
        countyMap = _countyMap

        val totalPopulation = countyAudit!!.countyData.filter { it.countyName != "Statewide"}.sumOf { it.npop }
        countyTotal = CountyBean(CountyData("=Total", countUniformMvrs, totalPopulation))
        countMvrsByCounty()
        countyList.add(countyTotal!!)

        // sort counties by nmvrs
        countyList.sortByDescending { it.nmvrsCardStyleSampling }
        countyTable.setBeans(countyList)
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

        val beans = mutableListOf<ContestCountyPoolBean>()
        this.countyPools.forEach { countyPool : CountyPools ->
            val countyContestTab = countyPool.contestMap[contestBean.getId()]
            if (countyContestTab != null) {
                val info = this.infos[contestBean.getId()]!!
                val auditcenterBean = ContestCountyPoolBean(countyPool, countyContestTab, info, false)
                beans.add(auditcenterBean)
                val cvrPool = countyCvrPools[countyPool.countyName]
                val cvrTab = cvrPool?.contestMap[contestBean.getId()]
                if (cvrTab != null) {
                    val mvrBean = ContestCountyPoolBean(cvrPool,cvrTab, info, true)
                    mvrBean.acBean = auditcenterBean
                    beans.add(mvrBean)
                }
            }
        }
        contestCountyTable.setBeans(beans)
    }

    override fun saveState() {
        contestTable.saveState(false)
        assertionTable.saveState(false)
        countyTable.saveState(false)
        contestCountyTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
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

        val startAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                resample()
            }
        }
        BAMutil.setActionProperties(startAction, "ambition.png", "Resample", false, 'S'.code, -1)
        BAMutil.addActionToContainer(container, startAction)

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

        logger.debug("CorlaAuditTable.getActions")
    }

    // all include or exclude
    fun onlyProcess(onlyInProgress: Boolean) {
        this.onlyShowInprogressContests = onlyInProgress
        loadAuditRecord()
    }

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
            bean.setInclude(bean.targeted())
            bean.setMaxRisk(.03)
        }
        samplingChanged = true
        contestTable.refresh()
    }

    // targeted plus some experimental algorithm using include and maxRisk
    fun includeTargetedPlus() {
        includeTargetedOnly()

        for (bean in contestTable.getBeans()) {
            if (bean.getStatus() != TestH0Status.InProgress.name) continue
            if (bean.contestRound == null) continue

            if (!bean.targeted()) {
                if (bean.getEstMvrs() < 500) {
                    bean.setInclude(true)
                    if (bean.getEstMvrs() >= 150) bean.setMaxRisk(.10)
                    else if (bean.getEstMvrs() >= 50) bean.setMaxRisk(.05)
                }
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
                countyBean.nmvrsCardStyleSampling = countyData.nmvrs
                countMvrs += countyData.nmvrs
            } else {
                logger.warn("cant find countyName '" + countyData.countyName + "'")
            }
        }
        if (countyTotal != null) {
            countyTotal!!.nmvrsCardStyleSampling = countMvrs
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

        f.format("%ntotal contests = %d %n", this.countyAudit!!.contests.size)
        f.format("auditable contests = %d %n", this.countyAudit!!.rounds.first().contestRounds.size)
        f.format("total cards = %d %n", this.config!!.election.totalCardCount)

        if (countyTotal != null) {
            f.format("%nmvrs for uniform sampling = %d %n", countyTotal!!.nmvrsUniformSampling)
            f.format("%nmvrs for card style sampling = %d %n", countyTotal!!.nmvrsCardStyleSampling)
        }
    }

    fun showContest(bean: ContestBean) = buildString {
        append(BeanProperties.showContestG(bean, contestTable.tableModel, bean.contestUA, bean.contestRound))
        append(bean.showSums(contestCountyTable.getBeans()))
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
            return if (risk != null) risk else auditRiskLimit
        }

        // TODO editable properties have to be primitive
        fun setMaxRisk(risk: Double) {
            if (contestRound == null) return
            contestRound!!.auditorWantRisk = risk
            // val estMvrs = this.getEstMvrs()
            // contestRound!!.estMvrs = estMvrs
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
            val minAssertion = contestUA.minClcaAssertion()
            if (minAssertion == null) return 0
            val noerror = minAssertion.noerror

            return estSampleSizeStandardBet(contestUA.population(), noerror, this.getMaxRisk())
        }

        fun getHaveMvrs() = if (contestRound == null) 0 else contestRound!!.haveSampleSize

        fun samplePct() : Double{
            val pop = this.getPopulation()
            return if (pop == 0) 0.0 else this.getEstMvrs() / (1.0 * pop)
        }

        fun getNCounties(): String {
            val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
            if (CORLAcounties == null) return "N/A"
            val toks: List<String> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
            if (toks.size == 1) return toks[0]
            return String.format("%02d", toks.size)
        }

        fun getCorlaHaveMvrs(): String {
            if (contestUA == null) return "N/A"
            val CORLAsample = contestUA!!.contest.info().metadata.get("CORLAhaveMvrs")
            if (CORLAsample == null) return "N/A"
            return CORLAsample.toInt().toString()
        }

        // same as corlaSampling plot
        fun getCorlaRisk(): Double {
            if (contestUA == null) return 1.0
            val cua = contestUA!!
            val contest = cua.contest
            val noerror = cua.minNoerror()
            if (noerror == null) return 0.0

            val haveMvrss: String? = contest.info().metadata.get("CORLAhaveMvrs")
            if (haveMvrss == null) return 1.0
            return estRiskStandardBet(cua.Npop, noerror, haveMvrss.toInt())
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

        fun getDiffNvotes() : Int {
            val CvrNvotes = contestUA.contest.info().metadata.get("CvrNvotes")
            if (CvrNvotes == null) return 0
            val nvotes = CvrNvotes.toInt()
            return contestUA.contest.nvotes() - nvotes
        }

         // Nc - cvrs.ncards / Nc = % phantoms ?
         fun getPctPhantoms() : Double {
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

        fun showSums(beans: List<ContestCountyPoolBean>) = buildString {
            val acSum = ContestTabulation(contestUA.contest.info())
            val cvrSum = ContestTabulation(contestUA.contest.info())
            beans.forEach { bean ->
                if (bean.isCvrs) cvrSum.sum(bean.contestTab)
                else acSum.sum(bean.contestTab)
            }
            appendLine("ac   = $acSum")
            appendLine("cvrs = $cvrSum")
        }

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

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CorlaAuditTable::class.java)
    }

    // all the counties for this contest
    class ContestCountyPoolBean(val pool: CountyPools, val contestTab: ContestTabulation, val info: ContestInfo, val isCvrs: Boolean) {
        val countyName: String
        val contestId: Int
        val vunderTab: Vunder
        var acBean: ContestCountyPoolBean? = null

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
        fun getDiffNvotes() : Int {
            return if (acBean == null) -1 else (acBean!!.nvotes - vunderTab.nvotes)
        }
        fun getPctDiffNvotes() : String {
            return if (acBean == null) "" else dfn((acBean!!.nvotes - vunderTab.nvotes) / acBean!!.nvotes.toDouble(), 4)
        }

        val ncards = contestTab.ncards()
        val source = if (isCvrs) "cvrs" else "auditcenter"

        fun show() = buildString {
            append("${nfn(contestId, 3)}, ${trunc(countyName, 12)},    ${nfn(contestTab.ncards(), 6)}, ") // ${trunc(votes, 25)}, ")
            append("    ${nfn(nvotes, 6)},   ${nfn(undervotes, 6)},    ${getDiffNvotes()}")
        }

        companion object {
            val header = " id,    countyName, tabNCards, styleNCards, diffNCards, nvotes, undervotes, diffVotes"

            @JvmStatic
            fun hiddenProperties() = "pool contestTab vunderTab info acBean cvrs"
        }
    }
}