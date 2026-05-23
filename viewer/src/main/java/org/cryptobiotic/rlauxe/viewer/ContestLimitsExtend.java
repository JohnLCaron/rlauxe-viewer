/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.audit.AssertionRound;
import org.cryptobiotic.rlauxe.audit.ContestRound;
import org.cryptobiotic.rlauxe.core.Assertion;
import org.cryptobiotic.rlauxe.core.ContestWithAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContestLimitsExtend {
    static private final Logger logger = LoggerFactory.getLogger(ContestLimitsExtend.class);

    public ContestLimitsExtend() {
    }

    // could the ContestsPanel (CountyPanel?) use ContestBean or ContestLimitBean ?? Or maybe not worth the trouble ??
    static public class ContestLimitBean extends ContestsPanel.ContestBean  {
        public ContestLimitBean() {
        }

        ContestLimitBean(ContestRound contestRound, ContestsPanel.AuditData auditData) {
            super(contestRound);
            this.auditData = auditData;
        }

        /* editable properties
        static public String editableProperties() { return "mvrLimit"; }
        public boolean canedit() { return true; }
        public int getMvrLimit() { return mvrLimit; }
        public void setMvrLimit( int limit) {
            // logger.debug("setWantNewMvrs={} current={}", mvrLimit, mvrLimit);
            this.mvrLimit = limit;
        } */

        @Override
        public Integer getHaveMvrs() {
            return (mvrLimit >= 0) ? mvrLimit : lastRound.getHaveSampleSize();
        }

    }

    static public class AssertionLimitBean extends ContestsPanel.AssertionBean {

        public AssertionLimitBean() {
        }

        AssertionLimitBean(ContestsPanel.ContestBean contestBean, AssertionRound assertionRound) {
            super(contestBean, assertionRound);
        }
    }

}

