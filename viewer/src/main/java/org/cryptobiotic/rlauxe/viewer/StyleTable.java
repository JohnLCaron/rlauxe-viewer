/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.StyleIF;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord;
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager;
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

import static java.util.Collections.emptyList;

public class StyleTable  extends JPanel  implements ViewerPanelIF{
    static private final Logger logger = LoggerFactory.getLogger(StyleTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<PopBean> poolTable;
    TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private AuditRecord auditRecord;
    PersistedMvrManager mvrManager;

    public StyleTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        poolTable = new BeanTable<>(PopBean.class, (PreferencesExt) prefs.node("styleTable"), false,
                "CardStyles", "CardStyle", null);
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

    public boolean setAuditRecord(String auditRecordLocation) {
        logger.debug("StyleTable setAuditRecord "+ auditRecordLocation);
        poolTable.setBeans(emptyList());

        AuditRecordIF auditRecord = AuditRecord.Companion.read(auditRecordLocation);
        if (auditRecord == null) {
            logger.info("StyleTable failed on readFrom "+ auditRecordLocation);
            return false;
        }
        if (auditRecord instanceof CompositeAuditRecord) return false;
        this.auditRecord = (AuditRecord) auditRecord;
        this.mvrManager = new PersistedMvrManager(this.auditRecord, false);

        try {
            List<PopBean> beanList = new ArrayList<>();

            var styles = mvrManager.styles();
            if (styles != null) {
                for (var pop : styles) {
                    beanList.add(new PopBean(pop));
                }
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

    public void saveState() {
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
        StyleIF pop;

        public PopBean() {
        }

        PopBean(StyleIF pop) {
            this.pop = pop;
        }

        public String getName() {
            return pop.name();
        }

        public Integer getId() {
            return pop.id();
        }

        public boolean getExactContests() {
            return pop.hasExactContests();
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
            return pop.toString();
        }
    }

}
