/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ContestUnderAudit;
import org.cryptobiotic.rlauxe.core.Contest;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.*;

import static java.lang.Math.max;
import static org.cryptobiotic.rlauxe.util.UtilsKt.mean2margin;

public class AuditTable extends JPanel {
    private final PreferencesExt prefs;

    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;
    private final BeanTable<AuditRoundResultBean> auditRoundTable;

    private final JSplitPane split2, split3;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    private AuditConfig auditConfig;
    private AuditRound lastAuditState;

    public AuditTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
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
            bean -> ((ContestBean) bean).show()));

        assertionTable = new BeanTable<>(AssertionBean.class, (PreferencesExt) prefs.node("assertionTable"), false,
                "Assertion", "Assertion", null);
        assertionTable.addListSelectionListener(e -> {
            AssertionBean assertion = assertionTable.getSelectedBean();
            if (assertion != null) {
                setSelectedAssertion(assertion);
            }
        });
        assertionTable.addPopupOption("Show Assertion", assertionTable.makeShowAction(infoTA, infoWindow,
                bean -> bean.toString()));

        auditRoundTable = new BeanTable<>(AuditRoundResultBean.class, (PreferencesExt) prefs.node("assertionRoundTable"), false,
                "AuditRounds", "AuditRoundResult", null);
        auditRoundTable.addPopupOption("Show AuditRoundResult", auditRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AuditRoundResultBean)bean).show()));
        setFontSize(fontSize);

        // layout of tables
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));
        split3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, auditRoundTable);
        split3.setDividerLocation(prefs.getInt("splitPos3", 200));
        setLayout(new BorderLayout());
        add(split3, BorderLayout.CENTER);
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
        auditRoundTable.setFontSize(size);
    }

    void setSelected(String wantRecordDir) {
        setAuditRecord(wantRecordDir);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);

        try {
            this.auditConfig = auditRecord.getAuditConfig();

            java.util.Map<Integer, ContestRound> contests = new TreeMap<>(); // sorted

            if (auditRecord.getRounds().isEmpty()) {
                for (var contest : auditRecord.getContests()) {
                    contests.put(contest.getId(), new ContestRound(contest, new ArrayList<>(), 0));
                }
            }

            for (var round : auditRecord.getRounds()) {
                for (var contest : round.getContestRounds()) {
                    contests.put(contest.getId(), contest); // get the last time it appears in a round
                }
            }

            java.util.List<ContestBean> beanList = new ArrayList<>();
            for (var contest : contests.values()) {
                beanList.add(new ContestBean(contest));
            }
            contestTable.setBeans(beanList);

            if (!auditRecord.getRounds().isEmpty()) {
                // select contest with smallest margin
                ContestBean minByMargin = beanList
                        .stream()
                        .min(Comparator.comparing(ContestBean::getReportedMargin))
                        .orElseThrow(NoSuchElementException::new);
                contestTable.setSelectedBean(minByMargin);

                this.lastAuditState = auditRecord.getRounds().getLast();
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
        return true;
    }

    void setSelectedContest(ContestBean contestBean) {
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (AssertionRound a : contestBean.contestRound.getAssertionRounds()) {
            beanList.add(new AssertionBean(contestBean.contestRound, a));
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

    void setSelectedAssertion(AssertionBean assertionBean) {
        java.util.List<AuditRoundResultBean> auditList = new ArrayList<>();

        int maxRound = assertionBean.assertionRound.getRoundIdx();
        for (AuditRound auditRound : auditRecord.getRounds()) {
            if (auditRound.getRoundIdx() > maxRound) break;

            for (ContestRound contestRound : auditRound.getContestRounds()) {
                if (contestRound.getContestUA().equals(assertionBean.contestRound.getContestUA())) {
                    for (AssertionRound assertionRound : contestRound.getAssertionRounds()) {
                        if (assertionRound.getAssertion().equals(assertionBean.assertionRound.getAssertion())) {
                            if (assertionRound.getAuditResult() != null)
                                auditList.add(new AuditRoundResultBean(assertionRound.getAuditResult()));
                        }
                    }
                }
            }
        }
        auditRoundTable.setBeans(auditList);
    }

    /*
    void setSelectedAssertion(AssertionBean assertionBean) {
        java.util.List<AssertionRoundBean> beanList = new ArrayList<>();
        for (AuditRoundResult a : assertionBean.assertionRound.getRoundResults()) {
            beanList.add(new AssertionRoundBean(a));
        }
        assertionRoundTable.setBeans(beanList);
    } */

    void save() {
        contestTable.saveState(false);
        assertionTable.saveState(false);
        auditRoundTable.saveState(false);

        prefs.putInt("splitPos2", split2.getDividerLocation());
        prefs.putInt("splitPos3", split3.getDividerLocation());
    }

    public class ContestBean {
        ContestRound contestRound;
        ContestUnderAudit contestUA;

        public ContestBean() {
        }

        ContestBean(ContestRound contestRound) {
            this.contestRound = contestRound;
            this.contestUA = contestRound.getContestUA();
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

        public Integer getPhantoms() {
            return contestUA.getNp();
        }

        public Integer getUndervotes() {
            return contestUA.getContest().getUndervotes();
        }

        public String getWinners() {
            return contestUA.getContest().getWinners().toString();
        }

        public Integer getTotalMvrs() {
            return contestRound.getActualMvrs();
        }

        public boolean isSuccess() {
            return contestRound.getStatus().getSuccess();
        }

        public String getStatus() {
            return Naming.status(contestRound.getStatus());
        }

        public String getVotes() {
            if (contestUA.getContest() instanceof Contest) {
                return ((Contest) contestUA.getContest()).getVotes().toString();
            } else {
                return "N/A";
            }
        }

        public double getReportedMargin() {
            var minAssertion = contestUA.minAssertion();
            if (minAssertion == null) {
                return 0.0;
            } else {
                return minAssertion.getAssorter().reportedMargin();
            }
        }

        public double getRecountMargin() {
            return contestUA.recountMargin();
        }

        public Integer getCompleted() {
            if (!contestRound.getStatus().getComplete()) return 0;
            int round = 0;
            for (AssertionRound assertion : contestRound.getAssertionRounds()) {
                round = max(round, assertion.getRound());
            }
            return round;
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(contestRound.getRoundIdx()));
            sb.append("estSampleSize = %d%n".formatted(contestRound.getEstSampleSize()));
            sb.append("estNewSampleSize = %d%n".formatted(contestRound.getEstNewSamples()));
            sb.append("actualMvrs = %d%n".formatted(contestRound.getActualMvrs()));
            sb.append("actualNewMvrs = %d%n".formatted(contestRound.getActualNewMvrs()));
            sb.append("status = %s%n".formatted(Naming.status(contestRound.getStatus())));
            sb.append("included = %s%n".formatted(contestRound.getIncluded()));
            sb.append("done = %s%n".formatted(contestRound.getDone()));
            sb.append("\ncontest = %s%n".formatted(contestUA.show()));
            return sb.toString();
        }
    }

    public class AssertionBean {
        ContestRound contestRound;
        AssertionRound assertionRound;
        Assertion assertion;

        public AssertionBean() {
        }

        AssertionBean(ContestRound contestRound, AssertionRound assertionRound) {
            this.contestRound = contestRound;
            this.assertionRound = assertionRound;
            this.assertion = assertionRound.getAssertion();
        }

        public String getDesc() {
            return assertion.getAssorter().desc();
        }

        public Integer getEstMvrs() {
            return assertionRound.getEstSampleSize();
        }

        public Integer getCompleted() {
            return assertionRound.getRound();
        }

        public String getStatus() {
            return Naming.status(assertionRound.getStatus());
        }

        public double getReportedMargin() {
           return assertion.getAssorter().reportedMargin();
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(assertionRound.getRoundIdx()));
            sb.append("estSampleSize = %d%n".formatted(assertionRound.getEstSampleSize()));
            sb.append("estNewSampleSize = %d%n".formatted(assertionRound.getEstNewSampleSize()));
            sb.append("status = %s%n".formatted(Naming.status(assertionRound.getStatus())));
            sb.append("round = %d%n".formatted(assertionRound.getRound()));
            sb.append("\nassertion = %s%n".formatted(assertionRound.getAssertion().show()));
            sb.append("\nassorter = %s%n".formatted(assertionRound.getAssertion().getAssorter().toString()));
            if (assertionRound.getPrevAuditResult() != null) sb.append("\nprevAuditResult = %s%n".formatted(assertionRound.getPrevAuditResult().toString()));
            if (assertionRound.getEstimationResult() != null) sb.append("\nestimationResult = %s%n".formatted(assertionRound.getEstimationResult().toString()));
            if (assertionRound.getAuditResult() != null) sb.append("\nauditResult = %s%n".formatted(assertionRound.getAuditResult().toString()));
            return sb.toString();
        }
    }

    public class AuditRoundResultBean {
        AuditRoundResult auditRound;

        public AuditRoundResultBean() {
        }

        AuditRoundResultBean(AuditRoundResult auditRound) {
            this.auditRound = auditRound;
        }

        public Integer getRoundIdx() {
            return auditRound.getRoundIdx();
        }

        public Integer getMvrs() {
            return auditRound.getNmvrs();
        }

        /* public Integer getMaxBallotsUsed() {
            return auditRound.getMaxBallotsUsed();
        } */

        public Double getPValue() {
            return auditRound.getPvalue();
        }

        /* public Integer getSamplesNeeded() {
            return auditRound.getSamplesNeeded();
        } */

        public Integer getMvrsUsed() {
            return auditRound.getSamplesUsed();
        }

        public Integer getMvrsExtra() {
            return (Math.max(0, auditRound.getNmvrs() - auditRound.getSamplesUsed()));
        }

        public String getStatus() {
            return Naming.status(auditRound.getStatus());
        }

        public Double getMeasuredMargin() {
            return mean2margin(auditRound.getMeasuredMean());
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(getRoundIdx()));
            sb.append("measuredMean = %f%n".formatted(auditRound.getMeasuredMean()));
            sb.append("measuredMargin = %f%n".formatted(mean2margin(auditRound.getMeasuredMean())));
            sb.append("estSampleSize = %d%n".formatted(auditRound.getNmvrs()));
            sb.append("samplesUsed = %d%n".formatted(auditRound.getSamplesUsed()));
            sb.append("samplesExtra = %d%n".formatted(getMvrsExtra()));
            sb.append("pvalue = %f%n".formatted(auditRound.getPvalue()));
            sb.append("status = %s%n".formatted(Naming.status(auditRound.getStatus())));
            if (auditRound.getMeasuredRates() != null) sb.append("measuredErrors = %s%n".formatted(auditRound.getMeasuredRates().toString()));
            if (auditRound.getStartingRates() != null) sb.append("aprioriErrors = %s%n".formatted(auditRound.getStartingRates().toString()));
            sb.append("maxBallotsUsed = %d%n".formatted(auditRound.getMaxBallotIndexUsed()));
            return sb.toString();
        }
    }

}
