package org.cryptobiotic.rlauxe.bridge;

import java.util.List;

import org.cryptobiotic.rlauxe.core.ContestUnderAudit;
import org.cryptobiotic.rlauxe.workflow.AuditConfig;
import org.cryptobiotic.rlauxe.workflow.AuditRound;
import org.cryptobiotic.rlauxe.workflow.BallotOrCvr;
import org.cryptobiotic.rlauxe.workflow.RlauxWorkflowProxy;

import org.jetbrains.annotations.NotNull;

import static org.cryptobiotic.rlauxe.estimate.ConsistentSamplingKt.createSampleIndices;

//    public abstract fun auditConfig(): org.cryptobiotic.rlauxe.workflow.AuditConfig
//    public abstract fun getContests(): kotlin.collections.List<org.cryptobiotic.rlauxe.core.ContestUnderAudit>
//    public abstract fun getBallotsOrCvrs(): kotlin.collections.List<org.cryptobiotic.rlauxe.workflow.BallotOrCvr>
public class RlauxWorkflowProxyBridge implements RlauxWorkflowProxy {
    org.cryptobiotic.rlauxe.workflow.AuditConfig auditConfig;
    List<org.cryptobiotic.rlauxe.core.ContestUnderAudit> contests;
    List<org.cryptobiotic.rlauxe.workflow.BallotOrCvr> ballotsOrCvrs;

    public RlauxWorkflowProxyBridge(
            org.cryptobiotic.rlauxe.workflow.AuditConfig auditConfig,
            List<org.cryptobiotic.rlauxe.core.ContestUnderAudit> contests,
            List<org.cryptobiotic.rlauxe.workflow.BallotOrCvr> ballotsOrCvrs) {
        this.auditConfig = auditConfig;
        this.contests = contests;
        this.ballotsOrCvrs = ballotsOrCvrs;
    }

    // fun createSampleIndices(workflow: RlauxWorkflowProxy, auditRound: AuditRound, wantNewMvrs: Int, quiet: Boolean): List<Int> {
    public List<Integer> createSampleIndicesBridge(AuditRound auditRound, Integer wantedNewMvrs) {
        return createSampleIndices(this, auditRound, wantedNewMvrs, false);
    }

    @Override
    public @NotNull AuditConfig auditConfig() {
        return auditConfig;
    }

    @Override
    public @NotNull List<BallotOrCvr> getBallotsOrCvrs() {
        return ballotsOrCvrs;
    }

    @Override
    public @NotNull List<ContestUnderAudit> getContests() {
        return contests;
    }
}


