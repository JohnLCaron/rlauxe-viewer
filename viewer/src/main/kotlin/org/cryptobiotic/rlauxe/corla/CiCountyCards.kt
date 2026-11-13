/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.beans.TableBeanProperty
import org.cryptobiotic.rlauxe.corlaCounty.CorlaContestInfo
import org.cryptobiotic.rlauxe.corlaCounty.CountyCard
import org.cryptobiotic.rlauxe.corlaCounty.CountyCardsFromManifest
import org.cryptobiotic.rlauxe.corlacvr.CvrSchema
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.viewer.SubPanelIF
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSplitPane

private val logger = KotlinLogging.logger("CiCvrs")

// countyInput.readCorlaCvrs()
class CiCountyCards(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), SubPanelIF {

    val tables = mutableListOf<BeanTable<out Any>>()
    val countyCardTable: BeanTable<CountyCardBean>
    val countyContestCvrs: BeanTable<CountyContestCvrBean>

    private val split1: JSplitPane
    // private val split2: JSplitPane

     // var corlaCvrs: CorlaCountyCvrsIF? = null
    var needsReading = true
    var currentCountyCards: CountyCardsFromManifest? = null
    var currentSchema: CvrSchema? = null

    init {
        countyCardTable = BeanTable(
            CountyCardBean::class.java, prefs.node("cardTable") as PreferencesExt, false,
            "CountyCards from Manifest", "CountyCards from County Manifest and redacted CVR file", null
        )
        countyCardTable.addPopupOption(
            "Show CountyCard",
            countyCardTable.makeShowAction(infoTA, infoWindow) { bean: CountyCardBean -> showSelectedRow(bean) }
        )
        tables.add(countyCardTable)

        countyContestCvrs = BeanTable(
            CountyContestCvrBean::class.java, prefs.node("precinctStyleTable") as PreferencesExt, false,
            "CountyContest Tabs from CountyCards", "contest/county tabulation from cvr file", null
        )
        tables.add(countyContestCvrs)

        //cardTable.addPopupOption("Show Population", cardTable.makeShowAction(localInfo,
        //    bean -> ((cardTable) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyCardTable, countyContestCvrs)
        split1.setDividerLocation(prefs.getInt("splitPos1", 800))
        //split2 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, stylesTable)
        //split2.setDividerLocation(prefs.getInt("splitPos2", 1000))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "CiCvrs init" }
    }

    override fun setFontSize(size: Float) {
        tables.forEach { it.setFontSize(size) }
    }

    override fun saveState() {
        tables.forEach { it.saveState(false) }
        prefs.putInt("splitPos1", split1.getDividerLocation())
        // prefs.putInt("splitPos2", split2.getDividerLocation())
    }

    fun showSelectedRow(bean: CountyCardBean) = buildString {
        append(bean.show(currentSchema!!))
    }

    fun setCountyInput(countyCards: CountyCardsFromManifest): Boolean {
        if (currentCountyCards == countyCards) return true

        countyCardTable.setBeans(null)
        val maxRead = 100000

        try {
            val beans = mutableListOf<CountyCardBean>()
            var count = 1
            for (card in countyCards.iterator()) {
                beans.add(CountyCardBean(card, count))
                if (count++ > maxRead) break
            }
            countyCardTable.setBeans(beans)

            val infos = countyCards.infos

            val cvrBeans = mutableListOf<CountyContestCvrBean>()
            countyCards.cvrTabs.values.forEach { cvrTab ->
                cvrBeans.add(CountyContestCvrBean(cvrTab, infos[cvrTab.contestId]))
            }
            countyContestCvrs.setBeans(cvrBeans)

            this.currentCountyCards = countyCards
            this.currentSchema = countyCards.corlaCvrs.schema

        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) { "setCountyInput failed" }
            return false
        }
        return true
    }
}
// data class CountyCard (
//   val imprintedId: String,
//   val location: String,
//   val style: String, // must have a style or "missing"
//   val votes: List<ContestVotes>, // contest -> votedFor
//   val poolName: String?, // must be set if its from a OneAudit CardPool
//)
class CountyCardBean(val row: CountyCard, val index: Int) {
    val imprintedId = row.imprintedId
    val location = row.location
    val style = row.style
    val poolName = row.poolName

    val votes = buildString {
        row.votes.forEach {
            append("${it.contestId}:${it.votedFor}, ")
        }
    }

    fun show(schema: CvrSchema) = buildString {
        appendLine("id=${row.imprintedId}, location=${row.location}, style=${row.style}, pool=${row.poolName}")
        row.votes.forEach {
            val contest = schema.contests.get(it.contestId)
            append("  contest: ${sfn(contest.contestName, 60)} (${nfn(it.contestId, 3)}), ")
            appendLine(" candidate votes: ${it.candVotes()}")
        }
    }

    companion object {
        @JvmStatic
        fun hiddenProperties() = "row"
    }
}

// tabulation from the Cvr file
class CountyContestCvrBean(val cvrTab: ContestTabulation, info: CorlaContestInfo?) {
    val contestId = cvrTab.contestId
    val contestName = info?.name ?: "N/A"
    val ncards = cvrTab.ncards()
    val votes = cvrTab.votes
    val undervotes = cvrTab.undervotes
    val uvPct = cvrTab.undervotes / (cvrTab.ncards().toDouble() * cvrTab.voteForN)

    companion object {
        val header = " id,                                     name, tabNCards, styleNCards, diffNCards, nvotes, undervotes, uvPct"

        @JvmStatic
        fun hiddenProperties() = "cvrTab info"

        @JvmStatic
        val beanProperties = listOf(
            TableBeanProperty("contestId", "contest Id"),
            TableBeanProperty("contestName", "contest name"),
            TableBeanProperty("ncards", "number of cards with this contest on it"),
            TableBeanProperty("votes", "tabulated votes for this contest"),
            TableBeanProperty("undervotes", "undervotes (no candidate selected) for this contest"),
            TableBeanProperty("uvPct", "pct undervotes / ncards"),
        )

    }
}


