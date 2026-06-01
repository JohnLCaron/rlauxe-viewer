/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.Config;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.CountyAudit;
import org.cryptobiotic.rlauxe.persist.CountyContestData;
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
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.cryptobiotic.rlauxe.util.UtilsKt.roundUp;

public class CountyPanel extends JPanel implements ViewerPanelIF {
    static private final Logger logger = LoggerFactory.getLogger(CountyPanel.class);

    private final PreferencesExt prefs;

    private final BeanTable<CountyBean> countyTable;
    private final BeanTable<CountyContestBean> contestTable;

    private final JSplitPane split2;

    private String auditRecordLocation = "none";
    private CountyAudit countyAudit;
    private Config config;
    private Map<Integer, Integer> oneshotMvrs;
    Map<String, CountyBean> countyMap = emptyMap();
    CountyBean totalBean = null;

    List<CountyContestData> countyContestData = emptyList();

    public CountyPanel(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        countyTable = new BeanTable<>(CountyBean.class, (PreferencesExt) prefs.node("countyTable"), false,
                "Counties", "County Audit", null);
        countyTable.addListSelectionListener(e -> {
            CountyBean county = countyTable.getSelectedBean();
            if (county != null) {
                setSelectedCounty(county);
            }
        });

        contestTable = new BeanTable<>(CountyContestBean.class, (PreferencesExt) prefs.node("countyContestTable"), false,
                "CountyContest", "CountyContest", null);
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

    void setSelectedTab() {
        readSample(); // TODO does this mean we dont need the button ??
    }

    public void getActions(JPanel container) {
        /*
        AbstractAction readSampleMvrs = new AbstractAction() {
            public void actionPerformed(ActionEvent e) { readSample();}
        };
        BAMutil.setActionProperties(readSampleMvrs, "count.png", "Count mvrs used", false, 'S', -1);
        BAMutil.addActionToContainer(container, readSampleMvrs); */
    }

    void readSample() {
        int countMvrs = 0;
        var mvrCounts = countyAudit.countMvrsByCounty();
        for (var countyData : mvrCounts.values()) {
            var countyBean = countyMap.get(countyData.getCountyName());
            if (countyBean != null) {
                countyBean.nmvrsConsistent = countyData.getNmvrs();
                countMvrs += countyData.getNmvrs();
            } else {
                logger.warn( "cant find countyName '" + countyData.getCountyName() +"'");
            }
        }
        if (totalBean != null) {
            totalBean.nmvrsConsistent = countMvrs;
        }
        countyTable.refresh();
    }

    public void setFontSize(float size) {
        contestTable.setFontSize(size);
        countyTable.setFontSize(size);
    }

    public boolean setAuditRecord(String auditRecordLocation) {
        this.auditRecordLocation = auditRecordLocation;
        contestTable.setBeans(emptyList());

        logger.debug("countyPanel setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        var record = AuditRecord.Companion.read(auditRecordLocation);
        if (record == null) return false;
        if (!(record instanceof CountyAudit)) return false;
        this.countyAudit = (CountyAudit) record;

        try {
            this.config = countyAudit.getConfig();

            int countUniformMvrs = 0;
            CountyBean statewide = null;

            java.util.List<CountyBean> countyList = new ArrayList<>();
            for (var countyData : countyAudit.getCountyData()) {
                var bean = new CountyBean(countyData);
                if (bean.getName().equals("Statewide")) statewide = bean;
                    else countUniformMvrs += bean.getNmvrsUniform(); // statewide now included in counties, so dont count twice
                countyList.add(bean);
            }
            var npop = (statewide == null) ? 0 : statewide.npop;
            totalBean = new CountyBean(new CountyData("=Total", countUniformMvrs, npop));
            countyList.add(totalBean);
            countyTable.setBeans(countyList);

            // if (statewide != null) statewide.nmvrsUniform = countUniformMvrs;
            this.countyMap = countyList.stream()
                    .collect(Collectors.toMap(CountyBean::getName, user -> user));

            this.countyContestData = countyAudit.getCountyContestData();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void setSelectedCounty(CountyBean countyBean) {
        java.util.List<CountyContestBean> beanList = new ArrayList<>();
        for (var countyContest : countyContestData) {
            if (countyContest.getCountyName().equals(countyBean.countyName)) {
                var bean = new CountyContestBean(countyContest);
                beanList.add(bean);
                bean.npop = countyBean.getNpop();
                bean.nmvrsConsistent = countyBean.getNmvrsConsistent();
                bean.nmvrsUniform = countyBean.getNmvrsUniform();
            }
        }
        contestTable.setBeans(beanList);
    }

    public String showContest(ContestBean contestBean) {
        StringBuilder sb = new StringBuilder();
        sb.append("%n%s%n".formatted(contestTable.tableModel.showBean(contestBean, BeanProperties.contests)));
        sb.append("\n%s%n".formatted(contestBean.contestUA.show()));
        return sb.toString();
    }

    public void saveState() {
        contestTable.saveState(false);
        countyTable.saveState(false);

        prefs.putInt("splitPos2", split2.getDividerLocation());
    }

    //////////////////////////////////////////////////////////////////

    public class CountyBean {
        String countyName;
        Integer npop;
        Integer nmvrsUniform;
        Integer nmvrsConsistent = 0;

        public CountyBean() {
        }

        CountyBean(CountyData countyData) {
            this.countyName = countyData.getCountyName();
            this.npop = countyData.getNpop();
            this.nmvrsUniform = countyData.getNmvrs();
        }

        public String getName() {return countyName;}
        public Integer getNpop() { return npop; }
        public Integer getNmvrsUniform() { return nmvrsUniform; }
        public Integer getNmvrsConsistent() { return nmvrsConsistent; }
        public Integer getDiff() { return nmvrsUniform - nmvrsConsistent; }
        public Integer getUniformInvRate() { return (nmvrsUniform == 0) ? 0 : roundUp((double) getNpop() / nmvrsUniform); }
        public Integer getConsistentInvRate() { return (nmvrsConsistent == 0) ? 0 : roundUp((double) getNpop() / nmvrsConsistent); }
    }

    public class CountyContestBean {
        CountyContestData countyContestData;
        Integer npop;
        Integer nmvrsUniform;
        Integer nmvrsConsistent = 0;

        public CountyContestBean() {
        }

        CountyContestBean(CountyContestData countyContestData) {
            this.countyContestData = countyContestData;
        }

        public String getContestName() { return countyContestData.getContestName(); }
        public Integer getVoteDiff() { return countyContestData.getVoteDiff(); }
        public String getVotes() { return countyContestData.getVotes().toString(); }
        public Integer getNpop() { return npop; }
        //public Integer getNmvrsUniform() { return nmvrsUniform; }
        //public Integer getNmvrsConsistent() { return nmvrsConsistent; }
    }
}
