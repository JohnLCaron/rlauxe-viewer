/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInputWithCvrs
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaInput.StrataInfo
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.beans.printTable
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.corlaInput.CountyInputData
import org.cryptobiotic.rlauxe.corlaInput.readCountyInputData
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class ColoradoCounties(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
    val setCountyInput: (CorlaCountyInput) -> Unit,
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
        countyTable.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent ->
            val selected = countyTable.getSelectedBean()
            if (selected != null) setSelectedCounty(selected) })

        countyTable.addPopupOption(
            "Show County Summary",
            countyTable.makeShowAction(infoTA, infoWindow) { bean: CountyTabBean -> showCounty(bean) }
        )
        countyTable.addPopupOption(
            "Set CountyCvrs to this county",
            countyTable.makeActionOnCurrentBean { bean: CountyTabBean? ->
                if (bean?.corlaCountyInput != null) {
                    setCountyInput(bean.corlaCountyInput)
                }
                return@makeActionOnCurrentBean ((bean?.corlaCountyInput != null))
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

    fun setColoradoInput(input: ColoradoInput) {
        countyTable.setBeans(emptyList())
        val strataMap: Map<String, StrataInfo> = input.strataMap

        val requireCountyInput = (input is ColoradoInputWithCvrs)
        val inputWithCvrs: ColoradoInputWithCvrs? = if (input is ColoradoInputWithCvrs) input else null

        val filename = when(input.name) {
            "Colorado2020General" -> "/home/stormy/datadrive/rla/cases/corlaState/2020/countyInputData.csv"
            "Colorado2026PwithCvrs" -> "/home/stormy/datadrive/rla/cases/corlaState/2026p/countyInputData.csv"
            else -> null
        }
        val inputDataMap = if (filename == null) emptyMap() else
            readCountyInputData(filename).associateBy{ it.county }

        logger.info { (inputDataMap.toString()) }

        val countyContests = mutableListOf<CountyTabBean>()
        input.countyTabsAllContests().forEach { (county, countyTab) ->
            val corlaCountyInput = inputWithCvrs?.corlaCountyInput(county)
            if (!requireCountyInput || (corlaCountyInput != null)) {
                logger.info { "$county = ${inputDataMap[county]}" }

                countyContests.add(CountyTabBean(county, countyTab, strataMap[county], corlaCountyInput, inputDataMap[county]))
            }
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
                        val corlaCountyInput: CorlaCountyInput?, val data: CountyInputData?) {
        val ncontests = countyTab.contests.size
        val nmvrs = strata?.nmvrs ?: 0
        val population = strata?.ballotCardCount ?: 0
        val hasCvrs = (corlaCountyInput != null)

        fun getManifestCount() =  data?.manifestCount ?: 0
        fun getCvrUnredacted() =  data?.ncvrs ?: 0
        fun getCvrRedacted() =  data?.redactedCvrs ?: 0
        fun getCvrInManifest() =  data?.cvrInManifest ?: 0
        fun getCvrNoManifest() =  data?.cvrNoManifest ?: 0
        fun getManifestNoCvr() =  data?.manifestNoCvr ?: 0
        fun getNgroups() =  data?.ngroups ?: 0
        fun getMinCardsForVote() =  data?.minCards ?: 0

        // fun getTotalCvrs() =  if (data != null) (data.ncvrs + data.nredactedCvrs) else 0
        fun getMissing() =  if (data != null) (population - getCvrUnredacted() - getCvrRedacted()) else 0

        fun show() = buildString {
            appendLine("Contest Tabulations for this County")
            appendLine(countyTab.show())
        }

        companion object {
            @JvmStatic
            fun hiddenProperties() = "countyTab strata corlaCountyInput data"

            @JvmStatic
            val beanProperties = listOf(
                TableBeanProperty("county", "county name"),
                TableBeanProperty("ncontests", "number of contests from CountyTabAllContests"),
                TableBeanProperty("nmvrs", "corla uniform sampling MVRs in the county"),
                TableBeanProperty("population", "county population from round.ballotCardCount"),
                TableBeanProperty("hasCvrs", "has county CVRs"),
                // TableBeanProperty("nrows", "number of rows in the CVR file"),

                TableBeanProperty("manifestCount", "number of entries in the manifest"),
                TableBeanProperty("cvrUnredacted", "count of unredacted Cvrs"),
                TableBeanProperty("cvrRedacted", "count of redacted Cvrs"),
                TableBeanProperty("cvrInManifest", "count of Cvrs that match entries in the Manifest"),
                TableBeanProperty("cvrNoManifest", "count of Cvrs that dont match entries in the Manifest"),
                TableBeanProperty("manifestNoCvr", "count of Manifest entries that dont match cvrs"),
                TableBeanProperty("ngroups", "number of redacted groups"),
                // TableBeanProperty("totalCvrs", "ncvrs + redactedCvrs"),
                TableBeanProperty("missing", "manifestCount - (cvrUnredacted + cvrRedacted)"),
                TableBeanProperty("minCardsForVote", "minimum cards needed for missing votes"),
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
        private val logger = KotlinLogging.logger("ColoradoCounties")
    }
}
