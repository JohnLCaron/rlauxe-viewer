/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.core.ContestInfo;
import org.cryptobiotic.rlauxe.audit.CardPool;
import org.cryptobiotic.rlauxe.estimate.Vunder;
import org.cryptobiotic.rlauxe.persist.*;
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
import java.util.List;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.cryptobiotic.rlauxe.persist.csv.CardPoolCsvKt.readCardPoolCsvFile;

public class PoolTable extends JPanel implements ViewerPanelIF {
    static private final Logger logger = LoggerFactory.getLogger(PoolTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<PoolBean> poolTable;
    private final BeanTable<ContestTabBean> contestTable;
    // TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    PersistedMvrManager mvrManager;

    public PoolTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        poolTable = new BeanTable<>(PoolBean.class, (PreferencesExt) prefs.node("poolTable"), false,
                "Pool", "CardPool", null);
        poolTable.addListSelectionListener(e -> {
            PoolBean poolBean = poolTable.getSelectedBean();
            if (poolBean != null) {
                setSelectedPool(poolBean);
            }
        });

        contestTable = new BeanTable<>(ContestTabBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contest", "Vunder", null);

        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, poolTable, contestTable);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));

        setLayout(new BorderLayout());
        add(split1, BorderLayout.CENTER);

        logger.debug("poolTable init");
    }

    public void setFontSize(float size) {
        poolTable.setFontSize(size);
        contestTable.setFontSize(size);
    }

    public boolean setAuditRecord(String auditRecordLocation) {
        logger.debug("PoolTable setAuditRecord "+ auditRecordLocation);
        poolTable.setBeans(emptyList());
        contestTable.setBeans(emptyList());

        this.auditRecordLocation = auditRecordLocation;
        AuditRecordIF auditRecord = AuditRecord.Companion.read(auditRecordLocation);
        if (auditRecord == null) {
            logger.info("PoolTable failed on readFrom "+ auditRecordLocation);
            return false;
        }

        if (auditRecord instanceof CompositeAuditRecord) return false;
        this.auditRecord = (AuditRecord) auditRecord;
        this.mvrManager = new PersistedMvrManager(this.auditRecord, false);

        List<CardPool> pools = mvrManager.pools();
        if (pools == null) return false;

        java.util.List<PoolBean> beanList = new ArrayList<>();
        for (var pool : pools) {
            beanList.add(new PoolBean(pool));
        }
        poolTable.setBeans(beanList);

        return true;
    }

    void setSelectedPool(PoolBean bean) {
        java.util.List<ContestTabBean> beanList = new ArrayList<>();
        for (ContestTabulation tab : bean.pool.getContestTabs().values()) {
            beanList.add(new ContestTabBean(bean.pool, tab));
        }
        contestTable.setBeans(beanList);
    }

    public void saveState() {
        poolTable.saveState(false);
        contestTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
    }


    //////////////////////////////////////////////////////////////////

    public class PoolBean {
        CardPool pool = null;

        public PoolBean() {
        }

        PoolBean(CardPool pool) {
            this.pool = pool;
        }

        public String getName() {
            return pool.name();
        }

        public Integer getId() {
            return pool.id();
        }

        public boolean getSingleStyle() {
            return pool.hasExactContests();
        }

        public Integer getNcards() {
            return pool.ncards();
        }

        public String getContests() {
            int[] ids = pool.possibleContests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }

        public Integer getNcontests() {
            return pool.possibleContests().length;
        }

        public String getClassName() { return pool.getClass().getSimpleName(); }

        public String show() {
            return pool.toString();
        }
    }

    public class ContestTabBean {
        ContestTabulation contestTab;
        Vunder vunder;

        public ContestTabBean() {
        }

        ContestTabBean(CardPool pool, ContestTabulation contestTab) {
            this.contestTab = contestTab;
            this.vunder = contestTab.votesAndUndervotes(pool.getPoolId(), pool.ncards(), pool.hasExactContests());
        }

        public Integer getContestId() {return vunder.getContestId();}
        public String getIsIrv() { return ((contestTab.isIrv()) ? "yes" : ""); }
        public Integer getVoteForN() {return vunder.getVoteForN();}

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


}
