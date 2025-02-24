/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.AuditRoundResult;
import org.cryptobiotic.rlauxe.core.Contest;
import org.cryptobiotic.rlauxe.core.ContestUnderAudit;
import org.cryptobiotic.rlauxe.persist.json.Publisher;
import org.cryptobiotic.rlauxe.workflow.AuditConfig;
import org.cryptobiotic.rlauxe.workflow.AuditState;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;

import static com.github.michaelbull.result.UnwrapKt.unwrap;
import static org.cryptobiotic.rlauxe.persist.json.AuditConfigJsonKt.readAuditConfigJsonFile;
import static org.cryptobiotic.rlauxe.persist.json.AuditStateJsonKt.readAuditStateJsonFile;

public class AuditRoundsTable extends JPanel {
    private final PreferencesExt prefs;

    private final BeanTable<AuditStateBean> auditRoundTable;
    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;
    private final BeanTable<AssertionRoundBean> assertionRoundTable;

    private final JSplitPane split1, split2, split3;

    private String auditRecordLocation = "none";
    private AuditConfig auditConfig;

    public AuditRoundsTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        auditRoundTable = new BeanTable<>(AuditStateBean.class, (PreferencesExt) prefs.node("auditStateTable"), false,
                "Audit Rounds", "AuditState", null);
        auditRoundTable.addListSelectionListener(e -> {
            AuditStateBean state = auditRoundTable.getSelectedBean();
            if (state != null) {
                setSelectedAuditRound(state);
            }
        });
        auditRoundTable.addPopupOption("Show AuditState", auditRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AuditStateBean) bean).toString()));

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
                "AssertionRound", "AuditRoundResult", null);
        assertionRoundTable.addPopupOption("Show AuditRoundResult", assertionRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> bean.toString()));
        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, auditRoundTable, contestTable);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));
        split3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, assertionRoundTable);
        split3.setDividerLocation(prefs.getInt("splitPos3", 200));
        setLayout(new BorderLayout());
        add(split3, BorderLayout.CENTER);
    }

    public void setFontSize(float size) {
        auditRoundTable.setFontSize(size);
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
        assertionRoundTable.setFontSize(size);
    }

    void setSelected(String wantRecordDir) {
        if (!Objects.equals(wantRecordDir, auditRecordLocation)) {
            setAuditRecord(wantRecordDir);
        }
    }

    boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        try {
            var publisher = new Publisher(auditRecordLocation);
            var auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile());
            this.auditConfig = unwrap(auditConfigResult);

            //     for (int i = listeners.length - 2; i >= 0; i -= 2) {
            var totalMvrs = 0;
            java.util.List<AuditStateBean> beanList = new ArrayList<>();
            for (int roundIdx = 1; roundIdx <= publisher.rounds(); roundIdx++) {
                //println("Round $roundIdx ------------------------------------")
                AuditState state = unwrap(readAuditStateJsonFile(publisher.auditRoundFile(roundIdx)));
                totalMvrs += state.getNewMvrs();
                beanList.add(new AuditStateBean(state));
            }
            auditRoundTable.setBeans(beanList);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
        return true;
    }


    //////////////////////////////////////////////////////////////////

    void setSelectedAuditRound(AuditStateBean auditBean) {
        java.util.List<ContestBean> beanList = new ArrayList<>();
        for (ContestUnderAudit c : auditBean.state.getContests()) {
            beanList.add(new ContestBean(c));
        }
        contestTable.setBeans(beanList);
        assertionTable.setBeans(new ArrayList<>());
        assertionRoundTable.setBeans(new ArrayList<>());
    }

    void setSelectedContest(ContestBean contestBean) {
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (Assertion a : contestBean.contestUA.assertions()) {
            beanList.add(new AssertionBean(a));
        }
        assertionTable.setBeans(beanList);
        assertionRoundTable.setBeans(new ArrayList<>());
    }

    void setSelectedAssertion(AssertionBean assertionBean) {
        java.util.List<AssertionRoundBean> beanList = new ArrayList<>();
        for (AuditRoundResult a : assertionBean.assertion.getRoundResults()) {
            beanList.add(new AssertionRoundBean(a));
        }
        assertionRoundTable.setBeans(beanList);
    }

    void save() {


        auditRoundTable.saveState(false);
        contestTable.saveState(false);
        assertionTable.saveState(false);
        assertionRoundTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
        prefs.putInt("splitPos2", split2.getDividerLocation());
        prefs.putInt("splitPos3", split3.getDividerLocation());
    }

    //     val name: String,
    //    val roundIdx: Int,
    //    val nmvrs: Int,
    //    val newMvrs: Int,
    //    val auditWasDone: Boolean,
    //    val auditIsComplete: Boolean,
    //    val contests: List<ContestUnderAudit>,
    public static class AuditStateBean {
        AuditState state;

        public AuditStateBean() {
        }

        AuditStateBean(AuditState state) {
            this.state = state;
        }

        public Integer getRound() {
            return state.getRoundIdx();
        }

        public Integer getNmvrs() {
            return state.getNmvrs();
        }

        public Integer getNewMvrs() {
            return state.getNewMvrs();
        }

        public boolean isAuditWasDone() {
            return state.getAuditWasDone();
        }

        public boolean isAuditIsComplete() {
            return state.getAuditIsComplete();
        }
    }

    // open class ContestUnderAudit(
    //    val contest: ContestIF,
    //    val isComparison: Boolean = true, // TODO change to AuditType?
    //    val hasStyle: Boolean = true,
    //) {
    //    val id = contest.info.id
    //    val name = contest.info.name
    //    val choiceFunction = contest.info.choiceFunction
    //    val ncandidates = contest.info.candidateIds.size
    //    val Nc = contest.Nc
    //    val Np = contest.Np
    //
    //    var pollingAssertions: List<Assertion> = emptyList()
    //    var clcaAssertions: List<ClcaAssertion> = emptyList()
    //
    //    var estSampleSize = 0 // Estimate of the sample size required to confirm the contest
    //    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformPolling (Polling, no style only)
    //    var done = false
    //    var status = TestH0Status.InProgress // or its own enum ??
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

        public Integer getNCandidate() {
            return contestUA.getNcandidates();
        }

        public Integer getNc() {
            return contestUA.getNc();
        }

        public Integer getNp() {
            return contestUA.getNp();
        }

        public Integer getEstSampleSize() {
            return contestUA.getEstSampleSize();
        }

        public boolean isDone() {
            return contestUA.getDone();
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

        public double getMinMargin() {
            var minAssertion = contestUA.minAssertion();
            if (minAssertion == null) {
                return 0.0;
            } else {
                return minAssertion.getAssorter().reportedMargin();
            }
        }
    }

    // open class Assertion(
    //    val contest: ContestIF,
    //    val assorter: AssorterFunction,
    //) {
    //    val winner = assorter.winner()
    //    val loser = assorter.loser()
    //
    //    // these values are set during estimateSampleSizes()
    //    var estSampleSize = 0   // estimated sample size for current round
    //
    //    // these values are set during runAudit()
    //    val roundResults = mutableListOf<AuditRoundResult>()
    //    var status = TestH0Status.InProgress
    //    var round = 0           // round when set to proved or disproved
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

        public Integer getRound() {
            return assertion.getRound();
        }

        public String getStatus() {
            return assertion.getStatus().toString();
        }

        public double getMinMargin() {
            return assertion.getAssorter().reportedMargin();
        }
    }

    // data class AuditRoundResult(
    //    val roundIdx: Int,
    //    val estSampleSize: Int,   // estimated sample size
    //    val maxBallotsUsed: Int,  // maximum ballot index (for multicontest audits)
    //    val pvalue: Double,       // last pvalue when testH0 terminates
    //    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    //    val samplesUsed: Int,     // sample count when testH0 terminates
    //    val status: TestH0Status, // testH0 status
    //    val errorRates: ClcaErrorRates? = null, // measured error rates (clca only)
    //)
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

        public Integer getMaxBallotsUsed() {
            return auditRound.getMaxBallotsUsed();
        }

        public Double getPValue() {
            return auditRound.getPvalue();
        }

        public Integer getSamplesNeeded() {
            return auditRound.getSamplesNeeded();
        }

        public Integer getSamplesUsed() {
            return auditRound.getSamplesUsed();
        }

        public String getStatus() {
            return auditRound.getStatus().toString();
        }

        public String getErrorRates() {
            var er =  auditRound.getErrorRates();
            if (er == null) {
                return "N/A";
            } else {
                return er.toString();
            }
        }
    }

}
