package org.cryptobiotic.rlauxe.beans

import org.cryptobiotic.rlauxe.util.trunc
import java.beans.Introspector
import java.beans.PropertyDescriptor
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.collections.mutableListOf
import kotlin.text.appendLine

private val debug = false

class Bean<T>(val beanName: String, val beanClass: Class<T>) {
    var properties = mutableListOf<PropertyDescriptor>()
    var hiddenProperties: String = ""
    val className = beanClass.simpleName

    init {
        // get bean info
        val info =
            if (!beanClass.isInterface()) Introspector.getBeanInfo(beanClass, Any::class.java)
            else Introspector.getBeanInfo(beanClass) // allows interfaces to be beans


        // see if editableProperties method exists
        var editableProperties = ""
        val mds = info.getMethodDescriptors()
        checkNotNull(mds) { "no public methods" }

        for (md in mds) {
            val m = md.getMethod()
            if (m != null && m.getName() == "editableProperties") {
                editableProperties = m.invoke(null) as String // try static
                if (debug) println(" static editableProperties: " + editableProperties)
            }
        }

        // see if hiddenProperties method exists
        for (md in mds) {
            val m = md.getMethod()

            if (m.getName() == "hiddenProperties") {
                hiddenProperties = m.invoke(null) as String // class property
                if (debug) println(" hiddenProperties: " + hiddenProperties)
            }
        }

        // properties must have read method, not be hidden
        val pds = info.getPropertyDescriptors()
        for (pd in pds) {
            if ((pd.getReadMethod() != null) && !isHidden(pd)) {
                properties.add(pd)
            }
        }
    }

    fun getProperty(idx: Int): PropertyDescriptor? {
        return properties.get(idx)
    }
    fun getProperty(wantName: String): PropertyDescriptor? {
        return properties.find { it.name == wantName }
    }

    fun setProperty(propertyName: String, displayName: String?, toolTipText: String?) {
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

    private fun isHidden(pd: PropertyDescriptor): Boolean {
        val hiddenP = hiddenProperties.split(" ")
        return hiddenP.contains(pd.getName())
    }

    fun show() = buildString {
        appendLine("Properties:")
        appendLine("               name  |            type | read | write ")
        for (pd in properties) {
            val displayName = pd.getDisplayName()
            val name = pd.getName()
            val typeName = pd.propertyType.getSimpleName()
            val rm = pd.getReadMethod()
            val wm = pd.getWriteMethod()
            appendLine("${trunc(name, 20)} | ${trunc(typeName, 15)} | ${rm != null} | ${wm != null} | ${isHidden(pd)}")
        }
    }

}

class Beans(val beans: List<Bean<out Any>>, tableProps: List<TableBeanProperty>) {
    val propertyNameMap = mutableMapOf<String, MutableList<String>>() // propertyName -> list<className>
    val tablePropMap = tableProps.associateBy { it.name }

    init {
        beans.forEach { bean ->
            bean.properties.forEach { pd ->
                val name = pd.name
                val nameList = propertyNameMap.getOrPut(name) {mutableListOf() }
                nameList.add(bean.beanName)
            }
        }
    }

    val propertyWidth = 15
    val classWidth = 15

    fun header() = buildString {
        append("| ")
        beans.forEach {
            append(" ${trunc(it.beanName, classWidth)} |")
        }
        appendLine(" ${trunc("property", propertyWidth)} | desc")
    }

    fun show() = buildString {
        append(header())

        // lines
        val smap = propertyNameMap.filter{ it.value.size > 1 }.toSortedMap()
        append(lines(smap))
        appendLine()

        // lines with only one class using
        append(header())
        val smap1 = propertyNameMap.filter{ it.value.size == 1 }.map { Pair(it.key, it.value) }.sortedBy{ it.second.first() }.toMap()
        append(lines(smap1))
    }

    fun lines(properties: Map<String, MutableList<String>>) = buildString {
        // lines
        properties.forEach { (propertyName, classNames: List<String> ) ->
            append("| ")
            beans.forEach {
                val hasClass = if (classNames.contains(it.beanName)) "X    " else ""
                append(" ${trunc(hasClass, classWidth)} |")
            }
            val tp = tablePropMap[propertyName]?.desc ?: ""
            appendLine(" ${trunc(propertyName, propertyWidth)} | $tp")
        }
        appendLine()
    }

}

data class BeanClassInfo(val className: String, val index: Int)
data class CP(val beanName: String, val pd: PropertyDescriptor)