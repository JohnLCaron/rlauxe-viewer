package org.cryptobiotic.rlauxe.beans

class TableBeanProperty(var name: String, var desc: String) : Comparable<TableBeanProperty> {
    var visible: Boolean = false
    var viewColumnIndex: Int = 0

    override fun compareTo(other: TableBeanProperty): Int {
        return this.viewColumnIndex - other.viewColumnIndex
    }

    override fun toString(): String {
        return "TableBeanProperty{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", visible=" + visible +
                ", viewColumnIndex=" + viewColumnIndex +
                '}'
    }

}