/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.CountyContestData
import org.cryptobiotic.rlauxe.persist.CountyData
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.prefs.BeanTable
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class CountyTable(
    private val prefs: PreferencesExt,
    infoTA: TextHistoryPane?,
    infoWindow: IndependentWindow?,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {
    private val countyTable: BeanTable<CountyBean>
    private val contestTable: BeanTable<CountyContestBean>

    private val split2: JSplitPane

    private var auditRecordLocation: String? = "none"
    private var countyAudit: CountyAuditRecord? = null
    // private var config: Config? = null

    var countyMap = emptyMap<String, CountyBean>()
    var totalBean : CountyBean? = null

    var contestMap = emptyMap<Int, ContestWithAssertions>()
    var contestRoundMap = emptyMap<Int, ContestRound>()
    var countyContestData = emptyList<CountyContestData>()
    var auditRiskLimit = .03

    init {
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

        contestTable = BeanTable(
            CountyContestBean::class.java, prefs.node("countyContestTable") as PreferencesExt?, false,
            "CountyContest", "CountyContest", null
        )
        contestTable.addPopupOption(
            "Show Contest", contestTable.makeShowAction(
                infoTA, infoWindow,
                Function { bean: Any? -> showContest((bean as CountyContestBean)) })
        )

        setFontSize(fontSize)

        // layout of tables
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, contestTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 200))

        // auditRoundTable not used for now
        setLayout(BorderLayout())
        add(split2, BorderLayout.CENTER)

        logger.debug("corlaPanel init")
    }

    fun setSelectedTab() {
        countMvrsByCounty() // TODO does this mean we dont need the button ??
    }

    fun countMvrsByCounty() {
        var countMvrs = 0
        val mvrCounts = countyAudit!!.countMvrsByCounty() // mvr counts (cardStyle sampling)
        for (countyData in mvrCounts.values) {
            val countyBean = countyMap.get(countyData.countyName)
            if (countyBean != null) {
                countyBean.nmvrsCardStyleSampling = countyData.nmvrs
                countMvrs += countyData.nmvrs
            } else {
                logger.warn("cant find countyName '" + countyData.countyName + "'")
            }
        }
        if (totalBean != null) {
            totalBean!!.nmvrsCardStyleSampling = countMvrs
        }
        countyTable.refresh()
    }

    override fun setFontSize(size: Float) {
        contestTable.setFontSize(size)
        countyTable.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        this.auditRecordLocation = auditRecordLocation
        contestTable.setBeans(mutableListOf<CountyContestBean?>())

        logger.debug("countyPanel setAuditRecord " + auditRecordLocation)

        this.auditRecordLocation = auditRecordLocation
        val record = read(auditRecordLocation)
        if (record == null) return false
        if (record !is CountyAuditRecord) return false

        this.countyAudit = record
        val config = countyAudit!!.config
        this.auditRiskLimit = config.riskLimit
        val lastAuditRound = countyAudit!!.rounds.last()

        try {
            var countUniformMvrs = 0
            var statewide: CountyBean? = null

            val countyList = mutableListOf<CountyBean>()
            for (countyData in countyAudit!!.countyData) {
                val bean = CountyBean(countyData)
                if (bean.name == "Statewide") statewide = bean
                    else countUniformMvrs += bean.nmvrsUniformSampling // statewide now included in counties, so dont count twice

                countyList.add(bean)
            }
            val totalPopulation = countyAudit!!.countyData.filter { it.countyName != "Statewide"}.sumOf { it.npop }
            val totalBean = CountyBean( CountyData("=Total", countUniformMvrs, totalPopulation))

            countyList.add(totalBean)
            countyTable.setBeans(countyList)
            this.totalBean = totalBean

            // if (statewide != null) statewide.nmvrsUniform = countUniformMvrs;
            this.countyMap = countyList.associateBy { it.name }
            this.countyContestData = countyAudit!!.countyContestData
            countMvrsByCounty()

            this.contestMap =  countyAudit!!.contests.associateBy { it.id }
            this.contestRoundMap =  lastAuditRound.contestRounds.associateBy { it.id }

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("setAuditRecord failed", e)
        }

        return true
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
                    bean.nmvrsConsistent = countyBean.nmvrsCardStyleSampling
                    bean.nmvrsUniform = countyBean.nmvrsUniformSampling

                    bean.contestUA = contestMap[countyContest.id]
                    bean.contestRound = contestRound
                }
            }
        }
        contestTable.setBeans(beanList)
    }

    fun showContest(contestBean: CountyContestBean) = buildString {
        append(contestTable.tableModel.showBean(contestBean, BeanProperties.contests))
        append(contestBean.contestUA!!.show())
    }

    override fun saveState() {
        contestTable.saveState(false)
        countyTable.saveState(false)
        prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CountyTable::class.java)
    }
}

////////////////////////////////////////////////////////////////
class CountyBean(countyData: CountyData) {
    val name: String
    val population: Int
    val nmvrsUniformSampling: Int
    var nmvrsCardStyleSampling: Int = 0

    init {
        this.name = countyData.countyName
        this.population = countyData.npop
        this.nmvrsUniformSampling = countyData.nmvrs
    }

    fun getDiff() = nmvrsUniformSampling - nmvrsCardStyleSampling
}

// data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)
class CountyContestBean(val countyContestData: CountyContestData, val auditRiskLimit: Double) {
    var countyPopulation = 1
    var nmvrsConsistent = 0
    var nmvrsUniform = 0
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

    fun getCorlaSample(): String {
        if (contestUA == null) return "N/A"
        val CORLAsample = contestUA!!.contest.info().metadata.get("CORLAsample")
        if (CORLAsample == null) return "N/A"
        return CORLAsample.toInt().toString()
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
        val haveMvrss: String? = contest.info().metadata.get("CORLAhaveMvrs")
        if (haveMvrss == null) return 1.0
        return estRiskStandardBet(cua.Npop, getNoerror(), haveMvrss.toInt())
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