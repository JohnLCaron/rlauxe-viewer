package demo;

import org.cryptobiotic.rlauxe.persist.AuditRecord;

public class TestReadAudit {
    public static void main(String[] args) {
        var topdir = "/home/stormy/rla/persist/testRunCli/clca";
        var auditConfig = AuditRecord.Companion.read(topdir);
        if (auditConfig == null) System.out.println("failed");
        else System.out.println("success");
    }
}
