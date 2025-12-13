package org.cryptobiotic.rlauxe.bridge;

import java.util.Set;

import org.cryptobiotic.rlauxe.estimate.ConsistentSamplingKt;
import org.cryptobiotic.rlauxe.audit.*;
import static org.cryptobiotic.rlauxe.audit.RunAuditKt.runRoundAgain;


public class RlauxWorkflowProxy {
    org.cryptobiotic.rlauxe.audit.AuditConfig auditConfig;
    org.cryptobiotic.rlauxe.workflow.MvrManager mvrManager;

    public RlauxWorkflowProxy(
            org.cryptobiotic.rlauxe.audit.AuditConfig auditConfig,
            org.cryptobiotic.rlauxe.workflow.MvrManager mvrManager) {
        this.auditConfig = auditConfig;
        this.mvrManager = mvrManager;
    }

    // fun sample(
    //    workflow: RlauxWorkflowProxy,
    //    auditRound: AuditRound,
    //    previousSamples: Set<Long> = emptySet(),
    //    quiet: Boolean = true
    //)
    // TODO sampleCheckLimits or sample ??
    public void sample(AuditRound auditRound, Set<Long> previousSamples) {
        ConsistentSamplingKt.sampleWithContestCutoff(auditConfig, mvrManager, auditRound, previousSamples, false);
    }

    // fun runAudit(auditDir: String, contestRound: ContestRound, assertionRound: AssertionRound, auditRoundResult: AuditRoundResult): String {
    public static String runRoundAgain(String auditDir, ContestRound contestRound, AssertionRound assertionRound, AuditRoundResult auditRoundResult) {
        return RunAuditKt.runRoundAgain(auditDir, contestRound, assertionRound, auditRoundResult);
    }

}


