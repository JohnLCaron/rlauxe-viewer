package org.cryptobiotic.rlauxe.viewer

import org.cryptobiotic.rlauxe.corla.SubPanelIF

interface ViewerPanelIF: SubPanelIF {
    fun setAuditRecord(auditRecordLocation: String): Boolean
}
