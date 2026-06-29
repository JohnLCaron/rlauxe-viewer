package org.cryptobiotic.rlauxe.beans

import org.cryptobiotic.rlauxe.core.Assertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object BeanProperties {
    fun getContestBeanProperties() = contests // for java access

    // used by AuditRoundsTable.contestRoundTable
    // ContestsPanel.contestTable
    // BelgiumContestsTable.contestTable
    // CorlaContestsTable.contestTable and SamplingTable.contestTable (same CorlaContestBean)
    var contests = mutableListOf<TableBeanProperty>()

    init {
        contests.add(TableBeanProperty("NCand", "number of candidates"))
        contests.add(TableBeanProperty("NCounties", "number of counties, or the county name if only one"))
        contests.add(TableBeanProperty("contestId", "contest identifier"))
        contests.add(TableBeanProperty("cvrNcards", "number of cards from cvrs"))
        contests.add(TableBeanProperty("diffNcards", "contest.Nc  - cvrNcards"))

        contests.add(TableBeanProperty("estMvrs", "estimated number of mvrs needed"))
        contests.add(TableBeanProperty("estRisk", "estimated risk"))
        contests.add(TableBeanProperty("haveMvrs", "number of mvrs that were sampled for this contest"))
        contests.add(TableBeanProperty("id", "contest identifier"))
        contests.add(TableBeanProperty("include", "include the contest in this audit round"))
        contests.add(TableBeanProperty("margin", "voteMargin / population size (smallest assertion)"))
        contests.add(TableBeanProperty("maxRisk", "when included, the maximum risk allowed"))
        // contests.add(TableBeanProperty("missing", "TODO"))
        contests.add(TableBeanProperty("mvrLimit", "limit on number of mvrs to audit; set by auditor"))
        contests.add(TableBeanProperty("mvrsExtra", "(haveMvrs-estMvrs)"))
        contests.add(TableBeanProperty("mvrsUsed", "number of mvrs actually used during audit"))
        contests.add(TableBeanProperty("name", "contest name"))
        contests.add(TableBeanProperty("nc", "trusted upper bound on contest number of cards"))
        // contests.add(TableBeanProperty("ncast", "number of cards cast"))
        contests.add(TableBeanProperty("noerror", "assort value when mvr agrees with the cvr"))
        contests.add(TableBeanProperty("npop", "Npop size (for diluted margin)"))
        contests.add(TableBeanProperty("nvotes", "tabulated nvotes from auditcenter or cvrs"))
        contests.add(TableBeanProperty("payoff", "payoff factor for each mvr that agrees with the cvr"))
        contests.add(TableBeanProperty("population", "Nc (hasStyle) or Npop (noStyle)"))
        contests.add(TableBeanProperty("phantoms", "number of phantom cards"))
        contests.add(TableBeanProperty("recountMargin", "(winner-loser)/winner (smallest assertion)"))
        contests.add(TableBeanProperty("risk", "measured risk"))
        contests.add(TableBeanProperty("source", "auditcenter or cvrs"))
        contests.add(TableBeanProperty("status", "status of contest"))
        contests.add(TableBeanProperty("target", "is a Corla targeted contest"))
        contests.add(TableBeanProperty("type", "contest type"))
        contests.add(TableBeanProperty("undervotes", "reported undervote count"))
        contests.add(TableBeanProperty("uvPct", "percent undervote count"))
        contests.add(TableBeanProperty("voteMargin", "(winner-loser) votes (smallest assertion)"))
        contests.add(TableBeanProperty("votes", "reported vote count"))
        contests.add(TableBeanProperty("winners", "list of winning candidates"))

        //contests.add(TableBeanProperty("dilutedMargin", "voteMargin/Npop (smallest assertion)"))
        //contests.add(TableBeanProperty("reportedMargin", "voteMargin/Nc (smallest assertion)"))
        // contests.add(TableBeanProperty("samplePct", "estMvrs/population size"))

        // Belgium
        contests.add(TableBeanProperty("NFailures", "number of contested assertions"))
        contests.add(TableBeanProperty("nseats", "nseats to win"))

        // ContestRound
        contests.add(TableBeanProperty("done", "contest has completed"))
        contests.add(TableBeanProperty("estNewMvrs", "estimated new samples needed"))
        contests.add(TableBeanProperty("estPct", "estimated samples needed / contest Nc"))
        contests.add(TableBeanProperty("haveNewMvrs", "new contest cards in sample"))
        contests.add(TableBeanProperty("maxIndex", "max sample index for this contest"))
        contests.add(TableBeanProperty("round", "index of audit round"))

        // Corla
        contests.add(TableBeanProperty("corlaHaveMvrs", "Corla mvrs for contest's strata (from contestComparisonFile)"))
        contests.add(TableBeanProperty("corlaRisk", "estimated Corla uniform risk"))
        contests.add(TableBeanProperty("corlaStrata", "Corla strata size (from contestRoundFile ballot_card_count)"))
        contests.add(TableBeanProperty("corlaVoteDiff", "TODO"))
        contests.add(TableBeanProperty("corlaVoteMargin", "Corla margin in votes (from contestRoundFile min_margin)"))
        contests.add(TableBeanProperty("cvrNvotes", "number of votes from cvrs"))
        contests.add(TableBeanProperty("cvrNundervotes", "number of undervotes from cvrs"))
        contests.add(TableBeanProperty("diffNvotes", "auditcenter.votes.sum() - cvrs.votes.sum()"))
        contests.add(TableBeanProperty("pctNcardsMissing", "diffNcards / contest.Nc"))
        contests.add(TableBeanProperty("pctNvotesMissing", "diffNvotes / auditcenter.votes.sum()"))
        contests.add(
            TableBeanProperty(
                "corlaEstMvrs",
                "Corla estimated nmvrs (from contestRoundFile estimated_samples_to_audit)"
            )
        )

        // CountyContest
        contests.add(TableBeanProperty("contestName", "contest name"))
        contests.add(
            TableBeanProperty(
                "compareNvotes",
                "tabulated nvotes from auditcenter - cvrs for this county and contest"
            )
        )
        contests.add(
            TableBeanProperty(
                "pctCompareNvotes",
                "compareNvotes / (auditcenter nvotes) for this county and contest"
            )
        )

        // ContestCounty
        contests.add(TableBeanProperty("countyName", "county name"))
        contests.add(TableBeanProperty("countyPoolId", "county pool id"))
        contests.add(TableBeanProperty("estNcards", "estimated ncards in this county and contest"))
        contests.add(TableBeanProperty("pctDiffNvotes", "diffNvotes / auditcenter.nvotes"))
        contests.add(TableBeanProperty("voteForN", "allowed number of votes"))
    }

    // CorlaContestsTable.contestCountyTable
    // CountyTable.countyContestTable
    // SamplingTable.CountyContest
    var assertions = mutableListOf<TableBeanProperty>()

    init {
        assertions.add(TableBeanProperty("desc", "assertion description"))
        assertions.add(TableBeanProperty("difficulty", "assertion difficulty measure"))
        assertions.add(TableBeanProperty("estMvrs", "estimated number of mvrs needed"))
        assertions.add(TableBeanProperty("estRisk", "estimated risk"))

        assertions.add(TableBeanProperty("loser", "assertion loser candidate"))
        assertions.add(TableBeanProperty("margin", "voteMargin / population size"))
        assertions.add(TableBeanProperty("mean", "average assorter value"))
        assertions.add(TableBeanProperty("name", "assertion short name"))
        assertions.add(TableBeanProperty("noerror", "assort value when mvr agrees with the cvr"))
        assertions.add(TableBeanProperty("payoff", "payoff factor for each mvr that agrees with the cvr"))

        assertions.add(TableBeanProperty("recountMargin", "(winner-loser)/winner"))
        assertions.add(TableBeanProperty("type", "assorter type"))
        assertions.add(TableBeanProperty("upper", "assorter upper bound"))
        assertions.add(TableBeanProperty("winner", "assertion winner candidate"))

        // assertion round
        assertions.add(TableBeanProperty("completed", "round that assertions was proved"))
        assertions.add(TableBeanProperty("estNewMvrs", "estimated new samples needed"))
        assertions.add(TableBeanProperty("mvrsUsed", "mvrs used in this round"))
        assertions.add(TableBeanProperty("prevMvrs", "mvrs from previous rounds"))
        assertions.add(TableBeanProperty("round", "index of audit round"))
        assertions.add(TableBeanProperty("status", "status of assertion completion"))
        assertions.add(TableBeanProperty("risk", "measured risk (minimum PValue of audit)"))

        // belgium dhondt
        assertions.add(TableBeanProperty("scoreDiff", "(winner-loser) score"))
        assertions.add(TableBeanProperty("scoreRange", "max scoreDiff for this number of samples"))
    }
}

fun <T> showContestWithDesc(bean: T, tableModel: BeanTableModel<T>, cua: ContestWithAssertions?) = buildString {
    appendLine(tableModel.showBean(bean, BeanProperties.contests))
    if (cua != null) append(cua.show())
}

fun <T> showAssertionWithDesc(bean: T, tableModel: BeanTableModel<T>, cua: ContestWithAssertions, assertion: Assertion) = buildString {
    appendLine(tableModel.showBean(bean, BeanProperties.assertions))
    appendLine(assertion.show())
    appendLine("difficulty: ${cua.contest.showAssertionDifficulty(assertion.assorter)}")
}

fun <T> printTable(
    beanTable: BeanTable<T>,
    properties: List<TableBeanProperty>,
    name: String,
): String {
    val indexBeans = beanTable.indexedBeans()
    val sortedIndexBeans = indexBeans.sortedBy { it.viewIndex }
    val sortedBeans = sortedIndexBeans.map { it.bean }

    val sb = StringBuilder()
    val sb2 = StringBuilder()

    val header: String = beanTable.tableModel.beanTableHeader(properties)
    sb.append(header)
    sb2.append("| " + header.replace(',', '|'))

    for (bean in sortedBeans) {
        val beanCsv: String = beanTable.tableModel.beanCsv(bean, properties)
        sb.append(beanCsv)
        sb2.append("| " + beanCsv.replace(',', '|'))
    }

    val file = "/home/stormy/rla/temp/" + name + ".csv"
    try {
        FileOutputStream(file).use { fout ->
            OutputStreamWriter(fout, StandardCharsets.UTF_8).use { writer ->
                writer.write(sb.toString())
                writer.flush()
            }
        }
    } catch (e: Exception) {
        throw RuntimeException(e)
    }

    return sb2.toString()
}
