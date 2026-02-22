package demo;

import org.cryptobiotic.rlauxe.persist.Publisher;

import static org.cryptobiotic.rlauxe.persist.json.AuditConfigJsonKt.readAuditConfigUnwrapped;

public class TestReadAudit {
    public static void main(String[] args) {
        var publisher = new Publisher("/home/stormy/tla/persist/testRunCli/clca/audit");
        var auditConfig = readAuditConfigUnwrapped(publisher.auditConfigFile());
        if (auditConfig == null) System.out.println("failed");
        else System.out.println("success");
    }
}
