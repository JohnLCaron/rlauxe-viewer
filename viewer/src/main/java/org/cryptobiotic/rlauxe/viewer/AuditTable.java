/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditRecord;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.workflow.*;
import org.cryptobiotic.rlauxe.core.Contest;
import org.cryptobiotic.rlauxe.core.ContestUnderAudit;
import org.cryptobiotic.rlauxe.persist.json.Publisher;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static java.lang.Math.max;
import static org.cryptobiotic.rlauxe.util.UtilsKt.mean2margin;

public class AuditTable extends JPanel {
    private final PreferencesExt prefs;

    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;
    private final BeanTable<AssertionRoundBean> assertionRoundTable;

    private final JSplitPane split2, split3;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    private AuditConfig auditConfig;
    private AuditState lastAuditState;
    private List<BallotOrCvr> cvrs;

    private int totalMvrs = 0;
    private int totalBallots = 0;

    public AuditTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contests", "ContestUnderAudit", null);
        contestTable.addListSelectionListener(e -> {
            ContestBean contest = contestTable.getSelectedBean();
            if (contest != null) {
                setSelectedContest(contest);
            }
        });
        contestTable.addPopupOption("Show ContestUnderAudit", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> bean.toString()));

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

        assertionRoundTable = new BeanTable<>(AssertionRoundBean.class, (PreferencesExt) prefs.node("assertionRoundTable"), false,
                "AuditRounds", "AuditRoundResult", null);
        assertionRoundTable.addPopupOption("Show AuditRoundResult", assertionRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> bean.toString()));
        setFontSize(fontSize);

        // layout of tables
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));
        split3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, assertionRoundTable);
        split3.setDividerLocation(prefs.getInt("splitPos3", 200));
        setLayout(new BorderLayout());
        add(split3, BorderLayout.CENTER);
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
        assertionRoundTable.setFontSize(size);
    }

    void setSelected(String wantRecordDir) {
            setAuditRecord(wantRecordDir);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        this.totalMvrs = 0;

        try {
            this.auditConfig = auditRecord.getAuditConfig();

            java.util.Map<Integer, ContestUnderAudit> contests = new TreeMap<>(); // sorted
            for (var round : auditRecord.getRounds()) {
                for (var contest : round.getState().getContests()) {
                    contests.put(contest.getId(), contest); // get the last time it appears in a round
                }
                this.totalMvrs += round.getNewSamples().size();
            }
            java.util.List<ContestBean> beanList = new ArrayList<>();
            for (var contest : contests.values()) {
                beanList.add(new ContestBean(contest));
            }
            contestTable.setBeans(beanList);

            // select contest with smallest margin
            ContestBean minByMargin = beanList
                    .stream()
                    .min(Comparator.comparing(ContestBean::getReportedMargin))
                    .orElseThrow(NoSuchElementException::new);
            contestTable.setSelectedBean(minByMargin);

            this.lastAuditState = auditRecord.getRounds().getLast().getState();
            this.cvrs = new ArrayList<>(this.auditRecord.getCvrs());
            this.totalBallots = cvrs.size();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
        return true;
    }

    /* boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        try {
            var publisher = new Publisher(auditRecordLocation);

            var auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile());
            this.auditConfig = unwrap(auditConfigResult);

            if (auditConfig.getAuditType() == AuditType.CLCA) {
                var cvrs = unwrap(readCvrsJsonFile(publisher.cvrsFile()));
                this.totalBallots = cvrs.size();
            } else {
                var manifest = unwrap(readBallotManifestJsonFile(publisher.ballotManifestFile()));
                this.totalBallots = manifest.getBallots().size();
            }

            this.totalMvrs = 0;
            java.util.Map<Integer, ContestUnderAudit> contests = new TreeMap<>(); // sorted
            for (int roundIdx = 1; roundIdx <= publisher.rounds(); roundIdx++) {
                //println("Round $roundIdx ------------------------------------")
                this.lastAuditState = unwrap(readAuditStateJsonFile(publisher.auditRoundFile(roundIdx)));
                totalMvrs += this.lastAuditState.getNewMvrs();
                for (var contest : this.lastAuditState .getContests()) {
                    contests.put(contest.getId(), contest); // get the last time it appears in a round
                }
            }
            java.util.List<ContestBean> beanList = new ArrayList<>();
            for (var contest : contests.values()) {
                beanList.add(new ContestBean(contest));
            }
            contestTable.setBeans(beanList);

            // select contest with smallest margin
            ContestBean minByMargin = beanList
                    .stream()
                    .min(Comparator.comparing(ContestBean::getReportedMargin))
                    .orElseThrow(NoSuchElementException::new);
            contestTable.setSelectedBean(minByMargin);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
        return true;
    } */

    void setSelectedContest(ContestBean contestBean) {
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (Assertion a : contestBean.contestUA.assertions()) {
            beanList.add(new AssertionBean(a));
        }
        assertionTable.setBeans(beanList);

        // select assertion with smallest margin
        AssertionBean minByMargin = beanList
                .stream()
                .min(Comparator.comparing(AssertionBean::getReportedMargin))
                .orElseThrow(NoSuchElementException::new);
        assertionTable.setSelectedBean(minByMargin);
    }

    void setSelectedAssertion(AssertionBean assertionBean) {
        java.util.List<AssertionRoundBean> beanList = new ArrayList<>();
        for (AuditRoundResult a : assertionBean.assertion.getRoundResults()) {
            beanList.add(new AssertionRoundBean(a));
        }
        assertionRoundTable.setBeans(beanList);
    }

    void save() {
        contestTable.saveState(false);
        assertionTable.saveState(false);
        assertionRoundTable.saveState(false);

        prefs.putInt("splitPos2", split2.getDividerLocation());
        prefs.putInt("splitPos3", split3.getDividerLocation());
    }

    public class ContestBean {
        ContestUnderAudit contestUA;

        public ContestBean() {
        }

        ContestBean(ContestUnderAudit contest) {
            this.contestUA = contest;
        }

        public String getName() {
            return contestUA.getName();
        }

        public Integer getId() {
            return contestUA.getId();
        }

        public String getChoiceFunction() {
            return contestUA.getChoiceFunction().toString();
        }

        public Integer getNCand() {
            return contestUA.getNcandidates();
        }

        public Integer getNc() {
            return contestUA.getNc();
        }

        public Integer getNp() {
            return contestUA.getNp();
        }

        public Integer getEstMvrs() {
            return contestUA.getEstMvrs();
        }

        public boolean isSuccess() {
            return contestUA.getStatus().getSuccess();
        }

        public String getStatus() {
            return contestUA.getStatus().toString();
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

        public Integer getCompleted() {
            if (!contestUA.getStatus().getComplete()) return 0;
            int round = 0;
            for (Assertion assertion : contestUA.assertions()) {
                round = max(round, assertion.getRound());
            }
            return round;
        }
    }

    public class AssertionBean {
        Assertion assertion;

        public AssertionBean() {
        }

        AssertionBean(Assertion assertion) {
            this.assertion = assertion;
        }

        public String getDesc() {
            return assertion.getAssorter().desc();
        }

        public Integer getEstSampleSize() {
            return assertion.getEstSampleSize();
        }

        public Integer getCompleted() {
            return assertion.getRound();
        }

        public String getStatus() {
            return assertion.getStatus().toString();
        }

        public double getReportedMargin() {
           return assertion.getAssorter().reportedMargin();
        }
    }

    public class AssertionRoundBean {
        AuditRoundResult auditRound;

        public AssertionRoundBean() {
        }

        AssertionRoundBean(AuditRoundResult auditRound) {
            this.auditRound = auditRound;
        }

        public Integer getRound() {
            return auditRound.getRoundIdx();
        }

        public Integer getEstSampleSize() {
            return auditRound.getEstSampleSize();
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

        public Integer getSamplesUsed() {
            return auditRound.getSamplesUsed();
        }

        public Integer getSamplesExtra() {
            return (auditRound.getEstSampleSize() - auditRound.getSamplesUsed());
        }

        public String getStatus() {
            return auditRound.getStatus().toString();
        }

        public Double getMeasuredMargin() {
            return mean2margin(auditRound.getMeasuredMean());
        }

        /*


        public String getMeasuredErrors() {
            var er =  auditRound.getMeasuredRates();
            if (er == null) {
                return "N/A";
            } else {
                return er.toString();
            }
        }

        public String getAprioriErrors() {
            var er =  auditRound.getStartingRates();
            if (er == null) {
                return "N/A";
            } else {
                return er.toString();
            }
        }

         */
    }

}
