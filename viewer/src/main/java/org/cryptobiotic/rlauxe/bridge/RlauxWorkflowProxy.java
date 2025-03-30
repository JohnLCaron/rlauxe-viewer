package org.cryptobiotic.rlauxe.bridge;

import java.util.List;
import java.util.Set;

import org.cryptobiotic.rlauxe.estimate.ConsistentSamplingKt;
import org.cryptobiotic.rlauxe.audit.*;

import org.jetbrains.annotations.NotNull;

//    public abstract fun auditConfig(): org.cryptobiotic.rlauxe.workflow.AuditConfig
//    public abstract fun getContests(): kotlin.collections.List<org.cryptobiotic.rlauxe.core.ContestUnderAudit>
//    public abstract fun getBallotsOrCvrs(): kotlin.collections.List<org.cryptobiotic.rlauxe.workflow.BallotOrCvr>
public class RlauxWorkflowProxy implements RlauxAuditProxy {
    org.cryptobiotic.rlauxe.audit.AuditConfig auditConfig;
    org.cryptobiotic.rlauxe.audit.MvrManager mvrManager;

    public RlauxWorkflowProxy(
            org.cryptobiotic.rlauxe.audit.AuditConfig auditConfig,
            org.cryptobiotic.rlauxe.audit.MvrManager mvrManager) {
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
        ConsistentSamplingKt.sampleCheckLimits(this, auditRound, previousSamples, false);
    }

    @Override
    public @NotNull AuditConfig auditConfig() {
        return auditConfig;
    }

    @Override
    public @NotNull MvrManager mvrManager() {
        return mvrManager;
    }

}


