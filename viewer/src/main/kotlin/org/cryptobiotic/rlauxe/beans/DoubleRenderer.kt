package org.cryptobiotic.rlauxe.beans

import org.cryptobiotic.rlauxe.util.dfn
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

// TODO allow bean table to set n
class DoubleRenderer(val n: Int) : DefaultTableCellRenderer() {
    init {
        setHorizontalAlignment(RIGHT) // Right-align numbers
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any,
        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
    ): Component {
        // Let the superclass handle background, foreground, and selections
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (value is Number) {
            setText(dfn(value.toDouble(), n))
        }

        return this
    }
}
