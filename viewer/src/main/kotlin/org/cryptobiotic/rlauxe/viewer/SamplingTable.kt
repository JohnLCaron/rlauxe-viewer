/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.resampleAndSaveResults
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.CountyContestData
import org.cryptobiotic.rlauxe.persist.CountyData
import org.cryptobiotic.rlauxe.strata.Strata
import org.cryptobiotic.rlauxe.strata.calcCountyStrataWant
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.viewer.CorlaContestsTable.CorlaContestBean
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.prefs.BeanTable
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.util.*
import java.util.function.Function
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.math.max

class SamplingTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane?,
    infoWindow: IndependentWindow?,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {

    private val localTA = TextHistoryPane()
    private val localWindow  = IndependentWindow("Details", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(localTA))

    private val contestTable: BeanTable<CorlaContestBean>
    private val countyTable: BeanTable<CountyBean>
    private val countyContestTable: BeanTable<CountyContestBean>

    private val split1: JSplitPane
    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAuditRecord? = null
    private var lastAuditRound: AuditRound? = null

    var countyMap = emptyMap<String, CountyBean>()
    var totalBean : CountyBean? = null

    var contestMap = emptyMap<Int, ContestWithAssertions>()
    var contestRoundMap = emptyMap<Int, ContestRound>()
    var countyContestData = emptyList<CountyContestData>()

    private var config : Config? = null
    private var auditRiskLimit: Double = 0.0
    private var samplingChanged = false
    private var onlyShowInprogressContests = false

    init {
        localWindow.setBounds(prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(50, 50, 400, 40)) as Rectangle)

        contestTable = BeanTable<CorlaContestBean>(
            CorlaContestBean::class.java,
            prefs.node("corlaContestTable") as PreferencesExt?,
            false,
            "Contests",
            "Contests",
            null
        )
        contestTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        contestTable.addPopupOption(
            "Show Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: Any? -> showContest(bean as CorlaContestBean) }
        )

        countyTable = BeanTable(
            CountyBean::class.java, prefs.node("countyTable") as PreferencesExt?, false,
            "Counties", "County Audit", null
        )
        countyTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent? ->
            val county = countyTable.getSelectedBean()
            if (county != null) {
                setSelectedCounty(county)
            }
        })

        countyContestTable = BeanTable(
            CountyContestBean::class.java, prefs.node("countyContestTable") as PreferencesExt?, false,
            "CountyContest", "CountyContest", null
        )
        countyContestTable.addPopupOption(
            "Show Contest", countyContestTable.makeShowAction(
                infoTA, infoWindow,
                Function { bean: Any? -> showCountyContest((bean as CountyContestBean)) })
        )

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, countyTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 500))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, countyContestTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("SamplingTable init")
    }

    // may not be needed anymore
    fun setSelectedTab() {
        countMvrsByCounty() // TODO does this mean we dont need the button ??
    }

    override fun setFontSize(size: Float) {
        contestTable.setFontSize(size)
        countyContestTable.setFontSize(size)
        countyTable.setFontSize(size)
        localTA.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean( "onlyInProgress", false)
        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(emptyList<CorlaContestBean>())
        countyTable.setBeans(emptyList<CountyBean>())
        countyContestTable.setBeans(emptyList<CountyContestBean>())

        logger.debug("samplingTable setAuditRecord " + auditRecordLocation)

        try {
            val record = read(auditRecordLocation)
            if (record == null) return false
            if (record !is CountyAuditRecord) return false

            this.countyAudit = record
            this.config = countyAudit!!.config
            this.auditRiskLimit = config!!.riskLimit
            lastAuditRound = countyAudit!!.rounds.last()

            contestRoundMap = lastAuditRound!!.contestRounds.associateBy { it.id }

            /* val mvrManager = PersistedMvrManager(this.countyAudit!!, false)
            val countyPools = mvrManager.countyPools()
            if (countyPools == null) return false
            this.countyPools = countyPools
            val countyCvrPools = mvrManager.countyCvrPools()
            if (countyCvrPools != null)
                this.countyCvrPools = countyCvrPools.associateBy { it.countyName }

            this.infos = countyAudit!!.contests.associate { it.contest.info().id to it.contest.info() } */

            // if (statewide != null) statewide.nmvrsUniform = countUniformMvrs;

            loadAuditRecord()

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("setAuditRecord failed", e)
        }

        return true
    }

    fun loadAuditRecord() {
        //// contestTable
        val contestList = mutableListOf<CorlaContestBean>()
        countyAudit!!.contests.filter { !onlyShowInprogressContests || it.preAuditStatus == TestH0Status.InProgress }.forEach { cwa ->
            val cr = contestRoundMap.get(cwa.id)
            val bean = CorlaContestBean(cwa, cr) { b -> samplingChanged = b }
            contestList.add(bean)
        }
        // sort contests by payoff
        contestList.sortBy { it.getPayoff() }
        contestTable.setBeans(contestList)

        //// countyTable
        var countUniformMvrs = 0
        var statewide: CountyBean? = null

        val countyList = mutableListOf<CountyBean>()
        val _countyMap = mutableMapOf<String, CountyBean>()
        for (countyData in countyAudit!!.countyData) {
            val bean = CountyBean(countyData)
            if (bean.name == "Statewide") statewide = bean
            else countUniformMvrs += bean.corlaSampling // statewide now included in counties, so dont count twice

            countyList.add(bean)
            _countyMap.put(bean.name, bean)
        }
        countyMap = _countyMap

        val totalPopulation = countyAudit!!.countyData.filter { it.countyName != "Statewide"}.sumOf { it.npop }
        val countyTotal = CountyBean( CountyData("=Total", countUniformMvrs, totalPopulation))
        countyList.add(countyTotal)

        // sort counties by nmvrs
        countyList.sortByDescending { it.rlauxeSampling }
        countyTable.setBeans(countyList)
        this.totalBean = countyTotal

        // if (statewide != null) statewide.nmvrsUniform = countUniformMvrs;
        this.countyMap = countyList.associateBy { it.name }
        this.countyContestData = countyAudit!!.countyContestData
        countMvrsByCounty()

        this.contestMap =  countyAudit!!.contests.associateBy { it.id }
        this.contestRoundMap =  lastAuditRound!!.contestRounds.associateBy { it.id }

        val contestRoundMap: MutableMap<Int?, ContestRound?> = HashMap<Int?, ContestRound?>()
        for (contestRound in this.lastAuditRound!!.contestRounds) {
            contestRoundMap.put(contestRound.id, contestRound)
        }
    }

    fun setSelectedCounty(countyBean: CountyBean) {
        val beanList = mutableListOf<CountyContestBean>()
        for (countyContest in countyContestData) {
            val contestRound = contestRoundMap[countyContest.id]
            if (contestRound != null && contestRound.status == TestH0Status.InProgress) {
                if (countyContest.countyName == countyBean.name) {
                    val bean = CountyContestBean(countyContest, this.auditRiskLimit)
                    beanList.add(bean)
                    bean.countyPopulation = countyBean.population
                    bean.rlauxeNmvrs = countyBean.rlauxeSampling
                    bean.corlaNmvrs = countyBean.corlaSampling

                    bean.contestUA = contestMap[countyContest.id]
                    bean.contestRound = contestRound
                }
            }
        }
        countyContestTable.setBeans(beanList)
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

        val targetLessThanAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                includeTargetedPlusContestsLessThan(100) // TODO allow user to set maxMvrs
            }
        }
        BAMutil.setActionProperties(targetLessThanAction, "goal.png", "Include Targets and LessThan 100", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, targetLessThanAction)

        val targetPlusAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                includeTargetedPlus()
            }
        }
        BAMutil.setActionProperties(targetPlusAction, "goal.png", "Include Targets plus relaxed risks", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, targetPlusAction)

        val includeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(true)
            }
        }
        BAMutil.setActionProperties(includeAllAction, "add.png", "Include selected Contests", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, includeAllAction)

        val excludeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(false)
            }
        }
        BAMutil.setActionProperties(excludeAllAction, "exemption.png", "Exclude selected Contests", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, excludeAllAction)

        val reportAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                // localTA.setFont(localTA.getFont().deriveFont(fontSize))
                localTA.setText(reportRisks())
                localWindow.show()
            }
        }
        BAMutil.setActionProperties(reportAction, "count.png", "Show Risk Report", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, reportAction)

        logger.debug("SamplingTable.getActions")
    }

    fun reportRisks() = buildString {
        countMvrsByCounty()
        appendLine("rlauxe nmvrs = ${totalBean!!.rlauxeSampling}")
        appendLine(" corla nmvrs = ${totalBean!!.corlaSampling}")

        var countU = IntArray(4)
        var countS = IntArray(4)
        var countBeans = 0
        val risks = mutableListOf<Double>()
        val riskus = mutableListOf<Double>()
        contestTable.getBeans().forEach { bean ->
            countBeans++
            val maxRisk = bean.getMaxRisk()

            val risk = bean.getRisk()
            risks.add(risk)
            if (risk <= maxRisk) countS[0]++
            if (risk <= auditRiskLimit) countS[1]++
            if (risk <= .05) countS[2]++
            if (risk <= .10) countS[3]++

            val riskU = bean.getCorlaRisk()
            riskus.add(riskU)
            if (riskU <= maxRisk) countU[0]++
            if (riskU <= auditRiskLimit) countU[1]++
            if (riskU <= .05) countU[2]++
            if (riskU <= .10) countU[3]++
        }
        val pct = 100 * countS[0] / countBeans.toDouble()
        val pctU = 100 * countU[0] / countBeans.toDouble()
        appendLine("contests under maxRisk (style) = ${countS[0]} / $countBeans = ${nfn(pct.toInt(),2)}%")
        appendLine("contests under maxRisk (uniform) = ${countU[0]} / $countBeans = ${nfn(pctU.toInt(),2)}%")
        appendLine()

        val under = listOf("maxRisk", "${(100*auditRiskLimit).toInt()}%", "5%", "10%")
        appendLine("|               |  style  |  uniform |")
        appendLine("|---------------|---------|----------|")
        repeat (4) {
            appendLine("| under ${sfn(under[it], 7)} |   ${nfn(countS[it], 2)}   |    ${nfn(countU[it], 2)}   |")
        }

        appendLine()
        appendLine("style based sampling")
        appendLine("  risk cumulative distribution = ${showDeciles(risks, 2)}")

        appendLine()
        appendLine("uniform sampling")
        appendLine("  risk cumulative distribution = ${showDeciles(riskus, 2)}")
        appendLine()

        appendLine("    style sampling = ${showDecilesShort(risks, 2)}")
        appendLine("  uniform sampling = ${showDecilesShort(riskus, 2)}")
        appendLine()
    }

    // all include or exclude
    fun onlyProcess(onlyInProgress: Boolean) {
        this.onlyShowInprogressContests = onlyInProgress
        loadAuditRecord()
    }

    fun resample() {
        try {
            if (lastAuditRound == null) return

            // if uniform
            val contestsIncluded: List<ContestRound> = contestTable.getBeans().map { it.contestRound }.filterNotNull().filter { it.included }
            val countyStrata: List<Strata> = calcCountyStrataWant(contestsIncluded, auditRiskLimit)
            lastAuditRound!!.countyStrata = countyStrata
            logger.info(String.format("call resampleAndSaveResults wantFromPools=$countyStrata"))
            if (config!!.isUniform) {
                lastAuditRound!!.auditorMaxNewMvrs = 8245
                logger.info(String.format("call resampleAndSaveResults with auditorMaxNewMvrs = 8245"))
            } else {
                lastAuditRound!!.auditorMaxNewMvrs = null
            }

            resampleAndSaveResults(countyAudit!!, lastAuditRound!!)
            countMvrsByCounty()
            contestTable.refresh()
            countyTable.refresh()

            samplingChanged = false // perhaps not needed
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("AuditRoundsTable.resample failed", e)
        }
    }

    // all include or exclude
    fun setInclude(include: Boolean) {
        var selectedRows: List<CorlaContestBean> = contestTable.getSelectedBeans()
        if (selectedRows.isEmpty()) selectedRows = contestTable.getBeans()

        selectedRows.forEach { bean ->
            if (bean.contestRound != null) bean.contestRound!!.included = include
        }
        samplingChanged = true
        contestTable.refresh()
    }

    // set targeted to be included
    fun includeTargetedOnly() {
        for (bean in contestTable.getBeans()) {
            bean.setInclude(bean.targeted())
            bean.setMaxRisk(auditRiskLimit)
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
        val mvrCounts = countyAudit!!.countMvrsByCounty() // mvr counts (cardStyle sampling)
        for (countyData in mvrCounts.values) {
            val countyBean = countyMap.get(countyData.countyName)
            if (countyBean != null) {
                countyBean.rlauxeSampling = countyData.nmvrs
                countMvrs += countyData.nmvrs
            } else {
                logger.warn("cant find countyName '" + countyData.countyName + "'")
            }
        }
        if (totalBean != null) {
            totalBean!!.rlauxeSampling = countMvrs
        }
        countyTable.refresh()
    }

    fun showContest(bean: CorlaContestBean) = buildString {
        append(BeanProperties.showContestG(bean, contestTable.tableModel, bean.contestUA))
        val votes: Map<Int, Int> = bean.contestUA.contest.votes()!!
        val sortedVotes = votes.toList().sortedBy { it.first }.toMap()
        appendLine("sortedVotes   = $sortedVotes")
    }

    fun showCountyContest(countyContestBean: CountyContestBean) = buildString {
        append(countyContestTable.tableModel.showBean(countyContestBean, BeanProperties.contests))
        append(countyContestBean.contestUA!!.show())
    }

    override fun saveState() {
        contestTable.saveState(false)
        countyContestTable.saveState(false)
        countyTable.saveState(false)
        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
        prefs.putBeanObject(ViewerMain.INFO_BOUNDS, localWindow.getBounds())
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SamplingTable::class.java)
    }
}

////////////////////////////////////////////////////////////////
class CountyBean(countyData: CountyData) {
    val name: String
    val population: Int
    val corlaSampling: Int
    var rlauxeSampling: Int = 0

    init {
        this.name = countyData.countyName
        this.population = countyData.npop
        this.corlaSampling = countyData.nmvrs
    }

    fun getCorlaSamplePct() = 100 * corlaSampling / population.toDouble()
    fun getRlauxeSamplePct() = 100 * rlauxeSampling / population.toDouble()

    fun getDiff() = corlaSampling - rlauxeSampling
}

// data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)
class CountyContestBean(val countyContestData: CountyContestData, val auditRiskLimit: Double) {
    var countyPopulation = 1
    var rlauxeNmvrs = 0
    var corlaNmvrs = 0
    var contestUA: ContestWithAssertions? = null
    var contestRound: ContestRound? = null

    // kotlin adds getters, do have to remove the ones that shouldnt be in the table

    fun getCounty() = countyContestData.countyName
    fun getContestName() = countyContestData.contestName
    fun getContestId() = countyContestData.id
    fun getVoteDiff() = countyContestData.voteDiff
    fun getVotes() = countyContestData.votes.toString()

    fun getContestPopulation() = contestUA?.population() ?: 1

    fun getMargin() = countyContestData.voteDiff / getContestPopulation().toDouble()

    fun getNoerror(): Double {
        if (contestUA == null) return 0.0
        val cua = contestUA!!
        val noerror = cua.minNoerror()
        return noerror?: 0.0
    }

    fun getNCounties(): String {
        if (contestUA == null) return "N/A"
        val CORLAcounties = contestUA!!.contest.info().metadata.get("CORLAcounties")
        if (CORLAcounties == null) return "N/A"
        val toks: List<String> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
        if (toks.size == 1) return toks[0]
        return String.format("%02d", toks.size)
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

    //                 val haveMvrss: String = it.contest.info().metadata.get("CORLAhaveMvrs")!!
    //                val haveMvrs = haveMvrss.toInt()
    //                val estRisk = estRiskStandardBet(it.Npop, noerror, haveMvrs)
    //                plotData.add(PlotData(it.id, noerror, estRisk, cat))

    fun getTarget(): String {
        return if (targeted()) "YES" else ""
    }

    fun contained(): Boolean {
        if (contestUA == null) return false
        val CORLAcounties = contestUA!!.contest.info().metadata.get("CORLAcounties")
        if (CORLAcounties == null) return false
        val toks: List<String> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
        return  (toks.size == 1)
    }

    fun targeted(): Boolean {
        if (contestUA == null) return false
        val reason = contestUA!!.contest.info().metadata.get("CORLAauditReason")
        if (reason == null) return false
        // return reason == "county_wide_contest"
        return reason == "state_wide_contest" || reason == "county_wide_contest"
    }

    fun isInclude(): Boolean {
        return contestRound != null && contestRound!!.included
    }

    fun getMaxRisk(): String {
        return dfn(maxrisk(), 4)
    }

    fun maxrisk(): Double {
        if (contestRound == null) return 0.0
        val risk = contestRound!!.auditorWantRisk
        return if (risk != null) risk else auditRiskLimit
    }

    fun getEstRisk(): String {
        val haveMvrs: Int = havemvrs()
        return dfn(estRiskStandardBet(getContestPopulation(), getNoerror(), haveMvrs), 4)
    }

    fun getEstMvrs(): Int {
        if (contestUA == null) return 0
        return estSampleSizeStandardBet(contestUA!!.population(), getNoerror(), maxrisk())
    }

    fun getEstNewMvrs(): Int {
        val minAssertion = contestUA!!.minClcaAssertion()!!
        return estSampleSizeStandardBet(getCorlaVoteDiff(), getCorlaStrata(), minAssertion.upper, maxrisk())
    }

    fun getHaveMvrs(): String {
        return nfn(havemvrs(), -6)
    }

    fun havemvrs(): Int {
        return if (contestRound == null) 0 else contestRound!!.haveSampleSize
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "countyContestData npop nmvrsConsistent nmvrsUniform contestUA contestRound auditRiskLimit";
    }
}