package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.beans.BeanProperties
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecordIF
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.CountyContestData
import org.cryptobiotic.rlauxe.strata.Strata
import org.cryptobiotic.rlauxe.viewer.AuditRecordViewerIF
import org.cryptobiotic.rlauxe.viewer.INFO_BOUNDS
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
class CvCounties(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane,
    infoWindow: IndependentWindow,
    fontSize: Float,
    val headerLabel: JLabel,
    val setCountyMvrs: (String, Int) -> Unit,
) : JPanel(), AuditRecordViewerIF {

    private val localTA = TextHistoryPane()
    private val localWindow  = IndependentWindow("Details", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(localTA))

    private val countyTable: BeanTable<CountyBean>
    val countySampling =  CvCountySampling(prefs, infoTA, infoWindow, fontSize, headerLabel, setCountyMvrs)
    private val countyContestTable = countySampling.samplingTable

    private val split1: JSplitPane
    //private val split2: JSplitPane

    // private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAuditRecord? = null
    private var lastAuditRound: AuditRound? = null

    // var countyMap = emptyMap<Int, ContestWithAssertions>()
    var contestMap = emptyMap<Int, ContestWithAssertions>()
    var contestRoundMap = emptyMap<Int, ContestRound>()
    var countyContestData = emptyList<CountyContestData>()
    var contestCounties =  mutableMapOf<String, MutableList<CountyContestData>>() // contestName -> List<CountyContestData>, for each county
    var totalBean : CountyBean? = null

    private var config : Config? = null
    private var auditRiskLimit: Double = 0.0
    private var samplingChanged = false
    private var onlyShowInprogressContests = false

    init {
        localWindow.setBounds(prefs.getBean(INFO_BOUNDS, Rectangle(50, 50, 400, 40)) as Rectangle)

        countyTable = BeanTable(
            CountyBean::class.java, prefs.node("countyTable") as PreferencesExt, false,
            "Counties", "County Audit", null
        )
        countyTable.addListSelectionListener {
            val county = countyTable.getSelectedBean()
            if (county != null) {
                setSelectedCounty(county)
            }
        }
        /*countyTable.addPopupOption(
            "Go to county sampling",
            countyTable.makeActionOnCurrentBean { bean: CountyBean? ->
                if (bean != null) sampleCounty(bean.name, false)
                return@makeActionOnCurrentBean true
            }
        )
        countyTable.addPopupOption(
            "Calculate county sample",
            countyTable.makeActionOnCurrentBean { bean: CountyBean? ->
                if (bean != null) sampleCounty(bean.name, true)
                return@makeActionOnCurrentBean true
            }
        )

        countyTable.addPopupOption(
            "Resample all counties",
            countyTable.makeActionOnCurrentBean { bean: CountyBean? ->
                if (bean != null) resampleAllCounties()
                return@makeActionOnCurrentBean true
            }
        )

        countyContestTable = BeanTable(
            CvContestBean::class.java, prefs.node("countyContestTable") as PreferencesExt, false,
            "Contests in selected County", "CountyContest", null
        )

        countyContestTable.addPopupOption(
            "Show Row",
            countyContestTable.makeShowAction(infoTA, infoWindow)
                { bean: CvContestBean -> showCountyContest((bean)) }
        ) */

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, countyContestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 500))
        //split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, countyContestTable)
        //split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyContests init" }
    }

    override fun setFontSize(size: Float) {
        // contestTable.setFontSize(size)
        countyContestTable.setFontSize(size)
        countyTable.setFontSize(size)
        localTA.setFontSize(size)
    }

    override fun setAuditRecord(auditRecord: AuditRecordIF): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean( "onlyInProgress", false)
        // this.auditRecordLocation = auditRecordLocation
        // contestTable.setBeans(emptyList<CorlaContestBean>())
        countyTable.setBeans(emptyList())
        countyContestTable.setBeans(emptyList())

        if (auditRecord !is CountyAuditRecord) {
            logger.debug{"${auditRecord.topdir} not a CountyAuditRecord"}
            return false
        }

        this.countyAudit = auditRecord
        this.config = countyAudit!!.config
        this.auditRiskLimit = config!!.riskLimit
        if (countyAudit!!.rounds.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No AuditRounds have been made")
            return false
        }

        lastAuditRound = countyAudit!!.rounds.last()
        contestRoundMap = lastAuditRound!!.contestRounds.associateBy { it.id }

        loadAuditRecord()
        return countySampling.setAuditRecord(auditRecord)
    }

    fun loadAuditRecord(): Boolean {
        // countyTable
        val countyList = mutableListOf<CountyBean>()
        val _countyMap = mutableMapOf<String, CountyBean>()
        for (countyData in countyAudit!!.countyData) {
            if (countyData.strataName != "Statewide") {
                val bean = CountyBean(countyData)
                countyList.add(bean)
                _countyMap.put(bean.name, bean)
            }
        }
        countyList.sortBy{ it.name }
        addTotal(countyList)

        // if (statewide != null) statewide.nmvrsUniform = countUniformMvrs;
        // this.countyMap = countyList.associateBy { it.name }
        this.countyContestData = countyAudit!!.countyContestData
        contestCounties =  mutableMapOf()
        for (countyContest in countyContestData) {
            val counties = contestCounties.getOrPut(countyContest.contestName) { mutableListOf() }
            counties.add(countyContest)
        }

        this.contestMap =  countyAudit!!.contests.associateBy { it.id }
        this.contestRoundMap =  lastAuditRound!!.contestRounds.associateBy { it.id }

        return true
    }

    fun addTotal(countyList: MutableList<CountyBean>) {
        var population = 0
        var corlaSampling = 0
        var rlauxeSampling = 0
        var totalDiff = 0
        println("nbeans = ${countyTable.beans.size}")
        countyTable.beans.forEach { bean ->
            if (bean.name != "=Total") {
                println(bean)
                population += bean.population
                corlaSampling += bean.corlaSampling
                rlauxeSampling += bean.rlauxeSampling
                totalDiff += bean.getDiff()
            }
        }

        val countyTotal = CountyBean( Strata("=Total", corlaSampling, population))
        countyTotal.rlauxeSampling = rlauxeSampling
        countyTotal.totalDiff = totalDiff
        //this.totalBean = countyTotal

        countyList.add(countyTotal)
        countyTable.setBeans(countyList)
    }

    fun setSelectedCounty(countyBean: CountyBean) {
        countySampling.setCounty(countyBean.name)
        countyContestTable.refresh()
    }

    fun setCountyNmvrs(county: String, nmvrs: Int) {
        val countyBean: CountyBean? = countyTable.beans.find { it.name == county }
        if (countyBean != null) countyBean.rlauxeSampling = nmvrs

        addTotal(countyTable.beans.filter{ it.name != "=Total"}.toMutableList())
        countyTable.refresh()
    }

    fun resampleAllCounties() {
        countyTable.beans.filter{ it.name != "=Total"}.forEach {
            countySampling.setCounty(it.name)
            countySampling.resample()
            countyTable.refresh()
        }
        addTotal(countyTable.beans.filter{ it.name != "=Total"}.toMutableList())
        countyTable.refresh()
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Actions


    // actions on right side of Audit record chooser
    fun getActions(container: JPanel) {
        val resampleAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(p0: ActionEvent?) {
                resampleAllCounties()
            }
        }
        BAMutil.setActionProperties(resampleAllAction, "hamster.png", "ResampleAll", false, 'S'.code, -1)
        BAMutil.addActionToContainer(container, resampleAllAction)
        countySampling.getActions(container)

        logger.debug { "CountyContests.getActions" }
    }

    // all include or exclude
    fun onlyProcess(onlyInProgress: Boolean) {
        this.onlyShowInprogressContests = onlyInProgress
        loadAuditRecord()
    }

    fun showContest(bean: CvContestBean) = buildString {
        append(showContestWithDesc(bean, countyContestTable.tableModel, bean.contestUA))
        val votes: Map<Int, Int> = bean.contestUA.contest.votes()!!
        val sortedVotes = votes.toList().sortedBy { it.first }.toMap()
        appendLine("sortedVotes   = $sortedVotes")
    }

    fun showCountyContest(countyContestBean: CvContestBean) = buildString {
        append(countyContestTable.tableModel.showBean(countyContestBean, BeanProperties.contests))
        append(countyContestBean.contestUA.show())
    }

    override fun saveState() {
        countyContestTable.saveState(false)
        countyTable.saveState(false)
        prefs.putInt("splitPos1", split1.getDividerLocation())
        // prefs.putInt("splitPos2", split2.getDividerLocation())
        prefs.putBeanObject(INFO_BOUNDS, localWindow.getBounds())
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyContests")
    }
}

////////////////////////////////////////////////////////////////
class CountyBean(countyData: Strata) {
    val name: String
    val population: Int
    val corlaSampling: Int
    var rlauxeSampling: Int = 0
    var totalDiff: Int = 0

    init {
        this.name = countyData.strataName
        this.population = countyData.population
        this.corlaSampling = countyData.nmvrs
    }

    fun getCorlaSamplePct() = 100 * corlaSampling / population.toDouble()
    fun getRlauxeSamplePct() = 100 * rlauxeSampling / population.toDouble()

    fun getDiff(): Int {
        return when {
            (totalDiff != 0) -> totalDiff
            (rlauxeSampling == 0) -> 0
            else -> corlaSampling - rlauxeSampling
        }
    }

    override fun toString(): String {
        return "CountyBean(name='$name', population=$population, corlaSampling=$corlaSampling, rlauxeSampling=$rlauxeSampling, totalDiff=$totalDiff)"
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "totalDiff"
    }
}

// data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)
class CountyContestBean(val contestRound: ContestRound, val counties: List<CountyContestData>, val auditRiskLimit: Double,
                        val sampleChanged: (Boolean) -> Any) {
    val contestUA: ContestWithAssertions = contestRound.contestUA
    /* var mvrLimit: Int = -1

    fun canedit(): Boolean {
        return true
    }

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
        return if (risk != null) risk else CorlaContestBean.auditRiskLimit
    }

    // TODO editable properties have to be primitive
    fun setMaxRisk(risk: Double) {
        if (contestRound == null) return
        contestRound!!.auditorWantRisk = risk
        contestRound!!.estMvrs = this.calcEstMvrs(risk)
        // this.setInclude(true)
        sampleChanged(true)
    } */

    fun calcEstMvrs(maxRisk: Double): Int {
        val minAssertion = contestUA.minClcaAssertion()
        if (minAssertion == null) return 0
        val noerror = minAssertion.noerror

        return estSampleSizeStandardBet(contestUA.population(), noerror, maxRisk)
    }

    fun getName() = contestUA.name
    fun getId() = contestUA.id
    fun getNc() = contestUA.Nc
    fun getPopulation() = contestUA.population()
    fun isInclude() = contestRound.included

    var countyPopulation = 1
    var rlauxeNmvrs = 0
    var corlaNmvrs = 0

    fun getVoteMargin(): Int {
        val minAssertion = contestUA.minAssertion() ?: return 0
        return contestUA.contest.marginInVotes(minAssertion.assorter)
    }
    // fun getVoteMargin() = countyContestData.voteDiff
    // fun getVotes() = countyContestData.votes.toString()
    // fun getMargin() = countyContestData.voteDiff / getContestPopulation().toDouble()

    fun getMargin(): Double{
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
        val CORLAcounties = contestUA.contest.info().metadata.get("CanonicalCounties") // TODO change to "CanonicalCounties"
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

    fun getStrataNmvrs(): Int {
        val haveMvrss = contestUA.contest.info().metadata.get("StrataNmvrs")
        if (haveMvrss == null) return -1
        return haveMvrss.toInt()
    }

    fun getCorlaStrata(): Int {
        if (contestUA == null) return -1
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

        // fun estRiskStandardBet(voteDiff: Int, Npop: Int, upper: Double, nsamples: Int, ): Double {
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
        return  (toks.size == 1)
    }

    fun targeted(): Boolean {
        val reason = contestUA.contest.info().metadata.get("CORLAauditReason")
        if (reason == null) return false
        // return reason == "county_wide_contest"
        return reason == "state_wide_contest" || reason == "county_wide_contest"
    }

    fun getNoerror(): Double {
        val noerror = contestUA.minNoerror()
        return noerror?: 0.0
    }

    fun getEstRisk(): Double {
        val haveMvrs = this.getHaveMvrs()
        return estRiskStandardBet(contestUA.population(), getNoerror(), haveMvrs)
    }

    fun getEstMvrs(): Int {
        return estSampleSizeStandardBet(contestUA.population(), getNoerror(), maxrisk())
    }

    fun getEstNewMvrs(): Int {
        val minAssertion = contestUA.minClcaAssertion()!!
        return estSampleSizeStandardBet(getCorlaVoteMargin(), getCorlaStrata(), minAssertion.upper, maxrisk())
    }

    fun maxrisk(): Double {
        val risk = contestRound.auditorWantRisk
        return if (risk != null) risk else auditRiskLimit
    }

    fun getHaveMvrs() = havemvrs()

    fun havemvrs(): Int {
        return contestRound.haveSampleSize
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "counties npop nmvrsConsistent nmvrsUniform contestUA contestRound auditRiskLimit sampleChanged"
    }
}