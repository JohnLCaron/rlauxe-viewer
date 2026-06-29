package org.cryptobiotic.rlauxe.beans

import javax.swing.table.DefaultTableCellRenderer

class BooleanRenderer internal constructor() : DefaultTableCellRenderer() {
    public override fun setValue(value: Any?) {
        if (value == null) setText("")
        else {
            val bvalue = value as Boolean
            if (bvalue) setText("true") else setText("false")
        }
    }
}