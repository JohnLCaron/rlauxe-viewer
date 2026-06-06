/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AssertionRound;
import org.cryptobiotic.rlauxe.audit.AuditRoundIF;
import org.cryptobiotic.rlauxe.audit.Config;
import org.cryptobiotic.rlauxe.audit.ContestRound;
import org.cryptobiotic.rlauxe.betting.TestH0Status;
import org.cryptobiotic.rlauxe.betting.UtilsKt;
import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ClcaAssertion;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.cryptobiotic.rlauxe.dhondt.*;
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord;
import org.cryptobiotic.rlauxe.persist.SampleLimit;
import org.cryptobiotic.rlauxe.viewer.ViewerMain.ViewerProfile;
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
import static org.cryptobiotic.rlauxe.dhondt.CandidateSeatsKt.makeAllSeats;
import static org.cryptobiotic.rlauxe.util.UtilsKt.dfn;
import static org.cryptobiotic.rlauxe.viewer.BeanProperties.*;

public class BelgiumAuditPanel extends JPanel implements ViewerPanelIF {
    static private final Logger logger = LoggerFactory.getLogger(BelgiumAuditPanel.class);

    private final PreferencesExt prefs;
    private final ViewerProfile profile;
    AuditData auditData;
    AllSeats allSeats;
    CandidateBean coalitionTotal;
    Map<Integer, String> partyNames;

    private final BeanTable<BelgiumAuditPanel.ContestBean> contestTable;
    private final BeanTable<BelgiumAuditPanel.AssertionBean> assertionTable;
    private final BeanTable<BelgiumAuditPanel.CandidateBean> candidateTable;

    private final JSplitPane split1, split2;

    private String auditRecordLocation = "none";
    private CompositeAuditRecord auditRecord;
    private Config config;
    private AuditRoundIF lastAuditRound; // may be null

    public BelgiumAuditPanel(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize,
                             JButton statusButton, ViewerProfile profile) {
        this.prefs = prefs;
        this.profile = profile;
        auditData = new AuditData(statusButton); // so each panel gets its own AuditData, but for all audit records.
        statusButton.addActionListener(e -> {
            Formatter f = new Formatter();
            showCoalitionReport(f);
            infoTA.setFont(infoTA.getFont().deriveFont(fontSize));
            infoTA.setText(f.toString());
            infoWindow.show();
        });

        contestTable =
                new BeanTable<>(BelgiumAuditPanel.ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false, "Contests", "Contests", null);
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

        candidateTable =
                new BeanTable<>(CandidateBean.class, (PreferencesExt) prefs.node("candidateTable"), false, "Party Coalition", "Parties", null);
        candidateTable.addPopupOption("Show Party", candidateTable.makeShowAction(infoTA, infoWindow, bean -> ((CandidateBean) bean).show()));


        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split1.setDividerLocation(prefs.getInt("splitPos1", 400));
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, candidateTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 800));

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("BelgiumAuditPanel init");
    }

    public void getActions(JPanel container) {
        if (profile.isBelgium()) {
            AbstractAction limitAction = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    applySampleLimits();
                }
            };
            BAMutil.setActionProperties(limitAction, "speedometer.png", "reread sample limits", false, 'L', -1);
            BAMutil.addActionToContainer(container, limitAction);
            /*
            AbstractAction saveAction = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    saveConfig();
                }
            };
            BAMutil.setActionProperties(saveAction, "saveConfig.png", "Save these limits", false, 'S', -1);
            BAMutil.addActionToContainer(container, saveAction); */
        }
    }

    /*
    public void saveConfig() {
        try {
            if (this.lastAuditRound == null) {
                JOptionPane.showMessageDialog(null, "There is no audit round to save");
                return;
            }

            saveAuditRound(auditRecord, lastAuditRound);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("AuditRoundsTable.resample failed", e);
        }
    } */

    public void applySampleLimits() {
        List<SampleLimit> limits = auditRecord.readSampleLimits(); // should this be global ?
        for (ContestBean bean : contestTable.getBeans()) {
            boolean foundit = false;
            for (SampleLimit limit : limits) {
                if (bean.getId().equals(limit.getId())) {
                    bean.mvrLimit = limit.getLimit();
                    bean.contestRound.setHaveSampleSize(limit.getLimit());
                    foundit = true;
                    logger.debug("read contest limit {}", limit);
                }
            }
            if (!foundit) {
                bean.contestRound.setHaveSampleSize(bean.orgSampleSize);
            }
        }
        auditData.updateStatus();
        // updateCandidateTable();
        repaint();
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
        candidateTable.setFontSize(size);
    }

    public boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        contestTable.setBeans(emptyList());

        logger.debug("setAuditRecord " + auditRecordLocation+ " with profile "+ profile);

        try {
            this.auditRecordLocation = auditRecordLocation;
            var record = AuditRecord.Companion.read(auditRecordLocation);
            if (record == null) return false;
            if (record.getRounds().isEmpty()) {
                logger.info("first round was not started"); // TODO plan B
                return false;
            }
            if (!(record instanceof CompositeAuditRecord)) {
                logger.info("must be CompositeAuditRecord");
                return false;
            }
            this.auditRecord = (CompositeAuditRecord) record;
            this.lastAuditRound = auditRecord.getRounds().getLast();

            this.config = auditRecord.getConfig();
            ContestBean.alpha = config.getRiskLimit();

            List<ContestBean> beanList = new ArrayList<>();
            for (var contestRound : lastAuditRound.getContestRounds()) {
                if (contestRound.getContestUA().getPreAuditStatus().equals(TestH0Status.InProgress)) {
                    var bean = new ContestBean(contestRound);
                    bean.auditData = auditData;
                    beanList.add(bean);
                }
            }
            // sort contests by payoff
            beanList.sort(Comparator.comparing(ContestBean::getPayoff));
            contestTable.setBeans(beanList);

            auditData.setNewBeans(beanList);
            applySampleLimits(); // read in sample limits and apply them

            // candidates
            partyNames = auditRecord.readPartyNames();
            List<SampleLimit> sampleLimits = auditRecord.readSampleLimits();
            allSeats = makeAllSeats(this.lastAuditRound, sampleLimits);
            List<CandidateBean> candBeans = new ArrayList<>();
            for (var candidateSeat : allSeats.getCandidateSums()) {
                if (candidateSeat.getMaxSeats() > 0) {
                    var bean = new CandidateBean(candidateSeat);
                    candBeans.add(bean);
                }
            }
            coalitionTotal = makeCandidatesTotal(candBeans);
            candBeans.add(coalitionTotal);

            // sort candidates by reportedSeats
            candBeans.sort(Comparator.comparing(CandidateBean::getReportedSeats).reversed());
            candidateTable.setBeans(candBeans);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedContest(BelgiumAuditPanel.ContestBean contestBean) {
        List<AssertionBean> beanList = new ArrayList<>();
        for (AssertionRound ar : contestBean.contestRound.getAssertionRounds()) {
            var bean = new AssertionBean(contestBean, ar);
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
        candidateTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    /// ///////////////////////////////////////////////////////////////

    void showCoalitionReport(Formatter f) {
        f.format("%s", coalitionTotal.show());
    }

    //// Actions
    void showInfo(Formatter f) {
        if (this.auditRecord == null) return;

        f.format("Audit record at %s%n%n", auditRecord.getLocation());
        f.format("%s%n", this.config);
        if (this.lastAuditRound == null) return;

        f.format("AuditRounds");
        int totalExtra = 0;
        for (AuditRoundIF round : auditRecord.getRounds()) {
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

        f.format("%s", CandSeatRanges.Companion.showMergedSeatRanges(this.lastAuditRound));
    }

    public String printContests() {
        return printContestsG(contestTable.getBeans(), contestTable.tableModel);
    }

    static public class AuditData {
        JButton status;
        Integer useMvrs = 0;
        Integer contestedSeats = 0;
        List<ContestBean> beans;

        AuditData(JButton status) {
            this.status = status;
        }

        void updateStatus() {
            useMvrs = countMvrs();
            contestedSeats = countContestsSeats();
            SwingUtilities.invokeLater(() -> {
                status.setText(String.format("mvrs=%d failures=%d", useMvrs, contestedSeats));
                status.repaint();
            });
        }

        void setNewBeans(List<ContestBean> beans) {
            this.beans = beans;
            useMvrs = countMvrs();
            contestedSeats = countContestsSeats();
            SwingUtilities.invokeLater(() -> {
                status.setText(String.format("mvrs=%d failures=%d", useMvrs, contestedSeats));
            });
        }

        Integer countMvrs() {
            var total = 0;
            for (ContestBean bean : beans) {
                total += bean.getHaveMvrs();
            }
            return total;
        }

        Integer countContestsSeats() {
            var total = 0;
            for (ContestBean bean : beans) {
                var contest = bean.contestUA.getContest();
                if (contest instanceof DHondtContest dhcontest) {
                    var count = dhcontest.showContestedSeats(bean.contestRound).getFirst();
                    total += count;
                }
            }
            return total;
        }
    }

    public class ContestBean {
        static Double alpha = .03;

        // editable properties
        static public String editableProperties() { return "mvrLimit"; }

        public boolean canedit() { return true; }
        public int getMvrLimit() { return mvrLimit; }
        public void setMvrLimit( int limit) {
            this.mvrLimit = limit;
            if (limit < 0) contestRound.setHaveSampleSize(orgSampleSize); else contestRound.setHaveSampleSize(limit);
            if (auditData != null) auditData.updateStatus(); // also update this row I hope
        }

        AuditData auditData;
        Integer mvrLimit = -1;
        ContestRound contestRound;
        ContestWithAssertions contestUA;
        Integer orgSampleSize;

        public ContestBean() {
        }

        ContestBean(ContestRound contestRound) {
            this.contestRound = contestRound;
            this.contestUA = contestRound.getContestUA();
            orgSampleSize = this.contestRound.getHaveSampleSize();
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
            return UtilsKt.estRiskStandardBet(contestUA.getNpop(), noerror, haveMvrs);
        }

        public Integer getEstMvrs() { return contestRound.getEstNewMvrs(); }

        /*
        public Integer getEstMvrs() {
            if (contestUA.getHasStyle()) {
                return ((lastRound == null)) ? 0 : lastRound.getEstMvrs();
            }

            Double margin = contestUA.minMargin();
            if (margin == null) return 0;
            return roundUp(UtilsKt.estSamplesFromMarginUpper(2.0 / 1.03905, margin, alpha));
        } */

        public Integer getHaveMvrs() {
            if (mvrLimit >= 0) return mvrLimit;
            return contestRound.getHaveSampleSize();

            /* TODO
            if (contestUA.getHasStyle()) {
                return ((lastRound == null)) ? 0 : lastRound.getHaveSampleSize();
            }

            var have = contestUA.getContest().info().getMetadata().get("CORLAhaveMvrs");
            return (have == null) ? 0 : Integer.parseInt(have); */
        }

        public String getNoerror() {
            Assertion minAssertion = contestUA.minAssertion();
            if (minAssertion == null) return "N/A";
            return dfn(minAssertion.getAssorter().noerror(contestUA.getHasStyle()), 5);
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

        public Integer getMvrsUsed() {
            return (contestRound == null) ? 0 : contestRound.maxSamplesUsed();
        }

        public Integer getNc() {
            return contestUA.getNc();
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

        public double getRecountMargin() {
            Double min = contestUA.minRecountMargin();
            return min == null ? 0.0 : min;
        }

        // TODO maybe not needed
        public String getStatus() {
            return (contestRound == null) ? Naming.status(contestUA.getPreAuditStatus()) : Naming.status(contestRound.getStatus());
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
        AssertionRound assertionRound;
        ContestWithAssertions cua;
        ClcaAssertion cassertion;
        Assertion assertion;
        Map<Integer, String> candidates;
        OneAuditClcaAssorter oaAssorter;

        public AssertionBean() {
        }

        AssertionBean(ContestBean contestBean, AssertionRound assertionRound) {
            this.contestBean = contestBean;
            this.cua = contestBean.contestUA;
            this.assertionRound = assertionRound;
            this.assertion = assertionRound.getAssertion();
            this.candidates = cua.getContest().info().getCandidateIdToName();
            if (assertion instanceof ClcaAssertion ca) {
                this.cassertion = ca;
                if (ca.getCassorter() instanceof OneAuditClcaAssorter) {
                    this.oaAssorter = (OneAuditClcaAssorter) ca.getCassorter();
                }
            }
        }

        public String getType() {
            return assertion.getAssorter().getClass().getSimpleName();
        }

        public String getWinner() {
            if (assertion.getAssorter() instanceof DHondtAssorter dassorter) {
                return dassorter.winnerNameRound();
            }
            int winner = assertion.getAssorter().winner();
            return candidates.get(winner);
        }

        public String getLoser() {
            if (assertion.getAssorter() instanceof DHondtAssorter dassorter) {
                return dassorter.loserNameRound();
            }
            int loser = assertion.getAssorter().loser();
            return candidates.get(loser);
        }

        public String getDesc() {
            return assertion.getAssorter().hashcodeDesc();
            // return cua.getContest().showAssertionDifficulty(assertion.getAssorter());
        }

        public Double getEstRisk() {
            double noerror = cassertion.getNoerror();
            Integer haveMvrs = contestBean.getHaveMvrs();
            return UtilsKt.estRiskStandardBet(cua.getNpop(), noerror, haveMvrs);
        }

        public Integer getEstMvrs() { return assertionRound.getEstNewMvrs(); }

        public double getMargin() {
            return (cassertion != null) ? cassertion.getCassorter().getAssorterMargin() : assertion.getAssorter().dilutedMargin();
        }

        public String getDifficulty() {
            return cua.getContest().showAssertionDifficulty(assertion.getAssorter());
        }

        public double getRecountMargin() {
            return cua.getContest().recountMargin(assertion.getAssorter());
        }

        public double getMean() {
            return assertion.getAssorter().dilutedMean();
        }

        // could use payoff
        public String getNoerror() {
            return dfn(assertion.getAssorter().noerror(cua.getHasStyle()), 5);
        }

        public String getPayoff() {
            double noerror = assertion.getAssorter().noerror(cua.getHasStyle());
            return dfn(UtilsKt.payoff(2.0 / 1.03905, noerror), 6);
        }

        public double getUpper() {
            return assertion.getAssorter().upperBound();
        }

        public String show() {
            return showAssertionG(this, assertionTable.tableModel, this.cua, this.cassertion);
        }
    }


    public class CandidateBean {
        CandidateSeats cand;
        boolean include = true;
        boolean isTotal = false;
        Coalition coal;

        public CandidateBean() {
        }

        CandidateBean(CandidateSeats candSeatRange) {
            this.cand = candSeatRange;
        }

        // editable properties
        static public String editableProperties() { return "include"; }
        public boolean canedit() { return true; } // do we need?

        public boolean isInclude() { return include; }
        public void setInclude(boolean include) {
            this.include = include;
            updateCandidateTotal();
            candidateTable.repaint();
        }

        public String getPartyName() {return cand.getCandName();}
        public int getPartyId() {return cand.getCandId(); }
        public int getMinSeats() {return cand.getMinSeats(); }
        public int getReportedSeats() {return cand.getReportedSeats(); }
        public int getMaxSeats() {return cand.getMaxSeats(); }
        public int getNFailures() {return cand.getFailures().size(); }
        /* public String getFailures() {
            var sb = new StringBuilder();
            for (var failure : cand.getFailures()) {
                sb.append(String.format("%s, ", failure.getAssorter().hashcodeDesc()));
            }
            return sb.toString();
        } */
        public String getInCoalition() { return (include && !isTotal && getMaxSeats() > 0) ? "YES" : ""; }


        String show() {
            // only the coalition total bean has a coalition attached
            if (coal != null) return coal.toString();
            else return cand.toString();
        }
    }

    CandidateBean makeCandidatesTotal(List<CandidateBean> beans) {
        var candidates = new HashSet<Integer>();
        for (var bean : beans) {
            candidates.add(bean.getPartyId());
        }
        Coalition allcoal = allSeats.calcCoalition(candidates, partyNames);

        var cand = new CandidateSeats(0, "-- coalition --");
        cand.setReportedSeats(allcoal.reportedSeats());
        cand.setMinSeats(allcoal.minSeats());
        cand.setMaxSeats(allcoal.maxSeats());
        cand.getFailures().addAll(allcoal.all());

        var totalBean = new CandidateBean(cand);
        totalBean.isTotal = true;
        totalBean.include = false;
        totalBean.coal = allcoal;

        return totalBean;
    }

    void updateCandidateTotal(List<CandidateBean> beans, CandidateBean totalBean) {
        var candidates = new HashSet<Integer>();
        for (var bean : beans) {
            if (bean.include && !bean.equals(totalBean)) {
                candidates.add(bean.getPartyId());
            }
        }
        Coalition coal = allSeats.calcCoalition(candidates, partyNames);
        var cand = new CandidateSeats(0, "-- coalition --");
        cand.setReportedSeats(coal.reportedSeats());
        cand.setMinSeats(coal.minSeats());
        cand.setMaxSeats(coal.maxSeats());
        cand.getFailures().addAll(coal.all());

        totalBean.coal = coal;
        totalBean.cand = cand;
    }

    void updateCandidateTotal() {
        updateCandidateTotal(candidateTable.getBeans(), coalitionTotal);
    }

}

