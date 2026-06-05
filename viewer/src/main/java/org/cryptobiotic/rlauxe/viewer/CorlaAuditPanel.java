/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditRound;
import org.cryptobiotic.rlauxe.audit.AuditRoundIF;
import org.cryptobiotic.rlauxe.audit.Config;
import org.cryptobiotic.rlauxe.audit.ContestRound;
import org.cryptobiotic.rlauxe.betting.TestH0Status;
import org.cryptobiotic.rlauxe.betting.UtilsKt;
import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.AssorterIF;
import org.cryptobiotic.rlauxe.core.ClcaAssertion;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.cryptobiotic.rlauxe.dhondt.*;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.CountyAudit;
import org.cryptobiotic.rlauxe.persist.CountyContestData;
import org.cryptobiotic.rlauxe.persist.CountyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.BAMutil;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.cryptobiotic.rlauxe.audit.RunAuditRoundKt.resampleAndSaveResults;
import static org.cryptobiotic.rlauxe.util.UtilsKt.dfn;
import static org.cryptobiotic.rlauxe.util.UtilsKt.roundUp;
import static org.cryptobiotic.rlauxe.viewer.BeanProperties.*;

public class CorlaAuditPanel extends JPanel implements ViewerPanelIF {
    static private final Logger logger = LoggerFactory.getLogger(CorlaAuditPanel.class);

    private final PreferencesExt prefs;

    CountyBean countyTotal;
    Map<String, CountyBean> countyMap = emptyMap();

    private final BeanTable<CorlaAuditPanel.ContestBean> contestTable;
    private final BeanTable<CorlaAuditPanel.AssertionBean> assertionTable;
    private final BeanTable<CorlaAuditPanel.CountyBean> countyTable;

    private final JSplitPane split1, split2;

    private String auditRecordLocation = "none";
    private CountyAudit countyAudit;
    private Config config;
    private AuditRoundIF lastAuditRound; // may not be null
    private Double auditRiskLimit;
    private Boolean samplingChanged = false;

    public CorlaAuditPanel(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;
        /* auditData = new AuditData(statusButton); // so each panel gets its own AuditData, but for all audit records.
        statusButton.addActionListener(e -> {
            Formatter f = new Formatter();
            showCoalitionReport(f);
            infoTA.setFont(infoTA.getFont().deriveFont(fontSize));
            infoTA.setText(f.toString());
            infoWindow.show();
        }); */

        contestTable =
                new BeanTable<>(CorlaAuditPanel.ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false, "Contests", "Contests", null);
        contestTable.addListSelectionListener(e -> {
            ContestBean contest = contestTable.getSelectedBean();
            if (contest != null) {
                setSelectedContest(contest);
            }
        });
        contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> ((ContestBean)bean).show()));
        contestTable.addPopupOption("Print Contests", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> printContests()));

        assertionTable =
                new BeanTable<>(AssertionBean.class, (PreferencesExt) prefs.node("assertionTable"), false, "Assertions", "Assertions", null);
        assertionTable.addPopupOption("Show Assertion", assertionTable.makeShowAction(infoTA, infoWindow, bean -> ((AssertionBean) bean).show()));

        countyTable =
                new BeanTable<>(CountyBean.class, (PreferencesExt) prefs.node("countyTable"), false, "Counties", "County", null);
        // countyTable.addPopupOption("Show County", countyTable.makeShowAction(infoTA, infoWindow, bean -> ((CountyBean) bean).show()));

        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split1.setDividerLocation(prefs.getInt("splitPos1", 400));
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, countyTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 800));

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("CorlaAuditPanel init");
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
        countyTable.setFontSize(size);
    }

    public boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        contestTable.setBeans(emptyList());

        logger.debug("setAuditRecord " + auditRecordLocation);

        try {
            this.auditRecordLocation = auditRecordLocation;
            var record = AuditRecord.Companion.read(auditRecordLocation);
            if (record == null) return false;
            if (record.getRounds().isEmpty()) {
                logger.info("first round was not started"); // TODO plan B
                return false;
            }
            if (!(record instanceof CountyAudit)) {
                logger.info("must be CountyAudit");
                return false;
            }
            this.countyAudit = (CountyAudit) record;
            this.lastAuditRound = countyAudit.getRounds().getLast();

            this.config = countyAudit.getConfig();
            this.auditRiskLimit = config.getRiskLimit();

            java.util.Map<Integer, ContestRound> contestRoundMap = new HashMap<>();
            for (var contestRound : lastAuditRound.getContestRounds()) {
                contestRoundMap.put(contestRound.getId(), contestRound);
            }

            java.util.List<ContestBean> beanList = new ArrayList<>();
            for (var cwa : countyAudit.getContests()) {
                var cr = contestRoundMap.get(cwa.getId());
                var bean = new ContestBean(cwa, cr);
                beanList.add(bean);
            }
            // sort contests by payoff
            beanList.sort(Comparator.comparing(ContestBean::getPayoff));
            contestTable.setBeans(beanList);

            /////////////////////
            int countUniformMvrs = 0;
            CountyBean statewide = null;
            java.util.List<CountyBean> countyList = new ArrayList<>();
            java.util.Map<String, CountyBean> _countyMap= new HashMap<>();
            for (var countyData : countyAudit.getCountyData()) {
                var bean = new CountyBean(countyData);
                if (bean.getName().equals("Statewide")) statewide = bean;
                else countUniformMvrs += bean.getNmvrsUniform(); // statewide now included in counties, so dont count twice
                countyList.add(bean);
                _countyMap.put(bean.getName(), bean);
            }
            countyMap = _countyMap;

            var npop = (statewide == null) ? 0 : statewide.npop;
            countyTotal = new CountyBean(new CountyData("=Total", countUniformMvrs, npop));
            countMvrsByCounty();
            countyList.add(countyTotal);

            // sort counties by nmvrs
            countyList.sort(Comparator.comparing(CountyBean::getNmvrsConsistent).reversed());
            countyTable.setBeans(countyList);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedContest(ContestBean contestBean) {

        List<AssertionBean> beanList = new ArrayList<>();
        for (ClcaAssertion cassert : contestBean.contestUA.getClcaAssertions()) {
            var bean = new AssertionBean(contestBean, cassert);
            beanList.add(bean);
        }

        if (beanList.isEmpty()) return;

        // sort assertions by payoff
        beanList.sort(Comparator.comparing(AssertionBean::getPayoff));
        assertionTable.setBeans(beanList);
    }

    public void saveState() {
        contestTable.saveState(false);
        assertionTable.saveState(false);
        countyTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    public void getActions(JPanel container) {
        AbstractAction startAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                resample();
            }
        };
        BAMutil.setActionProperties(startAction, "ambition.png", "Resample", false, 'S', -1);
        BAMutil.addActionToContainer(container, startAction);

        /* TODO put into separate thread
        AbstractAction runAuditRoundAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                callRunRound();
                contestsPanel.resetAuditRecord();
            }
        };
        BAMutil.setActionProperties(runAuditRoundAction, "hamster.png", "Run Audit Round", false, 'R', -1);
        BAMutil.addActionToContainer(container, runAuditRoundAction); */

        AbstractAction targetAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                includeTargetedOnly();
            }
        };
        BAMutil.setActionProperties(targetAction, "goal.png", "Include Targets Only", false, 'T', -1);
        BAMutil.addActionToContainer(container, targetAction);

        AbstractAction targetPlusAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                includeTargetedPlus();
            }
        };
        BAMutil.setActionProperties(targetPlusAction, "goal.png", "Include Targets plus", false, 'T', -1);
        BAMutil.addActionToContainer(container, targetPlusAction);

        AbstractAction targetLessThanAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                includeTargetedPlusContestsLessThan(100); // TODO allow user to set maxMvrs
            }
        };
        BAMutil.setActionProperties(targetLessThanAction, "goal.png", "Include Targets and LessThan 100", false, 'T', -1);
        BAMutil.addActionToContainer(container, targetLessThanAction);

        AbstractAction includeAllAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) { setInclude(true); }
        };
        BAMutil.setActionProperties(includeAllAction, "add.png", "Include All Contests", false, 'T', -1);
        BAMutil.addActionToContainer(container, includeAllAction);

        AbstractAction excludeAllAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) { setInclude(false); }
        };
        BAMutil.setActionProperties(excludeAllAction, "exemption.png", "Exclude All Contests", false, 'T', -1);
        BAMutil.addActionToContainer(container, excludeAllAction);
    }

    //// Actions

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    void resample() {
        try {
            java.util.List<ContestWithAssertions> cuas = new ArrayList<>();
            for (ContestRound cr : this.lastAuditRound.getContestRounds()) {
                cuas.add(cr.getContestUA());
            }

            int nrounds = countyAudit.getRounds().size();
            if (nrounds == 0) return;

            logger.info(String.format("call resampleAndSaveResults"));

            resampleAndSaveResults(countyAudit, (AuditRound) lastAuditRound);
            countMvrsByCounty();

            contestTable.refresh();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("AuditRoundsTable.resample failed", e);
        }
    }

    // all include or exclude
    void setInclude(Boolean include) {
        for (ContestRound contestRound: lastAuditRound.getContestRounds()) {
            contestRound.setIncluded(include);
        }
        samplingChanged = true;
        contestTable.refresh();
    }

    // set targeted to be included
    void includeTargetedOnly() {
        for (ContestBean bean : contestTable.getBeans()) {
            if (bean.contestRound != null) {
                bean.contestRound.setIncluded(bean.targeted());
            }
        }
        samplingChanged = true;
        contestTable.refresh();
    }

    // targeted plus some experimental algorithm using include and maxRisk
    void includeTargetedPlus() {

        for (ContestBean bean : contestTable.getBeans()) {
            if (!bean.getStatus().equals(TestH0Status.InProgress.name())) continue;
            if (bean.contestRound == null) continue;

            bean.setMaxRisk(auditRiskLimit); // also sets estMvrs
            if (bean.targeted()) {
                bean.setInclude(true);
            } else {
                if (bean.getEstMvrs() >= 500) bean.setInclude(false);
                else if (bean.getEstMvrs() >= 150) bean.setMaxRisk(.10);
                else if (bean.getEstMvrs() >= 50) bean.setMaxRisk(.05);
            }
        }

        samplingChanged = true;
        contestTable.refresh();
    }

    // targeted and contests with estMvrs < needMvrs
    Boolean includeTargetedPlusContestsLessThan(Integer maxMvrs) {
        includeTargetedOnly();

        for (ContestBean bean : contestTable.getBeans()) {
            if (bean.contestRound == null) continue;
            if (bean.getEstMvrs() <= maxMvrs && bean.getStatus().equals(TestH0Status.InProgress.name()))
                bean.contestRound.setIncluded(true);
        }

        samplingChanged = true;
        contestTable.refresh();
        return true;
    }

    void countMvrsByCounty() {
        int countMvrs = 0;
        var mvrCounts = countyAudit.countMvrsByCounty();
        for (var countyData : mvrCounts.values()) {
            var countyBean = countyMap.get(countyData.getCountyName());
            if (countyBean != null) {
                countyBean.nmvrsConsistent = countyData.getNmvrs();
                countMvrs += countyData.getNmvrs();
            } else {
                logger.warn( "cant find countyName '" + countyData.getCountyName() +"'");
            }
        }
        if (countyTotal != null) {
            countyTotal.nmvrsConsistent = countMvrs;
        }
        countyTable.refresh();
    }

    void showInfo(Formatter f) {
        if (this.countyAudit == null) return;

        f.format("Audit record at %s%n%n", countyAudit.getLocation());
        f.format("%s%n", this.config);
        if (this.lastAuditRound == null) return;

        f.format("AuditRounds");
        int totalExtra = 0;
        for (AuditRoundIF round : countyAudit.getRounds()) {
            if (round.getAuditWasDone()) {
                int roundIdx = round.getRoundIdx();
                int nmvrs = round.getSamplePrns().size();
                f.format("%n  number of Mvrs in round %d = %d %n", roundIdx, nmvrs);
                int extra = round.getMvrsUnused();
                f.format("  extraBallotsUsed = %d %n", extra);
                totalExtra += extra;
            }
        }
        f.format("%n  total extraBallotsUsed = %d %n", totalExtra);
        f.format("  total Mvrs = %d%n", this.lastAuditRound.getNmvrs());
    }

    public String printContests() {
        return printContestsG(contestTable.getBeans(), contestTable.tableModel);
    }

    public class ContestBean {
        // editable properties
        static public String editableProperties() { return "include maxRisk"; }

        ContestWithAssertions contestUA;
        ContestRound contestRound; // may be null

        Integer mvrLimit = -1;
        Integer orgSampleSize;

        public ContestBean() {
        }

        ContestBean(ContestWithAssertions contestUA, ContestRound contestRound) {
            this.contestUA = contestUA;
            this.contestRound = contestRound;
            orgSampleSize = -1; // lastRound.getHaveSampleSize();
        }

        public boolean canedit() {
            return true;
        }

        public boolean isInclude() {
            return contestRound != null && contestRound.getIncluded();
        }
        public void setInclude(boolean include) {
            if (contestRound == null) return;
            boolean oldState = contestRound.getIncluded();
            if (oldState != include) {
                contestRound.setIncluded(include);
                samplingChanged = true;
            }
        }

        // editable properties have to be primitive
        public void setMaxRisk(double risk) {
            if (contestRound == null) return;
            contestRound.setAuditorWantRisk(risk);
            var estMvrs = getEstMvrs();
            contestRound.setEstMvrs(estMvrs);
            setInclude(true);
            samplingChanged = true;
        }
        public double getMaxRisk() {
            if (contestRound == null) return 1.0;
            var risk = contestRound.getAuditorWantRisk();
            return (risk != null) ? risk : auditRiskLimit;
        }

        public String getName() {
            return contestUA.getName();
        }

        public Integer getId() {
            return contestUA.getId();
        }

        public Double getEstRisk() {
            ClcaAssertion minAssertion = contestUA.minClcaAssertion();
            if (minAssertion == null) return 1.0;
            double noerror = minAssertion.getNoerror();

            Integer haveMvrs = getHaveMvrs();
            return UtilsKt.estRiskStandardBet(contestUA.population(), noerror, haveMvrs);
        }

        public Integer getEstMvrs() {
            ClcaAssertion minAssertion = contestUA.minClcaAssertion();
            if (minAssertion == null) return 0;
            double noerror = minAssertion.getNoerror();

            return UtilsKt.estSampleSizeStandardBet(contestUA.population(), noerror, getMaxRisk());
        }

        public Integer getHaveMvrs() {
            return (contestRound == null) ? 0 : contestRound.getHaveSampleSize();
        }

        public Double getSamplePct() {
            int pop = getPopSize();
            return pop == 0 ? 0.0 : getEstMvrs()/(1.0 * pop);
        }

        public String getNCounties() {
            var CORLAcounties =  contestUA.getContest().info().getMetadata().get("CORLAcounties");
            if (CORLAcounties == null) return "N/A";
            var toks = CORLAcounties.split(",");
            if (toks.length == 1) return toks[0];
            return String.format("%02d", toks.length);
        }

        public String getTarget() {
            return targeted() ? "YES" : "";
        }

        boolean targeted() {
            var reason = contestUA.getContest().info().getMetadata().get("CORLAauditReason");
            if (reason == null) return false;
            return reason.equals("state_wide_contest") || reason.equals("county_wide_contest");
        }

        public boolean statewide() {
            var CORLAcounties =  contestUA.getContest().info().getMetadata().get("CORLAcounties");
            if (CORLAcounties == null) return false;
            var toks = CORLAcounties.split(",");
            return  (toks.length > 60);
        }

        public String getPayoff() {
            Assertion minAssertion = contestUA.minAssertion();
            if (minAssertion == null) return "N/A";
            double noerror = minAssertion.getAssorter().noerror(contestUA.getHasStyle());
            return dfn(UtilsKt.payoff(2.0 / 1.03905, noerror), 6);
        }

        public double getMargin() {
            Double margin = contestUA.minMargin();
            return margin == null ? 0.0 : margin;
        }

        public Integer getMvrsExtra() {
            return getHaveMvrs() - getEstMvrs();
        }

        public Integer getNCand() {
            return contestUA.getNcandidates();
        }

        public Integer getPopSize() {
            return contestUA.population();
        }

        public Integer getPhantoms() {
            return contestUA.getNphantoms();
        }

        public double getRecountMargin() {
            Double min = contestUA.minRecountMargin();
            return min == null ? 0.0 : min;
        }

        public String getStatus() {
            return Naming.status(contestUA.getPreAuditStatus());
        }

        public String getType() {
            return contestUA.getChoiceFunction().toString();
        }

        public Integer getUndervotes() {
            return contestUA.getContest().Nundervotes();
        }

        public Integer getUvPct() {
            return contestUA.getContest().undervotePct();
        }

        public String getVotes() {
            var votes = contestUA.getContest().votes();
            if (votes != null) return votes.toString();
            return "N/A";
        }

        public Integer getVoteDiff() {
            Assertion minAssertion = contestUA.minAssertion();
            if (minAssertion == null) return 0;
            return contestUA.getContest().marginInVotes(minAssertion.getAssorter());
        }

        public String getWinners() {
            return contestUA.getContest().winners().toString();
        }

        public String show() {
            return showContestG(this, contestTable.tableModel, this.contestUA, this.contestRound);
        }
    }

    public class AssertionBean {
        ContestBean contestBean;
        ContestWithAssertions cua;
        ClcaAssertion cassertion;
        AssorterIF assorter;
        Map<Integer, String> candidates;

        public AssertionBean() {
        }

        AssertionBean(ContestBean contestBean, ClcaAssertion cassertion) {
            this.contestBean = contestBean;
            this.cua = contestBean.contestUA;
            this.cassertion = cassertion;
            this.assorter = cassertion.getAssorter();
            this.candidates = cua.getContest().info().getCandidateIdToName();
        }

        public String getType() {
            return assorter.getClass().getSimpleName();
        }

        public String getWinner() {
            if (assorter instanceof DHondtAssorter dassorter) {
                return dassorter.winnerNameRound();
            }
            int winner = assorter.winner();
            return candidates.get(winner);
        }

        public String getLoser() {
            if (assorter instanceof DHondtAssorter dassorter) {
                return dassorter.loserNameRound();
            }
            int loser = assorter.loser();
            return candidates.get(loser);
        }

        public String getDesc() {
            return assorter.hashcodeDesc();
            // return cua.getContest().showAssertionDifficulty(assorter);
        }

        public Double getEstRisk() {
            double noerror = cassertion.getNoerror();
            Integer haveMvrs = contestBean.getHaveMvrs();
            return UtilsKt.estRiskStandardBet(cua.population(), noerror, haveMvrs);
        }

        public Integer getEstMvrs() {
            double noerror = cassertion.getNoerror();
            return UtilsKt.estSampleSizeStandardBet(contestBean.contestUA.population(), noerror, contestBean.getMaxRisk());
        }

        public double getMargin() {
            return cassertion.getCassorter().getAssorterMargin();
        }

        public String getDifficulty() {
            return cua.getContest().showAssertionDifficulty(assorter);
        }

        public double getRecountMargin() {
            return cua.getContest().recountMargin(assorter);
        }

        public double getMean() {
            return assorter.dilutedMean();
        }

        // could use payoff
        public String getNoError() {
            return dfn(assorter.noerror(cua.getHasStyle()), 5);
        }

        public String getPayoff() {
            double noerror = assorter.noerror(cua.getHasStyle());
            return dfn(UtilsKt.payoff(2.0 / 1.03905, noerror), 6);
        }

        public double getUpper() {
            return assorter.upperBound();
        }

        public String show() {
            return showAssertionG(this, assertionTable.tableModel, this.cua, this.cassertion);
        }
    }


    public class CountyBean {
        String countyName;
        Integer npop;
        Integer nmvrsUniform;
        Integer nmvrsConsistent = 0;

        public CountyBean() {
        }

        CountyBean(CountyData countyData) {
            this.countyName = countyData.getCountyName();
            this.npop = countyData.getNpop();
            this.nmvrsUniform = countyData.getNmvrs();
        }

        public String getName() {return countyName;}
        public Integer getNpop() { return npop; }
        public Integer getNmvrsUniform() { return nmvrsUniform; }
        public Integer getNmvrsConsistent() { return nmvrsConsistent; }
        public Integer getDiff() { return nmvrsUniform - nmvrsConsistent; }
        public Integer getUniformInvRate() { return (nmvrsUniform == 0) ? 0 : roundUp((double) getNpop() / nmvrsUniform); }
        public Integer getConsistentInvRate() { return (nmvrsConsistent == 0) ? 0 : roundUp((double) getNpop() / nmvrsConsistent); }
    }

    public class CountyContestBean {
        CountyContestData countyContestData;
        Integer npop;
        Integer nmvrsUniform;
        Integer nmvrsConsistent = 0;

        public CountyContestBean() {
        }

        CountyContestBean(CountyContestData countyContestData) {
            this.countyContestData = countyContestData;
        }

        public String getContestName() { return countyContestData.getContestName(); }
        public Integer getVoteDiff() { return countyContestData.getVoteDiff(); }
        public String getVotes() { return countyContestData.getVotes().toString(); }
        public Integer getNpop() { return npop; }
    }

}

