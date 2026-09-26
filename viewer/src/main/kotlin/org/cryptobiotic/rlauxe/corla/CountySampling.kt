package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.beans.BeanProperties
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.corlaCounty.sampleCountyCvrs
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.CountyContestData
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.viewer.ViewerMain
import org.cryptobiotic.rlauxe.viewer.ViewerPanelIF
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*
import kotlin.text.drop

// TODO County specific sampling tool
class CountySampling(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane,
    infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {

    private val localTA = TextHistoryPane()
    private val localWindow = IndependentWindow("CountySampling Report", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(localTA))

    private val countyContestTable: BeanTable<CountySamplingBean>

    // private val split1: JSplitPane
    //private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAuditRecord? = null
    private var lastAuditRound: AuditRound? = null
    private var county: String = ""

    var countyMap = emptyMap<String, CountyBean>()
    var totalBean: CountyBean? = null

    var contestMap = emptyMap<Int, ContestWithAssertions>()
    var contestRoundMap = emptyMap<Int, ContestRound>()
    var countyContestData = emptyList<CountyContestData>()
    var mvrManager: PersistedMvrManager? = null

    private var config: Config? = null
    private var auditRiskLimit: Double = 0.0
    private var samplingChanged = false
    private var onlyShowInprogressContests = false

    var wantNmvrs = emptyMap<Int, Int>()

    // fun sampleCountyCvrs(wantNmvrs: Map<Int, Int>, cvrs: List<AuditableCard>, maxSamples: Int, ntrials: Int): List<Int> {
    var dist = emptyList<Int>()

    init {
        localWindow.setBounds(prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(50, 50, 400, 40)) as Rectangle)

        countyContestTable = BeanTable(
            CountySamplingBean::class.java, prefs.node("countyContestTable") as PreferencesExt, false,
            "All Contests in selected County", "CountyContest", null
        )
        countyContestTable.addPopupOption(
            "Show Row",
            countyContestTable.makeShowAction(infoTA, infoWindow) { bean: CountySamplingBean -> showCountyContest((bean)) }
        )

        setFontSize(fontSize)

        // layout of tables
        // split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, countyContestTable)
        // split1.setDividerLocation(prefs.getInt("splitPos1", 500))
        //split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, countyContestTable)
        //split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(countyContestTable, BorderLayout.CENTER)

        logger.debug { "CountySampling init" }
    }

    override fun setFontSize(size: Float) {
        // contestTable.setFontSize(size)
        countyContestTable.setFontSize(size)
        localTA.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean("onlyInProgress", false)
        this.auditRecordLocation = auditRecordLocation
        // contestTable.setBeans(emptyList<CorlaContestBean>())
        countyContestTable.setBeans(emptyList<CountySamplingBean>())

        logger.debug { "CountySampling setAuditRecord $auditRecordLocation" }

        try {
            val record = read(auditRecordLocation)
            if (record == null) return false
            if (record !is CountyAuditRecord) return false

            this.countyAudit = record
            this.config = countyAudit!!.config
            this.auditRiskLimit = config!!.riskLimit
            if (countyAudit!!.rounds.isEmpty()) {
                JOptionPane.showMessageDialog(null, "No AuditRounds have been made")
                return false
            }

            lastAuditRound = countyAudit!!.rounds.last()
            this.contestMap = countyAudit!!.contests.associateBy { it.id }
            this.contestRoundMap = lastAuditRound!!.contestRounds.associateBy { it.id } // ??

            this.mvrManager = PersistedMvrManager(this.countyAudit!!, false)

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "setAuditRecord failed" }
        }

        return true
    }

    fun setCounty(county: String) {
        this.county = county
        val cvrTabs: Map<Int, ContestTabulation> = this.countyAudit?.readCountyCvrsAndTabulate(county) ?: emptyMap()

        val beanList = mutableListOf<CountySamplingBean>()
        cvrTabs.forEach { (contestId, contestTab) ->
            val contestRound = contestRoundMap[contestId]
            if (contestRound != null && contestRound.status == TestH0Status.InProgress) {
                val bean = CountySamplingBean(contestTab, contestRound, config?.riskLimit ?: .03) { }
                beanList.add(bean)
            }
        }
        countyContestTable.setBeans(beanList)
    }


    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Actions

    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {

        val onlyProcessAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val state = getValue(BAMutil.STATE)
                val onlyProcess = state as Boolean
                onlyProcess(onlyProcess)
                prefs.putBoolean("onlyInProgress", onlyProcess)
            }
        }
        val savedState = prefs.getBoolean("onlyInProgress", false)
        onlyProcessAction.putValue(BAMutil.STATE, savedState);
        BAMutil.setActionProperties(
            onlyProcessAction,
            "sunrise-icon.png",
            "Only show Contests InProgress",
            true,
            'S'.code,
            -1
        )
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
                includeImportant()
            }
        }
        BAMutil.setActionProperties(
            targetLessThanAction,
            "important.png",
            "Include Important Contests",
            false,
            'T'.code,
            -1
        )
        BAMutil.addActionToContainer(container, targetLessThanAction)

        val targetPlusAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setRisk()
            }
        }
        BAMutil.setActionProperties(targetPlusAction, "risk.png", "Set variable risk", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, targetPlusAction)

        val includeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(true)
            }
        }
        BAMutil.setActionProperties(includeAllAction, "add-cart.png", "Include selected Contests", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, includeAllAction)

        val excludeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(false)
            }
        }
        BAMutil.setActionProperties(excludeAllAction, "remove-cart.png", "Exclude selected Contests", false, 'T'.code, -1)

        BAMutil.addActionToContainer(container, excludeAllAction)
        val reportAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                localTA.setText(reportRisks())
                localWindow.show()
            }
        }
        BAMutil.setActionProperties(reportAction, "count.png", "Show Sampling Report", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, reportAction)

        logger.debug { "CountyContests.getActions" }
    }

    fun reportRisks() = buildString {
        logger.debug { "call reportRisks" }
        appendLine(county)
        appendLine("wantNmvrs = ${wantNmvrs.toSortedMap()}")
        appendLine("sumWantNmvrs = ${wantNmvrs.values.sum()}")
        appendLine("dist = ${calcDecilesFromInt(dist)}")

        if (totalBean != null) {
            appendLine("rlauxe nmvrs = ${totalBean!!.rlauxeSampling}")
            appendLine(" corla nmvrs = ${totalBean!!.corlaSampling}")
        }

        var countU = IntArray(5)
        var countS = IntArray(5)
        var countBeans = 0
        val risks = mutableListOf<Double>()
        val riskus = mutableListOf<Double>()
        countyContestTable.beans.forEach { bean ->
            countBeans++
            val maxRisk = bean.getMaxRisk()

            val risk = bean.getEstRisk()
            risks.add(risk)
            if (risk <= maxRisk) countS[0]++
            if (risk <= .05) countS[1]++
            if (risk <= .10) countS[2]++
            if (risk <= .20) countS[3]++
            if (risk <= .30) countS[4]++

            val riskU = bean.getCorlaRisk()
            riskus.add(riskU)
            if (riskU <= maxRisk) countU[0]++
            if (riskU <= .05) countU[1]++
            if (riskU <= .10) countU[2]++
            if (riskU <= .20) countU[3]++
            if (riskU <= .30) countU[4]++
        }
        val pct = 100 * countS[0] / countBeans.toDouble()
        val pctU = 100 * countU[0] / countBeans.toDouble()
        appendLine("contests under maxRisk (rlauxe) = ${countS[0]} / $countBeans = ${nfn(pct.toInt(), 2)}%")
        appendLine("contests under maxRisk (corla) = ${countU[0]} / $countBeans = ${nfn(pctU.toInt(), 2)}%")
        appendLine()

        val under = listOf("maxRisk", "5%", "10%", "20%", "30%")
        appendLine("|               | rlauxe  |   corla  |")
        appendLine("|---------------|---------|----------|")
        //repeat(under.size) {
       //     appendLine("| under ${sfn(under[it], 7)} |         val lastWinningScore = winnerScores.last()\n" +
        //            "        val lastWinner = parties.find { it.id == lastWinningScore.candidate }!!population  ${nfn(countS[it], 3)}   |    ${nfn(countU[it], 3)}   |")
        //}

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

    fun onlyProcess(onlyInProgress: Boolean) {
        this.onlyShowInprogressContests = onlyInProgress
// loadAuditRecord()
    }

    fun resample() {
        try {
            if (countyAudit == null || lastAuditRound == null) return
            val useAudit = countyAudit!!
            val useRound = lastAuditRound!!

            /*
            val contestsIncluded = countyContestTable.beans.map { it.contestRound }.filter { it.included }
            if (config!!.isUniform) {
                val countyStrata: List<Strata> = calcCountyStrataWant(contestsIncluded, auditRiskLimit)
                useRound.countyStrata = countyStrata
                logger.debug { "call resampleAndSaveResults wantFromPools=$countyStrata" }
                useRound.auditorMaxNewMvrs = 8245 // TODO WTF !!
                logger.debug { "call resampleAndSaveResults with auditorMaxNewMvrs = 8245" }

            } else {
                useRound.auditorMaxNewMvrs = null
            } */

            logger.debug { "call sampleCountyCvrs for=$county" }

            wantNmvrs = countyContestTable.beans.filter { it.isInclude() }.map { Pair(it.getId(), it.getCountyMvrs())}.toMap()

            // fun sampleCountyCvrs(wantNmvrs: Map<Int, Int>, cvrs: List<AuditableCard>, maxSamples: Int, ntrials: Int): List<Int> {
            dist = sampleCountyCvrs(countyAudit!!, county, wantNmvrs, config?.maxSamples?: 10_000, 10)

            val sampleReport = buildString {
                appendLine(county)
                appendLine("wantNmvrs = ${wantNmvrs.toSortedMap()}")
                appendLine("sumWantNmvrs = ${wantNmvrs.values.sum()}")
                appendLine("dist = ${calcDecilesFromInt(dist)}")
            }

            localTA.setText(sampleReport)
            wantNmvrs
            countyContestTable.refresh()
            samplingChanged = false // perhaps not needed

        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "CountySampling.resample failed" }
        }
    }

    // all include or exclude
    fun setInclude(include: Boolean) {
        var selectedRows: List<CountySamplingBean> = countyContestTable.getSelectedBeans()
        if (selectedRows.size < 2) selectedRows = countyContestTable.beans // all

        selectedRows.forEach { bean ->
            if (bean.contestRound != null) bean.contestRound!!.included = include
        }
        samplingChanged = true
        countyContestTable.refresh()
    }

    // set targeted to be included
    fun includeTargetedOnly() {
        for (bean in countyContestTable.beans) {
            bean.setInclude(bean.targeted())
            bean.setMaxRisk(auditRiskLimit)
        }
        samplingChanged = true
        countyContestTable.refresh()
    }

    fun setRisk() {
        for (bean in countyContestTable.beans) {
            if (bean.getStatus() != TestH0Status.InProgress.name) continue
            if (bean.contestRound == null) continue

            if (bean.getEstMvrs() >= 250) bean.setMaxRisk(.20)
            else if (bean.getEstMvrs() >= 150) bean.setMaxRisk(.10)
            else if (bean.getEstMvrs() >= 50) bean.setMaxRisk(.05)
            else bean.setMaxRisk(auditRiskLimit)
        }
        samplingChanged = true
        countyContestTable.refresh()
    }

    fun includeImportant(): Boolean {
        for (bean in countyContestTable.beans) {
            if ((bean.counties()?.size ?: 0) > 1) bean.setInclude(true)
            if (bean.getName().startsWith("Representative to the")) bean.setInclude(true)
            if (bean.getName().startsWith("State")) bean.setInclude(true)
        }

        samplingChanged = true
        countyContestTable.refresh()
        return true
    }

    /*
    fun showContest(bean: CountySamplingBean) = buildString {
        append( showContestWithDesc(bean, countyContestTable.tableModel, bean.contestUA))
        appendLine()
        val votes: Map<Int, Int> = bean.contestUA.contest.votes()!!
        val sortedVotes = votes.toList().sortedBy { it.first }.toMap()
        appendLine("sortedVotes   = $sortedVotes")
        appendLine()

        // you would need ContestTabAllCounties to do this
        val showSums = buildString {
            var acNcards = 0
            var cvrNcards = 0
            var acNvotes = 0
            var cvrNvotes = 0
            var acNu = 0
            var cvrNu = 0
            val acSum = ContestTabulation(bean.contestUA.contest.info())
            val cvrSum = ContestTabulation(bean.contestUA.contest.info())

            appendLine("         county auditcenter   cvrs")
            appendLine("                ncards nvotes ncards nvotes ")
            countyContestTable.beans.forEach { bean ->
                val tab = bean.tab
                cvrSum.sum(tab)
                cvrNcards += tab.ncards()
                cvrNvotes += tab.nvotes()
                cvrNu += tab.undervotes
                appendLine("${trunc(bean.countyName, 15)} ${nfn(bean.acBean!!.ncards, 6)} ${nfn(bean.acBean!!.nvotes, 6)} ${nfn(bean.ncards, 6)} ${nfn(bean.nvotes, 6)} ")
            }
            appendLine("${trunc("Total", 15)} ${nfn(acNcards, 6)} ${nfn(acNvotes, 6)} ${nfn(cvrNcards, 6)} ${nfn(cvrNvotes, 6)} ")

            appendLine()
            appendLine("ac   = $acSum")
            appendLine("cvrs = $cvrSum")
            appendLine("acNvotes = $acNvotes, cvrNvotes = $cvrNvotes diff = ${acNvotes - cvrNvotes}")
            appendLine("acNu = $acNu, cvrNu = $cvrNu diff = ${acNu - cvrNu}")
        }
        append(showSums)
    } */

    fun showCountyContest(countyContestBean: CountySamplingBean) = buildString {
        appendLine(countyContestTable.tableModel.showBean(countyContestBean, BeanProperties.contests))
        appendLine()
        append(countyContestBean.contestUA.show())
    }

    override fun saveState() {
        countyContestTable.saveState(false)
        // countyTable.saveState(false)
        // prefs.putInt("splitPos1", split1.getDividerLocation())
        // prefs.putInt("splitPos2", split2.getDividerLocation())
        prefs.putBeanObject(ViewerMain.INFO_BOUNDS, localWindow.getBounds())
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyContests")
    }
}

// data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)
class CountySamplingBean(val tab: ContestTabulation, val contestRound: ContestRound, val auditRiskLimit: Double,
    val sampleChanged: (Boolean) -> Any
) {
    val contestUA: ContestWithAssertions = contestRound.contestUA
    var mvrLimit: Int = -1

    fun canedit(): Boolean {
        return true
    }

    fun isInclude() = contestRound.included

    fun maxrisk(): Double {
        val risk = contestRound.auditorWantRisk
        return if (risk != null) risk else auditRiskLimit
    }

    fun getMaxRisk() = maxrisk()

    fun setInclude(include: Boolean) {
        val oldState = contestRound.included
        if (oldState != include) {
            contestRound.included = include
            sampleChanged(true)
        }
    }

    // TODO editable properties have to be primitive
    fun setMaxRisk(risk: Double) {
        contestRound.auditorWantRisk = risk
        contestRound.estMvrs = this.calcEstMvrs(risk)
        sampleChanged(true)
    }

    fun calcEstMvrs(maxRisk: Double): Int {
        val minAssertion = contestUA?.minClcaAssertion()
        if (minAssertion == null) return 0
        val noerror = minAssertion.noerror

        return estSampleSizeStandardBet(getNpop(), noerror, maxRisk)
    }

    fun getName() = contestUA.name
    fun getId() = contestUA.id
    fun getNc() = contestUA.Nc
    fun getNpop() = contestUA.Npop

    fun getNCvrs() = tab.ncards()
    fun getNVotes() = tab.nvotes()

    fun getNoerror(): Double {
        val noerror = contestUA.minNoerror()
        return noerror ?: 0.0
    }

    fun getEstRisk(): Double {
        val haveMvrs = this.getHaveMvrs()
        return estRiskStandardBet(getNpop(), getNoerror(), haveMvrs)
    }

    fun getEstMvrs(): Int {
        return estSampleSizeStandardBet(getNpop(), getNoerror(), maxrisk())
    }

    fun getCountyMvrs(): Int {
        return if (getNpop() == 0) 0 else roundToClosest( getEstMvrs() * (getNCvrs()/getNpop().toDouble()))
    }

    fun getHaveMvrs() = havemvrs()

    fun havemvrs(): Int {
        return contestRound.haveSampleSize
    }

    fun getVoteMargin(): Int {
        val minAssertion = contestUA.minAssertion() ?: return 0
        return contestUA.contest.marginInVotes(minAssertion.assorter)
    }

    fun getMargin(): Double {
        val margin = contestUA.minMargin()
        return if (margin == null) 0.0 else margin
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

    fun getStatus() = Naming.status(contestUA.preAuditStatus)

    fun getCorlaEstMvrs(): Int {
        if (!targeted()) return -1
        val CORLAsample = contestUA.contest.info().metadata.get("CORLAsample")
        if (CORLAsample == null) return -1
        return CORLAsample.toInt()
    }

    fun getCorlaHaveMvrs(): Int {
        val haveMvrss = contestUA.contest.info().metadata.get("CORLAhaveMvrs")
        if (haveMvrss == null) return -1
        return haveMvrss.toInt()
    }

    fun getCorlaStrata(): Int {
        val haveMvrss = contestUA.contest.info().metadata.get("CORLAstrataNcards")
        if (haveMvrss == null) return -1
        return haveMvrss.toInt()
    }

    // same as corlaSampling plot
    fun getCorlaVoteMargin(): Int {
        val cua = contestUA
        val contest = cua.contest
        val minMargin: String? = contest.info().metadata.get("CORLAmarginInVotes")
        if (minMargin == null) return -1
        return minMargin.toInt()
    }

    // same as corlaSampling plot
    fun getCorlaRisk(): Double {
        val cua = contestUA
        val contest = cua.contest
        val haveMvrss: String? = contest.info().metadata.get("CORLAhaveMvrs")
        if (haveMvrss == null) return 1.0
        val minAssertion: Assertion? = cua.minAssertion()
        if (minAssertion == null) return 1.0
        return estRiskStandardBet(getCorlaVoteMargin(), getCorlaStrata(), minAssertion.upper, haveMvrss.toInt())
    }

    fun getTarget(): String {
        return if (targeted()) "YES" else ""
    }

    fun contained(): Boolean {
        val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
        if (CORLAcounties == null) return false
        val toks: List<String> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
        return (toks.size == 1)
    }

    fun targeted(): Boolean {
        val reason = contestUA.contest.info().metadata.get("CORLAauditReason")
        if (reason == null) return false
        return reason == "state_wide_contest" || reason == "county_wide_contest"
    }

    companion object {
        var auditRiskLimit: Double = 0.0

        @JvmStatic
        fun editableProperties() = "include maxRisk"

        @JvmStatic
        fun hiddenProperties() =
            "tab countyContestData npop nmvrsConsistent nmvrsUniform contestUA contestRound auditRiskLimit sampleChanged";
    }
}