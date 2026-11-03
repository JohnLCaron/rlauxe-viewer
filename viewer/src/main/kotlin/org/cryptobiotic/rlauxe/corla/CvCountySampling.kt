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
import org.cryptobiotic.rlauxe.persist.AuditRecordIF
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.CountyContestData
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.viewer.AuditRecordViewerIF
import org.cryptobiotic.rlauxe.viewer.INFO_BOUNDS
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*
import kotlin.text.appendLine
import kotlin.text.drop

// TODO County specific sampling tool
// what if this is a JTable instead of a JPanel
class CvCountySampling(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane,
    infoWindow: IndependentWindow,
    fontSize: Float,
    val headerLabel: JLabel,
    val setCountyMvrs: (String, Int) -> Unit,
) : AuditRecordViewerIF {

    private val localTA = TextHistoryPane()
    private val localWindow = IndependentWindow("CountySampling Report", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(localTA))

    val samplingTable: BeanTable<CvContestBean>

    // private val split1: JSplitPane
    //private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAuditRecord? = null
    private var lastAuditRound: AuditRound? = null
    private var county: String = ""

    var countyMap = emptyMap<String, CountyBean>()
    // var totalBean: CountyBean? = null

    // var contestMap = emptyMap<Int, ContestWithAssertions>()
    var contestRoundMap = emptyMap<Int, ContestRound>()
    var countyContestData = emptyList<CountyContestData>()
    var contestCounties =  mutableMapOf<Int, MutableList<CountyContestData>>() // contestId -> List<CountyContestData>, for each county

    private var config: Config? = null
    private var auditRiskLimit: Double = 0.0
    private var samplingChanged = false
    private var onlyShowInprogressContests = false

    var wantNmvrs = emptyMap<Int, Int>()
    var meanNmvrs: Int? = null

    var dist = emptyList<Int>()

    init {
        localWindow.setBounds(prefs.getBean(INFO_BOUNDS, Rectangle(50, 50, 400, 40)) as Rectangle)

        samplingTable = BeanTable(
            CvContestBean::class.java, prefs.node("countyContestTable") as PreferencesExt, false,
            "Contests in selected County", "CountyContest", null
        )
        samplingTable.addPopupOption(
            "Show Row",
            samplingTable.makeShowAction(infoTA, infoWindow) { bean: CvContestBean -> showCountyContest((bean)) }
        )

        setFontSize(fontSize)

        // layout of tables
        // split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, countyContestTable)
        // split1.setDividerLocation(prefs.getInt("splitPos1", 500))
        //split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, countyContestTable)
        //split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        //setLayout(BorderLayout())
        //add(samplingTable, BorderLayout.CENTER)

        logger.debug { "CountySampling init" }
    }

    override fun setFontSize(size: Float) {
        // contestTable.setFontSize(size)
        samplingTable.setFontSize(size)
        localTA.setFontSize(size)
    }

    // TODO share the AuditRecord with CvContests, so changes to ContestRound affect both
    // So use functions, not values for those fields like include
    override fun setAuditRecord(auditRecord: AuditRecordIF): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean("onlyInProgress", false)
        this.auditRecordLocation = auditRecordLocation
        samplingTable.setBeans(emptyList())

        if (auditRecord !is CountyAuditRecord) return false
        logger.debug { "CountySampling set CountyAuditRecord from ${auditRecord.topdir}" }

        this.countyAudit = auditRecord
        this.config = countyAudit!!.config
        this.auditRiskLimit = config!!.riskLimit
        if (countyAudit!!.rounds.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No AuditRounds have been made")
            return false
        }

        lastAuditRound = countyAudit!!.rounds.last()
        // this.contestMap = countyAudit!!.contests.associateBy { it.id }
        this.contestRoundMap = lastAuditRound!!.contestRounds.associateBy { it.id } // ??

        this.countyContestData = countyAudit!!.countyContestData
        contestCounties =  mutableMapOf()
        for (countyContest in countyContestData) {
            val counties = contestCounties.getOrPut(countyContest.id) { mutableListOf() }
            counties.add(countyContest)
        }
        this.meanNmvrs = null
        this.county = ""
        setHeaderText()
        return true
    }

    fun setCounty(county: String) {
        this.county = county

        val beanList = mutableListOf<CvContestBean>()
        countyContestData.filter { it.countyName == county }.forEach {
            val contestRound = contestRoundMap[it.id]
            if (contestRound != null && contestRound.status == TestH0Status.InProgress) {
                val counties = contestCounties[contestRound.id] ?: emptyList()
                if (counties.find { it.countyName == county } != null) {
                    // class CvContestBean(var contestUA: ContestWithAssertions, var contestRound: ContestRound?, val sampleChanged: (Boolean) -> Any) {
                    val bean = CvContestBean(contestRound.contestUA, contestRound, counties, county) { b -> samplingChanged = b }
                    beanList.add(bean)
                    //bean.countyPopulation = countyBean.population
                    //bean.rlauxeNmvrs = countyBean.rlauxeSampling
                    //bean.corlaNmvrs = countyBean.corlaSampling
                }
            }
        }
        samplingTable.setBeans(beanList)
        logger.debug{ "CountySampling.setCounty $county to ${beanList.size} contests"}

    }

    fun setHeaderText() {
        val text = if (this.meanNmvrs == null) county else "$county nmvrs=${this.meanNmvrs!!}"
        headerLabel.setText(text)
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Actions

    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {

        val resampleAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                resample()
                //localTA.setText(reportRisks())
                //localWindow.show()
            }
        }
        BAMutil.setActionProperties(resampleAction, "ambition.png", "Resample", false, 'S'.code, -1)
        BAMutil.addActionToContainer(container, resampleAction)

        /* val targetAction: AbstractAction = object : AbstractAction() {
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
        BAMutil.addActionToContainer(container, targetPlusAction) */

        val includeSelectedAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(true)
            }
        }
        BAMutil.setActionProperties(includeSelectedAction, "add-cart.png", "Include selected Contests", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, includeSelectedAction)

        val excludeSelectedAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(false)
            }
        }
        BAMutil.setActionProperties(excludeSelectedAction, "remove-cart.png", "Exclude selected Contests", false, 'T'.code, -1)

        BAMutil.addActionToContainer(container, excludeSelectedAction)
        val reportAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                localTA.setText(reportRisks(county, wantNmvrs, dist, samplingTable.beans))
                localWindow.show()
            }
        }
        BAMutil.setActionProperties(reportAction, "count.png", "Show Sampling Report", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, reportAction)
    }

    fun resample() {
        try {
            if (countyAudit == null || lastAuditRound == null || county.isEmpty()) return
            logger.debug { "call resample for=$county" }

            // TODO save this state ??
            wantNmvrs = samplingTable.beans.filter { it.isInclude() }.map { Pair(it.getId(), it.getCountyMvrs())}.toMap()

            // TODO maxSamples, ntrials ??
            val qdist = sampleCountyCvrs(countyAudit!!, county, wantNmvrs, 10)
            if (qdist == null) return
            this.dist = qdist

            val ddist = dist.map { it.toDouble()}
            val welford = Welford()
            ddist.forEach { welford.update(it)}

            this.meanNmvrs = roundToClosest(welford.mean)
            setHeaderText()
            setCountyMvrs(county, this.meanNmvrs!!)
            logger.debug{"setCountyMvrs ${this.meanNmvrs}"}

            samplingTable.refresh()
            samplingChanged = false // perhaps not needed

        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "CountySampling.resample failed" }
        }
    }

    // all include or exclude
    fun setInclude(include: Boolean) {
        var selectedRows: List<CvContestBean> = samplingTable.getSelectedBeans()
        if (selectedRows.size < 2) selectedRows = samplingTable.beans // all

        selectedRows.forEach { bean ->
            if (bean.contestRound != null) bean.contestRound!!.included = include
        }
        samplingChanged = true
        samplingTable.refresh()
    }

    /*
    fun reportRisks() = buildString {
        logger.debug { "call reportRisks" }
        if (dist.isEmpty()) {
            appendLine("please run resample first")
            return@buildString
        }
        appendLine(county)
        appendLine("want: ${ wantNmvrs }")
        append("mapOf(")
        wantNmvrs.forEach { append("${it.key} to ${it.value},")}
        appendLine(")")
        appendLine()
        appendLine("asking for ${wantNmvrs.values.sum()} nmvrs over ${wantNmvrs.size} contests")
        appendLine()

        val ddist = dist.map { it.toDouble()}

        appendLine("Deciles of sample distribution = ${showDeciles(ddist, 0)}")
        val welford = Welford()
        ddist.forEach { welford.update(it)}
        appendLine()
        appendLine("This will require ${roundToClosest(welford.mean())} samples on average (+/- ${dfn(welford.stddev(), 1)} standard deviation)")
        appendLine("  assuming all mvs match their cvrs")
        appendLine()
        // println(calcDecilesFromInt(dist))
        // println("Probability distribution:")
        val pds = calcProbabilityMassFunction(dist, 10, show = false)
        pds.forEach { (key, probability) ->
            appendLine("  sample range [${nfn(key.first, 4)} to ${nfn(key.second, 4)}) has probability = ${dfn(probability, 2)}")
        }
        appendLine("------------------------------------------------------------------")
        appendLine()

        /* if (totalBean != null) {
            appendLine("rlauxe nmvrs = ${totalBean!!.rlauxeSampling}")
            appendLine(" corla nmvrs = ${totalBean!!.corlaSampling}")
        } */

        var countU = IntArray(5)
        var countS = IntArray(5)
        var countBeans = 0
        val risks = mutableListOf<Double>()
        val riskus = mutableListOf<Double>()
        samplingTable.beans.forEach { bean ->
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
        repeat(under.size) {
            appendLine("| under ${sfn(under[it], 7)} |   ${nfn(countS[it], 3)}   |    ${nfn(countU[it], 3)}   |")
        }

        /* appendLine()
        appendLine("style based sampling")
        appendLine("  risk cumulative distribution = ${showDeciles(risks, 2)}")

        appendLine()
        appendLine("uniform sampling")
        appendLine("  risk cumulative distribution = ${showDeciles(riskus, 2)}") */

        appendLine()
        appendLine("    style sampling = ${showDecilesShort(risks, 2)}")
        appendLine("  uniform sampling = ${showDecilesShort(riskus, 2)}")
        appendLine()
    } */

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

    fun showCountyContest(countyContestBean: CvContestBean) = buildString {
        appendLine(samplingTable.tableModel.showBean(countyContestBean, BeanProperties.contests))
        appendLine()
        append(countyContestBean.contestUA.show())
    }

    override fun saveState() {
        samplingTable.saveState(false)
        // countyTable.saveState(false)
        // prefs.putInt("splitPos1", split1.getDividerLocation())
        // prefs.putInt("splitPos2", split2.getDividerLocation())
        prefs.putBeanObject(INFO_BOUNDS, localWindow.getBounds())
    }

    companion object {
        private val logger = KotlinLogging.logger("CountySampling")
    }
}

// TODO could these contest tables all be a single Bean Type ??
// data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)
class CvCountySamplingBean(val county: String,
                           val contestRound: ContestRound,
                           val counties: List<CountyContestData>,
                           val auditRiskLimit: Double,
                           val sampleChanged: (Boolean) -> Any
) {
    val contestUA: ContestWithAssertions = contestRound.contestUA
    var mvrLimit: Int = -1

    fun canedit(): Boolean {
        return counties().size < 5
    }

    fun isInclude() = contestRound.included

    fun maxrisk(): Double {
        val risk = contestRound.auditorWantRisk
        return if (risk != null) risk else auditRiskLimit
    }

    fun getMaxRisk() = maxrisk()

    fun setInclude(include: Boolean) {
        if (!canedit()) return
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

    // TODO this is all we are using the cvrTab for !
    // fun getNCvrs() = cvrTab.ncards()
    // fun getNVotes() = cvrTab.nvotes()

    fun getNoerror(): Double {
        val noerror = contestUA.minNoerror()
        return noerror ?: 0.0
    }

    fun getEstRisk(): Double {
        if (!isInclude()) return 1.0
        val haveMvrs = this.getHaveMvrs()
        return estRiskStandardBet(getNpop(), getNoerror(), haveMvrs)
    }

    fun getEstMvrs(): Int {
        return estSampleSizeStandardBet(getNpop(), getNoerror(), maxrisk())
    }

    fun getCountyMvrs(): Int {
        if (!isInclude()) return 0

        val myCounty = counties.find{ it.countyName == this.county }!!
        val sumAll = counties.sumOf { it.nPop }

        val pct = myCounty.nPop/sumAll.toDouble()
        return if (sumAll == 0) 0 else roundToClosest( getEstMvrs() * pct)
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
        if (counties.isEmpty()) return "N/A"
        if (counties.size == 1) return counties[0]
        return String.format("%02d", counties.size)
    }

    fun counties(): List<String> {
        val CORLAcounties = contestUA.contest.info().metadata.get("CanonicalCounties")
        if (CORLAcounties == null) return emptyList()
        val stripped = CORLAcounties.drop(1).dropLast(1)
        return stripped.split(",".toRegex()).dropLastWhile { it.isEmpty() }
    }

    fun getStatus() = Naming.status(contestUA.preAuditStatus)

    fun getCorlaEstMvrs(): Int {
        // if (!targeted()) return -1
        val CORLAsample = contestUA.contest.info().metadata.get("CORLAestMvrs")
        if (CORLAsample == null) return -1
        return CORLAsample.toInt()
    }

    fun getStrataNmvrs(): Int {
        val haveMvrss = contestUA.contest.info().metadata.get("StrataNmvrs")
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
        val haveMvrss: String? = contest.info().metadata.get("StrataNmvrs")
        if (haveMvrss == null) return 1.0
        val minAssertion: Assertion? = cua.minAssertion()
        if (minAssertion == null) return 1.0
        return estRiskStandardBet(getCorlaVoteMargin(), getCorlaStrata(), minAssertion.upper, haveMvrss.toInt())
    }

    fun getTarget(): String {
        val reason = contestUA.contest.info().metadata.get("CORLAauditReason") ?: return ""
        if (reason == "state_wide_contest") return "STATE"
        if (reason == "county_wide_contest") return "COUNTY"
        return ""
    }

    fun contained(): Boolean {
        val CORLAcounties = contestUA.contest.info().metadata.get("CanonicalCounties")
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
            "tab countyContestData nmvrsConsistent nmvrsUniform contestUA contestRound auditRiskLimit sampleChanged counties";
    }
}

fun reportRisks(who: String, wantNmvrs: Map<Int, Int>, dist: List<Int>, beans: List<CvContestBean>) = buildString {
    if (dist.isEmpty()) {
        appendLine("please run resample first")
        return@buildString
    }
    appendLine(who)
    if (who != "Election") {
        appendLine("want: ${wantNmvrs}")
        append("mapOf(")
        wantNmvrs.forEach { append("${it.key} to ${it.value},") }
        appendLine(")")
    }
    appendLine()
    appendLine("asking for ${wantNmvrs.values.sum()} nmvrs over ${wantNmvrs.size} contests")
    appendLine()

    val ddist = dist.map { it.toDouble()}

    appendLine("Deciles of sample distribution = ${showDeciles(ddist, 0)}")
    val welford = Welford()
    ddist.forEach { welford.update(it)}
    appendLine()
    appendLine("This will require ${roundToClosest(welford.mean())} samples on average (+/- ${dfn(welford.stddev(), 1)} standard deviation)")
    appendLine("  assuming all mvs match their cvrs")
    appendLine()
    // println(calcDecilesFromInt(dist))
    // println("Probability distribution:")
    val pds = calcProbabilityMassFunction(dist, 10, show = false)
    pds.forEach { (key, probability) ->
        appendLine("  sample range [${nfn(key.first, 4)} to ${nfn(key.second, 4)}) has probability = ${dfn(probability, 2)}")
    }
    appendLine("------------------------------------------------------------------")
    appendLine()

    /* if (totalBean != null) {
        appendLine("rlauxe nmvrs = ${totalBean!!.rlauxeSampling}")
        appendLine(" corla nmvrs = ${totalBean!!.corlaSampling}")
    } */

    var countU = IntArray(5)
    var countS = IntArray(5)
    var countBeans = 0
    val risks = mutableListOf<Double>()
    val riskus = mutableListOf<Double>()
    beans.forEach { bean ->
        countBeans++
        val maxRisk = bean.getMaxRisk()

        val risk = bean.getEstRisk()
        risks.add(risk)
        if (bean.isInclude()) countS[0]++  // TODO naive
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
    repeat(under.size) {
        appendLine("| under ${sfn(under[it], 7)} |   ${nfn(countS[it], 3)}   |    ${nfn(countU[it], 3)}   |")
    }

    /* appendLine()
    appendLine("style based sampling")
    appendLine("  risk cumulative distribution = ${showDeciles(risks, 2)}")

    appendLine()
    appendLine("uniform sampling")
    appendLine("  risk cumulative distribution = ${showDeciles(riskus, 2)}") */

    appendLine()
    appendLine("    style sampling = ${showDecilesShort(risks, 2)}")
    appendLine("  uniform sampling = ${showDecilesShort(riskus, 2)}")
    appendLine()
}