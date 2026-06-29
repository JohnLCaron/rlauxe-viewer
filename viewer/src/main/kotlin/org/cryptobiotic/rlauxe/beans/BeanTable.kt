package org.cryptobiotic.rlauxe.beans

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ucar.ui.table.HidableTableColumnModel
import ucar.ui.table.TableAligner
import ucar.ui.table.TableAppearanceAction
import ucar.ui.table.UndoableRowSorter
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.MultilineTooltip
import ucar.ui.widget.PopupMenu
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.EventListenerList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableColumnModel

/**
 * Constructor.
 * 
 * @param bc           JavaBean class
 * @param pstore       store data in this PreferencesExt store.
 * @param canAddDelete allow changes to the jtable - adds a New and Delete button to bottom panel
 * @param header       optional header label
 * @param tooltip      optional tooltip label
 * @param bean         needed for inner classes to call reflected methods; // TODO remove support ?
 */
class BeanTable<T>(
    val beanClass: Class<T>,
    val store: PreferencesExt,
    val canAddDelete: Boolean,
    val header: String,
    val tooltip: String,
    val innerbean: T? = null,
) : JPanel() {
    protected var scrollPane: JScrollPane
    var boolCellEditor = JCheckBox()

    var jtable: JTable
    var tableModel: BeanTableModel<T>
    val beans = mutableListOf<T>()

    protected var debug: Boolean = false
    protected var debugSelected: Boolean = false
    protected var debugBean: Boolean = false
    protected var debugEditing: Boolean = false

    private var headerLabel: JLabel? = null
    fun setHeader(header: String) {
        headerLabel!!.setText(header)
    }

    init {
        this.boolCellEditor.setIcon(SimpleCheckboxStyle(36))

        val storedBeans = store.getBean("beanList", null)
        if (storedBeans != null) {
            beans.addAll(storedBeans as List<T>) // I dont think we are are using this ??
        }

        // share the beans
        tableModel = BeanTableModel(store, beanClass, beans, innerbean)

        /* not going to support ?
        if (canAddDelete) {
            // button panel
            val buttPanel = JPanel()
            val newButton = JButton("New")
            buttPanel.add(newButton, null)
            val deleteButton = JButton("Delete")
            buttPanel.add(deleteButton, null)

            add(buttPanel, BorderLayout.SOUTH)

            // button listeners
            newButton.addActionListener(ActionListener { e: ActionEvent ->
                try {
                    val newbean = beanClass.newInstance()
                    addBean(newbean)
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            })

            deleteButton.addActionListener(ActionListener { e: ActionEvent ->
                if (JOptionPane.showConfirmDialog(
                        null, "Do you want to delete all selected records", "Delete Records",
                        JOptionPane.YES_NO_OPTION
                    ) == JOptionPane.YES_OPTION
                ) {
                    for (o in this.selectedBeans) {
                        beans.remove(o)
                    }
                    tableModel.fireTableDataChanged()
                }
            })
        } */
        
        // part 2
        val tcm: TableColumnModel = HidableTableColumnModel(tableModel)
        jtable = JTable(tableModel, tcm)
        tableModel.jtable = jtable
        jtable.setRowSorter(UndoableRowSorter(tableModel))

        ToolTipManager.sharedInstance().registerComponent(jtable)

        restoreState()

        // editor/renderers;
        jtable.setDefaultRenderer(Date::class.java, DateRenderer())
        jtable.setDefaultRenderer(Boolean::class.java, BooleanRenderer())
        jtable.setDefaultRenderer(Double::class.java, DoubleRenderer(4))

        jtable.setDefaultEditor(String::class.java, DefaultCellEditor(JTextField()))
        jtable.setDefaultEditor(Boolean::class.java, DefaultCellEditor(boolCellEditor))

        // Left-align every cell, including header cells.
        val aligner = TableAligner(jtable, SwingConstants.LEADING)
        jtable.getColumnModel().addColumnModelListener(aligner)

        // Create a button that will popup a menu containing options to configure the appearance of the table.
        val cornerButton = JButton(TableAppearanceAction(jtable))
        cornerButton.setHideActionText(true)
        cornerButton.setContentAreaFilled(false)

        // Install the button in the upper-right corner of the table's scroll pane.
        scrollPane = JScrollPane(jtable)
        scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, cornerButton)
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS)

        // This keeps the corner button visible even when the table is empty (or all columns are hidden).
        scrollPane.setColumnHeaderView(JViewport())
        scrollPane.getColumnHeader().setPreferredSize(jtable.getTableHeader().getPreferredSize())

        // UI
        setLayout(BorderLayout())
        add(scrollPane, BorderLayout.CENTER)

        if (header != null) {
            if (tooltip != null) {
                headerLabel = object : JLabel(header, CENTER) {
                    override fun createToolTip(): JToolTip {
                        return MultilineTooltip()
                    }
                }
                headerLabel!!.setToolTipText(tooltip)
            } else {
                headerLabel = JLabel(header, SwingConstants.CENTER)
            }
            add(headerLabel, BorderLayout.NORTH)
        }

        // event management
        listenerList = EventListenerList()

        val rowSM = jtable.getSelectionModel()
        rowSM.addListSelectionListener { e: ListSelectionEvent ->
            if (e.getValueIsAdjusting()) {
                val lsm = e.getSource() as ListSelectionModel
                if (!lsm.isSelectionEmpty()) fireEvent(e)
            }
        }

    }

    // debug
    override fun getToolTipText(event: MouseEvent?): String? {
        return super.getToolTipText(event)
    }

    fun setProperty(propertyName: String?, displayName: String?, toolTipText: String?) {
        tableModel.setProperty(propertyName, displayName, toolTipText)
    }

    fun setPropertyEditable(propertyName: String?, isHidden: Boolean) {
    }

    fun setPropertyHidden(propertyName: String?, isHidden: Boolean) {
    }

    fun addListSelectionListener(l: ListSelectionListener) {
        listenerList.add(ListSelectionListener::class.java, l)
    }

    fun removeListSelectionListener(l: ListSelectionListener) {
        listenerList.remove(ListSelectionListener::class.java, l)
    }

    private fun fireEvent(event: ListSelectionEvent?) {
        val listeners = listenerList.getListenerList()
        // Process the listeners last to first
        var i = listeners.size - 2
        while (i >= 0) {
            (listeners[i + 1] as ListSelectionListener).valueChanged(event)
            i -= 2
        }
    }

    private var varPopup: PopupMenu? = null

    /*
  public BeanTable(Class<T> bc, PreferencesExt pstore, String header, String tooltip, BeanInfo info) {
    this.beanClass = bc;
    this.store = pstore;

    beans = (store != null) ? (ArrayList<T>) store.getBean("beanList", new ArrayList<>()) : new ArrayList<>();
    model = new TableBeanModelInfo(info);
    init(header, tooltip);
  } */
    
    fun setSelectedBean(bean: T) {
        if (bean == null) return
        val modelRowIndex = beans.indexOf(bean)
        val viewRowIndex = jtable.convertRowIndexToView(modelRowIndex)

        if (viewRowIndex >= 0) jtable.getSelectionModel().setSelectionInterval(viewRowIndex, viewRowIndex)
        makeRowVisible(viewRowIndex)
    }

    fun getSelectedBean(): T? {
        val viewRowIndex = jtable.getSelectedRow()
        if (viewRowIndex < 0) return null
        val modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex)
        return if ((modelRowIndex < 0) || (modelRowIndex >= beans.size)) null else beans.get(modelRowIndex)
    }

    fun addPopupOption(title: String?, act: Action): PopupMenu? {
        if (this.varPopup == null) {
            this.varPopup = PopupMenu(this.jTable, "Options")
        }
        this.varPopup!!.addAction(title, act)
        return this.varPopup
    }

    fun makeShowAction(infoTA: TextHistoryPane, infoWindow: IndependentWindow, show: (T) -> String): Action {
        return object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val bean = getSelectedBean()
                if (bean != null) {
                    infoTA.setText(show(bean))
                    infoTA.gotoTop()
                    infoWindow.show()
                }
            }
        }
    }

    fun makeShowAction(infoTA: TextHistoryPane, show: (T) -> String): Action {
        return object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val bean = getSelectedBean()
                if (bean != null) {
                    infoTA.setText(show(bean))
                    infoTA.gotoTop()
                }
            }
        }
    }

    fun makeActionOnCurrentBean(act: (T) -> Boolean): Action {
        return object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val bean = getSelectedBean()
                if (bean != null) {
                    act(bean)
                }
            }
        }
    }

    fun getSelectedBeans(): List<T> {
            val list = mutableListOf<T>()
            val viewRowIndices = jtable.getSelectedRows()
            for (viewRowIndex in viewRowIndices) {
                val modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex)
                list.add(beans.get(modelRowIndex))
                if (debugSelected) println(" bean selected= " + modelRowIndex + " " + beans.get(modelRowIndex))
            }
            return list
        }

    val selectedCells: List<Any?>
        /**
         * Get the currently selected cells.
         * Use this for multiple row selection, when columnSelection is on
         * 
         * @return ArrayList of currently selected cells (wont be null).
         * @see setSelectionMode
         */
        get() {
            val list = mutableListOf<Any?>()
            val viewRowIndices = jtable.getSelectedRows()
            val viewColumnIndices = jtable.getSelectedColumns()
            for (viewRowIndex in viewRowIndices) {
                for (viewColumnIndex in viewColumnIndices) {
                    val modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex)
                    val modelColumnIndex = jtable.convertColumnIndexToModel(viewColumnIndex)
                    list.add(tableModel.getValueAt(modelRowIndex, modelColumnIndex))
                }
            }

            return list
        }

    /**
     * Set the currently selected cells (0, false or null).
     * Use this for multiple row selection, when columnSelection is on
     */
    fun clearSelectedCells() {
        val viewRowIndices = jtable.getSelectedRows()
        val viewColumnIndices = jtable.getSelectedColumns()
        val tcm = jtable.getColumnModel()

        for (viewColumnIndex in viewColumnIndices) {
            val tc = tcm.getColumn(viewColumnIndex)
            val modelColumnIndex = tc.getModelIndex()

            val colClass = jtable.getColumnClass(viewColumnIndex)
            val zeroValue = tableModel.zeroValue(colClass)
            for (viewRowIndex in viewRowIndices) {
                val modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex)
                tableModel.setValueAt(zeroValue, modelRowIndex, modelColumnIndex)
            }
        }
    }

    fun addBean(bean: T) {
        beans.add(bean)
        val row = beans.size - 1
        tableModel.fireTableRowsInserted(row, row)
    }

    fun addBeans(newBeans: MutableList<T>) {
        this.beans.addAll(newBeans)
        val row = beans.size - 1
        tableModel.fireTableRowsInserted(row - newBeans.size, row)
    }

    fun setBeans(beans: List<T>?) {
        this.beans.clear()
        if (beans != null) this.beans.addAll(beans)
        tableModel.fireTableDataChanged() // this should make the jtable update
        revalidate()
    }

    fun clearBeans() {
        setBeans(null)
    }

    fun indexedBeans(): List<IndexedBean<T>> =
        beans.mapIndexed { idx, bean ->
            IndexedBean(bean, idx, jtable.convertRowIndexToView(idx))
        }

    data class IndexedBean<T>(val bean: T, val modelIndex: Int, val viewIndex: Int)

    val jTable: JTable
        get() = jtable

    fun setFontSize(size: Float) {
        jtable.setFont(jtable.getFont().deriveFont(size))
        jtable.setRowHeight(size.toInt() + 4)
        refresh()
    }

    /**
     * Set the selection mode on the JTable
     * 
     * @param mode : JTable.setSelectionMode
     * @see JTable.setSelectionMode
     */
    fun setSelectionMode(mode: Int) {
        jtable.setSelectionMode(mode)
    }

    fun clearSelection() {
        jtable.getSelectionModel().clearSelection()
    }

    private fun makeRowVisible(viewRowIndex: Int) {
        val visibleRect = jtable.getCellRect(viewRowIndex, 0, true)
        if (debugSelected) println("----ensureRowIsVisible = " + visibleRect)
        visibleRect.x = scrollPane.getViewport().getViewPosition().x
        jtable.scrollRectToVisible(visibleRect)
        jtable.repaint()
    }

    fun refresh() {
        jtable.repaint()
    }

    /**
     * Set the ColumnSelection is allowed (default false)
     * 
     * @param b allowed or not
     */
    fun setColumnSelectionAllowed(b: Boolean) {
        jtable.setColumnSelectionAllowed(b)
    }

    /**
     * Save state to the PreferencesExt.
     */
    fun saveState(saveData: Boolean) {
        if (store == null) return

        try {
            // save data
            if (saveData) {
                store.putBeanCollection("beanList", beans)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val propCols = mutableListOf<PropertyCol>()
        val tableColumnModel = jtable.getColumnModel() as HidableTableColumnModel
        val columns = tableColumnModel.getColumns(false)

        while (columns.hasMoreElements()) {
            val column = columns.nextElement()
            val propCol = PropertyCol(
                column.getIdentifier().toString(),
                column.getWidth(),
                tableColumnModel.isColumnVisible(column))
            propCols.add(propCol)
        }

        store.putBeanCollection("propertyCol", propCols)
    }

    /**
     * Notifies the TableModel that the data in the specified bean has changed.
     * The TableModel will then fire an event of its own, which its listeners will hear (usually a JTable).
     * 
     * @param bean a bean that has changed.
     */
    fun fireBeanDataChanged(bean: T) {
        val row = beans.indexOf(bean)
        if (row >= 0) {
            tableModel.fireTableRowsUpdated(row, row)
        }
    }

    // Restore state from PreferencesExt
    fun restoreState() {
        val tableColumnModel = jtable.getColumnModel() as HidableTableColumnModel

        val propColObjs = store.getBean("propertyCol", null)
        if (propColObjs == null) return
        require(propColObjs is List<*>)
        if (propColObjs.size > 0 && propColObjs.first() is ucar.ui.prefs.BeanTable.PropertyCol ) {
            // convert from old serialization
            logger.debug("Converting ucar.ui.prefs.BeanTable.PropertyCol for class ${beanClass.getSimpleName()}")
            var newViewIndex = 0
            propColObjs.forEach {
                try {
                    val propCol = it as ucar.ui.prefs.BeanTable.PropertyCol
                    // TODO problem is these two dont know about invisible columns; but probably all start as visible
                    // throws IAE if propCol.name is unknown. Stupid.
                    val currentViewIndex = tableColumnModel.getColumnIndex(propCol.name)
                    val column = tableColumnModel.getColumn(currentViewIndex)
                    column.setPreferredWidth(propCol.width)

                    tableColumnModel.moveColumn(currentViewIndex, newViewIndex)

                    // We must do this last, since moveColumn() only works on visible columns.
                    tableColumnModel.setColumnVisible(column, propCol.isVisible)
                    if (propCol.isVisible) {
                        ++newViewIndex // Don't increment for hidden columns.
                    }
                } catch (e: IllegalArgumentException) {
                    // catch it and log it and ignore it
                    logger.debug("Column $it was present in the preferences file but not the dataset.")
                }
            }
            return
        }

        var newViewIndex = 0

        propColObjs.forEach {
            try {
                val propCol = it as PropertyCol
                // TODO problem is these two dont know about invisible columns; but probably all start as visible
                // throws IAE if propCol.name is unknown. Stupid.
                val currentViewIndex = tableColumnModel.getColumnIndex(propCol.name)
                val column = tableColumnModel.getColumn(currentViewIndex)
                column.setPreferredWidth(propCol.width)

                tableColumnModel.moveColumn(currentViewIndex, newViewIndex)

                // We must do this last, since moveColumn() only works on visible columns.
                tableColumnModel.setColumnVisible(column, propCol.isVisible)
                if (propCol.isVisible) {
                    ++newViewIndex // Don't increment for hidden columns.
                }
            } catch (e: IllegalArgumentException) {
                // catch it and log it and ignore it
                logger.debug("Column $it was present in the preferences file but not the dataset.")
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BeanTable::class.java)
    }
}

// beans must be mutable
data class PropertyCol(var name: String, var width: Int, var isVisible: Boolean) {
    constructor() : this("", 0, false)

    override fun toString(): String {
        return "PropertyCol('$name', $width, $isVisible)"
    }

}
