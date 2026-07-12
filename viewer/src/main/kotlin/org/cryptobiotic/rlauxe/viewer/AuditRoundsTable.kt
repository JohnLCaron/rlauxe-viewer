/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.showAssertionWithDesc
import org.cryptobiotic.rlauxe.beans.showContestWithDesc
import org.cryptobiotic.rlauxe.betting.estRiskStandardBet
import org.cryptobiotic.rlauxe.bridge.Naming
import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.AuditRecordIF
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.viewer.ViewerMain.MvrAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.widget.BAMutil
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ListSelectionEvent

private val logger: Logger = LoggerFactory.getLogger(AuditRoundsTable::class.java)

class AuditRoundsTable(
    private val prefs: PreferencesExt, infoTA: TextHistoryPane, infoWindow: IndependentWindow, fontSize: Float, mvrCall: MvrAction
) : JPanel(), ViewerPanelIF {

    private val auditRoundTable: BeanTable<AuditRoundBean>
    private val contestRoundTable: BeanTable<ContestRoundBean>
    private val assertionTable: BeanTable<AssertionRoundBean>
    private val estRoundTable: BeanTable<EstimationRoundBean>
    private val auditResultTable: BeanTable<AuditRoundResultBean>

    private val split1: JSplitPane
    private val split2: JSplitPane
    private val split3: JSplitPane
    private val split4: JSplitPane

    // private String auditRecordLocation = "none";
    private var auditRecord: AuditRecordIF? = null
    var isComposite: Boolean = false
    private var config: Config? = null
    private var auditRiskLimit = 0.0
    private var lastAuditRound: AuditRoundIF? = null // may be null
    var oneshotMvrs: Map<Int, Int>? = null

    private val rerunTA: TextHistoryPane
    private val rerunWindow: IndependentWindow

    private var samplingChanged = false

    var mvrCall: AbstractAction?

    init {
        this.mvrCall = mvrCall

        // Popup info window
        this.rerunTA = TextHistoryPane(false)
        rerunTA.setFontSize(fontSize)

        this.rerunWindow = IndependentWindow("Details", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(rerunTA))
        val bounds = prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(200, 50, 500, 700)) as Rectangle
        this.rerunWindow.setBounds(bounds)

        auditRoundTable = BeanTable<AuditRoundBean>(
            AuditRoundBean::class.java, (prefs.node("auditStateTable") as PreferencesExt?)!!, false,
            "Audit Rounds", "AuditRound"
        )
        auditRoundTable.addListSelectionListener { e: ListSelectionEvent? ->
            val round = auditRoundTable.getSelectedBean()
            if (round != null) {
                setSelectedAuditRound(round)
            }
        }
        val mvrAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val roundBean = auditRoundTable.getSelectedBean()
                if (roundBean != null) {
                    mvrCall.roundIdx = roundBean.round
                    mvrCall.actionPerformed(null)
                }
            }
        }
        auditRoundTable.addPopupOption(
            "Show AuditRound",
            auditRoundTable.makeShowAction(infoTA, infoWindow) { bean: AuditRoundBean -> showAuditRound(bean) })
        auditRoundTable.addPopupOption("Show sampled Mvrs", mvrAction)

        //   public BeanTable(Class<T> bc, PreferencesExt pstore, boolean canAddDelete, String header, String tooltip, T bean) {
        contestRoundTable = BeanTable(
            ContestRoundBean::class.java, (prefs.node("contestTable") as PreferencesExt?)!!, false,
            "Contest Rounds", "ContestWithAssertions"
        )
        contestRoundTable.addListSelectionListener { e: ListSelectionEvent? ->
            val contest = contestRoundTable.getSelectedBean()
            if (contest != null) {
                setSelectedContest(contest)
            }
        }
        contestRoundTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        contestRoundTable.addPopupOption(
            "Show ContestRound", contestRoundTable.makeShowAction(infoTA, infoWindow) { bean: ContestRoundBean -> showContestRound(bean) })
        contestRoundTable.addPopupOption(
            "Include All Contests",
            contestRoundTable.makeActionOnCurrentBean { e: ContestRoundBean? -> setInclude(true) })
        contestRoundTable.addPopupOption(
            "Exclude All Contests",
            contestRoundTable.makeActionOnCurrentBean { e: ContestRoundBean? -> setInclude(false) })

        assertionTable = BeanTable(
            AssertionRoundBean::class.java, (prefs.node("assertionTable") as PreferencesExt?)!!, false,
            "Assertion Rounds", "Assertion", null
        )
        assertionTable.addListSelectionListener { e: ListSelectionEvent? ->
            val assertion = assertionTable.getSelectedBean()
            if (assertion != null) {
                setSelectedAssertion(assertion)
            }
        }
        assertionTable.addPopupOption(
            "Show Assertion", assertionTable.makeShowAction(infoTA, infoWindow)
            { bean: AssertionRoundBean -> showAssertionRound(bean) })
        assertionTable.addPopupOption(
            "Show Assort values for pool", assertionTable.makeShowAction(
                infoTA, infoWindow
            ) { bean: AssertionRoundBean? -> (bean as AssertionRoundBean).showPoolAssortValues() })

        estRoundTable = BeanTable<EstimationRoundBean>(
            EstimationRoundBean::class.java, (prefs.node("estRoundTable") as PreferencesExt?)!!, false,
            "Estimation Rounds", "EstimationRoundResult", null
        )
        estRoundTable.addPopupOption(
            "Show Simulation details", estRoundTable.makeShowAction(infoTA, infoWindow)
            { bean: EstimationRoundBean -> showEstimationRound(bean) })

        auditResultTable = BeanTable(
            AuditRoundResultBean::class.java, (prefs.node("assertionRoundTable") as PreferencesExt?)!!, false,
            "Audit Results", "AuditRoundResult", null
        )
        auditResultTable.addPopupOption(
            "Show AuditRoundResult", auditResultTable.makeShowAction(infoTA, infoWindow)
            { bean: AuditRoundResultBean -> showAuditResultRound(bean)})
        auditResultTable.addPopupOption(
            "Rerun audit with details", auditResultTable.makeShowAction(
                rerunTA, rerunWindow
            ) { bean: AuditRoundResultBean -> runRoundAgain(bean) })

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, auditRoundTable, contestRoundTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, assertionTable)
        split2.setDividerLocation(prefs.getInt("splitPos2", 400))
        split3 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, estRoundTable)
        split3.setDividerLocation(prefs.getInt("splitPos3", 600))
        split4 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split3, auditResultTable)
        split4.setDividerLocation(prefs.getInt("splitPos4", 800))
        setLayout(BorderLayout())
        add(split4, BorderLayout.CENTER)
    }

    fun getActions(container: JPanel) { // }, contestsPanel: RlauxeContestsTable) {
        logger.debug("AuditRoundsTable getActions")

        val startAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                resample()
            }
        }
        BAMutil.setActionProperties(startAction, "ambition.png", "Resample", false, 'S'.code, -1)
        BAMutil.addActionToContainer(container, startAction)

        // TODO put into separate thread
        val runAuditRoundAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                callRunRound()
                //contestsPanel.resetAuditRecord()
            }
        }
        BAMutil.setActionProperties(runAuditRoundAction, "hamster.png", "Run Audit Round", false, 'R'.code, -1)
        BAMutil.addActionToContainer(container, runAuditRoundAction)

        val includeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(true)
            }
        }
        BAMutil.setActionProperties(includeAllAction, "add-cart.png", "Include selected Contests", false, '+'.code, -1)
        BAMutil.addActionToContainer(container, includeAllAction)

        val excludeAllAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                setInclude(false)
            }
        }
        BAMutil.setActionProperties(
            excludeAllAction,
            "remove-cart.png",
            "Exclude selected Contests",
            false,
            '-'.code,
            -1
        )
        BAMutil.addActionToContainer(container, excludeAllAction)
    }

    override fun setFontSize(size: Float) {
        auditRoundTable.setFontSize(size)
        contestRoundTable.setFontSize(size)
        assertionTable.setFontSize(size)
        auditResultTable.setFontSize(size)
        estRoundTable.setFontSize(size)
        rerunTA.setFontSize(size)
    }

    override fun setAuditRecord(location: String): Boolean {
        val auditRecord = read(location)
        if (auditRecord != null) setAuditRecord(auditRecord)
        return (auditRecord != null)
    }

    // TODO when resample from SampleTable, need to reread in the audit rounds
    fun setAuditRecord(auditRecord: AuditRecordIF) {
        auditRoundTable.setBeans(null)
        contestRoundTable.setBeans(null)
        assertionTable.setBeans(null)
        auditResultTable.setBeans(null)
        estRoundTable.setBeans(null)

        this.auditRecord = auditRecord
        this.isComposite = (this.auditRecord is CompositeAuditRecord)
        this.samplingChanged = false
        logger.debug("AuditRoundsTable setAuditRecord ${auditRecord.topdir}")

        this.config = auditRecord.config
        this.auditRiskLimit = config!!.riskLimit
        val beanList = mutableListOf<AuditRoundBean>()

        var prevTotal = 0
        for (round in auditRecord.rounds) {
            beanList.add(AuditRoundBean(round, prevTotal) {  samplingChanged = it })
            prevTotal += round.newmvrs
        }
        auditRoundTable.setBeans(beanList)

        if (!auditRecord.rounds.isEmpty()) {
            this.lastAuditRound = auditRecord.rounds.last()
        } else {
            this.lastAuditRound = null
        }

        oneshotMvrs = auditRecord.readOneShotMvrs()
    }

    fun setInclude(include: Boolean): Boolean {
        var selectedRows = contestRoundTable.getSelectedBeans()
        if (selectedRows.size < 2) selectedRows = contestRoundTable.beans

        for (bean in selectedRows) {
            bean.isInclude = include
        }
        samplingChanged = true
        contestRoundTable.refresh()
        return true
    }

    /**/////////////////////////////////////////////////////////////// */
    fun setSelectedAuditRound(auditBean: AuditRoundBean) {
        contestRoundTable.setBeans(null)
        assertionTable.setBeans(null)
        auditResultTable.setBeans(null)
        estRoundTable.setBeans(null)

        val beanList = mutableListOf<ContestRoundBean>()
        for (c in auditBean.auditRound.contestRounds) {
            val bean = ContestRoundBean(c, auditBean.round) {  samplingChanged = it }
            beanList.add(bean)
        }
        contestRoundTable.setBeans(beanList)

        if (beanList.isEmpty()) return

        // select contest with smallest margin
        // select assertion with smallest noerror
        val minBean = beanList.filter{ !it.isDone }.minByOrNull { it.contestUA.minNoerror() ?: 0.0 }
        if (minBean != null) {
            contestRoundTable.setSelectedBean(minBean)
            setSelectedContest(minBean)
        }
    }

    fun setSelectedContest(contestRoundBean: ContestRoundBean) {
        assertionTable.setBeans(null)
        auditResultTable.setBeans(null)
        estRoundTable.setBeans(null)

        val beanList: MutableList<AssertionRoundBean> = ArrayList<AssertionRoundBean>()
        for (ar in contestRoundBean.contestRound.assertionRounds) {
            beanList.add(AssertionRoundBean(ar, contestRoundBean.contestRound))
        }
        assertionTable.setBeans(beanList)

        if (beanList.isEmpty()) return

        // select assertion with smallest noerror
        val minBean = beanList.minByOrNull { it.noerror }
        if (minBean != null) {
            assertionTable.setSelectedBean(minBean)
            setSelectedAssertion(minBean)
        }
    }

    // TODO could you use ContestRound.resultsForAssertion(assorterDesc: String) ??
    fun setSelectedAssertion(assertionBean: AssertionRoundBean) {
        auditResultTable.setBeans(null)
        estRoundTable.setBeans(null)

        val auditList: MutableList<AuditRoundResultBean> = ArrayList<AuditRoundResultBean>()
        val estList: MutableList<EstimationRoundBean> = ArrayList<EstimationRoundBean>()

        val maxRound = assertionBean.assertionRound.roundIdx
        // show est, audir from all rounds
        for (auditRound in auditRecord!!.rounds) {
            // if (auditRound.getRoundIdx() > maxRound) break;

            for (contestRound in auditRound.contestRounds) {
                if (contestRound.contestUA.equals(assertionBean.contestUA)) {
                    for (assertionRound in contestRound.assertionRounds) {
                        if (assertionRound.assertion.equals(assertionBean.assertionRound.assertion)) {
                            if (assertionRound.auditResult != null) {
                                auditList.add(AuditRoundResultBean(contestRound, assertionRound))
                            }
                            if (assertionRound.estimationResult != null) {
                                estList.add(EstimationRoundBean(assertionRound, contestRound))
                            }
                        }
                    }
                }
            }
        }
        auditResultTable.setBeans(auditList)
        estRoundTable.setBeans(estList)
    }

    override fun saveState() {
        auditRoundTable.saveState(false)
        contestRoundTable.saveState(false)
        assertionTable.saveState(false)
        auditResultTable.saveState(false)
        estRoundTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
        prefs.putInt("splitPos2", split2.getDividerLocation())
        prefs.putInt("splitPos3", split3.getDividerLocation())
        prefs.putInt("splitPos4", split4.getDividerLocation())

        prefs.putBeanObject(ViewerMain.INFO_BOUNDS, rerunWindow.getBounds())
    }

    /**////////////////////////////////////////////////////////////////////////////////////////////////// */
    fun resample() {
        try {
            if (this.lastAuditRound == null) {
                JOptionPane.showMessageDialog(null, "There is no audit round to resample")
                return
            }
            if (isComposite) {
                JOptionPane.showMessageDialog(null, "Cant resample on Composite Record")
                return
            }

            val cuas: MutableList<ContestWithAssertions?> = ArrayList<ContestWithAssertions?>()
            for (cr in this.lastAuditRound!!.contestRounds) {
                cuas.add(cr.contestUA)
            }

            val nrounds = auditRecord!!.rounds.size
            if (nrounds == 0) return

            logger.info(String.format("call resampleAndSaveResults"))

            resampleAndSaveResults((auditRecord as AuditRecord?)!!, (lastAuditRound as AuditRound?)!!)

            auditRoundTable.refresh()
            contestRoundTable.refresh()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("resample failed", e)
        }
    }

    fun callRunRound() {
        try {
            if (isComposite) {
                JOptionPane.showMessageDialog(null, "Cant run Audit Round on Composite Record")
            } else {
                logger.debug("begin runRound")
                if (samplingChanged && lastAuditRound != null) resampleAndSaveResults(
                    (auditRecord as AuditRecord?)!!,
                    lastAuditRound as AuditRound
                )

                runRound(auditRecord!!.topdir, null, null)
                logger.debug("return from runRound")

                setAuditRecord(auditRecord!!) // reread in
                refreshAll()
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("runAuditRound failed", e)
        }
    }

    fun refreshAll() {
        auditRoundTable.refresh()
        contestRoundTable.refresh()
        assertionTable.refresh()
        estRoundTable.refresh()
        auditResultTable.refresh()
    }

    fun runRoundAgain(bean: AuditRoundResultBean): String {
        try {
            val result = runRoundAgain(auditRecord!!.topdir, bean.contestRound, bean.assertionRound)
            val sb = StringBuilder()
            sb.append(result)
            return sb.toString()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error("runRoundAgain failed", e)
            return e.message ?: ""
        }
    }

    fun showAuditRound(bean : AuditRoundBean): String {
        return auditRoundTable.tableModel.showBean(bean, AuditRoundBean.beanProperties)
    }

    fun showContestRound(bean : ContestRoundBean): String {
        return showContestWithDesc(bean, contestRoundTable.tableModel, bean.contestUA)
    }

    fun showAssertionRound(bean: AssertionRoundBean) = buildString {
        append( showAssertionWithDesc(bean, assertionTable.tableModel, bean.contestUA, bean.assertion))

        if (bean.assertionRound.auditResult != null) append(bean.assertionRound.auditResult)
        else append("\n auditResult is null")
        if (bean.assertionRound.prevAuditResult != null) append("%n prevAuditResult = ${bean.assertionRound.prevAuditResult}")
        else append("\n prevAuditResult is null")

        if (bean.oaAssorter != null) {
            append("oaAssortRates = ${bean.oaAssorter.oaAssortRates}")
        }
    }

    fun showEstimationRound(bean : EstimationRoundBean) = buildString {
        append(estRoundTable.tableModel.showBean(bean, EstimationRoundBean.beanProperties))

        if (bean.estRound!!.startingErrorRates != null) {
            append("startingErrors = ${ bean.estRound!!.startingErrorRates()}")
        }
    }

    fun showAuditResultRound(bean: AuditRoundResultBean) = buildString {
        append(auditResultTable.tableModel.showBean(bean, AuditRoundResultBean.beanProperties))

        if (bean.auditResultRound != null) {
            append("measuredErrors = ${ bean.auditResultRound.clcaErrorTracker!!.measuredClcaErrorCounts() }")
            append("measuredErrorTypes = ${ bean.auditResultRound.clcaErrorTracker!!.measuredClcaErrorCounts().show() }")
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AuditRoundsTable::class.java)
    }
}


class AuditRoundBean(val auditRound: AuditRoundIF, val prevTotal: Int, val setChange: (Boolean) -> Unit) {

    fun canedit() = true


    val totalMvrs: Int
        get() = prevTotal + auditRound.newmvrs

    val newMvrs: Int
        get() = auditRound.newmvrs

    var mvrLimit: Int
        get() = (if (auditRound.auditorMaxNewMvrs != null) auditRound.auditorMaxNewMvrs else -1)!!
        set (mvrLimit) {
            val currentValue = (if (auditRound.auditorMaxNewMvrs != null) auditRound.auditorMaxNewMvrs else -1)!!
            if (currentValue != mvrLimit) {
                if (mvrLimit < 0) auditRound.auditorMaxNewMvrs = null
                else auditRound.auditorMaxNewMvrs = mvrLimit
                setChange(true)
            }
        }

    val mvrsInRound = auditRound.nmvrs
    val mvrsUsed = auditRound.mvrsUsed
    val mvrsExtra = auditRound.mvrsUnused

    val isAuditWasDone = auditRound.auditWasDone
    val isAuditIsComplete = auditRound.auditIsComplete
    val round =  auditRound.roundIdx

    companion object {
        var beanProperties = mutableListOf<TableBeanProperty>()

        init {
            beanProperties.add(TableBeanProperty("round", "index of audit round"))
            beanProperties.add(TableBeanProperty("auditWasDone", "audit was performed"))
            beanProperties.add(TableBeanProperty("auditIsComplete", "audit is complete"))
            beanProperties.add(TableBeanProperty("totalMvrs", "total mvrs for all rounds"))
            beanProperties.add(TableBeanProperty("mvrsInRound", "est mvrs needed for this round"))
            beanProperties.add(TableBeanProperty("newMvrs", "est new mvrs needed for this round"))
            beanProperties.add(TableBeanProperty("mvrsUsed", "number of mvrs actually used during audit"))
            beanProperties.add(TableBeanProperty("mvrsExtra", "number of mvrs not needed"))
            beanProperties.add(TableBeanProperty("mvrLimit", "limit on number of mvrs to audit; set by auditor"))
        }

        // editable properties
        fun editableProperties(): String {
            return "mvrLimit"
        }
        @JvmStatic
        fun hiddenProperties() = "auditRound prevTotal setChange"
    }
}

class ContestRoundBean(val contestRound: ContestRound, val auditRound: Int, val setChange: (Boolean) -> Unit) {
    val contestUA = contestRound.contestUA
    val initialStatus = contestRound.status

    fun canedit() = true

    var isInclude: Boolean
        get() = contestRound.included
        set(include) {
            val oldState = contestRound.included
            if (oldState != include) {
                contestRound.included = include
                setChange(true)
            }
        }

    var mvrLimit: Int
        get() = (if (contestRound.auditorWantNewMvrs != null) contestRound.auditorWantNewMvrs else -1)!!
        set(mvrLimit) {
            val currentValue: Int =
                (if (contestRound.auditorWantNewMvrs != null) contestRound.auditorWantNewMvrs else -1)!!
            if (currentValue != mvrLimit) {
                contestRound.auditorWantNewMvrs = mvrLimit
                setChange(true)
            }
        }

    val name: String
        get() = contestUA.name
    val id: Int
        get() = contestUA.id
    val estMvrs: Int
        get() = contestRound.estMvrs
    val estPct: Double
        get() = contestRound.estMvrs / (contestRound.contestUA.Nc.toDouble())
    val estNewMvrs: Int
        get() = contestRound.estNewMvrs
    val isDone: Boolean
        get() = contestRound.done
    val haveNewMvrs: Int
        get() = contestRound.haveNewSampleSize
    val margin: Double?
        get() = contestUA.minMargin()
    val maxIndex: Int?
        get() = contestRound.maxSampleAllowed
    val mvrsExtra: Int
        get() = this.haveMvrs - this.estMvrs
    val mvrsUsed: Int
        get() = contestRound.maxSamplesUsed()
    val nc: Int
        get() = contestUA.Nc
    //val oneshotEst: Int?
    //    get() = oneshotMvrs!!.get(contestRound.id)
    val phantoms: Int
        get() = contestUA.Nphantoms
    val risk: Double
        get() = contestRound.measuredRisk()
    val round: Int
        get() = contestRound.roundIdx
    val status: String?
        get() = Naming.status(contestRound.status)
    val type: String?
        get() = contestUA.choiceFunction.toString()

    val haveMvrs: Int
        get() = contestRound.haveSampleSize

    val estRisk: Double
        get() {
            val minAssertion = contestUA.minClcaAssertion()
            if (minAssertion == null) return 1.0
            val noerror = minAssertion.noerror

            val haveMvrs = this.haveMvrs
            return estRiskStandardBet(contestUA.Npop, noerror, haveMvrs)
        }

    val maxRisk: Double?
        get() {
            val risk = contestRound.auditorWantRisk
            return if (risk != null) risk else 0.05
        }

    fun statewide(): Boolean {
        val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
        if (CORLAcounties == null) return false
        val toks: Array<String?> = CORLAcounties.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return (toks.size > 60)
    }

    val target: Boolean
        get() {
            val reason = contestUA.contest.info().metadata.get("CORLAauditReason")
            if (reason == null) return false
            return reason == "state_wide_contest" || reason == "county_wide_contest"
        }


    /* public Integer getMvrsExtra() {
       if (!contestRound.getDone()) return 0;
       if (!contestRound.getIncluded()) return 0;
       else return contestRound.getEstMvrs() - contestRound.maxSamplesUsed();
   } */
    // TODO REDO
    /* public int getProbSuccess() {
        if (getActualMvrs() == contestUA.getNc()) {
            return 100;
        }
        AssertionRound minAssertion = contestRound.minAssertion();
        if (minAssertion != null && (minAssertion.getEstimationResult() != null)) {
            var er = minAssertion.getEstimationResult();
            var dist = er.getEstimatedDistribution();
            if (getActualNewMvrs() < 0) return 0; else {
                var actualNM = getActualNewMvrs();
                var prob = probability(dist, getActualNewMvrs());
                return probability(dist, getActualNewMvrs());
            }
        }
        return 0;
    } */
    /* public Integer getCorlaEst() {
        return contestRound.corlaCalc(auditConfig.getRiskLimit());
    } */


    companion object {
        // editable properties
        fun editableProperties(): String {
            return "include mvrLimit"
        }
        @JvmStatic
        fun hiddenProperties() = "contestRound auditRound contestUA initialStatus setChange"
    }
}

class AssertionRoundBean(val assertionRound: AssertionRound, val contestRound: ContestRound, )  {
    val contestUA = contestRound.contestUA
    var assertion: Assertion = assertionRound.assertion
    val cassertion: ClcaAssertion?
    val oaAssorter: OneAuditClcaAssorter?
    init {
        if (assertion is ClcaAssertion) {
            cassertion = assertion as ClcaAssertion

            // why is this different from
            // var pair = assertionRound.calcNewMvrsNeeded(contestBean.contestUA, auditConfig.getClcaConfig().getMaxLoss(), auditConfig.getRiskLimit());
            // calcNewMvrsNeeded = pair.component1();
            // optimalBet = pair.component2();
            val cassorter = (assertion as ClcaAssertion).cassorter
            if (cassorter is OneAuditClcaAssorter) {
                oaAssorter = cassorter
            } else {
                oaAssorter = null
            }
        } else {
            cassertion = null
            oaAssorter = null
        }
    }

    val round: Int
        get() = assertionRound.roundIdx

    val name: String
        get() = assertion.assorter.shortName()

    val noerror: Double
        get() = assertion.assorter.noerror(contestUA.hasStyle)

    val upper: Double
        get() = assertion.assorter.upperBound()

    val prevMvrs: Int
        get() = assertionRound.estMvrs - assertionRound.estNewMvrs
    val estNewMvrs: Int
        get() = assertionRound.estNewMvrs

    val mvrsUsed: Int
        get() {
            var maxUsed = 0
            val auditResult = assertionRound.auditResult
            if (auditResult != null) {
                if (auditResult.samplesUsed > maxUsed) maxUsed = auditResult.samplesUsed
            }
            return maxUsed
        }

    val completed = assertionRound.roundProved

    val status: String?
        get() = Naming.status(assertionRound.status)

    val margin: Double
        get() = assertion.assorter.dilutedMargin()

    val recountMargin  = contestUA.contest.recountMargin(assertion.assorter)

    val risk: Double
        get() {
            if (assertionRound.auditResult != null) {
                return assertionRound.auditResult!!.pmin
            } else {
                return Double.NaN
            }
        }

    fun showPoolAssortValues(): String {
        if (this.oaAssorter != null) {
            return this.oaAssorter!!.assortValuesForPool(3526) // TODO
        } else return "not a OneAudit assertion"
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "assertionRound contestRound contestUA assertion cassertion oaAssorter"
    }
}

// data class EstimationRoundResult(
//    val roundIdx: Int,
//    val strategy: String,
//    val fuzzPct: Double,
//    val startingTestStatistic: Double,
//    val startingRates: ClcaErrorRates? = null, // apriori error rates (clca only)
//    val estimatedDistribution: List<Int>,   // distribution of estimated sample size; currently deciles
//)
class EstimationRoundBean(val assertionRound: AssertionRound, val contest: ContestRound?) {
    var estRound: EstimationRoundResult? = assertionRound.estimationResult

    val round: Int
        get() = estRound!!.roundIdx

    val strategy: String
        get() = estRound!!.strategy
    val startingRates: String
        get() = estRound!!.startingErrorRates()

    val startingPvalue: Double
        get() {
            val t = estRound!!.startingTestStatistic
            if (t == 0.0) return 0.0 else return 1.0 / t
        }
    val calcNewMvrs: Int
        // TODO calc on the fly maybe
        get() = estRound!!.calcNewMvrsNeeded

    val simulatedDistribution: String
        get() = String.format("%s (%d)", estRound!!.deciles(), estRound!!.ntrials)

    val lastIndex: Int
        get() = estRound!!.lastIndex
    val simMvrs: Int
        get() = estRound!!.simMvrsNeeded
    val simNewMvrs: Int
        get() = estRound!!.simNewMvrsNeeded
    val simPercentile: Int
        get() = estRound!!.percentile



    companion object {
        var beanProperties = mutableListOf<TableBeanProperty>()

        init {
            beanProperties.add(TableBeanProperty("round", "index of audit round"))
            beanProperties.add(TableBeanProperty("strategy", "estimation strategy"))
            beanProperties.add(TableBeanProperty("calcNewMvrs", "calculated new Mvrs needed"))
            beanProperties.add(TableBeanProperty("simulatedDistribution", "deciles of simulated distribution"))
            beanProperties.add(TableBeanProperty("simMvrs", "simulated Mvrs needed"))
            beanProperties.add(TableBeanProperty("simNewMvrs", "simulated new Mvrs needed"))
            beanProperties.add(TableBeanProperty("simQuantile", "use this quantile from distribution"))

            beanProperties.add(TableBeanProperty("startingPValue", "starting PValue for this round)"))
            beanProperties.add(TableBeanProperty("startingRates", "initial estimate of error rates"))
        }

        @JvmStatic
        fun hiddenProperties() = "assertionRound contest estRound"
    }
}

// data class AuditRoundResult(
//    val roundIdx: Int,
//    val estSampleSize: Int,   // estimated sample size
//    val maxBallotIndexUsed: Int,  // maximum ballot index (for multicontest audits)
//    val pvalue: Double,       // last pvalue when testH0 terminates
//    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
//    val samplesUsed: Int,     // sample count when testH0 terminates
//    val status: TestH0Status, // testH0 status
//    val measuredMean: Double, // measured population mean
//    val startingRates: ClcaErrorRates? = null, // apriori error rates (clca only)
//    val measuredRates: ClcaErrorRates? = null, // measured error rates (clca only)
//) {
class AuditRoundResultBean(val contestRound: ContestRound, val assertionRound: AssertionRound) {
    val auditResultRound = assertionRound.auditResult
    var prevMvrs: Int = 0

    init  {
        if (assertionRound.prevAuditResult != null) {
            this.prevMvrs = assertionRound.prevAuditResult!!.nmvrs
        }
    }

    val round: Int
        get() = auditResultRound?.roundIdx ?: 0

    val mvrs: Int
        get() = auditResultRound?.nmvrs ?: 0

    val mvrsUsed: Int
        get() = auditResultRound?.samplesUsed ?: 0

    val newMvrs: Int
        get() = (auditResultRound?.nmvrs ?: 0) - prevMvrs

    val newMvrsUsed: Int
        get() = (auditResultRound?.samplesUsed ?: 0) - prevMvrs

    val pValueLast: Double
        // public Integer getCvrsUsedInAudit() {return auditResultRound.getCountCvrsUsedInAudit();}
        get() = auditResultRound?.plast ?: 0.0

    val pValueMin: Double
        get() = auditResultRound?.pmin ?: 0.0

    val status: String
        get() = if (auditResultRound != null) Naming.status(auditResultRound.status) else "N/A"

    val measuredErrorCounts: String
        get() {
            if (auditResultRound != null) return auditResultRound.clcaErrorTracker!!.measuredClcaErrorCounts()
                .show()
            else return "N/A"
        }

    companion object {
        var beanProperties = mutableListOf<TableBeanProperty>()

        init {
            beanProperties.add(TableBeanProperty("round", "index of audit round"))
            beanProperties.add(
                TableBeanProperty(
                    "mvrs",
                    "number of mvrs in this round that could be used for this contest"
                )
            )
            beanProperties.add(TableBeanProperty("mvrsUsed", "number of mvrs used in this round"))
            beanProperties.add(TableBeanProperty("newMvrs", "new mvrs for this round"))
            beanProperties.add(TableBeanProperty("newMvrsUsed", "number of new mvrs actually used in this round"))

            beanProperties.add(TableBeanProperty("startingRates", "starting estimate of error rates"))
            beanProperties.add(TableBeanProperty("measuredErrorCounts", "measured CLCA error counts (cvr-mvr)"))

            beanProperties.add(TableBeanProperty("PValueLast", "ending PValue for this round"))
            beanProperties.add(TableBeanProperty("PValueMin", "minimum PValue achieved"))
            beanProperties.add(TableBeanProperty("status", "status of contest audit"))
            // beanProperties.add(new TableBeanProperty("cvrsUsedInAudit", "count cvrs used in audit"));
        }

        @JvmStatic
        fun hiddenProperties() = "contestRound assertionRound auditResultRound prevMvrs"
    }
}
