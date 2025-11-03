/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.core.*;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.workflow.MvrManagerTestFromRecord;
import org.cryptobiotic.rlauxe.persist.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static org.cryptobiotic.rlauxe.persist.json.AuditRoundJsonKt.writeAuditRoundJsonFile;
import static org.cryptobiotic.rlauxe.persist.json.SamplePrnsJsonKt.writeSamplePrnsJsonFile;
import static org.cryptobiotic.rlauxe.util.DecilesKt.probability;
import static org.cryptobiotic.rlauxe.util.UtilsKt.dfn;
import static org.cryptobiotic.rlauxe.util.UtilsKt.mean2margin;

public class AuditRoundsTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(AuditRoundsTable.class);

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
    private AuditRound lastAuditRound; // may be null

    void turnOffIncluded() {
        AuditRoundBean lastRoundBean = auditStateTable.getBeans().getLast();
        if (lastRoundBean != null) {
            java.util.List<ContestBean> beanList = new ArrayList<>();
            for (ContestRound c : lastRoundBean.round.getContestRounds()) {
                c.setIncluded(false);
                var contestBean = new ContestBean(c, lastRoundBean.getRound());
                beanList.add(new ContestBean(c, lastRoundBean.getRound()));
            }
            contestTable.setBeans(beanList);
            setSelectedAuditRound(lastRoundBean);
            contestTable.refresh();
        }
    }

    public AuditRoundsTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        auditStateTable = new BeanTable<>(AuditRoundBean.class, (PreferencesExt) prefs.node("auditStateTable"), false,
                "Audit Rounds", "AuditRound", new AuditRoundBean());
        auditStateTable.addListSelectionListener(e -> {
            AuditRoundBean round = auditStateTable.getSelectedBean();
            if (round != null) {
                setSelectedAuditRound(round);
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
                "Audit Results", "AuditRoundResult", null);
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
        logger.debug("AuditRoundsTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;

        try {
            this.auditConfig = auditRecord.getConfig();
            java.util.List<AuditRoundBean> beanList = new ArrayList<>();
            for (var round : auditRecord.getRounds()) {
                beanList.add(new AuditRoundBean(round));
            }
            auditStateTable.setBeans(beanList);
            if (!auditRecord.getRounds().isEmpty()) {
                this.lastAuditRound = auditRecord.getRounds().getLast();
            }

            contestTable.setBeans(new ArrayList<>());
            assertionTable.setBeans(new ArrayList<>());
            auditRoundTable.setBeans(new ArrayList<>());
            estRoundTable.setBeans(new ArrayList<>());

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("AuditRoundsTable.setAuditRecord failed", e);
        }
        return true;
    }

    void writeAuditState() {
        // if (sampleIndices.isEmpty()) return;

        AuditRoundBean lastBean = auditStateTable.getBeans().getLast();
        AuditRound lastRound = lastBean.round;
        int roundIdx = lastRound.getRoundIdx();

        var publisher = new Publisher(auditRecordLocation);
        writeAuditRoundJsonFile(lastRound, publisher.auditRoundFile(roundIdx));
        logger.info(String.format("   write auditRoundFile to %s%n", publisher.auditRoundFile(roundIdx)));
        writeSamplePrnsJsonFile(lastRound.getSamplePrns(), publisher.samplePrnsFile(roundIdx));
        logger.info(String.format("   write sampleNumbersFile to %s%n", publisher.samplePrnsFile(roundIdx)));
    }

    //////////////////////////////////////////////////////////////////
    ///
    void showInfo(Formatter f) {
        if (this.auditRecordLocation.equals("none")) { return; }
        // int totalBallots = this.auditRecord.getCvrs().size(); TODO

        f.format("Audit record at %s%n", this.auditRecordLocation);
        f.format("%s%n", this.auditConfig);
        // f.format(" total Ballots = %d%n", totalBallots);
        if (this.lastAuditRound != null) {
            f.format(" total Mvrs = %d%n", this.lastAuditRound.getNmvrs());
        }
        // f.format(" Mvrs/total = %d %% %n", (int) ((100.0 * this.lastAuditRound.getNmvrs())/Math.max(1,totalBallots)));

        int totalExtra = 0;
        for (AuditRound round : auditRecord.getRounds()) {
            if (round.getAuditWasDone()) {
                int roundIdx = round.getRoundIdx();
                f.format("%n maxBallotIndexUsed in round %d = %d %n", roundIdx, round.maxBallotsUsed());
                int nmvrs = round.getSamplePrns().size();
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
        for (ContestRound c : auditBean.round.getContestRounds()) {
            beanList.add(new ContestBean(c, auditBean.getRound()));
        }
        contestTable.setBeans(beanList);

        if (beanList.isEmpty()) return;

        // select contest with smallest margin
        ContestBean minByMargin = beanList
                .stream()
                .min(Comparator.comparing(ContestBean::getMargin))
                .orElseThrow(NoSuchElementException::new);
        contestTable.setSelectedBean(minByMargin);
    }

    void setSelectedContest(ContestBean contestBean) {
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (AssertionRound a : contestBean.contestRound.getAssertionRounds()) {
            beanList.add(new AssertionBean(contestBean, a));
        }
        assertionTable.setBeans(beanList);

        if (beanList.isEmpty()) return;

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

            for (ContestRound contestRound : auditRound.getContestRounds()) {
                if (contestRound.getContestUA().equals(assertionBean.contestBean.contestUA)) {
                    for (AssertionRound assertionRound : contestRound.getAssertionRounds()) {
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

    void resample() {
        if (this.lastAuditRound == null) {
            logger.info("there is no audit round to resample");
            return;
        }

        int nrounds = auditRecord.getRounds().size();
        if (nrounds == 0) return;
        Set<Long> previousSamples = org.cryptobiotic.rlauxe.audit.AuditRoundKt.previousSamples(auditRecord.getRounds(), nrounds);

        RlauxWorkflowProxy bridge = new RlauxWorkflowProxy(
                this.auditConfig,
                new MvrManagerTestFromRecord(auditRecord.getLocation())
        );

        AuditRoundBean lastBean = auditStateTable.getBeans().getLast();

        logger.info(String.format("call sample() with auditorWantNewMvrs = %d previousSamples = %d %n",
                this.lastAuditRound.getAuditorWantNewMvrs(), previousSamples.size()));
        bridge.sample( this.lastAuditRound, previousSamples);
        logger.info(String.format("  sample() = %d mvrs with newmvrs=%d %n", this.lastAuditRound.getSamplePrns().size(), this.lastAuditRound.getNewmvrs()));

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
        static public String editableProperties() { return "wantNewMvrs"; }

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

        public int getWantNewMvrs() { return round.getAuditorWantNewMvrs(); }
        public void setWantNewMvrs( int wantedNewMvrs) {
            if (round.getAuditorWantNewMvrs() != wantedNewMvrs) {
                round.setAuditorWantNewMvrs(wantedNewMvrs);
                // resample();
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
            sb.append("sample size = %d%n".formatted(round.getSamplePrns().size()));
            sb.append("nmvrs = %d%n".formatted(round.getNmvrs()));
            sb.append("newmvrs = %d%n".formatted(round.getNewmvrs()));
            sb.append("auditorWantNewMvrs = %d%n".formatted(round.getAuditorWantNewMvrs()));
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
        static public String editableProperties() { return "wantNewMvrs include done"; }
        public boolean canedit() {
            return (lastAuditRound != null)
                    && (!lastAuditRound.getAuditWasDone())
                    && (auditRound == lastAuditRound.getRoundIdx());
        }

        public int getWantNewMvrs() { return contestRound.getAuditorWantNewMvrs(); }
        public void setWantNewMvrs( int wantedNewMvrs) {
            if (contestRound.getAuditorWantNewMvrs() != wantedNewMvrs) {
                contestRound.setAuditorWantNewMvrs(wantedNewMvrs);
                // resample();
            }
        }

        public boolean isInclude() { return contestRound.getIncluded(); }
        public void setInclude(boolean include) {
            boolean oldState = contestRound.getIncluded();
            if (oldState != include) {
                contestRound.setIncluded(include);
                // resample(); // queued event might be better
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

        public int getMvrsUsed() {
            int maxUsed = 0;
            for (AssertionRound assertionRound : contestRound.getAssertionRounds()) {
                AuditRoundResult auditResult = assertionRound.getAuditResult();
                if (auditResult != null) {
                    if (auditResult.getSamplesUsed() > maxUsed) maxUsed = auditResult.getSamplesUsed();
                }
            }
            return maxUsed;
        }

        public double getRecountMargin() {
            return contestUA.minRecountMargin();
        }

        public Integer getCorlaEst() {
            return contestUA.getContest().info().getMetadata().get("CORLAsample");
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
            sb.append("\ncontest = %s%n".formatted(contestUA.show()));
            return sb.toString();
        }
    }

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
            return contestBean.contestUA.getContest().showAssertionDifficulty(assertion.getAssorter());
        }

        public double getRecountMargin() {
            return contestBean.contestUA.getContest().recountMargin(assertion.getAssorter());
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

        public double getRisk() {
            if (assertionRound.getAuditResult() != null) {
                return assertionRound.getAuditResult().getPvalue();
            } else {
                return Double.NaN;
            }
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(assertionRound.getRoundIdx()));
            sb.append("estSampleSize = %d%n".formatted(assertionRound.getEstSampleSize()));
            sb.append("estNewSampleSize = %d%n".formatted(assertionRound.getEstNewSampleSize()));
            sb.append("status = %s%n".formatted(Naming.status(assertionRound.getStatus())));
            sb.append("round = %d%n".formatted(assertionRound.getRound()));
            sb.append("%n assertion = %s".formatted(assertionRound.getAssertion().show()));
            sb.append("%n assorter = %s".formatted(assertionRound.getAssertion().getAssorter().toString()));
            sb.append("%n diff: %s".formatted(contestBean.contestUA.getContest().showAssertionDifficulty(assertion.getAssorter())));
            if (assertionRound.getPrevAuditResult() != null) sb.append("%nprevAuditResult = %s".formatted(assertionRound.getPrevAuditResult().toString()));
            if (assertionRound.getEstimationResult() != null) sb.append("%nestimationResult = %s".formatted(assertionRound.getEstimationResult().toString()));
            if (assertionRound.getAuditResult() != null) sb.append("%nauditResult = %s".formatted(assertionRound.getAuditResult().toString()));
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

        public Integer getFirstSample() {
            return round.getFirstSample();
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
        AuditRoundResult auditResultRound;

        public AuditRoundResultBean() {
        }

        AuditRoundResultBean(AuditRoundResult auditRound) {
            this.auditResultRound = auditRound;
        }

        public Integer getRound() {
            return auditResultRound.getRoundIdx();
        }

        public Integer getMvrs() {
            return auditResultRound.getNmvrs();
        }

        public Integer getMaxBallots() {
            return auditResultRound.getMaxBallotIndexUsed();
        }

        public Double getPValue() {
            return auditResultRound.getPvalue();
        }

        public Integer getMvrsUsed() {
            return auditResultRound.getSamplesUsed();
        }

        public Integer getMvrsExtra() {
            return ( Math.max(0, auditResultRound.getNmvrs() - auditResultRound.getSamplesUsed()));
        }

        public String getStatus() {
            return Naming.status(auditResultRound.getStatus());
        }

        public String getMeasuredMargin() {
            return dfn(mean2margin(auditResultRound.getMeasuredMean()), 5);
        }

        public String getMeasuredErrors() {
            var er =  auditResultRound.getMeasuredRates();
            if (er == null) {
                return "N/A";
            } else {
                return er.toString();
            }
        }

        public String getEstErrors() {
            var er =  auditResultRound.getStartingRates();
            if (er == null) {
                return "N/A";
            } else {
                return er.toString();
            }
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("roundIdx = %d%n".formatted(auditResultRound.getRoundIdx()));
            sb.append("measuredMean = %f%n".formatted(auditResultRound.getMeasuredMean()));
            sb.append("measuredMargin = %f%n".formatted(mean2margin(auditResultRound.getMeasuredMean())));
            sb.append("estSampleSize = %d%n".formatted(auditResultRound.getNmvrs()));
            sb.append("samplesUsed = %d%n".formatted(auditResultRound.getSamplesUsed()));
            sb.append("samplesExtra = %d%n".formatted(getMvrsExtra()));
            sb.append("pvalue = %f%n".formatted(auditResultRound.getPvalue()));
            sb.append("status = %s%n".formatted(Naming.status(auditResultRound.getStatus())));
            if (auditResultRound.getMeasuredRates() != null) sb.append("measuredErrors = %s%n".formatted(auditResultRound.getMeasuredRates().toString()));
            if (auditResultRound.getStartingRates() != null) sb.append("aprioriErrors = %s%n".formatted(auditResultRound.getStartingRates().toString()));
            sb.append("maxBallotsUsed = %d%n".formatted(auditResultRound.getMaxBallotIndexUsed()));
            return sb.toString();
        }
    }
}
