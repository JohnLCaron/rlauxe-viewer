package org.cryptobiotic.rlauxe.bridge;

import org.cryptobiotic.rlauxe.audit.*;

// not used

public class RlauxWorkflowProxy {
    org.cryptobiotic.rlauxe.audit.AuditConfig auditConfig;
    org.cryptobiotic.rlauxe.workflow.MvrManager mvrManager;

    public RlauxWorkflowProxy(
            org.cryptobiotic.rlauxe.audit.AuditConfig auditConfig,
            org.cryptobiotic.rlauxe.workflow.MvrManager mvrManager) {
        this.auditConfig = auditConfig;
        this.mvrManager = mvrManager;
    }

    // fun runAudit(auditDir: String, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): String {
    public static String runRoundAgain(String auditDir, ContestRound contestRound, AssertionRound assertionRound, AuditRoundResult auditRoundResult) {
        return RunAuditKt.runRoundAgain(auditDir, contestRound, assertionRound, auditRoundResult);
    }

}


