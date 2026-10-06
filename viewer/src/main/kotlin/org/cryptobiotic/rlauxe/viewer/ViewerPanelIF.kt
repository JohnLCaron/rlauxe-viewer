package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.persist.AuditRecordIF

interface AuditRecordViewerIF: SubPanelIF {
    fun setAuditRecord(auditRecord: AuditRecordIF): Boolean
}

interface ViewerPanelIF: SubPanelIF {
    fun setAuditRecordLocation(auditRecordLocation: String): Boolean
}

interface SubPanelIF {
    fun setFontSize(size: Float)
    fun saveState()
}
