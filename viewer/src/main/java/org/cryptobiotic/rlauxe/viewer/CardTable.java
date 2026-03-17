/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditableCard;
import org.cryptobiotic.rlauxe.workflow.CardManifest;
import org.cryptobiotic.rlauxe.audit.BatchIF;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
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

public class CardTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(CardTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<CardBean> cardTable;
    TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    private Boolean needsReading = true;

    private CardManifest cardManifest;
    Map<String, BatchIF> poolMap;

    public CardTable(PreferencesExt prefs, TextHistoryPane infoTA, IndependentWindow infoWindow, float fontSize) {
        this.prefs = prefs;

        cardTable = new BeanTable<>(CardBean.class, (PreferencesExt) prefs.node("cardTable"), false,
                "CardManifest (sorted)","AuditableCard",  null);
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

    boolean setAuditRecord(String auditRecordLocation) {
        logger.info("CardTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) {
            logger.info("CardTable failed on readFrom "+ auditRecordLocation);
            return false;
        }

        cardTable.setBeans(emptyList());
        needsReading = true;

        return true;
    }

    void setSelectedTab() {
        if (needsReading) {
            if (readCards()) needsReading = false;
        }
    }

    boolean readCards() {
        logger.info("readCards for "+ auditRecordLocation);

        Integer cutoff = auditRecord.getConfig().getContestSampleCutoff();
        Integer ncardsToRead = (cutoff == null || cutoff < 11111) ? 11111 : cutoff;

        try {
            this.cardManifest = this.auditRecord.readSortedManifest();

            // wtf ?
            Map<String, BatchIF> pools = new TreeMap<>(); // sorted
            for (BatchIF pool : cardManifest.getBatches()) {
                String cardStyle = "P" + pool.id();
                pools.put(cardStyle, pool);
            }
            this.poolMap = pools;

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
            logger.info("CardTable read " + index + " cards from "+ auditRecordLocation);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
            return false;
        }

        return true;
    }

    BatchIF findPool(String cardStyle) {
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
    //    val population: BatchIF? = null, // not needed if hasStyle ?
    //)

    public class CardBean {
        AuditableCard card;
        int index;

        public CardBean() {
        }

        CardBean(AuditableCard card, int index) {
            this.card = card;
            this.index = index;
        }

        public String getLocation() {
            return card.getLocation();
        }
        public Integer getIndex() {
            return this.index;
        }
        public Integer getCardIndex() {
            return card.getIndex();
        }
        public long getPrn() {
            return card.getPrn();
        }
        public Boolean getPhantom() { return card.getPhantom(); }
        public String getContests() {
            int[] ids = card.contests();
            StringBuilder sb = new StringBuilder();
            for (int id : ids) {
                sb.append("%d,".formatted(id));
            }
            return sb.toString();
        }
        public Integer getPoolId() { return card.getPoolId(); }
        public String getBatchName() { return card.getBatchName(); }
        public String getBatchContests() {
            var pop = card.getBatch();
            if (pop == null) return "";
            int[] ids = pop.possibleContests();
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
            SortedSet<Integer> ids = new TreeSet<>(votes.keySet());
            for (int contestId : ids) {
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
            sb.append("\n");

            var pop = card.getBatch();
            if (pop == null) {
                var cardStyle = card.getBatchName();
                if (cardStyle != null) {
                    pop = findPool(cardStyle);
                }
            }
            if (pop != null) {
                sb.append(pop.toString());
            }
            return sb.toString();
        }
    }

}
