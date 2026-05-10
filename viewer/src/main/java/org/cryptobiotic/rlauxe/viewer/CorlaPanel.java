/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.Config;
import org.cryptobiotic.rlauxe.betting.TestH0Status;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.CountyComposite;
import org.cryptobiotic.rlauxe.persist.CountyData;
import org.cryptobiotic.rlauxe.viewer.ContestsPanel.ContestBean;
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

public class CorlaPanel extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(CorlaPanel.class);

    private final PreferencesExt prefs;

    private final BeanTable<CountyBean> countyTable;
    private final BeanTable<ContestBean> contestTable;

    private final JSplitPane split2;

    private String auditRecordLocation = "none";
    private CountyComposite countyAudit;
    private Config config;
    private Map<Integer, Integer> oneshotMvrs;

    public CorlaPanel(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        countyTable = new BeanTable<>(CountyBean.class, (PreferencesExt) prefs.node("countyTable"), false,
                "Counties", "County Audit", null);
        countyTable.addListSelectionListener(e -> {
            CountyBean county = countyTable.getSelectedBean();
            if (county != null) {
                setSelectedCounty(county);
            }
        });

        contestTable = new BeanTable<>(ContestBean.class, (PreferencesExt) prefs.node("contestTable"), false,
                "Contests", "ContestRound", null);
        contestTable.addPopupOption("Show Contest", contestTable.makeShowAction(infoTA, infoWindow,
                bean -> showContest((ContestBean) bean)));

        setFontSize(fontSize);

        // layout of tables
        split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, countyTable, contestTable);
        split2.setDividerLocation(prefs.getInt("splitPos2", 200));

        // auditRoundTable not used for now

        setLayout(new BorderLayout());
        add(split2, BorderLayout.CENTER);

        logger.debug("corlaPanel init");
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        countyTable.setFontSize(size);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        contestTable.setBeans(emptyList());

        logger.debug("corlaPanel setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        var record = AuditRecord.Companion.read(auditRecordLocation);
        if (record == null) return false;
        if (!(record instanceof CountyComposite)) return false;
        this.countyAudit = (CountyComposite) record;

        try {
            this.config = countyAudit.getConfig();

            Map<String, CountyData> countyDataMap = countyAudit.countyData();

            java.util.List<CountyBean> countyList = new ArrayList<>();
            for (var county : countyAudit.getComponentRecords()) {
                var bean = new CountyBean(county, countyDataMap.get(county.name()));
                countyList.add(bean);
            }
            countyTable.setBeans(countyList);

            /* java.util.List<ContestBean> beanList = new ArrayList<>();
            for (var contest : countyAudit.getContests()) {
                var bean = new ContestBean(contest);
                beanList.add(bean);
            }
            contestTable.setBeans(beanList); */


        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedCounty(CountyBean countyBean) {
        java.util.List<ContestsPanel.ContestBean> beanList = new ArrayList<>();
        for (var contestUA : countyAudit.getContests()) {
            var CORLAcounties =  contestUA.getContest().info().getMetadata().get("CORLAcounties");
            if (contestUA.getPreAuditStatus().equals(TestH0Status.InProgress) && CORLAcounties.contains(countyBean.county.name())) {
                ContestBean contestBean = new ContestBean(contestUA);
                contestBean.countyMvrs = countyBean.getNmvrs();
                beanList.add(contestBean);
            }
        }
        contestTable.setBeans(beanList);
    }

    public String showContest(ContestBean contestBean) {
        StringBuilder sb = new StringBuilder();
        sb.append("%n%s%n".formatted(contestTable.tableModel.showBean(this, ContestBean.beanProperties)));
        sb.append("\n%s%n".formatted(contestBean.contestUA.show()));
        return sb.toString();
    }

    void save() {
        contestTable.saveState(false);
        countyTable.saveState(false);

        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    //////////////////////////////////////////////////////////////////

    public class CountyBean {
        AuditRecord county;
        CountyData countyData;

        public CountyBean() {
        }

        CountyBean(AuditRecord county, CountyData countyData) {
            this.county = county;
            this.countyData = countyData;
        }

        public String getName() {
            return county.name();
        }
        public Integer getNcontests() {return county.getContests().size(); }
        public Integer getNpop() { return countyData.getNpop(); }
        public Integer getNmvrs() { return countyData.getNmvrs(); }
    }
}
