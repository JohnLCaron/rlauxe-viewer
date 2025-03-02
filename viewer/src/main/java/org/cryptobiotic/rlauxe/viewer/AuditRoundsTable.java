/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditRecord;
import org.cryptobiotic.rlauxe.audit.AuditRound;
import org.cryptobiotic.rlauxe.audit.ContestRound;
import org.cryptobiotic.rlauxe.core.*;
import org.cryptobiotic.rlauxe.persist.json.Publisher;
import org.cryptobiotic.rlauxe.workflow.*;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import org.cryptobiotic.rlauxe.bridge.RlauxWorkflowProxyBridge;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

import static java.lang.Math.max;
import static org.cryptobiotic.rlauxe.persist.json.AuditStateJsonKt.writeAuditStateJsonFile;
import static org.cryptobiotic.rlauxe.persist.json.SampleIndicesJsonKt.writeSampleIndicesJsonFile;
import static org.cryptobiotic.rlauxe.util.QuantileKt.probability;
import static org.cryptobiotic.rlauxe.util.UtilsKt.mean2margin;

public class AuditRoundsTable extends JPanel {
    private final PreferencesExt prefs;

    private final BeanTable<AuditStateBean> auditStateTable;
    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;
    private final BeanTable<AuditRoundBean> auditRoundTable;
    private final BeanTable<EstimationRoundBean> estRoundTable;

    private final JSplitPane split1, split2, split3, split4;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    private AuditConfig auditConfig;
    private AuditState lastAuditState;
    private List<BallotOrCvr> cvrs;
    List<Integer> sampleIndices = new ArrayList<>();
    int totalMvrs = 0;

    public AuditRoundsTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        auditStateTable = new BeanTable<>(AuditStateBean.class, (PreferencesExt) prefs.node("auditStateTable"), false,
                "Audit Rounds", "AuditState", new AuditStateBean());
        auditStateTable.addListSelectionListener(e -> {
            AuditStateBean state = auditStateTable.getSelectedBean();
            if (state != null) {
                setSelectedAuditRound(state);
            }
        });
        auditStateTable.addPopupOption("Write AuditState", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                writeAuditState();
            }
        });

        //   public BeanTable(Class<T> bc, PreferencesExt pstore, boolean canAddDelete, String header, String tooltip, T bean) {
        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contests", "ContestUnderAudit", new ContestBean());
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

        auditRoundTable = new BeanTable<>(AuditRoundBean.class, (PreferencesExt) prefs.node("assertionRoundTable"), false,
                "AuditRounds", "AuditRoundResult", null);
        auditRoundTable.addPopupOption("Show AuditRoundResult", auditRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> bean.toString()));

        estRoundTable = new BeanTable<>(EstimationRoundBean.class, (PreferencesExt) prefs.node("estRoundTable"), false,
                "EstimationRounds", "EstimationRoundResult", null);
        estRoundTable.addPopupOption("Show EstimationRoundResult", auditRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> bean.toString()));

        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, auditStateTable, contestTable);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 400));
        split3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, estRoundTable);
        split3.setDividerLocation(prefs.getInt("splitPos3", 600));
        split4 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split3, auditRoundTable);
        split4.setDividerLocation(prefs.getInt("splitPos4", 800));
        setLayout(new BorderLayout());
        add(split4, BorderLayout.CENTER);
    }

    public void setFontSize(float size) {
        auditStateTable.setFontSize(size);
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
        auditRoundTable.setFontSize(size);
        estRoundTable.setFontSize(size);
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
            java.util.List<AuditStateBean> beanList = new ArrayList<>();
            for (var round : auditRecord.getRounds()) {
                //println("Round $roundIdx ------------------------------------")
                beanList.add(new AuditStateBean(round));
                this.totalMvrs += round.getNewSamples().size();
            }
            auditStateTable.setBeans(beanList);
            this.lastAuditState = auditRecord.getRounds().getLast().getState();
            this.cvrs = new ArrayList<>(this.auditRecord.getCvrs());

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
        return true;
    }

    void writeAuditState() {
        if (sampleIndices.isEmpty()) return;

        AuditStateBean lastBean = auditStateTable.getBeans().getLast();
        AuditState lastState = lastBean.state;
        int roundIdx = lastState.getRoundIdx();
        var state = new AuditState(lastState.getName(), roundIdx, lastBean.nmvrs,
                false, false, lastState.getContests());

        var publisher = new Publisher(auditRecordLocation);
        writeAuditStateJsonFile(state, publisher.auditRoundFile(roundIdx));
        System.out.printf("   writeAuditStateJsonFile to %s%n", publisher.auditRoundFile(roundIdx));
        writeSampleIndicesJsonFile(sampleIndices, publisher.sampleIndicesFile(roundIdx));
        System.out.printf("   writeSampleIndicesJsonFile to %s%n", publisher.sampleIndicesFile(roundIdx));
    }

    //////////////////////////////////////////////////////////////////
    ///
    void showInfo(Formatter f) {
        if (this.auditRecordLocation.equals("none")) { return; }
        int totalBallots = this.auditRecord.getCvrs().size();

        f.format("Audit record at %s%n", this.auditRecordLocation);
        f.format("%s%n", this.auditConfig);
        f.format(" total Ballots = %d%n", totalBallots);
        f.format(" total Mvrs = %d%n", this.totalMvrs);
        f.format(" Mvrs/total = %d %% %n", (int) ((100.0 * this.totalMvrs)/totalBallots));

        int totalExtra = 0;
        for (AuditRound round : auditRecord.getRounds()) {
            if (round.getState().getAuditWasDone()) {
                int roundIdx = round.getState().getRoundIdx();
                f.format("%n maxBallotIndexUsed in round %d = %d %n", roundIdx, round.maxBallotsUsed());
                int nmvrs = round.getSampledIndices().size();
                f.format(" number of Mvrs in round %d = %d %n", roundIdx, nmvrs);
                int extra = nmvrs - round.maxBallotsUsed();
                f.format(" extraBallotsUsed = %d %n", extra);
                totalExtra += extra;
            }
        }
        f.format("total extraBallotsUsed = %d %n", totalExtra);

    }

    //////////////////////////////////////////////////////////////////

    void setSelectedAuditRound(AuditStateBean auditBean) {
        java.util.List<ContestBean> beanList = new ArrayList<>();
        for (ContestRound c : auditBean.round.getContests()) {
            beanList.add(new ContestBean(c, auditBean.getRound()));
        }
        contestTable.setBeans(beanList);

        // select contest with smallest margin
        ContestBean minByMargin = beanList
                .stream()
                .min(Comparator.comparing(ContestBean::getMinMargin))
                .orElseThrow(NoSuchElementException::new);
        contestTable.setSelectedBean(minByMargin);
    }

    void setSelectedContest(ContestBean contestBean) {
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (Assertion a : contestBean.contestUA.assertions()) {
            beanList.add(new AssertionBean(a));
        }
        assertionTable.setBeans(beanList);

        // select assertion with smallest margin
        AssertionBean minByMargin = beanList
                .stream()
                .min(Comparator.comparing(AssertionBean::getMargin))
                .orElseThrow(NoSuchElementException::new);
        assertionTable.setSelectedBean(minByMargin);
    }

    void setSelectedAssertion(AssertionBean assertionBean) {
        java.util.List<AuditRoundBean> auditList = new ArrayList<>();
        for (AuditRoundResult a : assertionBean.assertion.getRoundResults()) {
            auditList.add(new AuditRoundBean(a));
        }
        auditRoundTable.setBeans(auditList);

        java.util.List<EstimationRoundBean> estList = new ArrayList<>();
        for (EstimationRoundResult a : assertionBean.assertion.getEstRoundResults()) {
            estList.add(new EstimationRoundBean(a));
        }
        estRoundTable.setBeans(estList);
    }

    void save() {
        auditStateTable.saveState(false);
        contestTable.saveState(false);
        assertionTable.saveState(false);
        auditRoundTable.saveState(false);
        estRoundTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
        prefs.putInt("splitPos2", split2.getDividerLocation());
        prefs.putInt("splitPos3", split3.getDividerLocation());
        prefs.putInt("splitPos4", split4.getDividerLocation());
    }

    void includeChanged() {
        if (auditStateTable.getBeans().isEmpty()) return;
        AuditStateBean lastBean = auditStateTable.getBeans().getLast();
        int wantedNewMvrs = lastBean.wantedNewMvrs;

        RlauxWorkflowProxyBridge bridge = new RlauxWorkflowProxyBridge(
                this.auditConfig,
                this.lastAuditState.getContests(),
                this.cvrs);
        this.sampleIndices = bridge.createSampleIndicesBridge( this.lastAuditState.getRoundIdx(), wantedNewMvrs );
        System.out.printf("createSampleIndicesBridge returned = %d indices%n", this.sampleIndices.size());

        AuditRound lastRound = lastBean.round;
        lastRound.resetSamples(sampleIndices, this.auditRecord.getCvrs());
        lastBean.setNmvrs(this.sampleIndices.size());
        auditStateTable.refresh();
        contestTable.refresh();
    }

    ////////////////////////////////////////////////////////////////////////////
    public class AuditStateBean {
        AuditRound round;
        AuditState state;
        int nmvrs;
        int wantedNewMvrs;

        public AuditStateBean() {}
        AuditStateBean(AuditRound round) {
            this.round = round;
            this.state = round.getState();
            this.nmvrs = state.getNmvrs();
            this.wantedNewMvrs = -1;
        }

        // editable properties
        static public String editableProperties() { return "wantedNewMvrs"; }

        public boolean canedit() {
            return (lastAuditState != null) && (state != null ) &&
                    (state.getRoundIdx() == lastAuditState.getRoundIdx());
        }

        public Integer getRound() {
            return state.getRoundIdx();
        }

        // TODO needed?
        public void setNmvrs(int nmvrs) {
            this.nmvrs = nmvrs;
        }

        public Integer getNmvrs() {
            return round.getSampledIndices().size();
        }

        public Integer getNewMvrs() {
            return round.getNewSamples().size();
        }

        public int getWantedNewMvrs() { return wantedNewMvrs; }
        public void setWantedNewMvrs( int wantedNewMvrs) {
            if (this.wantedNewMvrs != wantedNewMvrs) {
                this.wantedNewMvrs = wantedNewMvrs;
                includeChanged(); // queued event might be better
            }
        }

        public Integer getMaxBallotsUsed() {
            return round.maxBallotsUsed();
        }

        public boolean isAuditWasDone() {
            return state.getAuditWasDone();
        }

        public boolean isAuditIsComplete() {
            return state.getAuditIsComplete();
        }
    }

    public class ContestBean {
        ContestRound contestRound;
        ContestUnderAudit contestUA;
        int auditRound; // only last row can be edited
        TestH0Status initialStatus; // only last row can be edited

        public ContestBean() {}
        ContestBean(ContestRound contestRound, int auditRound) {
            this.contestRound = contestRound;
            this.auditRound = auditRound;
            this.contestUA = contestRound.getContestUA();
            this.initialStatus = contestUA.getStatus();
        }

        // editable properties
        static public String editableProperties() { return "include done"; }
        public boolean canedit() {
            return (lastAuditState != null)
                    && (!lastAuditState.getAuditWasDone())
                    && (auditRound == lastAuditState.getRoundIdx());
        }

        public boolean isInclude() { return contestUA.getIncluded(); }
        public void setInclude(boolean include) {
            boolean oldState = contestUA.getIncluded();
            if (oldState != include) {
                contestUA.setIncluded(include);
                includeChanged(); // queued event might be better
            }
            // if (!include) { contestUA.setDone(true); }
        }
        public boolean isDone() { return contestUA.getDone(); }
        public void setDone(boolean done) {
            contestUA.setDone(done);
            if (done) contestUA.setStatus(TestH0Status.AuditorRemoved);
            else contestUA.setStatus(initialStatus);
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

        public Integer getEstNmvrs() {
            return contestUA.getEstMvrs();
        }
        public Integer getActualMvrs() {return contestRound.getActualMvrs(); }
        public Integer getActualNewNmvrs() { return contestRound.getActualNewMvrs(); }

        public boolean isSuccess() {
            return contestUA.getStatus().getSuccess();
        }

        public int getProbSuccess() {
            var minAssertion = contestUA.minAssertion();
            if (minAssertion != null && (minAssertion.getEstRoundResults().size() >= auditRound)) {
                var er = minAssertion.getEstRoundResults().get(auditRound-1);
                var dist = er.getEstimatedDistribution();
                if (getActualNewNmvrs() < 0) return 0; else {
                    var actualNM = getActualNewNmvrs();
                    var prob = probability(dist, getActualNewNmvrs());
                    return probability(dist, getActualNewNmvrs());
                }
            }
            return contestUA.getEstMvrs();
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

        public Integer getEstSampleSize() {return assertion.getEstSampleSize();}

        public Integer getCompleted() {
            return assertion.getRound();
        }

        public String getStatus() {
            return assertion.getStatus().toString();
        }

        public double getMargin() {
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
    public class AuditRoundBean {
        AuditRoundResult auditRound;

        public AuditRoundBean() {
        }

        AuditRoundBean(AuditRoundResult auditRound) {
            this.auditRound = auditRound;
        }

        public Integer getRound() {
            return auditRound.getRoundIdx();
        }

        public Integer getEstSampleSize() {
            return auditRound.getEstSampleSize();
        }

        public Integer getMaxBallotIndexUsed() {
            return auditRound.getMaxBallotIndexUsed();
        }

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
    }

    // data class EstimationRoundResult(
//    val roundIdx: Int,
//    val sampleDeciles: List<Int>,   // distribution of estimated sample size as deciles
//    val fuzzPct: Double, // measured population mean
//    val startingRates: ClcaErrorRates? = null, // aprioti error rates (clca only)
    public class EstimationRoundBean {
        EstimationRoundResult round;

        public EstimationRoundBean() {
        }

        EstimationRoundBean(EstimationRoundResult round) {
            this.round = round;
        }

        public Integer getRound() {
            return round.getRoundIdx();
        }

        public String getStrategy() {
            return round.getStrategy();
        }

        public Double getStartingPvalue() {
            var t = round.getStartingTestStatistic();
            if (t == 0.0) return 0.0; else return 1.0/t;
        }

        public Double getFuzzPct() {
            return round.getFuzzPct();
        }

        public String getEstimatedDistribution() {
            return round.getEstimatedDistribution().toString();
        }

        public String getStartingErrors() {
            var er =  round.getStartingRates();
            if (er == null) {
                return "N/A";
            } else {
                return er.toString();
            }
        }
    }

}
