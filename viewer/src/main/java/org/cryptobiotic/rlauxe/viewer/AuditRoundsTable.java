/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.betting.TestH0Status;
import org.cryptobiotic.rlauxe.core.*;
import org.cryptobiotic.rlauxe.estimate.ConsistentSamplingKt;
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.persist.CompositeRecord;
import org.cryptobiotic.rlauxe.persist.Publisher;
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import org.cryptobiotic.rlauxe.bridge.Naming;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;

import static org.cryptobiotic.rlauxe.audit.CreateAuditKt.writeMvrsForRound;
import static org.cryptobiotic.rlauxe.audit.RunAuditRoundKt.runRound;
import static org.cryptobiotic.rlauxe.persist.json.AuditRoundJsonKt.writeAuditRoundJsonFile;
import static org.cryptobiotic.rlauxe.persist.json.SamplePrnsJsonKt.writeSamplePrnsJsonFile;
import static org.cryptobiotic.rlauxe.util.DecilesKt.probability;

public class AuditRoundsTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(AuditRoundsTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<AuditRoundBean> auditRoundTable;
    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;
    private final BeanTable<EstimationRoundBean> estRoundTable;
    private final BeanTable<AuditRoundResultBean> auditResultTable;

    private final JSplitPane split1, split2, split3, split4;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    boolean isComposite;
    private AuditConfig auditConfig;
    private AuditRoundIF lastAuditRound; // may be null

    private boolean includeChanged = false;

    public AbstractAction mvrCall;

    void turnOffIncluded() {
        AuditRoundBean lastRoundBean = auditRoundTable.getBeans().getLast();
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

    public AuditRoundsTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize, ViewerMain.MvrAction mvrCall) {
        this.prefs = prefs;
        this.mvrCall = mvrCall;

        auditRoundTable = new BeanTable<>(AuditRoundBean.class, (PreferencesExt) prefs.node("auditStateTable"), false,
                "Audit Rounds", "AuditRound", new AuditRoundBean());
        auditRoundTable.addListSelectionListener(e -> {
            AuditRoundBean round = auditRoundTable.getSelectedBean();
            if (round != null) {
                setSelectedAuditRound(round);
            }
        });
        AbstractAction mvrAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                AuditRoundBean round = auditRoundTable.getSelectedBean();
                if (round != null) {
                    mvrCall.roundIdx = round.getRound();
                    mvrCall.actionPerformed(null);
                }
            }
        };
        auditRoundTable.addPopupOption("Show AuditRound", auditRoundTable.makeShowAction(infoTA, infoWindow, bean -> ((AuditRoundBean)bean).show()));
        auditRoundTable.addPopupOption("Show sampled Mvrs", mvrAction);

        //   public BeanTable(Class<T> bc, PreferencesExt pstore, boolean canAddDelete, String header, String tooltip, T bean) {
        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contests", "ContestWithAssertions", new ContestBean());
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

        estRoundTable = new BeanTable<>(EstimationRoundBean.class, (PreferencesExt) prefs.node("estRoundTable"), false,
                "Estimation Result", "EstimationRoundResult", null);
        estRoundTable.addPopupOption("Show EstimationRoundResult", estRoundTable.makeShowAction(infoTA, infoWindow,
                bean -> ((EstimationRoundBean)bean).show()));

        auditResultTable = new BeanTable<>(AuditRoundResultBean.class, (PreferencesExt) prefs.node("assertionRoundTable"), false,
                "Audit Results", "AuditRoundResult", null);
        auditResultTable.addPopupOption("Show AuditRoundResult", auditResultTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AuditRoundResultBean)bean).show()));
        auditResultTable.addPopupOption("Rerun audit with details", auditResultTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AuditRoundResultBean)bean).runRoundAgain()));


        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, auditRoundTable, contestTable);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 400));
        split3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split2, estRoundTable);
        split3.setDividerLocation(prefs.getInt("splitPos3", 600));
        split4 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split3, auditResultTable);
        split4.setDividerLocation(prefs.getInt("splitPos4", 800));
        setLayout(new BorderLayout());
        add(split4, BorderLayout.CENTER);
    }

    public void setFontSize(float size) {
        auditRoundTable.setFontSize(size);
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
        auditResultTable.setFontSize(size);
        estRoundTable.setFontSize(size);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        logger.debug("AuditRoundsTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;
        this.isComposite = (this.auditRecord instanceof CompositeRecord);
        this.includeChanged = false;

        try {
            this.auditConfig = auditRecord.getConfig();
            java.util.List<AuditRoundBean> beanList = new ArrayList<>();

            int prevTotal = 0;
            for (var round : auditRecord.getRounds()) {
                beanList.add(new AuditRoundBean(round, prevTotal));
                prevTotal += round.getNewmvrs();
            }
            auditRoundTable.setBeans(beanList);
            if (!auditRecord.getRounds().isEmpty()) {
                this.lastAuditRound = auditRecord.getRounds().getLast();
            } else {
                this.lastAuditRound = null;
            }

            contestTable.setBeans(new ArrayList<>());
            assertionTable.setBeans(new ArrayList<>());
            auditResultTable.setBeans(new ArrayList<>());
            estRoundTable.setBeans(new ArrayList<>());

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("AuditRoundsTable.setAuditRecord failed", e);
        }
        return true;
    }


    //////////////////////////////////////////////////////////////////
    ///
    void showInfo(Formatter f) {
        if (this.auditRecordLocation.equals("none")) { return; }
        // int totalBallots = this.auditRecord.getCvrs().size(); TODO

        f.format("Audit record at %s%n", this.auditRecordLocation);
        f.format("%s%n", this.auditConfig);
        if (this.lastAuditRound == null) return;

        f.format(" total Mvrs = %d%n", this.lastAuditRound.getNmvrs());

        int totalExtra = 0;
        for (AuditRoundIF round : auditRecord.getRounds()) {
            if (round.getAuditWasDone()) {
                int roundIdx = round.getRoundIdx();
                int nmvrs = round.getSamplePrns().size();
                f.format("%nnumber of Mvrs in round %d = %d %n", roundIdx, nmvrs);
                f.format("   Mvrs needed in round %d = %d %n", roundIdx, round.mvrsUsed());
                int extra = nmvrs - round.mvrsUsed();
                f.format("          extraBallotsUsed = %d %n", extra);
                totalExtra += extra;
            }
        }
        f.format("%ntotal extraBallotsUsed = %d %n", totalExtra);
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
                .min(Comparator.comparing(ContestBean::getMargin)) // TODO use noerror
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
                .min(Comparator.comparing(AssertionBean::getMargin)) // TODO use noerror
                .orElseThrow(NoSuchElementException::new);
        assertionTable.setSelectedBean(minByMargin);
    }

    void setSelectedAssertion(AssertionBean assertionBean) {
        java.util.List<AuditRoundResultBean> auditList = new ArrayList<>();
        java.util.List<EstimationRoundBean> estList = new ArrayList<>();

        int maxRound = assertionBean.assertionRound.getRoundIdx();
        for (AuditRoundIF auditRound : auditRecord.getRounds()) {
            if (auditRound.getRoundIdx() > maxRound) break;

            for (ContestRound contestRound : auditRound.getContestRounds()) {
                if (contestRound.getContestUA().equals(assertionBean.contestBean.contestUA)) {
                    for (AssertionRound assertionRound : contestRound.getAssertionRounds()) {
                        if (assertionRound.getAssertion().equals(assertionBean.assertionRound.getAssertion())) {
                            if (assertionRound.getAuditResult() != null) {
                                auditList.add(new AuditRoundResultBean(contestRound, assertionRound));
                            }
                            if (assertionRound.getEstimationResult() != null) {
                                estList.add(new EstimationRoundBean(assertionRound));
                            }
                        }
                    }
                }
            }
        }
        auditResultTable.setBeans(auditList);
        estList.sort(Comparator.comparing(EstimationRoundBean::getRound)); // TODO why out of order ?
        estRoundTable.setBeans(estList);
    }

    void save() {
        auditRoundTable.saveState(false);
        contestTable.saveState(false);
        assertionTable.saveState(false);
        auditResultTable.saveState(false);
        estRoundTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
        prefs.putInt("splitPos2", split2.getDividerLocation());
        prefs.putInt("splitPos3", split3.getDividerLocation());
        prefs.putInt("splitPos4", split4.getDividerLocation());
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    void resample() {
        try {
            if (this.lastAuditRound == null) {
                JOptionPane.showMessageDialog(null, "There is no audit round to resample");
                return;
            }
            if (isComposite) {
                JOptionPane.showMessageDialog(null, "Cant resample on Composite Record");
                return;
            }

            java.util.List<ContestWithAssertions> cuas = new ArrayList<>();
            for (ContestRound cr : this.lastAuditRound.getContestRounds()) {
                cuas.add(cr.getContestUA());
            }

            int nrounds = auditRecord.getRounds().size();
            if (nrounds == 0) return;

            Set<Long> previousSamples = org.cryptobiotic.rlauxe.audit.AuditRoundKt.previousSamples(auditRecord.getRounds(), nrounds);
            logger.info(String.format("call sampleWithContestCutoff() with auditorWantNewMvrs = %d previousSamples = %d",
                    this.lastAuditRound.getAuditorWantNewMvrs(), previousSamples.size()));

            PersistedWorkflow workflow = PersistedWorkflow.Companion.readFrom(auditRecordLocation);
            ConsistentSamplingKt.sampleWithContestCutoff(auditConfig, workflow.mvrManager(), this.lastAuditRound, previousSamples, false);

            auditRoundTable.refresh();
            contestTable.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("AuditRoundsTable.resample failed", e);
        }
    }

    void runAuditRound() {
        try {
            if (isComposite) {
                JOptionPane.showMessageDialog(null, "Cant run Audit Round on Composite Record");
            } else {
                if (includeChanged) {
                    resample();
                    writeAuditState();
                }
                includeChanged = false;
                logger.info("begin runRound");
                runRound(auditRecordLocation, null);
                logger.info("return from runRound");
                setAuditRecord(auditRecordLocation); // reread in
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("AuditRoundsTable.runAuditRound failed", e);
        }
    }

    // only is written if the audit is run. Could also let runRound do it.
    // probably needs to also rewrite sampleMvrs, in case samplePrns changed
    private void writeAuditState() {
        AuditRoundBean lastBean = auditRoundTable.getBeans().getLast();
        AuditRoundIF lastRound = lastBean.round;
        int roundIdx = lastRound.getRoundIdx();

        var publisher = new Publisher(auditRecordLocation);
        writeAuditRoundJsonFile(lastRound, publisher.auditStateFile(roundIdx));
        logger.info(String.format("   write auditRoundFile to %s", publisher.auditStateFile(roundIdx)));
        writeSamplePrnsJsonFile(lastRound.getSamplePrns(), publisher.samplePrnsFile(roundIdx));
        logger.info(String.format("   write %d samplePrns to %s", lastRound.getSamplePrns().size(), publisher.samplePrnsFile(roundIdx)));
        int ncards = writeMvrsForRound(publisher, roundIdx);
        logger.info(String.format("   write %d sampleMvrs to %s", ncards, publisher.sampleMvrsFile(roundIdx)));
    }

    ////////////////////////////////////////////////////////////////////////////
    public class AuditRoundBean {
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static {
            beanProperties.add(new BeanTable.TableBeanProperty("round", "index of audit round"));
            beanProperties.add(new BeanTable.TableBeanProperty("auditWasDone", "audit was performed"));
            beanProperties.add(new BeanTable.TableBeanProperty("auditIsComplete", "audit is complete"));
            beanProperties.add(new BeanTable.TableBeanProperty("totalMvrs", "total samples needed for all rounds"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrs", "all samples needed for this round"));
            beanProperties.add(new BeanTable.TableBeanProperty("newMvrs", "new samples needed for this round"));
            beanProperties.add(new BeanTable.TableBeanProperty("wantNewMvrs", "number of new samples wanted; set by auditor"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrsUsed", "number of mvrs actually used during audit"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrsExtra", "number of mvrs not needed"));
        }

        AuditRoundIF round;
        int prevTotal;

        AuditRoundBean() {}
        AuditRoundBean(AuditRoundIF round, int prevTotal) {
            this.round = round;
            this.prevTotal = prevTotal;
        }

        // editable properties
        static public String editableProperties() { return "wantNewMvrs"; }

        public boolean canedit() {
            return (!isComposite) &&
                    (lastAuditRound != null) && (round != null ) &&
                    (!lastAuditRound.getAuditWasDone()) &&
                    (round.getRoundIdx() == lastAuditRound.getRoundIdx());
        }

        public Integer getRound() {
            return round.getRoundIdx();
        }

        public Integer getTotalMvrs() {
            return prevTotal + round.getNewmvrs();
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

        public Integer getMvrs() {
            return round.getNmvrs();
        }
        public Integer getMvrsUsed() {
            return round.mvrsUsed();
        }

        public Integer getMvrsExtra() {
            return round.mvrsExtra();
        }

        public boolean isAuditWasDone() {
            return round.getAuditWasDone();
        }

        public boolean isAuditIsComplete() {
            return round.getAuditIsComplete();
        }

        public String show() {
            return auditRoundTable.tableModel.showBean(this, beanProperties);
        }
    }

    public class ContestBean {
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static {
            beanProperties.add(new BeanTable.TableBeanProperty("round", "index of audit round"));
            beanProperties.add(new BeanTable.TableBeanProperty("id", "contest identifier"));
            beanProperties.add(new BeanTable.TableBeanProperty("name", "contest name"));
            beanProperties.add(new BeanTable.TableBeanProperty("type", "contest type"));
            beanProperties.add(new BeanTable.TableBeanProperty("NCand", "number of candidates"));
            beanProperties.add(new BeanTable.TableBeanProperty("phantoms", "number of phantom votes"));
            beanProperties.add(new BeanTable.TableBeanProperty("votes", "reported vote count"));
            beanProperties.add(new BeanTable.TableBeanProperty("margin", "diluted margin (smallest assertion)"));
            beanProperties.add(new BeanTable.TableBeanProperty("NPop", "population size for diluted margin"));
            beanProperties.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner (smallest assertion)"));

            beanProperties.add(new BeanTable.TableBeanProperty("estMvrs", "estimated samples needed"));
            beanProperties.add(new BeanTable.TableBeanProperty("estNewMvrs", "estimated new samples needed"));
            beanProperties.add(new BeanTable.TableBeanProperty("actualMvrs", "number of ballots with this contest contained in this round's sample"));
            beanProperties.add(new BeanTable.TableBeanProperty("actualNewMvrs", "new samples needed for this round"));

            beanProperties.add(new BeanTable.TableBeanProperty("wantNewMvrs", "number of new samples set by auditor"));

            beanProperties.add(new BeanTable.TableBeanProperty("mvrsUsed", "number of mvrs actually used during audit"));
            beanProperties.add(new BeanTable.TableBeanProperty("status", "status of contest completion"));
            beanProperties.add(new BeanTable.TableBeanProperty("included", "contest was included in this audit round"));
            beanProperties.add(new BeanTable.TableBeanProperty("done", "contest has completed"));
        }

        ContestRound contestRound;
        ContestWithAssertions contestUA;
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
        static public String editableProperties() { return "wantNewMvrs include"; }
        public boolean canedit() {
            return (lastAuditRound != null)
                    && (!lastAuditRound.getAuditWasDone())
                    && (auditRound == lastAuditRound.getRoundIdx());
        }

        public Integer getRound() {
            return contestRound.getRoundIdx();
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
                includeChanged = true;
            }
            setDone(!include);
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

        public Integer getNpop() {
            return contestUA.getNpop();
        }

        public Integer getPhantoms() {
            return contestUA.getNphantoms();
        }

        public Integer getMaxIndex() {return contestRound.getMaxSampleIndex();}
        public Integer getEstMvrs() {return contestRound.getEstMvrs();}
        public Integer getEstNewMvrs() {return contestRound.getEstNewMvrs();}
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
                return minAssertion.getAssorter().dilutedMargin();
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

        // data class ContestRound(val contestUA: ContestWithAssertions, val assertions: List<AssertionRound>, val roundIdx: Int) {
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
            sb.append("%n%s%n".formatted(contestTable.tableModel.showBean(this, beanProperties)));
            sb.append("\n%s%n".formatted(contestUA.show()));
            return sb.toString();
        }
    }

    public class AssertionBean {
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static {
            beanProperties.add(new BeanTable.TableBeanProperty("round", "index of audit round"));
            beanProperties.add(new BeanTable.TableBeanProperty("name", "assertion name"));
            beanProperties.add(new BeanTable.TableBeanProperty("noerror", "noerror assort value (CLCA only)"));
            beanProperties.add(new BeanTable.TableBeanProperty("margin", "diluted margin"));
            beanProperties.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner"));

            beanProperties.add(new BeanTable.TableBeanProperty("estMvrs", "initial estimate samples needed"));
            beanProperties.add(new BeanTable.TableBeanProperty("estNewMvrs", "initial estimate new samples needed"));

            beanProperties.add(new BeanTable.TableBeanProperty("status", "status of contest completion"));
            beanProperties.add(new BeanTable.TableBeanProperty("risk", "measured risk (minimum PValue of audit)"));
            beanProperties.add(new BeanTable.TableBeanProperty("completed", "round assertions was done"));
        }

        AssertionRound assertionRound;
        ContestBean contestBean;
        Assertion assertion;
        ClcaAssorterOneAudit oaAssorter;

        public AssertionBean() {
        }

        AssertionBean(ContestBean contestBean, AssertionRound assertionRound) {
            this.contestBean = contestBean;
            this.assertionRound = assertionRound;
            this.assertion = assertionRound.getAssertion();
            if (assertion instanceof ClcaAssertion cassertion) {
                if (cassertion.getCassorter() instanceof ClcaAssorterOneAudit) {
                    this.oaAssorter = (ClcaAssorterOneAudit) cassertion.getCassorter();
                }
            }
        }

        public Integer getRound() {
            return assertionRound.getRoundIdx();
        }

        public String getName() {
            return assertion.getAssorter().shortName();
        }

        public double getNoError() {return assertion.getAssorter().noerror(); }

        public Integer getEstMvrs() {return assertionRound.getEstMvrs();}

        public int getMvrsUsed() {
            int maxUsed = 0;
            AuditRoundResult auditResult = assertionRound.getAuditResult();
            if (auditResult != null) {
                if (auditResult.getSamplesUsed() > maxUsed) maxUsed = auditResult.getSamplesUsed();
            }
            return maxUsed;
        }

        public Integer getCompleted() {
            return assertionRound.getRoundProved();
        }

        public String getStatus() {
            return Naming.status(assertionRound.getStatus());
        }

        public double getMargin() {
            return assertion.getAssorter().dilutedMargin();
        }

        public double getRisk() {
            if (assertionRound.getAuditResult() != null) {
                return assertionRound.getAuditResult().getPmin();
            } else {
                return Double.NaN;
            }
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("%n%s%n".formatted(assertionTable.tableModel.showBean(this, beanProperties)));

            sb.append("%n assertion = %s".formatted(assertionRound.getAssertion().show()));
            sb.append("%n assorter = %s".formatted(assertionRound.getAssertion().getAssorter().toString()));
            sb.append("%n difficulty = %s".formatted(contestBean.contestUA.getContest().showAssertionDifficulty(assertion.getAssorter())));
            if (assertionRound.getPrevAuditResult() != null) sb.append("%n prevAuditResult = %s".formatted(assertionRound.getPrevAuditResult().toString()));
            if (oaAssorter != null) sb.append("%n oaAssortRates = %s".formatted(oaAssorter.getOaAssortRates().toString()));
            if (assertionRound.getEstimationResult() != null) sb.append("%n estimationResult = %s".formatted(assertionRound.getEstimationResult().toString()));
            if (assertionRound.getAuditResult() != null) sb.append("%n auditResult = %s".formatted(assertionRound.getAuditResult().toString()));

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
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static {
            beanProperties.add(new BeanTable.TableBeanProperty("round", "index of audit round"));
            beanProperties.add(new BeanTable.TableBeanProperty("fuzzPct", "fuzzed percent for simulation"));
            beanProperties.add(new BeanTable.TableBeanProperty("estimatedDistribution", "deciles of estimated distribution"));
            beanProperties.add(new BeanTable.TableBeanProperty("estNewMvrs", "estimated sample size"));
            beanProperties.add(new BeanTable.TableBeanProperty("firstSample", "first estimation sample"));

            beanProperties.add(new BeanTable.TableBeanProperty("startingPValue", "starting PValue for this round)"));
            beanProperties.add(new BeanTable.TableBeanProperty("startingRates", "initial estimate of error rates"));
        }

        EstimationRoundResult estRound;
        ClcaAssorter cassorter = null;

        public EstimationRoundBean() {
        }

        EstimationRoundBean(AssertionRound round) {
            this.estRound = round.getEstimationResult();
            var assertion = round.getAssertion();
            if (assertion instanceof ClcaAssertion) {
                cassorter = ((ClcaAssertion)assertion).getCassorter();
            }

        }

        public Integer getRound() {
            return estRound.getRoundIdx();
        }

        public String getStrategy() {
            return estRound.getStrategy();
        }

        public Double getStartingPvalue() {
            var t = estRound.getStartingTestStatistic();
            if (t == 0.0) return 0.0; else return 1.0/t;
        }

        public Double getFuzzPct() {
            return estRound.getFuzzPct();
        }

        public String getEstimatedDistribution() {
            return estRound.getEstimatedDistribution().toString();
        }

        public Integer getEstNewMvrs() {
            return estRound.getEstNewMvrs();
        }

        public Integer getEstMvrsNoErrors() {
            if (cassorter == null) return 0;
            var alpha = auditConfig.getRiskLimit();
            var maxRisk = auditConfig.getClcaConfig().getMaxRisk();
            return cassorter.sampleSizeNoErrors(maxRisk, alpha);
        }

        public Integer getFirstSample() {
            return estRound.getFirstSample();
        }

        public String getStartingRates() {
            return estRound.startingErrorRates();
        }

        public String show() {

            StringBuilder sb = new StringBuilder();
            sb.append("%n%s%n".formatted(estRoundTable.tableModel.showBean(this, beanProperties)));

            if (estRound.getStartingErrorRates() != null) {
                sb.append("startingErrors = %s%n".formatted(estRound.startingErrorRates()));

            }
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
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static {
            beanProperties.add(new BeanTable.TableBeanProperty("round", "index of audit round"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrs", "number of mvrs with this contest contained in this round's sample"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrsUsed", "number of mvrs actually used during audit"));
            beanProperties.add(new BeanTable.TableBeanProperty("mvrsExtra", "mvrs - mvrsUsed"));

            beanProperties.add(new BeanTable.TableBeanProperty("startingRates", "starting estimate of error rates"));
            beanProperties.add(new BeanTable.TableBeanProperty("measuredErrorCounts", "measured CLCA error counts (cvr-mvr)"));

            beanProperties.add(new BeanTable.TableBeanProperty("PValueLast", "ending PValue for this round"));
            beanProperties.add(new BeanTable.TableBeanProperty("PValueMin", "minimum PValue acheived"));
            beanProperties.add(new BeanTable.TableBeanProperty("status", "status of contest completion"));
            beanProperties.add(new BeanTable.TableBeanProperty("maxSampleIndex", "maximum sample index for this contest"));
        }

        ContestRound contestRound;
        AssertionRound assertionRound;
        AuditRoundResult auditResultRound;

        public AuditRoundResultBean() {
        }

        AuditRoundResultBean(ContestRound contestRound, AssertionRound assertionRound) {
            this.contestRound = contestRound;
            this.assertionRound = assertionRound;
            this.auditResultRound = assertionRound.getAuditResult();
        }

        public Integer getRound() {
            return auditResultRound.getRoundIdx();
        }

        public Integer getMvrs() {
            return auditResultRound.getNmvrs();
        }

        public Integer getMaxSampleIndex() {
            return auditResultRound.getMaxBallotIndexUsed();
        }

        public Double getPValueLast() {
            return auditResultRound.getPlast();
        }

        public Double getPValueMin() {
            return auditResultRound.getPmin();
        }

        public Integer getMvrsUsed() {
            return auditResultRound.getSamplesUsed();
        }

        public Integer getMvrsExtra() {
            return (Math.max(0, auditResultRound.getNmvrs() - auditResultRound.getSamplesUsed()));
        }

        public String getStatus() {
            return Naming.status(auditResultRound.getStatus());
        }

        public String getMeasuredErrorCounts() {
            if (auditResultRound.getMeasuredCounts() != null)
                return auditResultRound.getMeasuredCounts().show();
            else
                return "N/A";
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("%n%s%n".formatted(auditResultTable.tableModel.showBean(this, beanProperties)));

            if (auditResultRound.getMeasuredCounts() != null) {
                sb.append("measuredErrors = %s%n".formatted(auditResultRound.getMeasuredCounts()));
                sb.append("measuredErrorTypes = %s%n".formatted(auditResultRound.getMeasuredCounts().show()));
            }
            return sb.toString();
        }

        public String runRoundAgain() {
            try {
                String result = RunAuditRoundKt.runRoundAgain(auditRecordLocation, contestRound, assertionRound, auditResultRound);
                StringBuilder sb = new StringBuilder();
                sb.append(show());
                sb.append(result);
                return sb.toString();

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, e.getMessage());
                logger.error("AuditRoundsTable.runRoundAgain failed", e);
                return e.getMessage();
            }
        }
    }
}
