package org.cryptobiotic.rlauxe.viewer

interface ViewerPanelIF: SubPanelIF {
    fun setAuditRecord(auditRecordLocation: String): Boolean
}
