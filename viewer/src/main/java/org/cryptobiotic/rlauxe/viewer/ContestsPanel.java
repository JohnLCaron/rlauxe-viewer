/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.betting.TestH0Status;
import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ClcaAssertion;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.util.UtilsKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.cryptobiotic.rlauxe.util.UtilsKt.*;

public class ContestsPanel extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(ContestsPanel.class);

    private final PreferencesExt prefs;

    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;

    private final JSplitPane split2;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    private Config config;
    private Map<Integer, Integer> oneshotMvrs;

    public ContestsPanel(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize, boolean isCorlaAudit) {
        this.prefs = prefs;

        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contests", "ContestRound", null);
        contestTable.addListSelectionListener(e -> {
            ContestBean contest = contestTable.getSelectedBean();
            if (contest != null) {
                setSelectedContest(contest);
            }
        });
        contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> showContest((ContestBean) bean)));
        contestTable.addPopupOption("Print Contests", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> printContests()));

        assertionTable = new BeanTable<>(AssertionBean.class, (PreferencesExt) prefs.node("assertionTable"), false,
                "Assertion", "Assertion", null);

        assertionTable.addPopupOption("Show Assertion", assertionTable.makeShowAction(infoTA, infoWindow,
                bean -> showAssertion((AssertionBean) bean)));

        setFontSize(fontSize);

        // layout of tables
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));

        // auditRoundTable not used for now

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("auditTable init");
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        contestTable.setBeans(emptyList());

        logger.debug("auditTable setAuditRecord " + auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.read(auditRecordLocation);
        if (this.auditRecord == null) return false;

        try {
            this.config = auditRecord.getConfig();
            ContestBean.alpha = config.getRiskLimit();

            var contestMap = new HashMap<Integer, ContestBean>();
            java.util.List<ContestBean> beanList = new ArrayList<>();
            for (var contestUA : auditRecord.getContests()) {
                if (contestUA.getPreAuditStatus().equals(TestH0Status.InProgress)) {
                    var bean = new ContestBean(contestUA);
                    beanList.add(bean);
                    contestMap.put(contestUA.getId(), bean);
                }
            }
            contestTable.setBeans(beanList);

            // put last round for each contest
            for (var round : auditRecord.getRounds()) {
                for (var contestRound : round.getContestRounds()) {
                    var bean = contestMap.get(contestRound.getId());
                    bean.lastRound = contestRound;
                }
            }

            if (!auditRecord.getRounds().isEmpty()) {
                // select inProgress contest with smallest margin
                Optional<ContestBean> minByMargin = beanList
                        .stream()
                        .filter(bean -> bean.getStatus().equals("InProgress"))
                        .min(Comparator.comparing(ContestBean::getDilutedMargin));
                minByMargin.ifPresent(contestTable::setSelectedBean);
            }

            oneshotMvrs = auditRecord.readOneShotMvrs();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedContest(ContestBean contestBean) {
        ContestWithAssertions cua = contestBean.contestUA;
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (Assertion a : cua.assertions()) {
            beanList.add(new AssertionBean(cua, a));
        }
        assertionTable.setBeans(beanList);

        if (beanList.isEmpty()) return;

        // select assertion with smallest margin
        AssertionBean minByMargin = beanList
                .stream()
                .min(Comparator.comparing(AssertionBean::getReportedMargin))
                .orElseThrow(NoSuchElementException::new);
        assertionTable.setSelectedBean(minByMargin);
    }

    void save() {
        contestTable.saveState(false);
        assertionTable.saveState(false);

        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    /// ///////////////////////////////////////////////////////////////

    /* void showInfo(Formatter f) {
        if (this.auditRecord == null) { return; }
        if (this.auditConfig == null) { return; }
        if (this.auditConfig.getAuditType() != AuditType.ONEAUDIT) { return; }

        var cardPools = makeCardPoolsFromAuditRecord(auditRecord);
        String poolVotes = cardPools.showPoolVotes(4);
        f.format("%s", poolVotes);
    } */
    public String showContest(ContestBean contestBean) {
        StringBuilder sb = new StringBuilder();
        // sb.append("%n%s%n".formatted(contestTable.tableModel.toString()));
        sb.append("%n%s%n".formatted(contestTable.tableModel.showBean(contestBean, ContestBean.beanProperties)));
        sb.append("\n%s%n".formatted(contestBean.contestUA.show()));
        return sb.toString();
    }

    public String printContests() {
        StringBuilder sb = new StringBuilder();
        sb.append(contestTable.tableModel.beanTableHeader(ContestBean.beanProperties));
        for (var bean : contestTable.getBeans()) {
            sb.append(contestTable.tableModel.beanCsv(bean, ContestBean.beanProperties));
        }
        var file = "/home/stormy/rla/temp/contests.csv";
        try (FileOutputStream fout = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fout, StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    public String showAssertion(AssertionBean assertionBean) {
        StringBuilder sb = new StringBuilder();
        sb.append("%n%s%n".formatted(assertionTable.tableModel.showBean(assertionBean, AssertionBean.beanProperties)));

        var assertion = assertionBean.assertion;
        sb.append(assertion.show());
        sb.append("\n   difficulty: %s".formatted(assertionBean.cua.getContest().showAssertionDifficulty(assertion.getAssorter())));
        if (assertionBean.oaAssorter != null)
            sb.append("%n oaAssortRates = %s".formatted(assertionBean.oaAssorter.getOaAssortRates().toString()));

        return sb.toString();
    }

    static public class ContestBean {
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static Double alpha = .03;

        static {
            beanProperties.add(new BeanTable.TableBeanProperty("id", "contest identifier"));
            beanProperties.add(new BeanTable.TableBeanProperty("name", "contest name"));
            beanProperties.add(new BeanTable.TableBeanProperty("type", "contest type"));
            beanProperties.add(new BeanTable.TableBeanProperty("NCand", "number of candidates"));
            beanProperties.add(new BeanTable.TableBeanProperty("winners", "list of winning candidates"));
            beanProperties.add(new BeanTable.TableBeanProperty("nc", "trusted upper bound on contest ncards"));
            beanProperties.add(new BeanTable.TableBeanProperty("npop", "population size for diluted margin"));
            beanProperties.add(new BeanTable.TableBeanProperty("phantoms", "number of phantom votes"));
            beanProperties.add(new BeanTable.TableBeanProperty("votes", "reported vote count"));
            beanProperties.add(new BeanTable.TableBeanProperty("undervotes", "reported undervote count"));
            beanProperties.add(new BeanTable.TableBeanProperty("uvPct", "percent undervote count"));

            beanProperties.add(new BeanTable.TableBeanProperty("voteDiff", "(winner-loser) votes (smallest assertion)"));
            beanProperties.add(new BeanTable.TableBeanProperty("reportedMargin", "voteDiff/Nc (smallest assertion) %"));
            beanProperties.add(new BeanTable.TableBeanProperty("dilutedMargin", "voteDiff/Npop (smallest assertion) %"));
            beanProperties.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner (smallest assertion) %"));
            beanProperties.add(new BeanTable.TableBeanProperty("estRisk", "estimated maximum risk %"));

            beanProperties.add(new BeanTable.TableBeanProperty("poolPct", "percent of cards in pools"));
            beanProperties.add(new BeanTable.TableBeanProperty("status", "status of contest completion"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrsUsed", "number of mvrs actually used during audit"));
            // beanProperties.add(new BeanTable.TableBeanProperty("mvrsExtra", "number of mvrs audited but not needed"));

            // Corla
            beanProperties.add(new BeanTable.TableBeanProperty("target", "is a Corla targeted contest"));
            beanProperties.add(new BeanTable.TableBeanProperty("NCounties", "number of counties, or the county name if only one"));
            beanProperties.add(new BeanTable.TableBeanProperty("estMvrs", "estimate number of mvrs needed (Corla)"));
            beanProperties.add(new BeanTable.TableBeanProperty("haveMvrs", "number of mvrs that were sampled for this contest (Corla)"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrsExtra", "(haveMvrs-estMvrs) (Corla)"));

        }

        ContestRound lastRound = null;
        ContestWithAssertions contestUA;
        Integer countyMvrs = -1;
        Integer countyNpop = -1;

        public ContestBean() {
        }

        ContestBean(ContestWithAssertions contestUA) {
            this.contestUA = contestUA;
        }

        public String getName() {
            return contestUA.getName();
        }

        public Integer getId() {
            return contestUA.getId();
        }

        public String getType() {
            return contestUA.getChoiceFunction().toString();
        }

        public Integer getNCand() {
            return contestUA.getNcandidates();
        }

        public Integer getNc() {
            return contestUA.getNc();
        }

        public Integer getNpop() {
            if (countyNpop >= 0) return countyNpop;
            return contestUA.getNpop();
        }

        public Integer getUndervotes() {
            return contestUA.getContest().Nundervotes();
        }

        public Integer getUvPct() {
            return contestUA.getContest().undervotePct();
        }

        public Integer getPhantoms() {
            return contestUA.getNphantoms();
        }

        public String getWinners() {
            return contestUA.getContest().winners().toString();
        }

        public String getVotes() {
            var votes = contestUA.getContest().votes();
            if (votes != null) return votes.toString();
            return "N/A";
        }

        public String getReportedMargin() {
            return pfz(contestUA.minReportedMargin(), 2);
        }

        public String getDilutedMargin() {
            return pfz(contestUA.minDilutedMargin(), 2);
        }

        private double dilutedMargin() {
            // clca gets assertion with minimum noerror, and returns dilutedMargin of that.
            Double min = contestUA.minDilutedMargin();
            return min == null ? 0.0 : 100 * min;
        }

        public String getEstRisk() {
            Double dilutedMargin = contestUA.minDilutedMargin();
            if (dilutedMargin == null) return "N/A";
            Integer nmvrs = getHaveMvrs();
            var risk = UtilsKt.estRiskFromMargin(2.0 / 1.03905, dilutedMargin, nmvrs);
            return pfz(risk, 1);
        }

        public Integer getEstMvrs() {
            Double dilutedMargin = contestUA.minDilutedMargin(); // TODO might be reportedMargin
            if (dilutedMargin == null || dilutedMargin <= 0) return -1;
            return roundUp(UtilsKt.estSamplesFromMarginUpper(2.0 / 1.03905, dilutedMargin, alpha));
        }

        public double getRecountMargin() {
            Double min = contestUA.minRecountMargin();
            return min == null ? 0.0 : 100 * min;
        }

        public Integer getVoteDiff() {
            Assertion minAssertion = contestUA.minAssertion();
            return contestUA.getContest().marginInVotes(minAssertion.getAssorter());
        }

    /* public Integer getTotalMvrs() {
        return (lastRound == null) ? 0 : lastRound.getActualMvrs();
    } */

        public String getStatus() {
            return (lastRound == null) ? Naming.status(contestUA.getPreAuditStatus()) : Naming.status(lastRound.getStatus());
        }

        public Integer getMvrsUsed() {
            return (lastRound == null) ? 0 : lastRound.maxSamplesUsed();
        }

        public Integer getMvrsExtras() {
            if (lastRound == null) return 0;
            if (!lastRound.getDone()) return 0;
            if (!lastRound.getIncluded()) return 0;
            else {
                return (lastRound.getEstMvrs() < lastRound.maxSamplesUsed()) ? 0 :
                        (lastRound.getEstMvrs() - lastRound.maxSamplesUsed());
            }
        }

        public Integer getMvrsExtra() {
            return getHaveMvrs() - getEstMvrs();
        }


    /*
    public Integer getCompleted() {
        if (!lastRound.getStatus().getComplete()) return 0;
        int round = 0;
        for (AssertionRound assertion : lastRound.getAssertionRounds()) {
            round = max(round, assertion.getRoundProved());
        }
        return round;
    } */

        public String getNCounties() {
            var CORLAcounties =  contestUA.getContest().info().getMetadata().get("CORLAcounties");
            if (CORLAcounties == null) return "N/A";
            var toks = CORLAcounties.split(",");
            if (toks.length == 1) return toks[0];
            return String.format("%02d", toks.length);
        }

        public Integer getPoolPct() {
            var pct =  contestUA.getContest().info().getMetadata().get("PoolPct");
            return (pct == null) ? 0 : Integer.parseInt(pct);
        }

        public Integer getCardDiff() {
            var votes = contestUA.getContest().info().getMetadata().get("CORLApoolTotalCards"); // change to CORLApoolTotalCards
            var poolCards = (votes == null) ? 0 : Integer.parseInt(votes);
            return contestUA.getNc() - (contestUA.getContest().Nundervotes() + contestUA.getContest().nvotes()) / contestUA.getContest().info().getVoteForN();
        }

        public Integer getPoolDiff() {
            var votes = contestUA.getContest().info().getMetadata().get("CORLApoolTotalCards");
            var poolCards = (votes == null) ? 0 : Integer.parseInt(votes);
            return contestUA.getNc() - poolCards;
        }

        public Integer getVotePoolDiff() {
            var votes = contestUA.getContest().info().getMetadata().get("CORLApoolTotalVotes");
            var poolVotes = (votes == null) ? 0 : Integer.parseInt(votes);
            return contestUA.getContest().nvotes() - poolVotes;
        }

        public Integer getHaveMvrs() {
            /* if (getStatewide()) {
                var stateMvrs = contestUA.getContest().info().getMetadata().get("CORLAstatewideNmvrs");
                return (stateMvrs == null) ? 0 : Integer.parseInt(stateMvrs);
            }

            if (countyMvrs >= 0) return countyMvrs; */
            var have = contestUA.getContest().info().getMetadata().get("CORLAhaveMvrs");
            return (have == null) ? 0 : Integer.parseInt(have);
        }

        public Integer getStateMvrs() {
            var nmvrss = contestUA.getContest().info().getMetadata().get("CORLAstatewideNmvrs");
            return (nmvrss == null) ? 0 : Integer.parseInt(nmvrss);
        }

        public Integer getCountyMvrs() {
            var nmvrss = contestUA.getContest().info().getMetadata().get("CORLAcountyMvrs");
            return (nmvrss == null) ? 0 : Integer.parseInt(nmvrss);
        }

        public Boolean getTarget() {
            var reason = contestUA.getContest().info().getMetadata().get("CORLAauditReason");
            return reason.equals("state_wide_contest") || reason.equals("county_wide_contest");
        }


    /*
    public Integer getOneshotEst() {
        Integer nmvrs = oneshotMvrs.get(contestUA.getId());
        return (nmvrs != null) ? nmvrs : 0;
    } */
    }

    static public class AssertionBean {
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();

        static {
            beanProperties.add(new BeanTable.TableBeanProperty("type", "assorter type"));
            beanProperties.add(new BeanTable.TableBeanProperty("winner", "assertion winner candidate"));
            beanProperties.add(new BeanTable.TableBeanProperty("loser", "assertion loser candidate"));
            beanProperties.add(new BeanTable.TableBeanProperty("desc", "assertion difficulty description"));
            beanProperties.add(new BeanTable.TableBeanProperty("difficulty", "assertion difficulty measure (IRV only)"));
            beanProperties.add(new BeanTable.TableBeanProperty("upper", "assorter upper bound"));
            beanProperties.add(new BeanTable.TableBeanProperty("noError", "noerror assort value (CLCA only)"));

            beanProperties.add(new BeanTable.TableBeanProperty("margin", "diluted margin"));
            beanProperties.add(new BeanTable.TableBeanProperty("mean", "diluted mean"));
            beanProperties.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner"));
        }

        ContestWithAssertions cua;
        Assertion assertion;
        Map<Integer, String> candidates;
        OneAuditClcaAssorter oaAssorter;

        public AssertionBean() {
        }

        AssertionBean(ContestWithAssertions cua, Assertion assertion) {
            this.cua = cua;
            this.assertion = assertion;
            this.candidates = cua.getContest().info().getCandidateIdToName();
            if (assertion instanceof ClcaAssertion cassertion) {
                if (cassertion.getCassorter() instanceof OneAuditClcaAssorter) {
                    this.oaAssorter = (OneAuditClcaAssorter) cassertion.getCassorter();
                }
            }
        }

        public String getType() {
            return assertion.getAssorter().getClass().getSimpleName();
        }

        public String getWinner() {
            int winner = assertion.getAssorter().winner();
            return candidates.get(winner);
        }

        public String getLoser() {
            int loser = assertion.getAssorter().loser();
            return candidates.get(loser);
        }

        public String getDesc() {
            return assertion.getAssorter().hashcodeDesc();
            // return cua.getContest().showAssertionDifficulty(assertion.getAssorter());
        }

        //public Integer getEstMvrs() {return assertionRound.getEstSampleSize();}

        // public Integer getCompleted() {return assertionRound.getRound();}

        // public String getStatus() {return Naming.status(assertionRound.getStatus());}

        public double getReportedMargin() {
            return 100 * assertion.getAssorter().reportedMargin();
        }
        public double getDilutedMargin() {
            return 100 * assertion.getAssorter().dilutedMargin();
        }

        public String getDifficulty() {
            return cua.getContest().showAssertionDifficulty(assertion.getAssorter());

        /* if (assertion.getAssorter() instanceof RaireAssorter) {
            RaireAssertion rassertion = ((RaireAssorter) assertion.getAssorter()).getRassertion();
            return Math.round(rassertion.getDifficulty());
        } else if (cua.getContest() instanceof DHondtContest) {
            return Math.round(((DHondtContest) cua.getContest()).difficulty(assertion.getAssorter()));
        }
        return -1; */
        }

        public double getRecountMargin() {
            return cua.getContest().recountMargin(assertion.getAssorter());
        }

        public double getMean() {
            return assertion.getAssorter().dilutedMean();
        }

        public double getNoError() {
            return assertion.getAssorter().noerror();
        }

        public double getUpper() {
            return assertion.getAssorter().upperBound();
        }
    }

}

