/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditConfig;
import org.cryptobiotic.rlauxe.workflow.CardManifest;
import org.cryptobiotic.rlauxe.audit.PopulationIF;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.persist.CompositeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.IndependentWindow;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PopulationTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(PopulationTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<PopBean> poolTable;
    TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private AuditRecordIF auditRecord;

    public PopulationTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        poolTable = new BeanTable<>(PopBean.class, (PreferencesExt) prefs.node("poolTable"), false,
                "Population", "Population", null);
        poolTable.addListSelectionListener(e -> {
            PopBean poolBean = poolTable.getSelectedBean();
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

        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;

        try {
            AuditConfig auditConfig = auditRecord.getConfig();
            List<ContestWithAssertions> contestsUA = auditRecord.getContests();
            CardManifest cardManifest = this.auditRecord.readCardManifest();

            List<PopBean> beanList = new ArrayList<>();
            for (var pop : cardManifest.getPopulations()) {
                beanList.add(new PopBean(pop));
            }
            poolTable.setBeans(beanList);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedPool(PopBean bean) {
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

    static public class PopBean {
        PopulationIF pop;
        OneAuditPoolIF pool = null;

        public PopBean() {
        }

        PopBean(PopulationIF pop) {
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
            int[] ids = pop.possibleContests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }

        public Integer getNcontests() {
            return pop.possibleContests().length;
        }

        public String getClassName() { return pop.getClass().getSimpleName(); }

        public String show() {
            if (pool != null) return pool.show();
            return pop.toString();
        }
    }

}
