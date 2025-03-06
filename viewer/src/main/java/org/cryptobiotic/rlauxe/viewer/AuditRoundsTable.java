/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditRecord;
import org.cryptobiotic.rlauxe.workflow.AuditRound;
import org.cryptobiotic.rlauxe.workflow.ContestRound;
import org.cryptobiotic.rlauxe.core.*;
import org.cryptobiotic.rlauxe.persist.json.Publisher;
import org.cryptobiotic.rlauxe.workflow.*;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.bridge.RlauxWorkflowProxy;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

import static org.cryptobiotic.rlauxe.persist.json.AuditRoundJsonKt.writeAuditRoundJsonFile;
import static org.cryptobiotic.rlauxe.persist.json.SampleIndicesJsonKt.writeSampleIndicesJsonFile;
import static org.cryptobiotic.rlauxe.util.QuantileKt.probability;
import static org.cryptobiotic.rlauxe.util.UtilsKt.mean2margin;

public class AuditRoundsTable extends JPanel {
    private final PreferencesExt prefs;

    private final BeanTable<AuditRoundBean> auditStateTable;
    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;
    private final BeanTable<AuditRoundResultBean> auditRoundTable;
    private final BeanTable<EstimationRoundBean> estRoundTable;

    private final JSplitPane split1, split2, split3, split4;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    private AuditConfig auditConfig;
    private AuditRound lastAuditRound;
    private List<BallotOrCvr> cvrs;
    List<Integer> sampleIndices = new ArrayList<>();

    public AuditRoundsTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        auditStateTable = new BeanTable<>(AuditRoundBean.class, (PreferencesExt) prefs.node("auditStateTable"), false,
                "Audit Rounds", "AuditRound", new AuditRoundBean());
        auditStateTable.addListSelectionListener(e -> {
            AuditRoundBean state = auditStateTable.getSelectedBean();
            if (state != null) {
                setSelectedAuditRound(state);
            }
        });
        auditStateTable.addPopupOption("Write AuditRound", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                writeAuditState();
            }
        });
        auditStateTable.addPopupOption("Show AuditRound", auditStateTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AuditRoundBean)bean).show()));

        //   public BeanTable(Class<T> bc, PreferencesExt pstore, boolean canAddDelete, String header, String tooltip, T bean) {
        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contests", "ContestUnderAudit", new ContestBean());
        contestTable.addListSelectionListener(e -> {
            ContestBean contest = contestTable.getSelectedBean();
            if (contest != null) {
                setSelectedContest(contest);
            }
        });
        contestTable.addPopupOption("Show ContestRound", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> ((ContestBean)bean).show()));

        assertionTable = new BeanTable<>(AssertionBean.class, (PreferencesExt) prefs.node("assertionTable"), false,
                "Assertion", "Assertion", null);
        assertionTable.addListSelectionListener(e -> {
            AssertionBean assertion = assertionTable.getSelectedBean();
            if (assertion != null) {
                setSelectedAssertion(assertion);
            }
        });
        assertionTable.addPopupOption("Show AssertionRound", assertionTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AssertionBean)bean).show()));

        auditRoundTable = new BeanTable<>(AuditRoundResultBean.class, (PreferencesExt) prefs.node("assertionRoundTable"), false,
                "AuditRounds", "AuditRoundResult", null);
        auditRoundTable.addPopupOption("Show AuditRoundResult", auditRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AuditRoundResultBean)bean).show()));

        estRoundTable = new BeanTable<>(EstimationRoundBean.class, (PreferencesExt) prefs.node("estRoundTable"), false,
                "EstimationRounds", "EstimationRoundResult", null);
        estRoundTable.addPopupOption("Show EstimationRoundResult", estRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> ((EstimationRoundBean)bean).show()));

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

        try {
            this.auditConfig = auditRecord.getAuditConfig();
            java.util.List<AuditRoundBean> beanList = new ArrayList<>();
            for (var round : auditRecord.getRounds()) {
                beanList.add(new AuditRoundBean(round));
            }
            auditStateTable.setBeans(beanList);
            this.lastAuditRound = auditRecord.getRounds().getLast();
            this.cvrs = new ArrayList<>(this.auditRecord.getCvrs());

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
        return true;
    }

    void writeAuditState() {
        if (sampleIndices.isEmpty()) return;

        AuditRoundBean lastBean = auditStateTable.getBeans().getLast();
        AuditRound lastRound = lastBean.round;
        int roundIdx = lastRound.getRoundIdx();

        var publisher = new Publisher(auditRecordLocation);
        writeAuditRoundJsonFile(lastRound, publisher.auditRoundFile(roundIdx));
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
        f.format(" total Mvrs = %d%n", this.lastAuditRound.getNmvrs());
        f.format(" Mvrs/total = %d %% %n", (int) ((100.0 * this.lastAuditRound.getNmvrs())/Math.max(1,totalBallots)));

        int totalExtra = 0;
        for (AuditRound round : auditRecord.getRounds()) {
            if (round.getAuditWasDone()) {
                int roundIdx = round.getRoundIdx();
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

    void setSelectedAuditRound(AuditRoundBean auditBean) {
        java.util.List<ContestBean> beanList = new ArrayList<>();
        for (ContestRound c : auditBean.round.getContests()) {
            beanList.add(new ContestBean(c, auditBean.getRound()));
        }
        contestTable.setBeans(beanList);

        // select contest with smallest margin
        ContestBean minByMargin = beanList
                .stream()
                .min(Comparator.comparing(ContestBean::getMargin))
                .orElseThrow(NoSuchElementException::new);
        contestTable.setSelectedBean(minByMargin);
    }

    void setSelectedContest(ContestBean contestBean) {
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (AssertionRound a : contestBean.contestRound.getAssertions()) {
            beanList.add(new AssertionBean(contestBean, a));
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
        java.util.List<AuditRoundResultBean> auditList = new ArrayList<>();
        java.util.List<EstimationRoundBean> estList = new ArrayList<>();

        int maxRound = assertionBean.assertionRound.getRoundIdx();
        for (AuditRound auditRound : auditRecord.getRounds()) {
            if (auditRound.getRoundIdx() > maxRound) break;

            for (ContestRound contestRound : auditRound.getContests()) {
                if (contestRound.getContestUA().equals(assertionBean.contestBean.contestUA)) {
                    for (AssertionRound assertionRound : contestRound.getAssertions()) {
                        if (assertionRound.getAssertion().equals(assertionBean.assertionRound.getAssertion())) {
                            if (assertionRound.getAuditResult() != null)
                                auditList.add(new AuditRoundResultBean(assertionRound.getAuditResult()));
                            if (assertionRound.getEstimationResult() != null)
                                estList.add(new EstimationRoundBean(assertionRound.getEstimationResult()));
                        }
                    }
                }
            }
        }
        auditRoundTable.setBeans(auditList);
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
        int nrounds = auditRecord.getRounds().size();
        if (nrounds == 0) return;
        Set<Integer> previousSamples = org.cryptobiotic.rlauxe.workflow.AuditRoundKt.previousSamples(auditRecord.getRounds(), nrounds);

        RlauxWorkflowProxy bridge = new RlauxWorkflowProxy(
                this.auditConfig,
                this.cvrs);

        AuditRoundBean lastBean = auditStateTable.getBeans().getLast();

        System.out.printf("call createSampleIndices auditorSetNewMvrs = %d previousSamples = %d %n",
                this.lastAuditRound.getAuditorSetNewMvrs(), previousSamples.size());
        this.sampleIndices = bridge.createSampleIndices( this.lastAuditRound, previousSamples);
        System.out.printf("  returned = %d indices newmvrs=%d %n", this.sampleIndices.size(), this.lastAuditRound.getNewmvrs());

        auditStateTable.refresh();
        contestTable.refresh();
    }

    ////////////////////////////////////////////////////////////////////////////
    public class AuditRoundBean {
        AuditRound round;

        public AuditRoundBean() {}
        AuditRoundBean(AuditRound round) {
            this.round = round;
        }

        // editable properties
        static public String editableProperties() { return "wantedNewMvrs"; }

        public boolean canedit() {
            return (lastAuditRound != null) && (round != null ) &&
                    (!lastAuditRound.getAuditWasDone()) &&
                    (round.getRoundIdx() == lastAuditRound.getRoundIdx());
        }

        public Integer getRound() {
            return round.getRoundIdx();
        }

        public Integer getTotalMvrs() {
            return round.getNmvrs();
        }

        public Integer getNewMvrs() {
            return round.getNewmvrs();
        }

        public int getWantNewMvrs() { return round.getAuditorSetNewMvrs(); }
        public void setWantNewMvrs( int wantedNewMvrs) {
            if (round.getAuditorSetNewMvrs() != wantedNewMvrs) {
                round.setAuditorSetNewMvrs(wantedNewMvrs);
                includeChanged();
            }
        }

        public Integer getMaxBallots() {
            return round.maxBallotsUsed();
        }

        public boolean isAuditWasDone() {
            return round.getAuditWasDone();
        }

        public boolean isAuditIsComplete() {
            return round.getAuditIsComplete();
        }

        // data class AuditRound(
        //    val roundIdx: Int,
        //    val contests: List<ContestRound>,
        //
        //    val auditWasDone: Boolean = false,
        //    var auditIsComplete: Boolean = false,
        //    var sampledIndices: List<Int>, // ballots to sample for this round
        //    var nmvrs: Int = 0,
        //    var newmvrs: Int = 0,
        //) {
        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(round.getRoundIdx()));
            sb.append("sampledIndices size = %d%n".formatted(round.getSampledIndices().size()));
            sb.append("nmvrs = %d%n".formatted(round.getNmvrs()));
            sb.append("newmvrs = %d%n".formatted(round.getNewmvrs()));
            sb.append("auditorSetNewMvrs = %d%n".formatted(round.getAuditorSetNewMvrs()));
            sb.append("auditWasDone = %s%n".formatted(round.getAuditWasDone()));
            sb.append("auditIsComplete = %s%n".formatted(round.getAuditIsComplete()));
            return sb.toString();
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
            this.initialStatus = contestRound.getStatus();
        }

        // editable properties
        static public String editableProperties() { return "include done"; }
        public boolean canedit() {
            return (lastAuditRound != null)
                    && (!lastAuditRound.getAuditWasDone())
                    && (auditRound == lastAuditRound.getRoundIdx());
        }

        public boolean isInclude() { return contestRound.getIncluded(); }
        public void setInclude(boolean include) {
            boolean oldState = contestRound.getIncluded();
            if (oldState != include) {
                contestRound.setIncluded(include);
                includeChanged(); // queued event might be better
            }
            // if (!include) { contestUA.setDone(true); }
        }
        public boolean isDone() { return contestRound.getDone(); }
        public void setDone(boolean done) {
            contestRound.setDone(done);
            if (done) contestRound.setStatus(TestH0Status.AuditorRemoved);
            else contestRound.setStatus(initialStatus);
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

        public Integer getEstMvrs() {return contestRound.getEstSampleSize();}
        public Integer getEstNewMvrs() {return contestRound.getEstNewSamples();}
        public Integer getActualMvrs() {return contestRound.getActualMvrs(); }
        public Integer getActualNewMvrs() { return contestRound.getActualNewMvrs(); }

        public boolean isSuccess() {
            return contestRound.getStatus().getSuccess();
        }

        public int getProbSuccess() {
            if (getActualMvrs() == contestUA.getNc()) {
                return 100;
            }
            AssertionRound minAssertion = contestRound.minAssertion();
            if (minAssertion != null && (minAssertion.getEstimationResult() != null)) {
                var er = minAssertion.getEstimationResult();
                var dist = er.getEstimatedDistribution();
                if (getActualNewMvrs() < 0) return 0; else {
                    var actualNM = getActualNewMvrs();
                    var prob = probability(dist, getActualNewMvrs());
                    return probability(dist, getActualNewMvrs());
                }
            }
            return 0;
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

        public double getMargin() {
            var minAssertion = contestUA.minAssertion();
            if (minAssertion == null) {
                return 0.0;
            } else {
                return minAssertion.getAssorter().reportedMargin();
            }
        }

        // data class ContestRound(val contestUA: ContestUnderAudit, val assertions: List<AssertionRound>, val roundIdx: Int) {
        //    val id = contestUA.id
        //    val name = contestUA.name
        //    val Nc = contestUA.Nc
        //
        //    var actualMvrs = 0 // Actual number of ballots with this contest contained in this round's sample.
        //    var actualNewMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.
        //
        //    var estNewSamples = 0 // Estimate of the new sample size required to confirm the contest
        //    var estSampleSize = 0 // number of total samples estimated needed, consistentSampling
        //    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformSampling
        //    var done = false
        //    var included = true
        //    var status = TestH0Status.InProgress // or its own enum ??
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
            sb.append("\ncontest = %s%n".formatted(contestUA.toString()));
            return sb.toString();
        }
    }

    // data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAuditResult: AuditRoundResult?) {
    //    // these values are set during estimateSampleSizes()
    //    var estSampleSize = 0   // estimated sample size for current round
    //    var estNewSampleSize = 0   // estimated new sample size for current round
    //    var estimationResult: EstimationRoundResult? = null
    //
    //    // these values are set during runAudit()
    //    var auditResult: AuditRoundResult? = null
    //    var status = TestH0Status.InProgress
    //    var round = 0           // round when set to proved or disproved
    //}
    // open class Assertion(
    //    val contest: ContestIF,
    //    val assorter: AssorterIF,
    //) {
    // open class ClcaAssertion(
    //    contest: ContestIF,
    //    val cassorter: ClcaAssorterIF,
    //): Assertion(contest, cassorter.assorter()) {
    // interface AssorterIF {
    //    fun assort(mvr: Cvr, usePhantoms: Boolean = false) : Double
    //    fun upperBound(): Double
    //    fun desc(): String
    //    fun winner(): Int
    //    fun loser(): Int
    //    fun reportedMargin(): Double
    public class AssertionBean {
        AssertionRound assertionRound;
        ContestBean contestBean;
        Assertion assertion;

        public AssertionBean() {
        }

        AssertionBean(ContestBean contestBean, AssertionRound assertionRound) {
            this.contestBean = contestBean;
            this.assertionRound = assertionRound;
            this.assertion = assertionRound.getAssertion();
        }

        public String getDesc() {
            return assertion.getAssorter().desc();
        }

        public Integer getEstMvrs() {return assertionRound.getEstSampleSize();}
        public Integer getEstNewMvrs() {return assertionRound.getEstNewSampleSize();}

        public Integer getCompleted() {
            return assertionRound.getRound();
        }

        public String getStatus() {
            return Naming.status(assertionRound.getStatus());
        }

        public double getMargin() {
            return assertion.getAssorter().reportedMargin();
        }

        // data class AssertionRound(val assertion: Assertion, val roundIdx: Int, var prevAuditResult: AuditRoundResult?) {
        //    // these values are set during estimateSampleSizes()
        //    var estSampleSize = 0   // estimated sample size for current round
        //    var estNewSampleSize = 0   // estimated new sample size for current round
        //    var estimationResult: EstimationRoundResult? = null
        //
        //    // these values are set during runAudit()
        //    var auditResult: AuditRoundResult? = null
        //    var status = TestH0Status.InProgress
        //    var round = 0           // round when set to proved or disproved
        //}
        // open class Assertion(
        //    val contest: ContestIF,
        //    val assorter: AssorterIF,
        //) {
        // open class ClcaAssertion(
        //    contest: ContestIF,
        //    val cassorter: ClcaAssorterIF,
        //): Assertion(contest, cassorter.assorter()) {
        // interface AssorterIF {
        //    fun assort(mvr: Cvr, usePhantoms: Boolean = false) : Double
        //    fun upperBound(): Double
        //    fun desc(): String
        //    fun winner(): Int
        //    fun loser(): Int
        //    fun reportedMargin(): Double
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

    // data class EstimationRoundResult(
    //    val roundIdx: Int,
    //    val strategy: String,
    //    val fuzzPct: Double,
    //    val startingTestStatistic: Double,
    //    val startingRates: ClcaErrorRates? = null, // apriori error rates (clca only)
    //    val estimatedDistribution: List<Int>,   // distribution of estimated sample size; currently deciles
    //)
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

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(round.getRoundIdx()));
            sb.append("strategy = %s%n".formatted(round.getStrategy()));
            sb.append("fuzzPct = %f%n".formatted(round.getFuzzPct()));
            sb.append("startingTestStatistic = %f%n".formatted(round.getStartingTestStatistic()));
            sb.append("startingPValue = %f%n".formatted(1.0 / round.getStartingTestStatistic()));
            sb.append("startingErrorRates = %s%n".formatted(round.getStartingRates()));
            sb.append("estimatedDistribution = %s%n".formatted(round.getEstimatedDistribution()));
            return sb.toString();
        }
    }

    // data class AuditRoundResult(
    //    val roundIdx: Int,
    //    val estSampleSize: Int,   // estimated sample size
    //    val maxBallotIndexUsed: Int,  // maximum ballot index (for multicontest audits)
    //    val pvalue: Double,       // last pvalue when testH0 terminates
    //    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    //    val samplesUsed: Int,     // sample count when testH0 terminates
    //    val status: TestH0Status, // testH0 status
    //    val measuredMean: Double, // measured population mean
    //    val startingRates: ClcaErrorRates? = null, // apriori error rates (clca only)
    //    val measuredRates: ClcaErrorRates? = null, // measured error rates (clca only)
    //) {
    public class AuditRoundResultBean {
        AuditRoundResult auditRound;

        public AuditRoundResultBean() {
        }

        AuditRoundResultBean(AuditRoundResult auditRound) {
            this.auditRound = auditRound;
        }

        public Integer getRound() {
            return auditRound.getRoundIdx();
        }

        public Integer getMvrs() {
            return auditRound.getNmvrs();
        }

        public Integer getMaxBallots() {
            return auditRound.getMaxBallotIndexUsed();
        }

        public Double getPValue() {
            return auditRound.getPvalue();
        }

        public Integer getMvrsUsed() {
            return auditRound.getSamplesUsed();
        }

        public Integer getMvrsExtra() {
            return ( Math.max(0, auditRound.getNmvrs() - auditRound.getSamplesUsed()));
        }

        public String getStatus() {
            return Naming.status(auditRound.getStatus());
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

        public String getEstErrors() {
            var er =  auditRound.getStartingRates();
            if (er == null) {
                return "N/A";
            } else {
                return er.toString();
            }
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(auditRound.getRoundIdx()));
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
