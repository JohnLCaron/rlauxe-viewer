/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.ui.prefs;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.table.HidableTableColumnModel;
import ucar.ui.table.TableAligner;
import ucar.ui.table.TableAppearanceAction;
import ucar.ui.table.UndoableRowSorter;
import ucar.ui.widget.*;
import ucar.ui.widget.PopupMenu;
import ucar.util.prefs.PreferencesExt;
import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.function.Function;

/**
 * A JTable that uses JavaBeans to store the data.
 * <p/>
 * The columns of the JTable are the Properties of the Javabean, found through introspection.
 * <p/>
 * The properties may be editable if they have type primitive or String, and you list
 * the editable properties in a static method editableProperties() in the bean.
 * You must also have a setXXX() method on the Bean.
 * 
 * <pre>
 * static public String editableProperties() {
 *   return "ID serverName active writeDirectory";
 * }
 * </pre>
 * 
 * or as an instance method with a no parameter constructor:
 * <pre>
 * MyClass() {}
 * 
 * public String editableProperties() {
 *   return "ID serverName active writeDirectory";
 * }
 * </pre>
 * <p/>
 * You may hide properties by listing them in a static method hiddenProperties() in the bean, eg :
 * <pre>
 * static public String hiddenProperties() {
 *   return "hideThisProperty DDDirectory";
 * }
 * </pre>
 * 
 * or as an instance method with a no parameter constructor, then you need to add innerbean in the constructor.
 * <pre>
 * MyClass() {}
 * 
 * public String hiddenProperties() {
 *   return "ID serverName active writeDirectory";
 * }
 * </pre>
 * <p/>
 * The data can be made persistent through a PreferencesExt store.
 * The width and order of the columns is persistent.
 * The javabean class may add or delete properties, and the stored data will be reasonably intact.
 *
 * @author John Caron
 */

public class BeanTable<T> extends JPanel {
  private static final Logger logger = LoggerFactory.getLogger(BeanTable.class);

  protected Class<T> beanClass;
  protected T innerbean;
  protected PreferencesExt store;
  public JTable jtable;
  protected JScrollPane scrollPane;

  protected ArrayList<T> beans;
  public TableBeanModel model;
  JCheckBox boolCellEditor;

  protected boolean debug, debugSelected, debugBean;
  protected boolean debugEditing = false;

  public BeanTable(Class<T> bc, PreferencesExt pstore, boolean canAddDelete) {
    this(bc, pstore, canAddDelete, null, null, null);
  }

  public BeanTable(Class<T> bc, PreferencesExt pstore, String header, String tooltip, BeanInfo info) {
    this.beanClass = bc;
    this.store = pstore;

    beans = (store != null) ? (ArrayList<T>) store.getBean("beanList", new ArrayList<>()) : new ArrayList<>();
    model = new TableBeanModelInfo(info);
    init(header, tooltip);
  }

  /**
   * Constructor.
   *
   * @param bc JavaBean class
   * @param pstore store data in this PreferencesExt store.
   * @param canAddDelete allow changes to the jtable - adds a New and Delete button to bottom panel
   * @param header optional header label
   * @param tooltip optional tooltip label
   * @param bean needed for inner classes to call reflected methods on
   */
  public BeanTable(Class<T> bc, PreferencesExt pstore, boolean canAddDelete, String header, String tooltip, T bean) {
    this.beanClass = bc;
    this.store = pstore;
    this.innerbean = bean;
    this.boolCellEditor = new JCheckBox();
    this.boolCellEditor.setIcon(new SimpleCheckboxStyle(24));

    beans = (store != null) ? (ArrayList<T>) store.getBean("beanList", new ArrayList()) : new ArrayList<>();
    model = new TableBeanModel(beanClass);
    init(header, tooltip);

    if (canAddDelete) {
      // button panel
      JPanel buttPanel = new JPanel();
      JButton newButton = new JButton("New");
      buttPanel.add(newButton, null);
      JButton deleteButton = new JButton("Delete");
      buttPanel.add(deleteButton, null);

      add(buttPanel, BorderLayout.SOUTH);

      // button listeners
      newButton.addActionListener(e -> {
        try {
          T newbean = beanClass.newInstance();
          addBean(newbean);
        } catch (Exception exc) {
          exc.printStackTrace();
        }
      });

      deleteButton.addActionListener(e -> {
        if (JOptionPane.showConfirmDialog(null, "Do you want to delete all selected records", "Delete Records",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
          return;

        for (T o : getSelectedBeans()) {
          beans.remove(o);
        }
        model.fireTableDataChanged();
      });
    }
  }

  public void setHeader(String header) {
    headerLabel.setText(header);
  }

  private JLabel headerLabel;

  private void init(String header, String tooltip) {
    TableColumnModel tcm = new HidableTableColumnModel(model);
    jtable = new JTable(model, tcm);
    jtable.setRowSorter(new UndoableRowSorter<>(model));

    ToolTipManager.sharedInstance().registerComponent(jtable);

    restoreState();

    // editor/renderers;
    jtable.setDefaultRenderer(java.util.Date.class, new DateRenderer());
    jtable.setDefaultRenderer(Boolean.class, new BooleanRenderer());

    jtable.setDefaultEditor(String.class, new DefaultCellEditor(new JTextField()));
    jtable.setDefaultEditor(Boolean.class, new DefaultCellEditor(boolCellEditor));

    // Fixes this bug: http://stackoverflow.com/questions/6601994/jtable-boolean-cell-type-background
    // ((JComponent) jtable.getDefaultRenderer(Boolean.class)).setOpaque(true);

    // Left-align every cell, including header cells.
    TableAligner aligner = new TableAligner(jtable, SwingConstants.LEADING);
    jtable.getColumnModel().addColumnModelListener(aligner);

    // Create a button that will popup a menu containing options to configure the appearance of the table.
    JButton cornerButton = new JButton(new TableAppearanceAction(jtable));
    cornerButton.setHideActionText(true);
    cornerButton.setContentAreaFilled(false);

    // Install the button in the upper-right corner of the table's scroll pane.
    scrollPane = new JScrollPane(jtable);
    scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, cornerButton);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

    // This keeps the corner button visible even when the table is empty (or all columns are hidden).
    scrollPane.setColumnHeaderView(new JViewport());
    scrollPane.getColumnHeader().setPreferredSize(jtable.getTableHeader().getPreferredSize());

    // UI
    setLayout(new BorderLayout());
    add(scrollPane, BorderLayout.CENTER);

    if (header != null) {
      if (tooltip != null) {
        headerLabel = new JLabel(header, SwingConstants.CENTER) {
          public JToolTip createToolTip() {
            return new MultilineTooltip();
          }
        };
        headerLabel.setToolTipText(tooltip);
      } else {
        headerLabel = new JLabel(header, SwingConstants.CENTER);
      }
      add(headerLabel, BorderLayout.NORTH);
    }

    // event management
    listenerList = new EventListenerList();

    ListSelectionModel rowSM = jtable.getSelectionModel();
    rowSM.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return;
      } // Ignore extra messages.
      ListSelectionModel lsm = (ListSelectionModel) e.getSource();
      if (!lsm.isSelectionEmpty()) {
        fireEvent(e);
      }
    });

  }

  // debug
  public String getToolTipText(MouseEvent event) {
    return super.getToolTipText(event);
  }

  public void setProperty(String propertyName, String displayName, String toolTipText) {
    model.setProperty(propertyName, displayName, toolTipText);
  }

  public void setPropertyEditable(String propertyName, boolean isHidden) {

  }

  public void setPropertyHidden(String propertyName, boolean isHidden) {

  }

  public void addListSelectionListener(ListSelectionListener l) {
    listenerList.add(javax.swing.event.ListSelectionListener.class, l);
  }

  public void removeListSelectionListener(ListSelectionListener l) {
    listenerList.remove(javax.swing.event.ListSelectionListener.class, l);
  }

  private void fireEvent(javax.swing.event.ListSelectionEvent event) {
    Object[] listeners = listenerList.getListenerList();
    // Process the listeners last to first
    for (int i = listeners.length - 2; i >= 0; i -= 2)
      ((javax.swing.event.ListSelectionListener) listeners[i + 1]).valueChanged(event);
  }

  private ucar.ui.widget.PopupMenu varPopup;

  public ucar.ui.widget.PopupMenu addPopupOption(String title, Action act) {
    if (this.varPopup == null) {
      this.varPopup = new PopupMenu(this.getJTable(), "Options");
    }
    this.varPopup.addAction(title, act);
    return this.varPopup;
  }

  public Action makeShowAction(TextHistoryPane infoTA, IndependentWindow infoWindow, Function<Object, String> show) {
    return new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Object bean = getSelectedBean();
        if (bean != null) {
          infoTA.setText(show.apply(bean));
          infoTA.gotoTop();
          infoWindow.show();
        }
      }
    };
  }

    public Action makeShowAction(TextHistoryPane infoTA, Function<Object, String> show) {
        return new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Object bean = getSelectedBean();
                if (bean != null) {
                    infoTA.setText(show.apply(bean));
                    infoTA.gotoTop();
                }
            }
        };
    }

  /**
   * Get the currently selected bean, or null if none selected.
   */
  public T getSelectedBean() {
    int viewRowIndex = jtable.getSelectedRow();
    if (viewRowIndex < 0)
      return null;
    int modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex);
    return (modelRowIndex < 0) || (modelRowIndex >= beans.size()) ? null : beans.get(modelRowIndex);
  }

  /**
   * Get the currently selected beans. Use this for multiple selection
   *
   * @return ArrayList of currently selected beans (wont be null).
   * @see #setSelectionMode
   */
  public List<T> getSelectedBeans() {
    ArrayList<T> list = new ArrayList<>();
    int[] viewRowIndices = jtable.getSelectedRows();
    for (int viewRowIndex : viewRowIndices) {
      int modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex);
      list.add(beans.get(modelRowIndex));
      if (debugSelected)
        System.out.println(" bean selected= " + modelRowIndex + " " + beans.get(modelRowIndex));
    }
    return list;
  }

  /**
   * Get the currently selected cells.
   * Use this for multiple row selection, when columnSelection is on
   *
   * @return ArrayList of currently selected cells (wont be null).
   * @see BeanTable#setSelectionMode(int).
   */
  public ArrayList<Object> getSelectedCells() {
    ArrayList<Object> list = new ArrayList<>();
    int[] viewRowIndices = jtable.getSelectedRows();
    int[] viewColumnIndices = jtable.getSelectedColumns();
    for (int viewRowIndex : viewRowIndices) {
      for (int viewColumnIndex : viewColumnIndices) {
        int modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex);
        int modelColumnIndex = jtable.convertColumnIndexToModel(viewColumnIndex);
        list.add(model.getValueAt(modelRowIndex, modelColumnIndex));
      }
    }

    return list;
  }

  /**
   * Set the currently selected cells (0, false or null).
   * Use this for multiple row selection, when columnSelection is on
   */
  public void clearSelectedCells() {
    int[] viewRowIndices = jtable.getSelectedRows();
    int[] viewColumnIndices = jtable.getSelectedColumns();
    TableColumnModel tcm = jtable.getColumnModel();

    for (int viewColumnIndex : viewColumnIndices) {
      TableColumn tc = tcm.getColumn(viewColumnIndex);
      int modelColumnIndex = tc.getModelIndex();

      Class<?> colClass = jtable.getColumnClass(viewColumnIndex);
      Object zeroValue = model.zeroValue(colClass);
      for (int viewRowIndex : viewRowIndices) {
        int modelRowIndex = jtable.convertRowIndexToModel(viewRowIndex);
        model.setValueAt(zeroValue, modelRowIndex, modelColumnIndex);
      }
    }
  }

  public void addBean(T bean) {
    beans.add(bean);
    int row = beans.size() - 1;
    model.fireTableRowsInserted(row, row);
  }

  public void addBeans(List<T> newBeans) {
    this.beans.addAll(newBeans);
    int row = beans.size() - 1;
    model.fireTableRowsInserted(row - newBeans.size(), row);
  }

  public void setBeans(List<T> beans) {
    this.beans = (beans == null) ? new ArrayList<>() : new ArrayList<>(beans);
    model.fireTableDataChanged(); // this should make the jtable update
    revalidate();
  }

  public void clearBeans() {
    setBeans(null);
  }

  public List<T> getBeans() {
    return beans;
  }

  public JTable getJTable() {
    return jtable;
  }

  public void setFontSize(float size) {
    jtable.setFont(jtable.getFont().deriveFont(size));
    jtable.setRowHeight((int) size+4);
    refresh();
  }

  /**
   * Set the selection mode on the JTable
   *
   * @param mode : JTable.setSelectionMode
   * @see JTable#setSelectionMode
   */
  public void setSelectionMode(int mode) {
    jtable.setSelectionMode(mode);
  }

  /**
   * Set which row is selected.
   *
   * @param bean select this one; must be in the list.
   */
  public void setSelectedBean(T bean) {
    if (bean == null)
      return;
    int modelRowIndex = beans.indexOf(bean);
    int viewRowIndex = jtable.convertRowIndexToView(modelRowIndex);

    if (viewRowIndex >= 0)
      jtable.getSelectionModel().setSelectionInterval(viewRowIndex, viewRowIndex);
    makeRowVisible(viewRowIndex);
  }

  public void clearSelection() {
    jtable.getSelectionModel().clearSelection();
  }

  /**
   * Set which rows are selected.
   * must also call setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
   *
   * @param want select these
   */
  public void setSelectedBeans(List<T> want) {
    jtable.getSelectionModel().clearSelection();
    for (T bean : want) {
      int modelRowIndex = beans.indexOf(bean);
      int viewRowIndex = jtable.convertRowIndexToView(modelRowIndex);

      if (viewRowIndex >= 0) {
        jtable.getSelectionModel().addSelectionInterval(viewRowIndex, viewRowIndex);
      }
    }
  }

  private void makeRowVisible(int viewRowIndex) {
    Rectangle visibleRect = jtable.getCellRect(viewRowIndex, 0, true);
    if (debugSelected)
      System.out.println("----ensureRowIsVisible = " + visibleRect);
    visibleRect.x = scrollPane.getViewport().getViewPosition().x;
    jtable.scrollRectToVisible(visibleRect);
    jtable.repaint();
  }

  public void refresh() {
    jtable.repaint();
  }

  /**
   * Set the ColumnSelection is allowed (default false)
   *
   * @param b allowed or not
   */
  public void setColumnSelectionAllowed(boolean b) {
    jtable.setColumnSelectionAllowed(b);
  }

  /**
   * Save state to the PreferencesExt.
   */
  public void saveState(boolean saveData) {
    if (store == null)
      return;

    try {
      // save data
      if (saveData) {
        store.putBeanCollection("beanList", beans);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    List<PropertyCol> propCols = new ArrayList<>();
    HidableTableColumnModel tableColumnModel = (HidableTableColumnModel) jtable.getColumnModel();
    Enumeration<TableColumn> columns = tableColumnModel.getColumns(false);

    while (columns.hasMoreElements()) {
      PropertyCol propCol = new PropertyCol();
      TableColumn column = columns.nextElement();
      propCol.setName(column.getIdentifier().toString());
      propCol.setWidth(column.getWidth());
      propCol.setVisible(tableColumnModel.isColumnVisible(column));
      propCols.add(propCol);
    }

    store.putBeanCollection("propertyCol", propCols);
  }

  /**
   * Notifies the TableModel that the data in the specified bean has changed.
   * The TableModel will then fire an event of its own, which its listeners will hear (usually a JTable).
   *
   * @param bean a bean that has changed.
   */
  public void fireBeanDataChanged(T bean) {
    int row = beans.indexOf(bean);
    if (row >= 0) {
      model.fireTableRowsUpdated(row, row);
    }
  }

  /**
   * Restore state from PreferencesExt
   */
  protected void restoreState() {
    if (store == null) {
      return;
    }

    ArrayList<?> propColObjs = (ArrayList<?>) store.getBean("propertyCol", new ArrayList<>());
    HidableTableColumnModel tableColumnModel = (HidableTableColumnModel) jtable.getColumnModel();
    int newViewIndex = 0;

    for (Object propColObj : propColObjs) {
      PropertyCol propCol = (PropertyCol) propColObj;
      try {
        int currentViewIndex = tableColumnModel.getColumnIndex(propCol.getName()); // May throw IAE.

        TableColumn column = tableColumnModel.getColumn(currentViewIndex);
        column.setPreferredWidth(propCol.getWidth());

        tableColumnModel.moveColumn(currentViewIndex, newViewIndex);

        // We must do this last, since moveColumn() only works on visible columns.
        tableColumnModel.setColumnVisible(column, propCol.isVisible());
        if (propCol.isVisible()) {
          ++newViewIndex; // Don't increment for hidden columns.
        }
      } catch (IllegalArgumentException e) {
        logger.debug(String.format("Column named \"%s\" was present in the preferences file but not the dataset.",
            propCol.getName()), e);
      }
    }
  }

  /**
   * Should be private. This is to store the width and visibility for each table column.
   */
  public static class PropertyCol {
    private String name;
    private int width;
    private boolean visible = true;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getWidth() {
      return width;
    }

    public void setWidth(int width) {
      this.width = width;
    }

    public boolean isVisible() {
      return visible;
    }

    public void setVisible(boolean visible) {
      this.visible = visible;
    }

    @Override
    public String toString() {
      return "PropertyCol{name='" + name + '\'' + ", width=" + width + ", visible=" + visible + '}';
    }
  }

  static class DateRenderer extends DefaultTableCellRenderer {
    private final java.text.SimpleDateFormat newForm, oldForm;
    private final Date cutoff;

    DateRenderer() {
      oldForm = new java.text.SimpleDateFormat("yyyy MMM dd HH:mm z");
      oldForm.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
      newForm = new java.text.SimpleDateFormat("MMM dd, HH:mm z");
      newForm.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
      Calendar cal = Calendar.getInstance();
      cal.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
      cal.add(Calendar.YEAR, -1); // "now" time format within a year
      cutoff = cal.getTime();
    }

    public void setValue(Object value) {
      if (value == null)
        setText("");
      else {
        Date date = (Date) value;
        if (date.before(cutoff))
          setText(oldForm.format(date));
        else
          setText(newForm.format(date));
      }
    }
  }

  static class BooleanRenderer extends DefaultTableCellRenderer {
    BooleanRenderer() {}

    public void setValue(Object value) {
      if (value == null)
        setText("");
      else {
        Boolean bvalue = (Boolean) value;
        if (bvalue) setText("true"); else setText("false");
      }
    }
  }

  /** Does the reflection on the bean objects */
  public class TableBeanModel extends AbstractTableModel {
    public List<PropertyDescriptor> properties = new ArrayList<>();
    private Method canedit;

    protected TableBeanModel() {}

    protected TableBeanModel(Class<?> beanClass) {
      // get bean info
      BeanInfo info;
      try {
        if (!beanClass.isInterface())
          info = Introspector.getBeanInfo(beanClass, Object.class);
        else
          info = Introspector.getBeanInfo(beanClass); // allows interfaces to be beans
      } catch (IntrospectionException e) {
        e.printStackTrace();
        return;
      }

      if (debugBean)
        System.out.println("Bean " + beanClass.getName());

      // see if editableProperties method exists
      String editableProperties = "";
      MethodDescriptor[] mds = info.getMethodDescriptors();
      if (mds == null)
        throw new IllegalStateException("no public methods");

      for (MethodDescriptor md : mds) {
        Method m = md.getMethod();
        if (m != null && m.getName().equals("editableProperties")) {
          try {
            editableProperties = (String) m.invoke(null, (Object[]) null); // try static
            if (debugEditing)
              System.out.println(" static editableProperties: " + editableProperties);
          } catch (Exception ee) {

            if (innerbean != null) {
              try {
                editableProperties = (String) m.invoke(innerbean, (Object[]) null); // try non static
                if (debugEditing)
                  System.out.println(" editableProperties: " + editableProperties);
              } catch (Exception e2) {
                e2.printStackTrace();
              }

            } else {
              ee.printStackTrace();
            }
          }
        }
      }

      // see if hiddenProperties method exists
      String hiddenProperties = "";
      for (MethodDescriptor md : mds) {
        Method m = md.getMethod();

        if (m.getName().equals("hiddenProperties")) {
          try {
            hiddenProperties = (String) m.invoke(null, (Object[]) null);
            if (debugBean)
              System.out.println(" hiddenProperties: " + hiddenProperties);

          } catch (Exception ee) {
            if (innerbean != null) {
              try {
                hiddenProperties = (String) m.invoke(innerbean, (Object[]) null); // try non static
                if (debugBean)
                  System.out.println(" hiddenProperties: " + hiddenProperties);
              } catch (Exception e2) {
                e2.printStackTrace();
              }

            } else {
              System.out.println("BeanTable: Bad hiddenProperties ");
              ee.printStackTrace();
            }
          }
        }
      }

      // see if canedit method exists
      if (innerbean != null) {
        for (MethodDescriptor md : mds) {
          Method m = md.getMethod();
          if (m.getName().equals("canedit")) {
            try {
              boolean wtf = (Boolean) m.invoke(innerbean, (Object[]) null); // see if method retuen boolean
              this.canedit = m;
              if (debugEditing) System.out.printf("BeanTable canedit: %s ", innerbean.getClass().getName());
            } catch (Exception e2) {
              e2.printStackTrace();
            }
          }
        }
      }

      // properties must have read method, not be hidden
      PropertyDescriptor[] pds = info.getPropertyDescriptors();
      for (PropertyDescriptor pd : pds) {
        if ((pd.getReadMethod() != null) && !isHidden(pd, hiddenProperties)) {
          properties.add(pd);
          // preferred == editable
          setEditable(pd, editableProperties);
        }
      }

      if (debugBean) {
        System.out.println("Properties:");
        System.out.println("  display name  type   read()       write()         editable");
        for (PropertyDescriptor pd : pds) {
          String displayName = pd.getDisplayName();
          String name = pd.getName();
          Class<?> type = pd.getPropertyType();
          Method rm = pd.getReadMethod();
          Method wm = pd.getWriteMethod();
          System.out.println(
              "  " + displayName + " " + name + " " + type.getName() + " " + rm + " " + wm + " " + pd.isPreferred());
        }
      }
    }

    public void setProperty(String propertyName, String displayName, String toolTipText) {
      PropertyDescriptor pd = getProperty(propertyName);
      if (pd != null) {
        if (displayName != null) {
          pd.setDisplayName(displayName);
          JLabel hl = (JLabel) pd.getValue("Header");
          if (hl != null)
            hl.setText(displayName);
        }
        if (toolTipText != null) {
          pd.setShortDescription(toolTipText);
          JComponent jc = (JComponent) pd.getValue("ToolTipComp");
          if (jc != null)
            jc.setToolTipText(toolTipText);
        }
      } else
        System.out.println("BeanTable.setProperty " + beanClass.getName() + " no property named " + propertyName);
    }

    // AbstractTableModel methods
    public int getRowCount() {
      return beans.size();
    }

    public int getColumnCount() {
      return properties.size();
    }

    public String getColumnName(int col) {
      return properties.get(col).getDisplayName();
    }

    public Object getValueAt(int row, int col) {
      Object bean = beans.get(row);
      Object value = "N/A";
      PropertyDescriptor pd = properties.get(col);
      try {
        Method m = pd.getReadMethod();
        value = m.invoke(bean, (Object[]) null);
      } catch (Exception ee) {
        System.out.println(
            "BeanTable: Bad getReadMethod " + row + " " + col + " " + beanClass.getName() + " " + pd.getDisplayName());
        ee.printStackTrace();
      }

      return value;
    }

    // for BeanTable
    public Object getValueAt(Object bean, int col) {
      Object value = "N/A";
      try {
        Method m = properties.get(col).getReadMethod();
        value = m.invoke(bean, (Object[]) null);
      } catch (Exception ee) {
        System.out.println("BeanTable: Bad Bean " + bean + " " + col + " " + beanClass.getName());
        ee.printStackTrace();
      }
      return value;
    }

    // editing

    public Class<?> getColumnClass(int col) {
      return wrapPrimitives(properties.get(col).getPropertyType());
    }

    public boolean isCellEditable(int row, int col) {
      PropertyDescriptor pd = properties.get(col);
      if (!pd.isPreferred() || !isRowEditable(row))
        return false;
      Class<?> type = pd.getPropertyType();
      var result = type.isPrimitive() || (type == String.class);
      if (debugEditing) System.out.println("isCellEditable " + row + " " + col + " == " + result);
      return result;
    }

    public boolean isRowEditable(int row) {
      if (this.canedit == null) return true;
      Object bean = beans.get(row);
      try {
        boolean ok = (Boolean) this.canedit.invoke(bean, (Object[]) null); // can canedit method
        if (debugEditing) System.out.println("isRowEditable " + row + " == " + ok);
        return ok;
      } catch (Exception e2) {
        e2.printStackTrace();
      }
      return false;
    }

    public void setValueAt(Object value, int row, int col) {
      Object bean = beans.get(row);
      try {
        Object[] params = new Object[1];
        params[0] = value;
        Method m = properties.get(col).getWriteMethod();
        if (m != null)
          m.invoke(bean, params);
      } catch (Exception ee) {
        ee.printStackTrace();
      }

      fireTableCellUpdated(row, col);
    }

    // extra stuff

    protected Class<?> wrapPrimitives(Class<?> c) {
      if (c == boolean.class)
        return Boolean.class;
      else if (c == int.class)
        return Integer.class;
      else if (c == float.class)
        return Float.class;
      else if (c == double.class)
        return Double.class;
      else if (c == short.class)
        return Short.class;
      else if (c == long.class)
        return Long.class;
      else if (c == byte.class)
        return Byte.class;
      else
        return c;
    }

    protected Object zeroValue(Class<?> c) {
      if (c == Boolean.class)
        return Boolean.FALSE;
      else if (c == Integer.class)
        return 0;
      else if (c == Float.class)
        return (float) 0.0;
      else if (c == Double.class)
        return 0.0;
      else if (c == Short.class)
        return (short) 0;
      else if (c == Long.class)
        return 0L;
      else if (c == Byte.class)
        return (byte) 0;
      else
        return null;
    }

    // return PropertyDescriptor with this property name, return null if not exists
    protected PropertyDescriptor getProperty(String wantName) {
      for (PropertyDescriptor property : properties) {
        if (property.getName().equals(wantName))
          return property;
      }
      return null;
    }

    // return PropertyDescriptor
    protected PropertyDescriptor getProperty(int idx) {
      return properties.get(idx);
    }

    private ArrayList<String> editP;

    private void setEditable(PropertyDescriptor pd, String editableProperties) {
      if (editP == null) {
        editP = new ArrayList<>();
        StringTokenizer toke = new StringTokenizer(editableProperties);
        while (toke.hasMoreTokens())
          editP.add(toke.nextToken());
      }

      pd.setPreferred(editP.contains(pd.getName()));
    }

    private ArrayList<String> hiddenP;

    private boolean isHidden(PropertyDescriptor pd, String hiddenProperties) {
      if (hiddenP == null) {
        hiddenP = new ArrayList<>();
        StringTokenizer toke = new StringTokenizer(hiddenProperties);
        while (toke.hasMoreTokens())
          hiddenP.add(toke.nextToken());
      }

      return hiddenP.contains(pd.getName());
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Properties:\n");
      sb.append("  display   name\n");
      for (PropertyDescriptor pd : properties) {
        String displayName = pd.getDisplayName();
        String name = pd.getName();
        sb.append("  %15s %s%n".formatted(displayName, name));
      }

      HidableTableColumnModel tableColumnModel = (HidableTableColumnModel) jtable.getColumnModel();
      ArrayList<PropertyCol> propertyCols = (ArrayList<PropertyCol>) store.getBean("propertyCol", new ArrayList<>());

      sb.append("PropertyCols:\n");
      for (PropertyCol pc : propertyCols) {
        try {
          int currentViewIndex = tableColumnModel.getColumnIndex(pc.getName()); // May throw IAE.
          sb.append("  %s %s %d%n".formatted(pc.name, pc.visible, currentViewIndex));
        } catch (Exception e) {
          sb.append(" %s %s %s%n".formatted(pc.name, pc.visible, e.getMessage()));
        }
      }

      return sb.toString();
    }

    public String showBean(Object bean, List<TableBeanProperty> props) {
      StringBuilder sb = new StringBuilder();

      HidableTableColumnModel tableColumnModel = (HidableTableColumnModel) jtable.getColumnModel();

      try {
        int maxName = 1;
        int maxValue = 1;
        int maxDesc = 1;
        for (TableBeanProperty pc : props) {
          maxName = Math.max(maxName, pc.name.length());
          maxDesc = Math.max(maxDesc, pc.desc.length());
          try {
            pc.viewColumnIndex = tableColumnModel.getColumnIndex(pc.name); // May throw IAE.
            pc.visible = true;
            Object colVal = getValueAt(bean, pc.viewColumnIndex);
            maxValue = Math.max(maxValue, colVal.toString().length());

          } catch (Exception e) {
            pc.visible = false;
          }
        }

        String rowf = new StringBuilder(" | %")
                .append(maxName)
                .append("s | %")
                .append(maxValue)
                .append("s | %-")
                .append(maxDesc)
                .append("s |%n")
                .toString();

        var sprops = props.stream().filter(p -> p.visible).sorted().toList();
        sb.append(rowf.formatted("field", "value", "description"));
        sb.append(rowf.formatted("-".repeat(maxName), "-".repeat(maxValue), "-".repeat(maxDesc)));

        for (var p : sprops) {
          int modelColumnIndex = jtable.convertColumnIndexToModel(p.viewColumnIndex);
          Object colVal = getValueAt(bean, modelColumnIndex);
          sb.append(rowf.formatted(p.name, colVal, p.desc));
        }

      } catch ( Throwable t) {
        logger.error("fail in showBean", t);
      }

      return sb.toString();
    }

  }

  static public class TableBeanProperty implements Comparable<TableBeanProperty> {
    String name;
    String desc;
    boolean visible;
    int viewColumnIndex;

    public TableBeanProperty(String name, String desc) {
      this.name = name;
      this.desc = desc;
    }

    @Override
    public int compareTo(@NotNull TableBeanProperty other) {
      return this.viewColumnIndex - other.viewColumnIndex;
    }
  }

  protected class TableBeanModelInfo extends TableBeanModel {

    protected TableBeanModelInfo(BeanInfo info) {
      PropertyDescriptor[] pds = info.getPropertyDescriptors();
      for (PropertyDescriptor pd : pds) {
        if (pd.getReadMethod() != null) {
          properties.add(pd);
        }
      }
    }
  }

  // TODO doesnt really work
  // https://stackoverflow.com/questions/22691353/scale-a-jcheckbox-box
  class SimpleCheckboxStyle implements Icon {

    int fontsize = 16;

    public SimpleCheckboxStyle (int fontsize){
      this.fontsize = fontsize;
    }

    protected int getDimension() {
      return fontsize;
    }

    public void paintIcon(Component component, Graphics g, int x, int y) {
      ButtonModel buttonModel = ((AbstractButton) component).getModel();

      Dimension componentSize = component.getSize();
      int y_offset = (int) (component.getSize().getHeight() / 2) - (int) (getDimension() / 2);
      int x_offset = 2; // this is wrong

      if (buttonModel.isSelected()) {
        g.setColor(new Color(0, 60, 120));
      } else if (buttonModel.isRollover()) {
        g.setColor(Color.BLACK);
      } else {
        g.setColor(Color.DARK_GRAY);
      }
      g.fillRect(x_offset, y_offset, fontsize, fontsize);
      if (buttonModel.isPressed()) {
        g.setColor(Color.GRAY);
      } else if (buttonModel.isRollover()) {
        g.setColor(new Color(240, 240, 250));
      } else {
        g.setColor(Color.WHITE);
      }
      g.fillRect(1 + x_offset, y_offset + 1, fontsize - 2, fontsize - 2);
      if (buttonModel.isSelected()) {
        int r_x = 1;
        g.setColor(Color.GRAY);
        g.fillRect(x_offset + r_x + 3, y_offset + 3 + r_x, fontsize - (7 + r_x), fontsize - (7 + r_x));
      }
    }

    public int getIconWidth() {
      return getDimension();
    }

    public int getIconHeight() {
      return getDimension();
    }
  }
}
