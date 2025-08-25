/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ClcaAssertion;
import org.cryptobiotic.rlauxe.core.ContestUnderAudit;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.raire.RaireAssertion;
import org.cryptobiotic.rlauxe.raire.RaireAssorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    static private final Logger logger = LoggerFactory.getLogger(AuditTable.class);

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
                // setSelectedAssertion(assertion);
            }
        });
        assertionTable.addPopupOption("Show Assertion", assertionTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AssertionBean) bean).show()));

        auditRoundTable = new BeanTable<>(AuditRoundResultBean.class, (PreferencesExt) prefs.node("assertionRoundTable"), false,
                "AuditRounds", "AuditRoundResult", null);
        auditRoundTable.addPopupOption("Show AuditRoundResult", auditRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AuditRoundResultBean)bean).show()));
        setFontSize(fontSize);

        // layout of tables
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));

        // auditRoundTable not used for now
        split3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, auditRoundTable);
        split3.setDividerLocation(prefs.getInt("splitPos3", 200));

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("auditTable init");
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
        logger.debug("auditTable setAuditRecord "+ auditRecordLocation);

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
        ContestUnderAudit cua = contestBean.contestUA;
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

    /*
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


    void setSelectedAssertion(AssertionBean assertionBean) {
        java.util.List<AuditRoundResultBean> beanList = new ArrayList<>();
        for (AuditRoundResult a : assertionBean.assertionRound.getRoundResults()) {
            beanList.add(new AssertionRoundBean(a));
        }
        auditRoundTable.setBeans(beanList);
    }
    */

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

        public String getWinners() {
            return contestUA.getContest().winners().toString();
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
            var votes =  contestUA.getContest().votes();
            if (votes != null) return votes.toString();
            return "N/A";
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
            sb.append("\n%s%n".formatted(contestUA.show()));
            return sb.toString();
        }
    }

    public class AssertionBean {
        ContestUnderAudit cua;
        Assertion assertion;
        AssertionRound assertionRoundMaybe;

        public AssertionBean() {
        }

        AssertionBean(ContestUnderAudit cua, Assertion assertion) {
            this.cua = cua;
            this.assertion = assertion;
        }

        public String getDesc() {
            return assertion.getAssorter().desc();
        }

        //public Integer getEstMvrs() {return assertionRound.getEstSampleSize();}

        // public Integer getCompleted() {return assertionRound.getRound();}

        // public String getStatus() {return Naming.status(assertionRound.getStatus());}

        public double getReportedMargin() {
            return assertion.getAssorter().reportedMargin();
        }

        public double getDifficulty() {
            if (assertion.getAssorter() instanceof RaireAssorter) {
                RaireAssertion rassertion = ((RaireAssorter) assertion.getAssorter()).getRassertion();
                return rassertion.getDifficulty();
            }
            return -1;
        }

        public double getReportedMean() {
            return assertion.getAssorter().reportedMean();
        }

        public Double getAssortValueFromCvrs() {
            if (assertion instanceof ClcaAssertion) {
                return ((ClcaAssertion)assertion).getCassorter().getAssortAverageFromCvrs();
            }
            return null;
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append(assertion.show());
            return sb.toString();
        }
    }

    // the last round, if any
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
