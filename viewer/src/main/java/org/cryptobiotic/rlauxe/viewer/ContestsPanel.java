/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.betting.TestH0Status;
import org.cryptobiotic.rlauxe.betting.UtilsKt;
import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ClcaAssertion;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter;
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter;
import org.cryptobiotic.rlauxe.persist.*;
import org.cryptobiotic.rlauxe.viewer.ViewerMain.ViewerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.cryptobiotic.rlauxe.util.UtilsKt.*;
import static org.cryptobiotic.rlauxe.viewer.BeanProperties.*;

public class ContestsPanel extends JPanel implements ViewerPanelIF {
    static private final Logger logger = LoggerFactory.getLogger(ContestsPanel.class);

    private final PreferencesExt prefs;
    private final ViewerProfile profile;

    private final BeanTable<ContestsPanel.ContestBean> contestTable;
    private final BeanTable<ContestsPanel.AssertionBean> assertionTable;

    private final JSplitPane split2;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    private Config config;
    private Map<Integer, Integer> oneshotMvrs;
    private AuditRoundIF lastAuditRound; // may be null

    public ContestsPanel(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize, ViewerProfile profile) {
        this.prefs = prefs;
        this.profile = profile;

        contestTable =
                new BeanTable<>(ContestsPanel.ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false, "Contests", "ContestRound", null);

        contestTable.addListSelectionListener(e -> {
            ContestBean contest = contestTable.getSelectedBean();
            if (contest != null) {
                setSelectedContest(contest);
            }
        });
        contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> showContest((ContestsPanel.ContestBean) bean)));
        contestTable.addPopupOption("Print Contests", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> printContests()));

        assertionTable =
                new BeanTable<>(AssertionBean.class, (PreferencesExt) prefs.node("assertionTable"), false, "Assertion", "Assertion", null);

        assertionTable.addPopupOption("Show Assertion", assertionTable.makeShowAction(infoTA, infoWindow,
                bean -> showAssertion((AssertionBean) bean)));

        setFontSize(fontSize);

        // layout of tables
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));

        // auditRoundTable not used for now

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("ContestsPanel init");
    }

    public void getActions(JPanel container) {
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
    }

    public void resetAuditRecord() {
        setAuditRecord(auditRecordLocation);
    }

    public boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        contestTable.setBeans(emptyList());

        logger.debug("ContestsPanel setAuditRecord " + auditRecordLocation+ " with profile "+ profile);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.read(auditRecordLocation);
        if (this.auditRecord == null) return false;
        if (!auditRecord.getRounds().isEmpty()) {
            logger.info("first round was not started"); // TODO plan B
        }
        this.lastAuditRound = auditRecord.getRounds().getLast();

        try {
            this.config = auditRecord.getConfig();
            ContestBean.alpha = config.getRiskLimit();

            var contestMap = new HashMap<Integer, ContestsPanel.ContestBean>();
            java.util.List<ContestsPanel.ContestBean> beanList = new ArrayList<>();

            java.util.Map<Integer, ContestRound> contestRoundMap = new HashMap<>();
            for (var contestRound : lastAuditRound.getContestRounds()) {
                contestRoundMap.put(contestRound.getId(), contestRound);
            }

            for (var cwa : auditRecord.getContests()) {
                var cr = contestRoundMap.get(cwa.getId());
                var bean = new ContestsPanel.ContestBean(cwa, cr);
                beanList.add(bean);
                contestMap.put(cwa.getId(), bean);
            }
            contestTable.setBeans(beanList);

            // sort contests by payoff
            beanList.sort(Comparator.comparing(ContestBean::getPayoff));
            contestTable.setBeans(beanList);

            if (!auditRecord.getRounds().isEmpty()) {
                // select inProgress contest with smallest margin
                Optional<ContestsPanel.ContestBean> minByMargin = beanList
                        .stream()
                        .filter(bean -> bean.getStatus().equals("InProgress"))
                        .min(Comparator.comparing(ContestsPanel.ContestBean::getMargin));
                minByMargin.ifPresent(contestTable::setSelectedBean);
            }

            oneshotMvrs = auditRecord.readOneShotMvrs();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("ContestsPanel setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedContest(ContestsPanel.ContestBean contestBean) {
        java.util.List<ContestsPanel.AssertionBean> beanList = new ArrayList<>();
        if (contestBean.contestRound != null) {
            for (AssertionRound ar : contestBean.contestRound.getAssertionRounds()) {
                var bean = new ContestsPanel.AssertionBean(contestBean, ar);
                beanList.add(bean);
            }
        }
        assertionTable.setBeans(beanList);

        if (beanList.isEmpty()) return;

        // select assertion with smallest noerror
        ContestsPanel.AssertionBean minByMargin = beanList
                .stream()
                .min(Comparator.comparing(ContestsPanel.AssertionBean::getNoerror))
                .orElseThrow(NoSuchElementException::new);
        assertionTable.setSelectedBean(minByMargin);
    }

    public void saveState() {
        contestTable.saveState(false);
        assertionTable.saveState(false);

        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    /// ///////////////////////////////////////////////////////////////

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
    }

    public String showContest(ContestBean contestBean) {
        return showContestG(contestBean, contestTable.tableModel, contestBean.contestUA, contestBean.contestRound);
    }

    public String printContests() {
        return printContestsG(contestTable.getBeans(), contestTable.tableModel);
    }

    public String showAssertion(AssertionBean assertionBean) {
        var assn = (assertionBean.cassertion != null) ? assertionBean.cassertion : assertionBean.assertion;
        return showAssertionG(assertionBean, assertionTable.tableModel, assertionBean.cua, assn);
    }

    static public class ContestBean {
        static Double alpha = .03;

        /* editable properties
        static public String editableProperties() { return "mvrLimit"; }
        // static public String hiddenProperties() { return "mvrLimit"; }

        public boolean canedit() { return true; }
        public int getMvrLimit() { return mvrLimit; }
        public void setMvrLimit( int limit) {
            this.mvrLimit = limit;
            if (limit < 0) lastRound.setHaveSampleSize(orgSampleSize); else lastRound.setHaveSampleSize(limit);
            if (auditData != null) auditData.updateStatus(); // also update this row I hope
        } */

        Integer mvrLimit = -1;
        ContestRound contestRound;
        ContestWithAssertions contestUA;
        Integer orgSampleSize;

        public ContestBean() {
        }

        ContestBean(ContestWithAssertions cwa, ContestRound contestRound) {
            this.contestUA = cwa;
            this.contestRound = contestRound;
            orgSampleSize = (contestRound != null) ? contestRound.getHaveSampleSize() : 0;
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

        public Integer getEstMvrs() { return (contestRound == null) ? 0 : contestRound.getEstNewMvrs(); }

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
            return (contestRound == null) ? 0 : contestRound.getHaveSampleSize();

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

        public Integer getMvrsExtras() {
            if (contestRound == null) return 0;
            if (!contestRound.getDone()) return 0;
            if (!contestRound.getIncluded()) return 0;
            else {
                return (contestRound.getEstMvrs() < contestRound.maxSamplesUsed()) ? 0 :
                        (contestRound.getEstMvrs() - contestRound.maxSamplesUsed());
            }
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

        public String getNCounties() {
            var CORLAcounties =  contestUA.getContest().info().getMetadata().get("CORLAcounties");
            if (CORLAcounties == null) return "N/A";
            var toks = CORLAcounties.split(",");
            if (toks.length == 1) return toks[0];
            return String.format("%02d", toks.length);
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
            return (contestRound == null || contestUA.getPreAuditStatus() != TestH0Status.InProgress)
                    ? Naming.status(contestUA.getPreAuditStatus())
                    : Naming.status(contestRound.getStatus());
        }

        public Boolean getTarget() {
            var reason = contestUA.getContest().info().getMetadata().get("CORLAauditReason");
            if (reason == null) return false;
            return reason.equals("state_wide_contest") || reason.equals("county_wide_contest");
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
            return contestUA.getContest().marginInVotes(minAssertion.getAssorter());
        }

        public String getWinners() {
            return contestUA.getContest().winners().toString();
        }

        /*
                public Integer getPoolPct() {
            var pct =  contestUA.getContest().info().getMetadata().get("PoolPct");
            return (pct == null) ? 0 : Integer.parseInt(pct);
        }
        public Integer getCardDiff() {
            var votes = contestUA.getContest().info().getMetadata().get("CORLApoolTotalCards"); // change to CORLApoolTotalCards
            var poolCards = (votes == null) ? 0 : Integer.parseInt(votes);
            return contestUA.getNc() - (contestUA.getContest().Nundervotes() + contestUA.getContest().nvotes()) / contestUA.getContest().info().getVoteForN();
        }

        public Integer getPoolDiff() {
            var votes = contestUA.getContest().info().getMetadata().get("CORLApoolTotalCards");
            var poolCards = (votes == null) ? 0 : Integer.parseInt(votes);
            return contestUA.getNc() - poolCards;
        }

        public Integer getVotePoolDiff() {
            var votes = contestUA.getContest().info().getMetadata().get("CORLApoolTotalVotes");
            var poolVotes = (votes == null) ? 0 : Integer.parseInt(votes);
            return contestUA.getContest().nvotes() - poolVotes;
        }

        public Integer getStateMvrs() {
            var nmvrss = contestUA.getContest().info().getMetadata().get("CORLAstatewideNmvrs");
            return (nmvrss == null) ? 0 : Integer.parseInt(nmvrss);
        }

        public Integer getCountyMvrs() {
            var nmvrss = contestUA.getContest().info().getMetadata().get("CORLAcountyMvrs");
            return (nmvrss == null) ? 0 : Integer.parseInt(nmvrss);
        }

        public Integer getOneshotEst() {
            Integer nmvrs = oneshotMvrs.get(contestUA.getId());
            return (nmvrs != null) ? nmvrs : 0;
        } */
    }

    static public class AssertionBean {
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

        /* if (assertion.getAssorter() instanceof RaireAssorter) {
            RaireAssertion rassertion = ((RaireAssorter) assertion.getAssorter()).getRassertion();
            return Math.round(rassertion.getDifficulty());
        } else if (cua.getContest() instanceof DHondtContest) {
            return Math.round(((DHondtContest) cua.getContest()).difficulty(assertion.getAssorter()));
        }
        return -1; */
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
    }

}

