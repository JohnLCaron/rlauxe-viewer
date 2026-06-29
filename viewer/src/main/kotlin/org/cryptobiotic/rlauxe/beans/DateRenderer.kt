package org.cryptobiotic.rlauxe.beans

import java.text.SimpleDateFormat
import java.util.*
import javax.swing.table.DefaultTableCellRenderer

class DateRenderer internal constructor() : DefaultTableCellRenderer() {
    private val newForm: SimpleDateFormat
    private val oldForm: SimpleDateFormat
    private val cutoff: Date

    init {
        oldForm = SimpleDateFormat("yyyy MMM dd HH:mm z")
        oldForm.setTimeZone(TimeZone.getTimeZone("GMT"))
        newForm = SimpleDateFormat("MMM dd, HH:mm z")
        newForm.setTimeZone(TimeZone.getTimeZone("GMT"))
        val cal = Calendar.getInstance()
        cal.setTimeZone(TimeZone.getTimeZone("GMT"))
        cal.add(Calendar.YEAR, -1) // "now" time format within a year
        cutoff = cal.getTime()
    }

    public override fun setValue(value: Any?) {
        if (value == null) setText("")
        else {
            val date = value as Date
            if (date.before(cutoff)) setText(oldForm.format(date))
            else setText(newForm.format(date))
        }
    }
}