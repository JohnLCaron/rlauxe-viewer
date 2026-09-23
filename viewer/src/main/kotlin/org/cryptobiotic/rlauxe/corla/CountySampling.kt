package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.resampleAndSaveResults
import org.cryptobiotic.rlauxe.beans.BeanProperties
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.CountyContestData
import org.cryptobiotic.rlauxe.strata.Strata
import org.cryptobiotic.rlauxe.strata.calcCountyStrataWant
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.viewer.CorlaContestsTable.CorlaContestBean
import org.cryptobiotic.rlauxe.viewer.ViewerMain
import org.cryptobiotic.rlauxe.viewer.ViewerPanelIF
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
import javax.swing.event.ListSelectionListener
import kotlin.text.drop
import kotlin.text.get

// TODO County specific sampling tool
class CountySampling(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane,
    infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {

    private val localTA = TextHistoryPane()
    private val localWindow  = IndependentWindow("Details", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(localTA))

    // private val contestTable: BeanTable<CorlaContestBean>
    private val countyTable: BeanTable<CountyBean>
    private val countyContestTable: BeanTable<CountyContestBean>

    private val split1: JSplitPane
    //private val split2: JSplitPane

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

        /*
        contestTable = BeanTable(
            CorlaContestBean::class.java,
            prefs.node("corlaContestTable") as PreferencesExt,
            false,
            "Contests",
            "Contests",
            null
        )
        contestTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        contestTable.addPopupOption(
            "Show Contest",
            contestTable.makeShowAction(infoTA, infoWindow) { bean: CorlaContestBean -> showContest(bean) }
        ) */

        countyTable = BeanTable(
            CountyBean::class.java, prefs.node("countyTable") as PreferencesExt, false,
            "Counties", "County Audit", null
        )
        countyTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent? ->
            val county = countyTable.getSelectedBean()
            if (county != null) {
                setSelectedCounty(county)
            }
        })

        countyContestTable = BeanTable(
            CountyContestBean::class.java, prefs.node("countyContestTable") as PreferencesExt, false,
            "All Contests in selected County", "CountyContest", null
        )
        countyContestTable.addPopupOption(
            "Show Row",
            countyContestTable.makeShowAction(infoTA, infoWindow)
                { bean: CountyContestBean -> showCountyContest((bean)) }
        )

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, countyContestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 500))
        //split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, countyContestTable)
        //split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountySampling init" }
    }

    // may not be needed anymore
    fun setSelectedTab() {
        countMvrsByCounty() // TODO does this mean we dont need the button ??
    }

    override fun setFontSize(size: Float) {
        // contestTable.setFontSize(size)
        countyContestTable.setFontSize(size)
        countyTable.setFontSize(size)
        localTA.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.onlyShowInprogressContests = prefs.getBoolean( "onlyInProgress", false)
        this.auditRecordLocation = auditRecordLocation
        // contestTable.setBeans(emptyList<CorlaContestBean>())
        countyTable.setBeans(emptyList<CountyBean>())
        countyContestTable.setBeans(emptyList<CountyContestBean>())

        logger.debug{"CountySampling setAuditRecord $auditRecordLocation"}

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
            logger.error(e) {"setAuditRecord failed"}
        }

        return true
    }

    fun loadAuditRecord() {
        /* contestTable
        val contestList = mutableListOf<CorlaContestBean>()
        countyAudit!!.contests.filter { !onlyShowInprogressContests || it.preAuditStatus == TestH0Status.InProgress }.forEach { cwa ->
            val cr = contestRoundMap.get(cwa.id)
            val bean = CorlaContestBean(cwa, cr) { b -> samplingChanged = b }
            contestList.add(bean)
        }
        // sort contests by payoff
        contestList.sortBy { it.getPayoff() }
        contestTable.setBeans(contestList) */

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

        val totalPopulation = countyAudit!!.countyData.filter { it.strataName != "Statewide"}.sumOf { it.population }
        val countyTotal = CountyBean( Strata("=Total", countUniformMvrs, totalPopulation))
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
                    val bean = CountyContestBean(contestRound, countyContest, this.auditRiskLimit)  { b -> samplingChanged = b }
                    beanList.add(bean)
                    bean.countyPopulation = countyBean.population
                    bean.rlauxeNmvrs = countyBean.rlauxeSampling
                    bean.corlaNmvrs = countyBean.corlaSampling
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
                includeImportant()
            }
        }
        BAMutil.setActionProperties(targetLessThanAction, "important.png", "Include Important Contests", false, 'T'.code, -1)
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
                // localTA.setFont(localTA.getFont().deriveFont(fontSize))
                localTA.setText(reportRisks())
                localWindow.show()
            }
        }
        BAMutil.setActionProperties(reportAction, "count.png", "Show Risk Report", false, 'T'.code, -1)
        BAMutil.addActionToContainer(container, reportAction)

        logger.debug { "CountySampling.getActions" }
    }

    fun reportRisks() = buildString {
        countMvrsByCounty()
        appendLine("rlauxe nmvrs = ${totalBean!!.rlauxeSampling}")
        appendLine(" corla nmvrs = ${totalBean!!.corlaSampling}")

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
        appendLine("contests under maxRisk (rlauxe) = ${countS[0]} / $countBeans = ${nfn(pct.toInt(),2)}%")
        appendLine("contests under maxRisk (corla) = ${countU[0]} / $countBeans = ${nfn(pctU.toInt(),2)}%")
        appendLine()

        val under = listOf("maxRisk", "5%", "10%", "20%", "30%")
        appendLine("|               | rlauxe  |   corla  |")
        appendLine("|---------------|---------|----------|")
        repeat (under.size) {
            appendLine("| under ${sfn(under[it], 7)} |   ${nfn(countS[it], 3)}   |    ${nfn(countU[it], 3)}   |")
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
            val contestsIncluded: List<ContestRound> = countyContestTable.beans.map { it.contestRound }.filter { it.included }
            val countyStrata: List<Strata> = calcCountyStrataWant(contestsIncluded, auditRiskLimit)
            lastAuditRound!!.countyStrata = countyStrata
            logger.debug{ "call resampleAndSaveResults wantFromPools=$countyStrata"}
            if (config!!.isUniform) {
                lastAuditRound!!.auditorMaxNewMvrs = 8245 // TODO WTF !!
                logger.debug {  "call resampleAndSaveResults with auditorMaxNewMvrs = 8245" }
            } else {
                lastAuditRound!!.auditorMaxNewMvrs = null
            }

            resampleAndSaveResults(countyAudit!!, lastAuditRound!!)
            countMvrsByCounty()
            countyContestTable.refresh()
            countyTable.refresh()

            samplingChanged = false // perhaps not needed
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) {"AuditRoundsTable.resample failed"}
        }
    }

    // all include or exclude
    fun setInclude(include: Boolean) {
        var selectedRows: List<CountyContestBean> = countyContestTable.getSelectedBeans()
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

    fun countMvrsByCounty() {
        var countMvrs = 0
        val mvrCounts = countyAudit!!.countMvrsByCounty() // mvr counts (cardStyle sampling)
        for (countyData in mvrCounts.values) {
            val countyBean = countyMap.get(countyData.strataName)
            if (countyBean != null) {
                countyBean.rlauxeSampling = countyData.nmvrs
                countMvrs += countyData.nmvrs
            } else {
                logger.warn { "cant find countyName '${countyData.strataName}'" }
            }
        }
        if (totalBean != null) {
            totalBean!!.rlauxeSampling = countMvrs
        }
        countyTable.refresh()
    }

    fun showContest(bean: CountyContestBean) = buildString {
        append(showContestWithDesc(bean, countyContestTable.tableModel, bean.contestUA))
        val votes: Map<Int, Int> = bean.contestUA.contest.votes()!!
        val sortedVotes = votes.toList().sortedBy { it.first }.toMap()
        appendLine("sortedVotes   = $sortedVotes")
    }

    fun showCountyContest(countyContestBean: CountyContestBean) = buildString {
        append(countyContestTable.tableModel.showBean(countyContestBean, BeanProperties.contests))
        append(countyContestBean.contestUA.show())
    }

    override fun saveState() {
        countyContestTable.saveState(false)
        countyTable.saveState(false)
        prefs.putInt("splitPos1", split1.getDividerLocation())
        // prefs.putInt("splitPos2", split2.getDividerLocation())
        prefs.putBeanObject(ViewerMain.INFO_BOUNDS, localWindow.getBounds())
    }

    companion object {
        private val logger = KotlinLogging.logger("CountySampling")
    }
}

////////////////////////////////////////////////////////////////
class CountyBean(countyData: Strata) {
    val name: String
    val population: Int
    val corlaSampling: Int
    var rlauxeSampling: Int = 0

    init {
        this.name = countyData.strataName
        this.population = countyData.population
        this.corlaSampling = countyData.nmvrs
    }

    fun getCorlaSamplePct() = 100 * corlaSampling / population.toDouble()
    fun getRlauxeSamplePct() = 100 * rlauxeSampling / population.toDouble()

    fun getDiff() = corlaSampling - rlauxeSampling
}

// data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)
class CountyContestBean(val contestRound: ContestRound, val countyContestData: CountyContestData, val auditRiskLimit: Double,
                        val sampleChanged: (Boolean) -> Any) {
    val contestUA: ContestWithAssertions = contestRound.contestUA
    var mvrLimit: Int = -1

    fun canedit(): Boolean {
        return true
    }

    fun isInclude() = contestRound != null && contestRound!!.included

    fun maxrisk(): Double {
        if (contestRound == null) return 0.0
        val risk = contestRound!!.auditorWantRisk
        return if (risk != null) risk else auditRiskLimit
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
    }

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
        val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
        if (CORLAcounties == null) return null
        val stripped = CORLAcounties.drop(1).dropLast(1)
        return stripped.split(",".toRegex()).dropLastWhile { it.isEmpty() }
    }

    fun getStatus() = Naming.status(contestUA.preAuditStatus)

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
    fun getCorlaVoteMargin(): Int {
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
        return estRiskStandardBet(getCorlaVoteMargin(), getCorlaStrata(), minAssertion.upper, haveMvrss.toInt())
    }

    //                 val haveMvrss: String = it.contest.info().metadata.get("CORLAhaveMvrs")!!
    //                val haveMvrs = haveMvrss.toInt()
    //                val estRisk = estRiskStandardBet(it.Npop, noerror, haveMvrs)
    //                plotData.add(PlotData(it.id, noerror, estRisk, cat))

    fun getTarget(): String {
        return if (targeted()) "YES" else ""
    }

    fun contained(): Boolean {
        val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
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
        if (contestUA == null) return 0
        return estSampleSizeStandardBet(contestUA!!.population(), getNoerror(), maxrisk())
    }

    fun getEstNewMvrs(): Int {
        val minAssertion = contestUA!!.minClcaAssertion()!!
        return estSampleSizeStandardBet(getCorlaVoteMargin(), getCorlaStrata(), minAssertion.upper, maxrisk())
    }

    fun getHaveMvrs() = havemvrs()

    fun havemvrs(): Int {
        return contestRound.haveSampleSize
    }

    companion object {
        var auditRiskLimit: Double = 0.0

        @JvmStatic
        fun editableProperties() = "include maxRisk"

        @JvmStatic
        fun hiddenProperties() = "countyContestData npop nmvrsConsistent nmvrsUniform contestUA contestRound auditRiskLimit sampleChanged";
    }
}