package org.cryptobiotic.rlauxe.viewer;

public interface ViewerPanelIF {
    void setFontSize(float size);
    boolean setAuditRecord(String auditRecordLocation);
    void saveState();
}
