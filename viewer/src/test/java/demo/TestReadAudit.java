package demo;

import org.cryptobiotic.rlauxe.persist.json.Publisher;

import static com.github.michaelbull.result.UnwrapKt.unwrap;
import static org.cryptobiotic.rlauxe.persist.json.AuditConfigJsonKt.readAuditConfigJsonFile;

public class TestReadAudit {
    public static void main(String[] args) {
        var publisher = new Publisher("/home/stormy/temp/persist/testRunCli/");
        var auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile());
        var auditConfig = unwrap(auditConfigResult);
        System.out.println(auditConfig);
    }
}
