/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditConfig;
import org.cryptobiotic.rlauxe.audit.AuditableCard;
import org.cryptobiotic.rlauxe.audit.CardManifest;
import org.cryptobiotic.rlauxe.audit.PopulationIF;
import org.cryptobiotic.rlauxe.core.ContestInfo;
import org.cryptobiotic.rlauxe.core.ContestUnderAudit;
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.Publisher;
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
import java.util.Map;
import java.util.TreeMap;

import static org.cryptobiotic.rlauxe.workflow.PersistedMvrManagerKt.readCardManifest;

public class CardTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(CardTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<CardBean> cardTable;
    TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    private AuditConfig auditConfig;
    private CardManifest cardManifest;
    Map<String, PopulationIF> poolMap;

    public CardTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        cardTable = new BeanTable<>(CardBean.class, (PreferencesExt) prefs.node("cardTable"), false,
                "AuditableCard", "CardManifest (sorted)", null);
        cardTable.addListSelectionListener(e -> {
            CardBean cardBean = cardTable.getSelectedBean();
            if (cardBean != null) {
                setSelectedCard(cardBean);
            }
        });
        // poolTable.addPopupOption("Show Population", poolTable.makeShowAction(localInfo,
        //    bean -> ((PoolBean) bean).show()));

        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, cardTable, localInfo);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));

        setLayout(new BorderLayout());
        add(split1, BorderLayout.CENTER);

        logger.debug("cardTable init");
    }

    public void setFontSize(float size) {
        cardTable.setFontSize(size);
        localInfo.setFontSize(size);
    }

    void setSelected(String wantRecordDir) {
        setAuditRecord(wantRecordDir);
    }

    boolean setAuditRecord(String auditRecordLocation) {
        logger.debug("auditTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;

        try {
            this.auditConfig = auditRecord.getConfig();
            List<ContestUnderAudit> contestsUA = auditRecord.getContests();

            Map<Integer, ContestInfo> infos = new TreeMap<>(); // sorted
            for (ContestUnderAudit contestUA : contestsUA) {
                infos.put(contestUA.getId(), contestUA.getContest().info());
            }

            Publisher publisher = new Publisher(auditRecordLocation);
            this.cardManifest = readCardManifest(publisher);

            Map<String, PopulationIF> pools = new TreeMap<>(); // sorted
            for (PopulationIF pool : cardManifest.getPopulations()) {
                String cardStyle = "P" + pool.id();
                pools.put(cardStyle, pool);
            }
            this.poolMap = pools;

            List<CardBean> beanList = new ArrayList<>();
            var iter = cardManifest.getCards().iterator();
            int count = 0;
            while (iter.hasNext() && count < 1000) {
                var card = iter.next();
                beanList.add(new CardBean(card));
                count++;
            }
            cardTable.setBeans(beanList);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    PopulationIF findPool(String cardStyle) {
        return poolMap.get(cardStyle);
    }

    void setSelectedCard(CardBean bean) {
        localInfo.setText(bean.show());
        localInfo.gotoTop();
    }

    void save() {
        cardTable.saveState(false);

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

    // data class AuditableCard (
    //    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    //    val index: Int,  // index into the original, canonical list of cards
    //    val prn: Long,   // psuedo random number
    //    val phantom: Boolean,
    //    val possibleContests: IntArray, // remove
    //
    //    val votes: Map<Int, IntArray>?, // must have this and/or population
    //    val poolId: Int?,
    //    val cardStyle: String? = null, // remove
    //    val population: PopulationIF? = null, // not needed if hasStyle ?
    //)

    public class CardBean {
        AuditableCard card;

        public CardBean() {
        }

        CardBean(AuditableCard card) {
            this.card = card;
        }

        public String getLocation() {
            return card.getLocation();
        }
        public Integer getIndex() {
            return card.getIndex();
        }
        public long getPrn() {
            return card.getPrn();
        }
        public Boolean getPhantom() { return card.getPhantom(); }
        /* public String getContests() {
            int[] ids = card.contests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        } */
        public Integer getPoolId() { return card.getPoolId(); }
        public String getCardStyle() { return card.getCardStyle(); }
        public String getPopulation() {
            var pop = card.getPopulation();
            if (pop == null) return "";
            int[] ids = pop.contests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }
        public String getVotes() {
            var votes = card.getVotes();
            if (votes == null) return "N/A";
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

            var pop = card.getPopulation();
            if (pop == null) {
                var cardStyle = card.getCardStyle();
                if (cardStyle != null) {
                    pop = findPool(cardStyle);
                }
            }
            if (pop != null) {
                OneAuditPoolIF oapool = null;
                if (pop instanceof OneAuditPoolIF) oapool = (OneAuditPoolIF) pop;
                if (oapool != null) {
                    sb.append(oapool.show());
                } else {
                    sb.append(pop.toString());
                }
            }
            return sb.toString();
        }
    }

}
