package org.cryptobiotic.rlauxe.bridge;

import java.util.List;
import java.util.Set;

import org.cryptobiotic.rlauxe.estimate.ConsistentSamplingKt;
import org.cryptobiotic.rlauxe.workflow.AuditConfig;
import org.cryptobiotic.rlauxe.workflow.AuditRound;

import org.jetbrains.annotations.NotNull;

//    public abstract fun auditConfig(): org.cryptobiotic.rlauxe.workflow.AuditConfig
//    public abstract fun getContests(): kotlin.collections.List<org.cryptobiotic.rlauxe.core.ContestUnderAudit>
//    public abstract fun getBallotsOrCvrs(): kotlin.collections.List<org.cryptobiotic.rlauxe.workflow.BallotOrCvr>
public class RlauxWorkflowProxy implements org.cryptobiotic.rlauxe.workflow.RlauxWorkflowProxy {
    org.cryptobiotic.rlauxe.workflow.AuditConfig auditConfig;
    org.cryptobiotic.rlauxe.workflow.BallotCards ballotCards;

    public RlauxWorkflowProxy(
            org.cryptobiotic.rlauxe.workflow.AuditConfig auditConfig,
            org.cryptobiotic.rlauxe.workflow.BallotCards ballotCards) {
        this.auditConfig = auditConfig;
        this.ballotCards = ballotCards;
    }

    // fun createSampleIndices(
    //    workflow: RlauxWorkflowProxy,
    //    auditRound: AuditRound,
    //    previousSamples: Set<Long> = emptySet(),
    //    quiet: Boolean = true
    //)
    // fun createSampleIndices(workflow: RlauxWorkflowProxy, auditRound: AuditRound, wantNewMvrs: Int, quiet: Boolean): List<Int> {
    public void createSampleIndices(AuditRound auditRound, Set<Long> previousSamples) {
        ConsistentSamplingKt.createSampleIndices(this, auditRound, previousSamples, false);
    }

    @Override
    public @NotNull AuditConfig auditConfig() {
        return auditConfig;
    }

    @Override
    public @NotNull org.cryptobiotic.rlauxe.workflow.BallotCards ballotCards() {
        return ballotCards;
    }

}


