/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaInput.StrataInfo
import org.cryptobiotic.rlauxe.corlaInput.RedactionCount
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlaInput.CountyInputData
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.StatewideInputWithCvrs
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class Counties(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
    val setCountyInput: (CountyInput) -> Unit,
    val setCounty: (String) -> Unit,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()

    private val countyTable: BeanTable<CountyTabBean>
    private val countyContestTable: BeanTable<CountyContestVotesBean>

    // TextHistoryPane localInfo = new TextHistoryPane();
    private val split1: JSplitPane
    // private val split2: JSplitPane

    init {
        countyTable = BeanTable(
            CountyTabBean::class.java, prefs.node("countyTable") as PreferencesExt, false,
            "CorlaCountyData", "CorlaCountyInput", null
        )
        countyTable.addListSelectionListener {
            val selected = countyTable.getSelectedBean()
            if (selected != null) setSelectedCounty(selected)
        }

        countyTable.addPopupOption(
            "Show Row",
            countyTable.makeShowAction(infoTA, infoWindow) { bean: CountyTabBean -> showCounty(bean) }
        )
        countyTable.addPopupOption(
            "Use this county for CountyCvrs tabs",
            countyTable.makeActionOnCurrentBean { bean: CountyTabBean? ->
                if (bean?.countyInput != null) {
                    setCountyInput(bean.countyInput)
                } else if (bean != null) {
                    setCounty(bean.county)
                }
                return@makeActionOnCurrentBean ((bean?.countyInput != null))
            }
        )
        tables.add(countyTable)
        countyTable.addPopupOption(
            "Print Table",
            countyTable.makeShowAction(infoTA, infoWindow)
            { printTable(countyTable, CountyTabBean.beanProperties,"CountyRedactions") }
        )

        countyContestTable = BeanTable(
            CountyContestVotesBean::class.java, prefs.node("contestTable") as PreferencesExt, false,
            "CountyContestVotes", "ContestVotes in selected county", null
        )
        /* countyContestTable.addPopupOption(
            "Show Canonical Contest",
            countyContestTable.makeShowAction(infoTA, infoWindow) { bean: CountyContestVotesBean -> showCanonicalContest(bean) }
        ) */
        tables.add(countyContestTable)

        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, countyContestTable)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))
        // split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, styleTable)
        // split2.setDividerLocation(prefs.getInt("splitPos2", 600))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CountyPoolTable init" }
    }

    fun setColoradoInput(input: StatewideInput) {
        logger.debug { "setColoradoInput to ${input.name()}." }

        countyTable.setBeans(emptyList())
        val strataMap: Map<String, StrataInfo> = input.strataMap

        // val requireCountyInput = (input is StatewideInputWithCvrs)
        val inputWithCvrs: StatewideInputWithCvrs? = if (input is StatewideInputWithCvrs) input else null

        val countySummaryData ="/resources/data/cases/corla/countySummaryData/${input.name()}.csv"
        val inputDataMap = try {
            println("reading CountyInputData from $countySummaryData")
            CountyInputData.readFromResource(countySummaryData).associateBy { it.county }
        } catch (e: Exception) {
            emptyMap()
        }
        logger.debug { inputDataMap.toString() }

        val redactedCountFilename = "/resources/data/cases/corla/redactedCount/${input.name()}.csv"
        val redactedCountMap = try {
            println("reading RedactionCount from $redactedCountFilename")
            RedactionCount.readFromResource(redactedCountFilename)
        } catch (e: Exception) {
            emptyMap()
        }
        redactedCountMap.forEach { (key, it) ->
            println("'$key' == $it")
        }
        logger.debug { redactedCountMap.toString() }

        val countyContests = mutableListOf<CountyTabBean>()
        input.countyTabsAllContests().forEach { (county, countyTab) ->
            val corlaCountyInput = inputWithCvrs?.corlaCountyInput(county)

            countyContests.add(CountyTabBean(county, countyTab, strataMap[county], corlaCountyInput,
                inputDataMap[county], redactedCountMap[county]))
        }
        countyTable.setBeans(countyContests)
    }

    fun setSelectedCounty(bean: CountyTabBean) {
        countyContestTable.setBeans(emptyList())

        val countyContests = mutableListOf<CountyContestVotesBean>()
        bean.countyTab.contests.forEach {
            countyContests.add(CountyContestVotesBean(it.value))
        }
        countyContestTable.setBeans(countyContests)
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }

        prefs.putInt("splitPos1", split1.getDividerLocation())
        //prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showCounty(bean: CountyTabBean) = buildString {
        appendLine(countyTable.tableModel.showBean(bean, CountyTabBean.beanProperties))
        appendLine(bean.show())
    }

    //////////////////////////////////////////////////////

    // data class CountyInputData(val county: String, val manifestCount: Int, val ncvrs, val cvrInManifest: Int, val cvrNoManifest:Int,
//    val manifestNoCvr: Int, val ngroups: Int, val minCards: Int)

    class CountyTabBean(val county: String, val countyTab: CountyTabAllContests, val strata: StrataInfo?,
                        val countyInput: CountyInput?, val data: CountyInputData?, val rc: RedactionCount?) {
        val ncontests = countyTab.contests.size
        val nmvrs = strata?.nmvrs ?: 0
        val total = strata?.ballotCardCount ?: 0
        val hasCvrs = (countyInput != null)

        fun getManifest() =  data?.manifestCount ?: 0
        fun getCvrUnredacted() =  data?.unredactedCvrs ?: 0
        fun getCvrRedacted() =  data?.redactedCvrs ?: 0
        fun getCvrInManifest() =  data?.cvrInManifest ?: 0
        fun getCvrNoManifest() =  data?.cvrNoManifest ?: 0
        fun getManifestNoCvr() =  data?.manifestNoCvr ?: 0
        fun getPythonNredact() =  rc?.pythonNredact ?: 0
        fun getExtraRedact() =  if (rc != null && data != null) (rc.rlauxeNredact - data.redactedCvrs) else 0
        fun getTotalRedact() =  rc?.rlauxeNredact ?: 0
        fun getMinCardsForVote() =  data?.minCards ?: 0
        fun getBlankPrecinct() =  data?.countBlankPrecincts
        fun getUniqueTypes() =  data?.ballotStylesUnique?.toString() ?: "unknown"
        fun getCardStyleMin() =  data?.ballotStyleMin
        fun getPrecinctStyleMin() =  data?.precinctStyleMin

        // fun getTotalCvrs() =  if (data != null) (data.ncvrs + data.nredactedCvrs) else 0
        fun getMissing() =  if (data != null) (getManifest() - getCvrUnredacted() - getCvrRedacted()) else 0
        fun getPhantoms() =  if (data != null) (total - getManifest()) else 0

        fun show() = buildString {
            appendLine("Contest Tabulations for this County")
            appendLine(countyTab.show())
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "countyTab strata countyInput data rc"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("county", "county name"),
                TableBeanProperty("ncontests", "number of contests from CountyTabAllContests"),
                TableBeanProperty("nmvrs", "corla uniform sampling MVRs in the county"),
                TableBeanProperty("total", "county total cards from round.ballotCardCount"),
                TableBeanProperty("hasCvrs", "has county CVRs"),
                TableBeanProperty("phantoms", "population - manifestCount"),

                TableBeanProperty("manifest", "number of entries in the manifest"),
                TableBeanProperty("cvrUnredacted", "count of unredacted Cvrs"),
                TableBeanProperty("cvrRedacted", "count of redacted Cvrs in existing file"),
                TableBeanProperty("cvrInManifest", "count of Cvrs that match entries in the Manifest"),
                TableBeanProperty("cvrNoManifest", "count of Cvrs that dont match entries in the Manifest"),
                TableBeanProperty("manifestNoCvr", "count of Manifest entries that dont match cvrs"),
                TableBeanProperty("nredacted", "number of redacted rows added by Anonymizer"),
                // TableBeanProperty("totalCvrs", "ncvrs + redactedCvrs"),
                TableBeanProperty("missing", "manifestCount - (cvrUnredacted + cvrRedacted)"),
                TableBeanProperty("minCardsForVote", "minimum cards needed for missing votes"),
                TableBeanProperty("blankPrecinct", "number of rows with blank precinct column"),
                TableBeanProperty("pythonNredact", "total redactions seen after python anonimizer"),
                TableBeanProperty("extraRedact", "number of additional redaction made by rlauxe"),
                TableBeanProperty("totalRedact", "total redactions seen after rlauxe anonimizer"),
                TableBeanProperty("uniqueTypes", "if the ballot style has a single contest set (style)"),
                TableBeanProperty("cardStyleMin", "minimum ncards on ballotType/style"),
                TableBeanProperty("precinctStyleMin", "minimum ncards on precinct/style"),

                )
        }
    }

    // all the contests in this county
    class CountyContestVotesBean(val countyContest: CountyContestVotes) {
        val county = countyContest.countyName
        val contest = countyContest.contestName
        val choices = countyContest.choices
        val totalVotes = countyContest.contestVotes()

        companion object {
            val header = " id,                                     name, tabNCards, styleNCards, diffNCards, nvotes, undervotes, uvPct"

            @JvmStatic
            fun hiddenProperties() = "countyContest"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("contestId", "contest Id"),
                TableBeanProperty("contestName", "contest name"),
                TableBeanProperty("diffNVotes", "auditcenter.votes - cvr.votes"),
                TableBeanProperty("pctDiffNVotes", "(auditcenter.votes - cvr.votes)/auditcenter.votes"),
            )

        }
    }

    companion object {
        private val logger = KotlinLogging.logger("Counties")
    }
}
