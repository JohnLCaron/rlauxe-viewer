/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.bridge.Naming;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
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

public class AuditTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(AuditTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<AssertionBean> assertionTable;

    private final JSplitPane split2;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    private AuditConfig auditConfig;

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

        assertionTable.addPopupOption("Show Assertion", assertionTable.makeShowAction(infoTA, infoWindow,
                bean -> ((AssertionBean) bean).show()));

        setFontSize(fontSize);

        // layout of tables
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, assertionTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));

        // auditRoundTable not used for now

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("auditTable init");
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        assertionTable.setFontSize(size);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;

        logger.debug("auditTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;

        try {
            this.auditConfig = auditRecord.getConfig();

            var contestMap = new HashMap<Integer, ContestBean>();
            java.util.List<ContestBean> beanList = new ArrayList<>();
            for (var contest : auditRecord.getContests()) {
                var bean = new ContestBean(contest);
                beanList.add(bean);
                contestMap.put(contest.getId(), bean);
            }
            contestTable.setBeans(beanList);

            // put last round for each contest
            for (var round : auditRecord.getRounds()) {
                for (var contestRound : round.getContestRounds()) {
                    var bean = contestMap.get(contestRound.getId());
                    bean.lastRound = contestRound;
                }
            }

            if (!auditRecord.getRounds().isEmpty()) {
                // select inProgress contest with smallest margin
                Optional<ContestBean> minByMargin = beanList
                        .stream()
                        .filter(bean ->bean.getStatus().equals("InProgress"))
                        .min(Comparator.comparing(ContestBean::getMargin));
                minByMargin.ifPresent(contestTable::setSelectedBean);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedContest(ContestBean contestBean) {
        ContestWithAssertions cua = contestBean.contestUA;
        java.util.List<AssertionBean> beanList = new ArrayList<>();
        for (Assertion a : cua.assertions()) {
            beanList.add(new AssertionBean(cua, a));
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

    void save() {
        contestTable.saveState(false);
        assertionTable.saveState(false);

        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    //////////////////////////////////////////////////////////////////

    /* void showInfo(Formatter f) {
        if (this.auditRecord == null) { return; }
        if (this.auditConfig == null) { return; }
        if (this.auditConfig.getAuditType() != AuditType.ONEAUDIT) { return; }

        var cardPools = makeCardPoolsFromAuditRecord(auditRecord);
        String poolVotes = cardPools.showPoolVotes(4);
        f.format("%s", poolVotes);
    } */

    //////////////////////////////////////////////////////////////////

    public class ContestBean {
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static {
            beanProperties.add(new BeanTable.TableBeanProperty("id", "contest identifier"));
            beanProperties.add(new BeanTable.TableBeanProperty("name", "contest name"));
            beanProperties.add(new BeanTable.TableBeanProperty("type", "contest type"));
            beanProperties.add(new BeanTable.TableBeanProperty("NCand", "number of candidates"));
            beanProperties.add(new BeanTable.TableBeanProperty("winners", "list of winning candidates"));
            beanProperties.add(new BeanTable.TableBeanProperty("nc", "trusted upper bound on contest ncards"));
            beanProperties.add(new BeanTable.TableBeanProperty("npop", "population size for diluted margin"));
            beanProperties.add(new BeanTable.TableBeanProperty("phantoms", "number of phantom votes"));
            beanProperties.add(new BeanTable.TableBeanProperty("votes", "reported vote count"));
            beanProperties.add(new BeanTable.TableBeanProperty("undervotes", "reported undervote count"));
            beanProperties.add(new BeanTable.TableBeanProperty("uvPct", "percent undervote count"));
            beanProperties.add(new BeanTable.TableBeanProperty("margin", "diluted margin (smallest assertion)"));
            beanProperties.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner (smallest assertion)"));

            beanProperties.add(new BeanTable.TableBeanProperty("poolPct", "percent of cards in pools"));
            beanProperties.add(new BeanTable.TableBeanProperty("status", "status of contest completion"));
        }

        ContestRound lastRound = null;
        ContestWithAssertions contestUA;

        public ContestBean() {
        }

        ContestBean(ContestWithAssertions contestUA) {
            this.contestUA = contestUA;
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

        public Integer getNpop() {return contestUA.getNpop();}

        public Integer getUndervotes() {
            return contestUA.getContest().Nundervotes();
        }

        public Integer getUvPct() {
            return contestUA.getContest().undervotePct();
        }

        public Integer getPhantoms() {
            return contestUA.getNphantoms();
        }

        public String getWinners() {
            return contestUA.getContest().winners().toString();
        }

        public String getVotes() {
            var votes =  contestUA.getContest().votes();
            if (votes != null) return votes.toString();
            return "N/A";
        }

        public double getMargin() {
            // clca gets assertion with minimum noerror, and returns dilutedMargin of that.
            Double min = contestUA.minDilutedMargin();
            return min == null ? 0.0 : min;
        }

        public double getRecountMargin() {
            Double min = contestUA.minRecountMargin();
            return min == null ? 0.0 : min;
        }

        /* public Integer getTotalMvrs() {
            return (lastRound == null) ? 0 : lastRound.getActualMvrs();
        } */

        public String getStatus() {
            return (lastRound == null) ? Naming.status(contestUA.getPreAuditStatus()) : Naming.status(lastRound.getStatus());
        }

        /*
        public Integer getCompleted() {
            if (!lastRound.getStatus().getComplete()) return 0;
            int round = 0;
            for (AssertionRound assertion : lastRound.getAssertionRounds()) {
                round = max(round, assertion.getRoundProved());
            }
            return round;
        } */

        public Integer getPoolPct() {
            return contestUA.getContest().info().getMetadata().get("PoolPct");
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            // sb.append("%n%s%n".formatted(contestTable.tableModel.toString()));
            sb.append("%n%s%n".formatted(contestTable.tableModel.showBean(this, beanProperties)));
            sb.append("\n%s%n".formatted(contestUA.show()));
            return sb.toString();
        }
    }

    public class AssertionBean {
        static ArrayList<BeanTable.TableBeanProperty> beanProperties = new ArrayList<>();
        static {
            beanProperties.add(new BeanTable.TableBeanProperty("name", "assertion name"));
            beanProperties.add(new BeanTable.TableBeanProperty("desc", "assertion difficulty description"));
            beanProperties.add(new BeanTable.TableBeanProperty("difficulty", "assertion difficulty measure (IRV only)"));
            beanProperties.add(new BeanTable.TableBeanProperty("upperBound", "assorter upper bound"));
            beanProperties.add(new BeanTable.TableBeanProperty("noError", "noerror assort value (CLCA only)"));

            beanProperties.add(new BeanTable.TableBeanProperty("margin", "diluted margin"));
            beanProperties.add(new BeanTable.TableBeanProperty("mean", "diluted mean"));
            beanProperties.add(new BeanTable.TableBeanProperty("recountMargin", "(winner-loser)/winner"));
        }

        ContestWithAssertions cua;
        Assertion assertion;
        AssertionRound assertionRoundMaybe;

        public AssertionBean() {
        }

        AssertionBean(ContestWithAssertions cua, Assertion assertion) {
            this.cua = cua;
            this.assertion = assertion;
        }

        public String getName() {
            return assertion.getAssorter().shortName();
        }
        public String getDesc() {
            return cua.getContest().showAssertionDifficulty(assertion.getAssorter());
        }

        //public Integer getEstMvrs() {return assertionRound.getEstSampleSize();}

        // public Integer getCompleted() {return assertionRound.getRound();}

        // public String getStatus() {return Naming.status(assertionRound.getStatus());}

        public double getMargin() {
            return assertion.getAssorter().dilutedMargin();
        }

        public double getDifficulty() {
            if (assertion.getAssorter() instanceof RaireAssorter) {
                RaireAssertion rassertion = ((RaireAssorter) assertion.getAssorter()).getRassertion();
                return rassertion.getDifficulty();
            }
            return -1;
        }

        public double getRecountMargin() {
            return cua.getContest().recountMargin(assertion.getAssorter());
        }

        public double getMean() {
            return assertion.getAssorter().dilutedMean();
        }

        public double getNoError() {return assertion.getAssorter().noerror(); }

        public double getUpperBound() {return assertion.getAssorter().upperBound(); }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append("%n%s%n".formatted(assertionTable.tableModel.showBean(this, beanProperties)));

            sb.append(assertion.show());
            sb.append("\n  difficulty: %s".formatted(cua.getContest().showAssertionDifficulty(assertion.getAssorter())));
            return sb.toString();
        }
    }

}
