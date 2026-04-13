/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.CardPool;
import org.cryptobiotic.rlauxe.core.Cvr;
import org.cryptobiotic.rlauxe.estimate.Vunder;
import org.cryptobiotic.rlauxe.estimate.VunderPool;
import org.cryptobiotic.rlauxe.estimate.VunderPools;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.persist.CompositeRecord;
import org.cryptobiotic.rlauxe.util.ContestTabulation;
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.cryptobiotic.rlauxe.estimate.VunderPoolsKt.makeCvrsForVunderPool;

public class ContestPoolsTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(ContestPoolsTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<ContestBean> contestTable;
    private final BeanTable<ContestPoolBean> contestPoolTable;
    private final BeanTable<CvrBean> cvrTable;
    // TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1, split2;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    PersistedMvrManager mvrManager;
    VunderPools vunderPools;

    public ContestPoolsTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contest", "Vunder", null);
        contestTable.addListSelectionListener(e -> {
            ContestBean selected = contestTable.getSelectedBean();
            if (selected != null) {
                setSelectedContest(selected);
            }
        });

        contestPoolTable = new BeanTable<>(ContestPoolBean.class, (PreferencesExt) prefs.node("contestPoolTable"), false,
                "ContestPool", "ContestPool", null);
        contestPoolTable.addPopupOption("Show ContestPool", contestPoolTable.makeShowAction(infoTA, infoWindow,
                bean -> ((ContestPoolBean) bean).show()));

        contestPoolTable.addListSelectionListener(e -> {
            ContestPoolBean selected = contestPoolTable.getSelectedBean();
            if (selected != null) {
                showSimulatedCards(selected);
            }
        });

        //contestPoolTable.addPopupOption("Show Simulated Cards",
        //    contestPoolTable.makeActionOnCurrentBean(bean -> new MyAbstractAction()
        //));

        cvrTable = new BeanTable<>(CvrBean.class, (PreferencesExt) prefs.node("cvrTable"), false,
                "Cvrs", "Cvr", null);

        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, contestTable, contestPoolTable);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split1, cvrTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 600));

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("poolTable init");
    }

    public void setFontSize(float size) {
        contestPoolTable.setFontSize(size);
        contestTable.setFontSize(size);
        cvrTable.setFontSize(size);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        logger.debug("ContestPoolsTable setAuditRecord "+ auditRecordLocation);
        contestPoolTable.setBeans(emptyList());
        contestTable.setBeans(emptyList());
        cvrTable.setBeans(emptyList());

        this.auditRecordLocation = auditRecordLocation;
        AuditRecordIF auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (auditRecord == null) {
            logger.info("ContestPoolsTable failed on readFrom "+ auditRecordLocation);
            return false;
        }

        if (auditRecord instanceof CompositeRecord) return false;
        this.auditRecord = (AuditRecord) auditRecord;
        this.mvrManager = new PersistedMvrManager(this.auditRecord, false);

        List<CardPool> pools = mvrManager.pools();
        if (pools == null) return false;

        var contests = new HashMap<Integer, ArrayList<PoolAndContest>>();
        for (var pool : pools) {
            for (Integer contestId : pool.possibleContests()) {
                var tab = pool.contestTab(contestId);
                var list = contests.computeIfAbsent(contestId, key -> new ArrayList<>());
                list.add(new PoolAndContest(contestId, pool, tab));
            }
        }

        List<ContestBean> beanList = new ArrayList<>();
        contests.forEach ( (id, pandc) -> {
            beanList.add(new ContestBean(id, pandc));
        });
        contestTable.setBeans(beanList);

        this.vunderPools = new VunderPools(pools);

        return true;
    }

    class PoolAndContest {
        Integer contestId;
        CardPool cardPool;
        ContestTabulation tab;

        PoolAndContest(Integer contestId, CardPool pool, ContestTabulation tab) {
            this.contestId = contestId;
            this.cardPool = pool;
            this.tab = tab;
        }
    }

    void setSelectedContest(ContestBean bean) {
        java.util.List<ContestPoolBean> beanList = new ArrayList<>();
        for (var pandc : bean.pandcs) {
            beanList.add(new ContestPoolBean(pandc));
        }
        contestPoolTable.setBeans(beanList);
    }


    public void showSimulatedCards(ContestPoolBean bean) {
        VunderPool vunderpool = vunderPools.getVunderPools().get(bean.pool.getPoolId());
        logger.info("found vunderpool " + vunderpool);

        List<Cvr> cvrs = makeCvrsForVunderPool(bean.pool, vunderpool);
        logger.info("made " + cvrs.size() + " cvrs");

        java.util.List<CvrBean> beanList = new ArrayList<>();
        int idx = 0;
        for (var cvr : cvrs) {
            beanList.add(new CvrBean(cvr, idx++));
        }
        cvrTable.setBeans(beanList);
    }

    void save() {
        contestPoolTable.saveState(false);
        contestTable.saveState(false);
        cvrTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    //////////////////////////////////////////////////////////////////

    public class ContestBean {
        Integer contestId;
        ArrayList<PoolAndContest> pandcs;
        ContestTabulation tab;

        public ContestBean() {
        }

        ContestBean(Integer id, ArrayList<PoolAndContest> pandcs) {
            this.contestId = id;
            this.pandcs = pandcs;
            if (pandcs.size() > 1) {
                tab = pandcs.getFirst().tab;
            }
        }

        public Integer getContestId() {return contestId;}
        public Integer getNPools() {return pandcs.size();}

        public String getIsIrv() { return ((tab.isIrv()) ? "yes" : ""); }
        public Integer getVoteForN() {return tab.getVoteForN();}
    }

    public class ContestPoolBean {
        CardPool pool;
        ContestTabulation contestTab;
        Vunder vunder;

        public ContestPoolBean() {
        }

        ContestPoolBean(PoolAndContest panc) {
            this.pool = panc.cardPool;
            this.contestTab = panc.tab;
            this.vunder = contestTab.votesAndUndervotes(pool.getPoolId(), pool.ncards(), pool.hasExactContests());
        }

        public String getPoolName() {
            return pool.name();
        }

        public Integer getPoolId() {
            return pool.id();
        }

        public boolean getSingleStyle() {
            return pool.hasExactContests();
        }

        // TODO why are these the same number ??
        public Integer getPoolTotalCards() {return pool.ncards();}
        public Integer getNCards() {return vunder.getNcards();}

        public Integer getUndervotes() {return vunder.getUndervotes();}
        public Integer getNVotes() {return vunder.getNvotes();}
        public Integer getMissing() {return vunder.getMissing();}
        public String getVotes() {
            if (!contestTab.isIrv()) return vunder.cands().toString();
            else return "VC " + vunder.getVoteCounts().size() + " unique rankings";
        }

        public String show() {
            return contestTab.toString() + "\n" +  vunder.toString();
        }
    }

    public class CvrBean {
        Cvr card;
        int index;

        public CvrBean() {
        }

        CvrBean(Cvr cvr, int index) {
            this.card = cvr;
            this.index = index;
        }

        public Integer getIndex() {
            return this.index;
        }
        public String getContests() {
            var ids = card.getVotes().keySet();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }
        public Integer getPoolId() { return card.poolId(); }
        public String getVotes() {
            var votes = card.getVotes();
            StringBuilder sb = new StringBuilder();
            for (int contestId : votes.keySet()) {
                sb.append("%d:".formatted(contestId));
                var cands = votes.get(contestId);
                if (cands.length == 0) sb.append(" ,");
                if (cands.length == 1) sb.append("%d, ".formatted(cands[0]));
                else if (cands.length > 1) {
                    sb.append("[");
                    for (int idx=0; idx<cands.length; idx++) {
                        int cand = cands[idx];
                        if (idx > 0)  sb.append(", ");
                        sb.append("%d".formatted(cand));
                    }
                    sb.append("],");
                }
            }
            return sb.toString();
        }

        public String show() {
            StringBuilder sb = new StringBuilder();
            sb.append(card.toString());
            return sb.toString();
        }
    }


    private class MyAbstractAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            ContestPoolBean selected = contestPoolTable.getSelectedBean();
            logger.info("found selected contestPoolTable" + selected);
            showSimulatedCards(selected);
        }
    }
}
