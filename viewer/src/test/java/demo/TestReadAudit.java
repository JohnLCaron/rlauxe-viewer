package demo;

import org.cryptobiotic.rlauxe.persist.AuditRecord;

public class TestReadAudit {
    public static void main(String[] args) {
        var auditdir = "/home/stormy/rla/persist/testRunCli/clca/audit";
        var auditConfig = AuditRecord.Companion.readFrom(auditdir);
        if (auditConfig == null) System.out.println("failed");
        else System.out.println("success");
    }
}
