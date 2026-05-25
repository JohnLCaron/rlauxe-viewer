/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.*;
import org.cryptobiotic.rlauxe.persist.*;
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager;
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

import static java.util.Collections.emptyList;

public class CardTable extends JPanel implements ViewerPanelIF {
    static private final Logger logger = LoggerFactory.getLogger(CardTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<CardBean> cardTable;
    TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private String auditRecordLocation = "none";
    private AuditRecord auditRecord;
    private PersistedMvrManager mvrManager;
    private Boolean needsReading = true;

    private SortedManifest cardManifest;
    Map<String, StyleIF> poolMap = Collections.emptyMap();

    public CardTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        cardTable = new BeanTable<>(CardBean.class, (PreferencesExt) prefs.node("cardTable"), false,
                "CardManifest", "AuditableCard", null);
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

    public boolean setAuditRecord(String auditRecordLocation) {
        logger.info("CardTable setAuditRecord " + auditRecordLocation);
        cardTable.setBeans(emptyList());

        this.auditRecordLocation = auditRecordLocation;
        AuditRecordIF auditRecord = AuditRecord.Companion.read(auditRecordLocation);
        if (auditRecord == null) {
            logger.info("CardTable failed on readFrom " + auditRecordLocation);
            return false;
        }
        if (auditRecord instanceof CompositeAuditRecord) return false;
        this.auditRecord = (AuditRecord) auditRecord;
        this.mvrManager = new PersistedMvrManager(this.auditRecord, false);

        needsReading = true;

        return true;
    }

    void setSelectedTab() {
        if (needsReading) {
            if (readCards()) needsReading = false;
        }
    }

    boolean readCards() {
        logger.info("start readCards for " + auditRecordLocation);

        Config config = auditRecord.getConfig();
        Integer cutoff = config.getRound().getSampling().getContestSampleCutoff();
        Integer ncardsToRead = (cutoff == null || cutoff < 11111) ? 11111 : cutoff;

        try {
            this.cardManifest = this.mvrManager.sortedManifest();

            List<StyleIF> styles = this.mvrManager.styles();
            if (styles != null) {
                Map<String, StyleIF> pools = new TreeMap<>(); // sorted
                for (StyleIF pool : styles) {
                    String cardStyle = "P" + pool.id();
                    pools.put(cardStyle, pool);
                }
                this.poolMap = pools;
            } else {
                List<CardPool> cardPools = this.mvrManager.pools();
                if (cardPools != null) {
                    Map<String, StyleIF> pools = new TreeMap<>(); // sorted
                    for (StyleIF pool : cardPools) {
                        String cardStyle = "P" + pool.id();
                        pools.put(cardStyle, pool);
                    }
                    this.poolMap = pools;
                }
            }

            List<CardBean> beanList = new ArrayList<>();
            int index = 1;
            try (var iter = cardManifest.getCards().iterator()) {
                while (iter.hasNext() && index < ncardsToRead) {
                    var card = iter.next();
                    beanList.add(new CardBean(card, index));
                    index++;
                }
            }
            cardTable.setBeans(beanList);
            logger.info("end readCards " + index + " cards from " + auditRecordLocation);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
            return false;
        }

        return true;
    }

    StyleIF findPool(String cardStyle) {
        return poolMap.get(cardStyle);
    }

    void setSelectedCard(CardBean bean) {
        localInfo.setText(bean.show());
        localInfo.gotoTop();
    }

    public void saveState() {
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
//    val population: StyleIF? = null, // not needed if hasStyle ?
//)

        // must be public
    public class CardBean {
        AuditableCardIF card;
        int index;

        public CardBean() {
        }

        CardBean(AuditableCardIF card, int index) {
            this.card = card;
            this.index = index;
        }

        public String getId() {
            return card.id();
        }
        public String getLocation() {
            return card.location();
        }

        public Integer getIndex() {
            return this.index;
        }

        public Integer getCardIndex() {
            return card.index();
        }

        public long getPrn() {
            return card.prn();
        }

        public Boolean getPhantom() {
            return card.phantom();
        }

        public String getContests() {
            int[] ids = card.possibleContests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }

        public Integer getPoolId() {
            return card.poolId();
        }

        public String getCardStyle() {
            return card.styleName();
        }

        public String getPossibleContests() {
            var pop = card.style();
            int[] ids = pop.possibleContests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }

        public String getVotes() {
            var votes = card.votes();
            if (votes == null) return "N/A";
            StringBuilder sb = new StringBuilder();
            SortedSet<Integer> ids = new TreeSet<>(votes.keySet());
            for (int contestId : ids) {
                sb.append("%d:".formatted(contestId));
                var cands = votes.get(contestId);
                if (cands.length == 0) sb.append(" ,");
                if (cands.length == 1) sb.append("%d, ".formatted(cands[0]));
                else if (cands.length > 1) {
                    sb.append("[");
                    for (int idx = 0; idx < cands.length; idx++) {
                        int cand = cands[idx];
                        if (idx > 0) sb.append(", ");
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
            sb.append("\n");

            var pop = card.style();
            sb.append(pop.toString());
            return sb.toString();
        }
    }
}

