package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.ContestRound;
import org.cryptobiotic.rlauxe.audit.CountyPools;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import ucar.ui.prefs.BeanTable;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BeanProperties {
    static ArrayList<BeanTable.TableBeanProperty> contests = new ArrayList<>();
    static {
        contests.add(new BeanTable.TableBeanProperty("id", "contest identifier"));
        contests.add(new BeanTable.TableBeanProperty("name", "contest name"));
        contests.add(new BeanTable.TableBeanProperty("type", "contest type"));
        contests.add(new BeanTable.TableBeanProperty("NCand", "number of candidates"));
        contests.add(new BeanTable.TableBeanProperty("winners", "list of winning candidates"));
        contests.add(new BeanTable.TableBeanProperty("nc", "trusted upper bound on contest number of cards"));
        contests.add(new BeanTable.TableBeanProperty("npop", "population size (for diluted margin)"));
        contests.add(new BeanTable.TableBeanProperty("ncast", "number of cards cast"));
        contests.add(new BeanTable.TableBeanProperty("population", "Nc (hasStyle) or Npop (noStyle)"));
        contests.add(new BeanTable.TableBeanProperty("phantoms", "number of phantom cards"));
        contests.add(new BeanTable.TableBeanProperty("status", "status of contest"));
        contests.add(new BeanTable.TableBeanProperty("votes", "reported vote count"));
        contests.add(new BeanTable.TableBeanProperty("undervotes", "reported undervote count"));
        contests.add(new BeanTable.TableBeanProperty("uvPct", "percent undervote count"));

        contests.add(new BeanTable.TableBeanProperty("voteDiff", "(winner-loser) votes (smallest assertion)"));
        contests.add(new BeanTable.TableBeanProperty("margin", "voteDiff / population size (smallest assertion)"));
        contests.add(new BeanTable.TableBeanProperty("dilutedMargin", "voteDiff/Npop (smallest assertion)"));
        contests.add(new BeanTable.TableBeanProperty("reportedMargin", "voteDiff/Nc (smallest assertion)"));
        contests.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner (smallest assertion)"));
        contests.add(new BeanTable.TableBeanProperty("noerror", "assort value when mvr agrees with the cvr"));
        contests.add(new BeanTable.TableBeanProperty("payoff", "payoff factor for each mvr that agrees with the cvr"));

        contests.add(new BeanTable.TableBeanProperty("estMvrs", "estimated number of mvrs needed"));
        contests.add(new BeanTable.TableBeanProperty("estRisk", "estimated risk"));
        contests.add(new BeanTable.TableBeanProperty("haveMvrs", "number of mvrs that were sampled for this contest"));
        contests.add(new BeanTable.TableBeanProperty("mvrsExtra", "(haveMvrs-estMvrs)"));
        contests.add(new BeanTable.TableBeanProperty("mvrsUsed", "number of mvrs actually used during audit"));
        contests.add(new BeanTable.TableBeanProperty("samplePct", "estMvrs/population size"));

        // Corla
        contests.add(new BeanTable.TableBeanProperty("target", "is a Corla targeted contest"));
        contests.add(new BeanTable.TableBeanProperty("NCounties", "number of counties, or the county name if only one"));
        contests.add(new BeanTable.TableBeanProperty("corlaEst", "estimate from Corla (super simple) algorithm"));
        contests.add(new BeanTable.TableBeanProperty("nvotes", "tabulated nvotes from auditcenter or cvrs"));
        contests.add(new BeanTable.TableBeanProperty("compareNvotes", "tabulated nvotes from auditcenter - cvrs"));
        contests.add(new BeanTable.TableBeanProperty("pctCompareNvotes", "compareNvotes / (auditcenter nvotes)"));

        // CountyPoolsTable.Contest
        contests.add(new BeanTable.TableBeanProperty("contestId", "contest identifier"));
        contests.add(new BeanTable.TableBeanProperty("contestName", "contest name"));
        contests.add(new BeanTable.TableBeanProperty("countyPopulation", "contestRound.ballotCardCount"));
        contests.add(new BeanTable.TableBeanProperty("contestPopulation", "contestRound.contestBallotCardCount"));
        contests.add(new BeanTable.TableBeanProperty("corlaSample", "contestRound.estimatedSamplesToAudit"));
        contests.add(new BeanTable.TableBeanProperty("corlaHaveMvrs", "estimated Corla uniform have mvrs"));
        contests.add(new BeanTable.TableBeanProperty("corlaRisk", "estimated Corla uniform risk"));
        contests.add(new BeanTable.TableBeanProperty("cvrNcards", "number or cards from cvrs"));
        contests.add(new BeanTable.TableBeanProperty("diffNcards", "contest.Nc  - cvrNcards"));
        contests.add(new BeanTable.TableBeanProperty("pctMissing", "diffNcards / contest.Nc"));
        contests.add(new BeanTable.TableBeanProperty("diffNvotes", "votes.sum() - cvrNvotes"));

        // AuditRound
        contests.add(new BeanTable.TableBeanProperty("round", "index of audit round"));
        contests.add(new BeanTable.TableBeanProperty("estNewMvrs", "estimated new samples needed"));
        contests.add(new BeanTable.TableBeanProperty("estPct", "estimated samples needed / contest Nc"));
        contests.add(new BeanTable.TableBeanProperty("haveNewMvrs", "new contest cards in sample"));
        contests.add(new BeanTable.TableBeanProperty("mvrLimit", "limit on number of mvrs to audit; set by auditor"));
        contests.add(new BeanTable.TableBeanProperty("risk", "measured risk"));

        contests.add(new BeanTable.TableBeanProperty("include", "include the contest in this audit round"));
        contests.add(new BeanTable.TableBeanProperty("done", "contest has completed"));
    }

    static ArrayList<BeanTable.TableBeanProperty> assertions = new ArrayList<>();
    static {
        assertions.add(new BeanTable.TableBeanProperty("type", "assorter type"));
        assertions.add(new BeanTable.TableBeanProperty("winner", "assertion winner candidate"));
        assertions.add(new BeanTable.TableBeanProperty("loser", "assertion loser candidate"));
        assertions.add(new BeanTable.TableBeanProperty("desc", "assertion description"));
        assertions.add(new BeanTable.TableBeanProperty("difficulty", "assertion difficulty measure"));
        assertions.add(new BeanTable.TableBeanProperty("name", "assertion short name"));

        assertions.add(new BeanTable.TableBeanProperty("upper", "assorter upper bound"));
        assertions.add(new BeanTable.TableBeanProperty("margin", "voteDiff / population size"));
        assertions.add(new BeanTable.TableBeanProperty("noerror", "assort value when mvr agrees with the cvr"));
        assertions.add(new BeanTable.TableBeanProperty("payoff", "payoff factor for each mvr that agrees with the cvr"));
        assertions.add(new BeanTable.TableBeanProperty("estMvrs", "estimated number of mvrs needed"));
        assertions.add(new BeanTable.TableBeanProperty("estRisk", "estimated risk"));

        assertions.add(new BeanTable.TableBeanProperty("mean", "average assorter value"));
        assertions.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner"));

        assertions.add(new BeanTable.TableBeanProperty("round", "index of audit round"));
        assertions.add(new BeanTable.TableBeanProperty("prevMvrs", "mvrs from previous rounds"));
        assertions.add(new BeanTable.TableBeanProperty("estNewMvrs", "estimated new samples needed"));
        assertions.add(new BeanTable.TableBeanProperty("mvrsUsed", "mvrs used in this round"));
        assertions.add(new BeanTable.TableBeanProperty("status", "status of assertion completion"));
        assertions.add(new BeanTable.TableBeanProperty("risk", "measured risk (minimum PValue of audit)"));
        assertions.add(new BeanTable.TableBeanProperty("completed", "round that assertions was proved"));
    }

    static ArrayList<BeanTable.TableBeanProperty> countyPools = new ArrayList<>();
    static {
        countyPools.add(new BeanTable.TableBeanProperty("countyName", "county name"));
        countyPools.add(new BeanTable.TableBeanProperty("countyPoolId", "county name"));
        countyPools.add(new BeanTable.TableBeanProperty("population", "county population from round.ballotCardCount"));
        countyPools.add(new BeanTable.TableBeanProperty("totalCards", "number of cvrs in the pool"));
        countyPools.add(new BeanTable.TableBeanProperty("diff", "population - totalCards"));
        countyPools.add(new BeanTable.TableBeanProperty("diffPct", "(population - totalCards)/population"));
        countyPools.add(new BeanTable.TableBeanProperty("diffNVotes", "auditcenter.votes - cvr.votes"));
        countyPools.add(new BeanTable.TableBeanProperty("pctDiffNVotes", "(auditcenter.votes - cvr.votes)/auditcenter.votes"));
    }

    static public <T> String showContestG(T bean, BeanTable<T>.TableBeanModel tableModel, ContestWithAssertions cua) {
        StringBuilder sb = new StringBuilder();
        sb.append("%n%s%n".formatted( tableModel.showBean(bean, BeanProperties.contests)));
        if (cua != null) sb.append("\n%s%n".formatted(cua.show()));
        return sb.toString();
    }

    static public <T> String showAssertionG(T bean, BeanTable<T>.TableBeanModel tableModel, ContestWithAssertions cua, Assertion assertion) {
        StringBuilder sb = new StringBuilder();
        sb.append("%n%s%n".formatted(tableModel.showBean(bean, BeanProperties.assertions)));
        sb.append(assertion.show());
        sb.append("\n   difficulty: %s".formatted(cua.getContest().showAssertionDifficulty(assertion.getAssorter())));
        return sb.toString();
    }

    static public <T> String showCountyPoolsG(T bean, BeanTable<T>.TableBeanModel tableModel, CountyPools pool) {
        StringBuilder sb = new StringBuilder();
        sb.append("%n%s%n".formatted(tableModel.showBean(bean, BeanProperties.countyPools)));
        return sb.toString();
    }

    static public <T> String printTableG(List<T> beans, BeanTable<T>.TableBeanModel tableModel, List<BeanTable.TableBeanProperty> properties, String name) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        String header = tableModel.beanTableHeader(properties);
        sb.append(header);
        sb2.append("| " + header.replace(',','|'));

        for (var bean : beans) {
            String beanCsv = tableModel.beanCsv(bean, properties);
            sb.append(beanCsv);
            sb2.append("| " + beanCsv.replace(',','|'));
        }

        var file = "/home/stormy/rla/temp/"+name+".csv";
        try (FileOutputStream fout = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fout, StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return sb2.toString();
    }

}
