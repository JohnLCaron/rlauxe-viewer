/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AuditConfig;
import org.cryptobiotic.rlauxe.audit.AuditableCard;
import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.cryptobiotic.rlauxe.persist.AuditRecordIF;
import org.cryptobiotic.rlauxe.persist.CompositeRecord;
import org.cryptobiotic.rlauxe.persist.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.prefs.BeanTable;
import ucar.ui.widget.TextHistoryPane;
import ucar.util.prefs.PreferencesExt;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.cryptobiotic.rlauxe.workflow.PersistedMvrManagerKt.readMvrsForRound;

public class MvrTable extends JPanel {
    static private final Logger logger = LoggerFactory.getLogger(MvrTable.class);

    private final PreferencesExt prefs;

    private final BeanTable<CardBean> mvrTable;
    TextHistoryPane localInfo = new TextHistoryPane();

    private final JSplitPane split1;

    private String auditRecordLocation = "none";
    private AuditRecordIF auditRecord;
    private Boolean isComposite;
    private AuditConfig auditConfig;
    private List<AuditableCard> mvrs;

    public MvrTable(PreferencesExt prefs, float fontSize) {
        this.prefs = prefs;

        mvrTable = new BeanTable<>(CardBean.class, (PreferencesExt) prefs.node("cardTable"), false,
                "AuditableCard", "CardManifest (sorted)", null);
        mvrTable.addListSelectionListener(e -> {
            CardBean cardBean = mvrTable.getSelectedBean();
            if (cardBean != null) {
                setSelectedCard(cardBean);
            }
        });
        setFontSize(fontSize);

        // layout of tables
        split1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, mvrTable, localInfo);
        split1.setDividerLocation(prefs.getInt("splitPos1", 200));

        setLayout(new BorderLayout());
        add(split1, BorderLayout.CENTER);

        logger.debug("cardTable init");
    }

    public void setFontSize(float size) {
        mvrTable.setFontSize(size);
        localInfo.setFontSize(size);
    }

    boolean setAuditRecord(String auditRecordLocation, int roundIdx) {
        logger.debug("auditTable setAuditRecord "+ auditRecordLocation);

        this.auditRecordLocation = auditRecordLocation;
        this.auditRecord = AuditRecord.Companion.readFrom(auditRecordLocation);
        if (this.auditRecord == null) return false;
        this.isComposite = (this.auditRecord instanceof CompositeRecord);

        try {
            this.auditConfig = auditRecord.getConfig();
                Publisher publisher = new Publisher(auditRecordLocation);
                this.mvrs = readMvrsForRound(publisher, roundIdx);

                List<CardBean> beanList = new ArrayList<>();
                int index = 1;
                for (AuditableCard mvr : this.mvrs) {
                    beanList.add(new CardBean(mvr, index));
                    index++;
                }
                mvrTable.setBeans(beanList);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.getMessage());
            logger.error("setAuditRecord failed", e);
        }

        return true;
    }

    void clear() {
        mvrTable.setBeans(emptyList());
    }

    void setSelectedCard(CardBean bean) {
        localInfo.setText(bean.show());
        localInfo.gotoTop();
    }

    void save() {
        mvrTable.saveState(false);

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
            return sb.toString();
        }
    }

}
