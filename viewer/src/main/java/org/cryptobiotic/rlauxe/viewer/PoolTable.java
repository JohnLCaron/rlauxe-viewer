/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.persist.CompositeRecord;
import org.cryptobiotic.rlauxe.persist.Publisher;
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

import static org.cryptobiotic.rlauxe.workflow.PersistedMvrManagerKt.readCardManifest;

public class PoolTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(PoolTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<PoolBean> poolTable;
    TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    private boolean isComposite;
    private AuditConfig auditConfig;

    public PoolTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        poolTable = new BeanTable<>(PoolBean.class, (PreferencesExt) prefs.node("poolTable"), false,
                "Population", "Population", null);
        poolTable.addListSelectionListener(e -> {
            PoolBean poolBean = poolTable.getSelectedBean();
            if (poolBean != null) {
                setSelectedPool(poolBean);
            }
        });
        // poolTable.addPopupOption("Show Population", poolTable.makeShowAction(localInfo,
        //    bean -> ((PoolBean) bean).show()));

        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, poolTable, localInfo);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));

        setLayout(new BorderLayout());
        add(split1, BorderLayout.CENTER);

        logger.debug("poolTable init");
    }

    public void setFontSize(float size) {
        poolTable.setFontSize(size);
        localInfo.setFontSize(size);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        logger.debug("auditTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;
        this.isComposite = (this.auditRecord instanceof CompositeRecord);

        try {
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

            } else {

                Publisher publisher = new Publisher(auditRecordLocation);
                CardManifest cardManifest = readCardManifest(publisher);

                java.util.List<PoolBean> beanList = new ArrayList<>();
                for (var pop : cardManifest.getPopulations()) {
                    beanList.add(new PoolBean(pop));
                }
                poolTable.setBeans(beanList);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedPool(PoolBean bean) {
        localInfo.setText(bean.show());
        localInfo.gotoTop();
    }

    void save() {
        poolTable.saveState(false);

        prefs.putInt("splitPos1", split1.getDividerLocation());
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

    static public class PoolBean {
        PopulationIF pop;
        OneAuditPoolIF pool = null;

        public PoolBean() {
        }

        PoolBean(PopulationIF pop) {
            this.pop = pop;
            if (pop instanceof OneAuditPoolIF) pool = (OneAuditPoolIF) pop;
        }

        public String getName() {
            return pop.name();
        }

        public Integer getId() {
            return pop.id();
        }

        public boolean getExactContests() {
            return pop.hasSingleCardStyle();
        }

        public Integer getNcards() {
            return pop.ncards();
        }

        public String getContests() {
            int[] ids = pop.contests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }

        public Integer getNcontests() {
            return pop.contests().length;
        }

        public String getClassName() { return pop.getClass().getSimpleName(); }

        public String show() {
            if (pool != null) return pool.show();
            return pop.toString();
        }
    }

}
