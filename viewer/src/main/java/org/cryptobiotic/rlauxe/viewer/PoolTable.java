/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.core.ContestInfo;
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.persist.CompositeRecord;
import org.cryptobiotic.rlauxe.persist.Publisher;
import org.cryptobiotic.rlauxe.util.ContestTabulation;
import org.cryptobiotic.rlauxe.util.Vunder;
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

import static org.cryptobiotic.rlauxe.persist.csv.CardPoolCsvKt.readCardPoolCsvFile;

public class PoolTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(PoolTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<PoolBean> poolTable;
    private final BeanTable<ContestBean> contestTable;
    // TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    private boolean isComposite;
    private AuditConfig auditConfig;

    public PoolTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        poolTable = new BeanTable<>(PoolBean.class, (PreferencesExt) prefs.node("poolTable"), false,
                "Pool", "OneAuditPoolFromCvrs", null);
        poolTable.addListSelectionListener(e -> {
            PoolBean poolBean = poolTable.getSelectedBean();
            if (poolBean != null) {
                setSelectedPool(poolBean);
            }
        });

        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
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

    boolean setAuditRecord(String auditRecordLocation) {
        logger.debug("auditTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;
        this.isComposite = (this.auditRecord instanceof CompositeRecord);
        var infos = new HashMap<Integer, ContestInfo>();
        for (var contest : auditRecord.getContests()) {
            infos.put(contest.getId(), contest.getContest().info());
        }

        /* try {
            this.auditConfig = auditRecord.getConfig();
            List<ContestWithAssertions> contestsUA = auditRecord.getContests();

            if (isComposite) {
                // TODO choose component
                CompositeRecord composite = (CompositeRecord) this.auditRecord;
                AuditRecord first = composite.getComponentRecords().getFirst();

                Publisher publisher = new Publisher(first.getLocation());
                CardManifest cardManifest = readCardManifest(publisher);

                java.util.List<PoolBean> beanList = new ArrayList<>();
                for (var pop : cardManifest.getPopulations()) {
                    beanList.add(new PoolBean(pop));
                }
                poolTable.setBeans(beanList);

            } else { */
                Publisher publisher = new Publisher(auditRecordLocation);

                List<OneAuditPoolFromCvrs> pools = readCardPoolCsvFile(publisher.cardPoolsFile(), infos);


                java.util.List<PoolBean> beanList = new ArrayList<>();
                for (var pool : pools) {
                    beanList.add(new PoolBean(pool));
                }
                poolTable.setBeans(beanList);
            /* }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        } */

        return true;
    }

    void setSelectedPool(PoolBean bean) {
        java.util.List<ContestBean> beanList = new ArrayList<>();
        for (ContestTabulation tab : bean.pool.getContestTabs().values()) {
            beanList.add(new ContestBean(bean.pool, tab));
        }
        contestTable.setBeans(beanList);
    }

    void save() {
        poolTable.saveState(false);
        contestTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
    }


    //////////////////////////////////////////////////////////////////

    public class PoolBean {
        OneAuditPoolFromCvrs pool = null;

        public PoolBean() {
        }

        PoolBean(OneAuditPoolFromCvrs pool) {
            this.pool = pool;
        }

        public String getName() {
            return pool.name();
        }

        public Integer getId() {
            return pool.id();
        }

        public boolean getSingleStyle() {
            return pool.hasSingleCardStyle();
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

        public String show() {
            if (pool != null) return pool.show();
            return pool.toString();
        }
    }

    public class ContestBean {
        ContestTabulation contestTab;
        Vunder vunder;

        public ContestBean() {
        }

        ContestBean(OneAuditPoolFromCvrs pool, ContestTabulation contestTab) {
            this.contestTab = contestTab;
            this.vunder = contestTab.votesAndUndervotes(pool.getPoolId(), pool.ncards());
        }

        public Integer getContestId() {return vunder.getContestId();}
        public String getIsIrv() { return ((contestTab.isIrv()) ? "yes" : ""); }
        public Integer getVoteForN() {return vunder.getVoteForN();}
        public Integer getNcards() {return vunder.getNcards();}
        public Integer getUndervotes() {return vunder.getUndervotes();}
        public String getVotes() {
            if (!contestTab.isIrv()) return vunder.cands().toString();
            else return "VC " + vunder.getVoteCounts().size() + " unique rankings";
        }
        public Integer getNVotes() {return vunder.getNvotes();}
        public Integer getMissing() {return vunder.getMissing();}

        public String show() {
            return contestTab.toString() + "\n" +  vunder.toString();
        }
    }


}
