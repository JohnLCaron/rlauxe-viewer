package org.cryptobiotic.rlauxe.beans

import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.table.HidableTableColumnModel
import ucar.util.prefs.PreferencesExt
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.util.function.Function
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import kotlin.Any
import kotlin.Byte
import kotlin.Double
import kotlin.Exception
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.Short
import kotlin.String
import kotlin.Throwable
import kotlin.arrayOfNulls
import kotlin.checkNotNull
import kotlin.math.max
import kotlin.math.min

private val logger: Logger = LoggerFactory.getLogger(BeanTableModel::class.java)


private val showTableTypes = false
private val debugBean = false
private val debugEditing = false

// shared beans
class BeanTableModel<T>(val store: PreferencesExt, val beanClass: Class<T>, val beans: MutableList<T>, val innerbean: T?, ) : AbstractTableModel() {
    var jtable = JTable()

    var properties: MutableList<PropertyDescriptor> = ArrayList<PropertyDescriptor>()
    private var canedit: Method? = null

    init {
        // get bean info
        val info =
            if (!beanClass.isInterface()) Introspector.getBeanInfo(beanClass, Any::class.java)
            else Introspector.getBeanInfo(beanClass) // allows interfaces to be beans

        if (debugBean) println("Bean " + beanClass.getName())

        // see if editableProperties method exists
        var editableProperties = ""
        val mds = info.getMethodDescriptors()
        checkNotNull(mds) { "no public methods" }

        //       for (MethodDescriptor md : mds) {
        //        Method m = md.getMethod();
        //        if (m != null && m.getName().equals("editableProperties")) {
        //          try {
        //            editableProperties = (String) m.invoke(null, (Object[]) null); // try static
        //            if (debugEditing)
        //              System.out.println(" static editableProperties: " + editableProperties);
        //          } catch (Exception ee) {
        //
        //            if (innerbean != null) {
        //              try {
        //                editableProperties = (String) m.invoke(innerbean, (Object[]) null); // try non static
        //                if (debugEditing)
        //                  System.out.println(" editableProperties: " + editableProperties);
        //              } catch (Exception e2) {
        //                e2.printStackTrace();
        //              }
        //
        //            } else {
        //              ee.printStackTrace();
        //            }
        //          }
        //        }
        //      }
        
        for (md in mds) {
            val m = md.getMethod()
            if (m != null && m.getName() == "editableProperties") {
                try {
                    editableProperties = m.invoke(null) as String // try static
                    if (debugEditing) println(" static editableProperties: " + editableProperties)
                } catch (ee: Exception) {
                    if (innerbean != null) {
                        try {
                            editableProperties = m.invoke(innerbean, null) as String // try non static
                            if (debugEditing) println(" editableProperties: " + editableProperties)
                        } catch (e2: Exception) {
                            e2.printStackTrace()
                        }
                    } else {
                        ee.printStackTrace()
                    }
                }
            }
        }

        // see if hiddenProperties method exists
        var hiddenProperties = ""
        for (md in mds) {
            val m = md.getMethod()

            if (m.getName() == "hiddenProperties") {
                try {
                    hiddenProperties = m.invoke(null) as String // class property
                    if (debugBean) println(" hiddenProperties: " + hiddenProperties)
                } catch (ee: Exception) {
                    if (innerbean != null) {
                        try {
                            hiddenProperties = m.invoke(innerbean, null) as String // try non static
                            if (debugBean) println(" hiddenProperties: " + hiddenProperties)
                        } catch (e2: Exception) {
                            e2.printStackTrace()
                        }
                    } else {
                        println("BeanTable: Bad hiddenProperties ")
                        ee.printStackTrace()
                    }
                }
            }
        }

        // see if canedit method exists
        if (innerbean != null) {
            for (md in mds) {
                val m = md.getMethod()
                if (m.getName() == "canedit") {
                    try {
                        val canedit = m.invoke(innerbean) as Boolean // see if method returns boolean
                        this.canedit = if (canedit) m else null
                        if (debugEditing) System.out.printf("BeanTable canedit: %s ", innerbean.javaClass.getName())
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }
        }

        // properties must have read method, not be hidden
        val pds = info.getPropertyDescriptors()
        for (pd in pds) {
            if ((pd.getReadMethod() != null) && !isHidden(pd, hiddenProperties)) {
                properties.add(pd)
                // preferred == editable
                setEditable(pd, editableProperties)
            }
        }

        if (showTableTypes) {
            println("Properties:")
            println("               name  | type |  wrap  | editable ")
            for (pd in pds) {
                val displayName = pd.getDisplayName()
                val name = pd.getName()
                val type = pd.getPropertyType()
                println("${trunc(displayName,20)} | ${type.getName()} | ${wrapPrimitives(type)}| ${pd.isPreferred()}")
            }
        }
    }

    fun setProperty(propertyName: String?, displayName: String?, toolTipText: String?) {
        val pd = getProperty(propertyName)
        if (pd != null) {
            if (displayName != null) {
                pd.setDisplayName(displayName)
                val hl = pd.getValue("Header") as JLabel?
                if (hl != null) hl.setText(displayName)
            }
            if (toolTipText != null) {
                pd.setShortDescription(toolTipText)
                val jc = pd.getValue("ToolTipComp") as JComponent?
                if (jc != null) jc.setToolTipText(toolTipText)
            }
        } else println("BeanTable.setProperty " + beanClass.getName() + " no property named " + propertyName)
    }

    // AbstractTableModel methods
    override fun getRowCount(): Int {
        return beans.size
    }

    override fun getColumnCount(): Int {
        return properties.size
    }

    override fun getColumnName(col: Int): String? {
        return properties.get(col).getDisplayName()
    }

    // col =  "model column", index into properties
    override fun getValueAt(row: Int, col: Int): Any? {
        val bean: Any? = beans.get(row)
        var value: Any? = "N/A"
        val pd = properties.get(col)
        try {
            val m = pd.getReadMethod()
            value = m.invoke(bean) // , null)
        } catch (ee: Exception) {
            logger.warn("BeanTable: Bad getReadMethod " + row + " " + col + " " + beanClass.getName() + " " + pd.getDisplayName())
            ee.printStackTrace()
        }
        if (debugBean) println("getValueAt($row $col) = $value")
        return value
    }

    // for BeanTable
    fun getValueAt(bean: T, col: Int): Any {
        var value: Any = "N/A"
        try {
            val m = properties.get(col).getReadMethod()
            value = m.invoke(bean) // , null)
        } catch (ee: Exception) {
            logger.error("BeanTable: Bad bean=" + bean!!.javaClass.getName() + ", beanClass=" + beanClass.getName())
            logger.error("BeanTable: getValueAt error message= {}", ee.message)
        }
        return value
    }

    // editing
    override fun getColumnClass(col: Int): Class<*> {
        return wrapPrimitives(properties.get(col).getPropertyType())
        // return properties.get(col).getPropertyType()
    }

    override fun isCellEditable(row: Int, col: Int): Boolean {
        val pd = properties.get(col)
        if (!pd.isPreferred() || !isRowEditable(row)) return false
        val type = pd.getPropertyType()
        val result = type.isPrimitive() || (type == String::class.java)
        if (debugEditing) println("isCellEditable " + row + " " + col + " == " + result)
        return result
    }

    fun isRowEditable(row: Int): Boolean {
        if (this.canedit == null) return true
        val bean: Any? = beans.get(row)
        try {
            val ok = this.canedit!!.invoke(bean, null) as Boolean // can canedit method
            if (debugEditing) println("isRowEditable " + row + " == " + ok)
            return ok
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
        return false
    }

    override fun setValueAt(value: Any?, row: Int, col: Int) {
        val bean: Any? = beans.get(row)
        try {
            val params = arrayOfNulls<Any>(1)
            params[0] = value
            val m = properties.get(col).getWriteMethod()
            if (m != null) {
                m.invoke(bean, *params)
                if (debugEditing) println("invoke " + m)
            }
        } catch (ee: Exception) {
            ee.printStackTrace()
        }

        fireTableCellUpdated(row, col)
    }

    // extra stuff
    fun wrapPrimitives(c: Class<*>): Class<*> {
        if (c == Boolean::class.javaPrimitiveType) return Boolean::class.javaObjectType
        else if (c == Int::class.javaPrimitiveType) return Int::class.javaObjectType
        else if (c == Float::class.javaPrimitiveType) return Float::class.javaObjectType
        else if (c == Double::class.javaPrimitiveType) return Double::class.javaObjectType
        else if (c == Short::class.javaPrimitiveType) return Short::class.javaObjectType
        else if (c == Long::class.javaPrimitiveType) return Long::class.javaObjectType
        else if (c == Byte::class.javaPrimitiveType) return Byte::class.javaObjectType
        else return c
    }

    fun zeroValue(c: Class<*>?): Any? {
        if (c == Boolean::class.java) return false
        else if (c == Int::class.java) return 0
        else if (c == Float::class.java) return 0.0.toFloat()
        else if (c == Double::class.java) return 0.0
        else if (c == Short::class.java) return 0.toShort()
        else if (c == Long::class.java) return 0L
        else if (c == Byte::class.java) return 0.toByte()
        else return null
    }

    // return PropertyDescriptor with this property name, return null if not exists
    fun getProperty(wantName: String?): PropertyDescriptor? {
        for (property in properties) {
            if (property.getName() == wantName) return property
        }
        return null
    }

    // return PropertyDescriptor
    fun getProperty(idx: Int): PropertyDescriptor? {
        return properties.get(idx)
    }
    
    private fun setEditable(pd: PropertyDescriptor, editableProperties: String) {
        val editP = editableProperties.split(" ")
        pd.setPreferred(editP.contains(pd.getName()))
    }

    private fun isHidden(pd: PropertyDescriptor, hiddenProperties: String): Boolean {
        val hiddenP = hiddenProperties.split(" ")
        return hiddenP.contains(pd.getName())
    }

    override fun toString() = buildString {
        val tableColumnModel = jtable.getColumnModel() as HidableTableColumnModel

        appendLine("visibleCols:")
        val visibleCols = tableColumnModel.getColumns(true)
        while (visibleCols.hasMoreElements()) {
            val vc = visibleCols.nextElement()
            // append("  %15s %d%n".formatted(vc.getIdentifier(), vc.getModelIndex()))
            append("  ${trunc(vc.getIdentifier().toString(), 15)} ${nfn(vc.getModelIndex(), 3)}")
        }
        append("allCols:\n")
        val allCols = tableColumnModel.getColumns(false)
        while (allCols.hasMoreElements()) {
            val vc = allCols.nextElement()
            // append("  %15s %d%n".formatted(vc.getIdentifier(), vc.getModelIndex()))
            append("  ${trunc(vc.getIdentifier().toString(), 15)} ${nfn(vc.getModelIndex(), 3)}")
        }

        val propertyCols = store.getBean("propertyCol", emptyList<PropertyCol>()) as List<PropertyCol>

        append("PropertyCols:\n")
        for (pc in propertyCols) {
            try {
                val currentViewIndex = tableColumnModel.getColumnIndex(pc.name) // May throw IAE.
                // append("  %s %s %d%n".formatted(pc.name, pc.visible, currentViewIndex))
                append("  ${pc.name} ${pc.isVisible} $currentViewIndex")

            } catch (e: Exception) {
                // append(" %s %s %s%n".formatted(pc.name, pc.visible, e.message))
                append("  ${pc.name} ${pc.isVisible} ${e.message}")

            }
        }
    }

    fun showBean(bean: T, props: List<TableBeanProperty>) = buildString {
        val maxValueWidth = 50
        try {
            val propm = props.stream()
                .collect(
                    Collectors.toMap(
                        Function { prop: TableBeanProperty -> prop!!.name },
                        Function { prop: TableBeanProperty -> prop })
                )

            // follow which columns are visible
            val tableColumnModel = jtable.getColumnModel() as HidableTableColumnModel
            var visibleCols = tableColumnModel.getColumns(true)

            var maxName = 1
            var maxValue = 1
            var maxDesc = 1
            while (visibleCols.hasMoreElements()) {
                val vc = visibleCols.nextElement()
                val id = vc.getIdentifier()
                val modelIdx = vc.getModelIndex()
                val pc = propm.get(id.toString())
                if (pc == null) {
                    logger.warn("cant find TableBeanProperty=" + id)
                    continue
                }
                maxName = max(maxName, pc.name.length)
                maxDesc = max(maxDesc, pc.desc.length)

                val colVal = getValueAt(bean, modelIdx)
                if (debugBean) logger.debug(vc.getIdentifier().toString() + " " + modelIdx + " " + colVal)
                maxValue = max(maxValue, colVal.toString().length)

                if (debugBean) logger.debug(pc.toString() + " " + colVal)
            }
            maxValue = min(maxValue, maxValueWidth)

            appendLine("| ${sfn("field", maxName)} | ${sfn("value", maxValue)} | ${sfn("description", maxDesc)} |")
            appendLine("| ${"-".repeat(maxName)} | ${"-".repeat(maxValue)} | ${"-".repeat(maxDesc)} |")

            visibleCols = tableColumnModel.getColumns(true)
            while (visibleCols.hasMoreElements()) {
                val vc = visibleCols.nextElement()
                val id = vc.getIdentifier()
                val modelIdx = vc.getModelIndex()
                val pc = propm.get(id.toString())
                if (pc == null) continue
                val colVal = getValueAt(bean, modelIdx).toString()
                appendLine("| ${sfn(pc.name, maxName)} | ${sfn(colVal, maxValue)} | ${sfn(pc.desc, maxDesc)} |")
            }
        } catch (t: Throwable) {
            logger.error("fail in showBean", t)
            append(t.message)
        }
    }

    fun beanTableHeader(props: List<TableBeanProperty>) = buildString {
        val propm = props.associateBy { it.name }

        try {
            // follow which columns are visible
            val tableColumnModel = jtable.getColumnModel() as HidableTableColumnModel
            var visibleCols = tableColumnModel.getColumns(true)
            visibleCols = tableColumnModel.getColumns(true)
            while (visibleCols.hasMoreElements()) {
                val vc = visibleCols.nextElement()
                val id = vc.getIdentifier()
                val pc = propm.get(id.toString())
                if (pc == null) continue

                append(pc.name)
                append(", ")
            }
            appendLine()
        } catch (t: Throwable) {
            logger.error("fail in beanTableHeader", t)
            append(t.message)
        }
    }

    fun beanCsv(bean: T, props: List<TableBeanProperty>) = buildString {
        val propm = props.stream()
            .collect(
                Collectors.toMap(
                    Function { prop: TableBeanProperty -> prop!!.name },
                    Function { prop: TableBeanProperty -> prop })
            )

        try {
            // follow which columns are visible
            val tableColumnModel = jtable.getColumnModel() as HidableTableColumnModel
            var visibleCols = tableColumnModel.getColumns(true)

            visibleCols = tableColumnModel.getColumns(true)
            while (visibleCols.hasMoreElements()) {
                val vc = visibleCols.nextElement()
                val id = vc.getIdentifier()
                val modelIdx = vc.getModelIndex()
                val pc = propm.get(id.toString())
                if (pc == null) continue
                val colVal = getValueAt(bean, modelIdx)
                append(colVal)
                append(", ")
            }
            append("\n")
        } catch (t: Throwable) {
            logger.error("fail in beanCsv", t)
            append(t.message)
        }
    }
}
