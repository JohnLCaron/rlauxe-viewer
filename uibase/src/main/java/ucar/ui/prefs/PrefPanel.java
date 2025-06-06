/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.ui.prefs;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import ucar.util.prefs.PersistenceManager;
import ucar.util.prefs.PreferencesExt;
import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Create a User Preferences Panel or Dialog.
 *
 * A PrefPanel manages a set of Fields with convenience methods for rapidly creating
 * User Dialogs whose values are made persistent by a Preferences Store. All Fields
 * contained in the PrefPanel share the same Preferences, and so must have unique names.
 *
 * Send ActionEvent when "accept" button is pressed. Can also listen on individual Fields.
 * You must call one finish() method exactly once, when you are done adding Fields.
 *
 * <p>
 * Example of use:
 * 
 * <pre>
 * PreferencesExt store = null;
 * try {
 *   xstore = XMLStore.createFromFile("E:/dev/prefs/test/panel/panel.xml", null);
 *   store = xstore.getPreferences();
 * } catch (Exception e) {
 *   System.exit(1);
 * }
 * PrefPanel pp = new PrefPanel("test", store);
 * pp.addTextField("name", "name", "defValue");
 * pp.newColumn();
 * pp.addTextField("name2", "name2", "defValue22");
 * pp.newColumn();
 * pp.addTextField("name3", "name3", "defValue22 asd jalskdjalksjd");
 * pp.finish();
 * 
 * pp.addActionListener(new ActionListener() {
 *   public void actionPerformed(ActionEvent e) {
 *     // accept was called
 *   }
 * });
 * </pre>
 *
 * <h3>Form layout</h3>
 * The PrefPanel is layed out with the jgoodies FormLayout layout manager and PanelBuilder, which use a row, colummn
 * grid.
 * Fields in the same column are aligned.
 *
 * <p>
 * There are 2 ways to do form layout: implicit and explicit. With implicit, the position is implicitly specified by the
 * order the fields are added, using, for example:
 * 
 * <pre>
 *   addDoubleField(String fldName, String label, double defValue)
 * </pre>
 *
 * The fields are all added in a column. To start a new column, use setCursor().
 * <p>
 * With explicit, you specify the row and col, and an optional constraint:
 * 
 * <pre>
 *   addDoubleField(String fldName, String label, double defValue, int col, int row, String constraint)
 * </pre>
 *
 * Row and column numbers are 0 based. Each field has a width of 2 columns (one for the label and one for the
 * component) and a height of 1 row, unless you specify otherwise using a constraint.
 * A heading takes up an entire row, spanning all columns
 */
public class PrefPanel extends JPanel {
  private static final boolean debugLayout = false;

  private final String name;
  private final Preferences prefs;
  private final PersistenceManager storeData;

  private boolean finished;
  private final HashMap<String, Field> flds = new HashMap<>(40);
  private final List<LayoutComponent> layoutComponents; // use with form layout
  private int cursorRow, cursorCol; // current row and column

  private final List<JComponent> auxButtons = new ArrayList<>();

  // event handling
  private final EventListenerList listenerList = new EventListenerList();

  /**
   * Constructor.
   * 
   * @param name may be null.
   * @param prefs keep values in here; may be null.
   */
  public PrefPanel(String name, PreferencesExt prefs) {
    this(name, prefs, prefs);
  }

  /**
   * Constructor.
   * 
   * @param name may be null.
   * @param storeData keep values in here; may be null.
   */
  public PrefPanel(String name, Preferences prefs, PersistenceManager storeData) {
    this.name = name;
    this.prefs = prefs;
    this.storeData = storeData;

    // colList = new ArrayList( 5);
    // currentComps = new ArrayList( 10);
    // colList.add( currentComps);
    layoutComponents = new ArrayList<>(20);

    /*
     * manager.addPropertyChangeListener( "focusOwner", new PropertyChangeListener() {
     * public void propertyChange(PropertyChangeEvent evt) {
     * Object val = evt.getNewValue();
     * String sval = (val == null) ? "null" : val.getClass().getName();
     * Component own = manager.getFocusOwner();
     * String sown = (own == null) ? "null" : own.getClass().getName();
     * }
     * });
     * manager.addPropertyChangeListener( "permanentFocusOwner", new PropertyChangeListener() {
     * public void propertyChange(PropertyChangeEvent evt) {
     * Object val = evt.getNewValue();
     * String sval = (val == null) ? "null" : val.getClass().getName();
     * Component pown = manager.getPermanentFocusOwner();
     * String sown = (pown == null) ? "null" : pown.getClass().getName();
     * }
     * });
     */
  }

  /*
   * public void setPersistenceManager (PersistenceManager storeData) {
   * this.storeData = storeData;
   * Iterator iter = flds.values().iterator();
   * while (iter.hasNext()) {
   * Field f = (Field) iter.next();
   * f.setPersistenceManager(storeData);
   * }
   * }
   */

  /** Add listener: action event sent if "apply" button is pressed */
  public void addActionListener(ActionListener l) {
    listenerList.add(java.awt.event.ActionListener.class, l);
  }

  /** Remove listener */
  public void removeActionListener(ActionListener l) {
    listenerList.remove(java.awt.event.ActionListener.class, l);
  }

  private void fireEvent(java.awt.event.ActionEvent event) {
    // Guaranteed to return a non-null array
    Object[] listeners = listenerList.getListenerList();
    // Process the listeners last to first, notifying
    // those that are interested in this event
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      ((java.awt.event.ActionListener) listeners[i + 1]).actionPerformed(event);
    }
  }

  /**
   * Call Field.accept() on all Fields. This puts any edits into the Store,
   * and fires PropertyChangeEvents if any values change, and sends an
   * ActionEvent to any listeners.
   */
  public boolean accept() {
    StringBuffer buff = new StringBuffer("Invalid field value ");
    boolean ok = true;
    for (Object o : flds.values())
      ok &= ((Field) o).accept(buff);

    if (!ok) {
      try {
        JOptionPane.showMessageDialog(PrefPanel.findActiveFrame(), buff.toString());
      } catch (HeadlessException e) {
      }
      return false;
    }

    /*
     * store the text widths if they exist
     * if (storeData != null) {
     * Preferences substore = prefs.node("sizes");
     * iter = flds.values().iterator();
     * while (iter.hasNext()) {
     * Field fld = (Field) iter.next();
     * JComponent comp = fld.getEditComponent();
     * substore.putInt(fld.getName(), (int) comp.getPreferredSize().getWidth());
     * }
     * }
     */
    fireEvent(new ActionEvent(this, 0, "Accept"));
    return true;
  }

  /**
   * Set enabled on all the fields in the prefPanel
   * 
   * @param enable enable if true
   */
  public void setEnabled(boolean enable) {
    for (Field field : flds.values())
      field.setEnabled(enable);
  }

  /** Return the name of the PrefPanel. */
  public String getName() {
    return name;
  }

  /** Iterator over the fields */
  public Iterator<Field> getFields() {
    return flds.values().iterator();
  }

  /**
   * Find the field with the specified name.
   * 
   * @param name of Field
   * @return Field or null if not found
   */
  public Field getField(String name) {
    return flds.get(name);
  }

  /**
   * Get current value of the named field
   * 
   * @param name of field
   * @return value of named field
   */
  public Object getFieldValue(String name) {
    Field fld = getField(name);
    if (fld == null)
      throw new IllegalArgumentException("no field named " + name);
    return fld.getValue();
  }

  /**
   * Set the current value of the named field
   * 
   * @param name of field
   * @param value of field
   */
  public void setFieldValue(String name, Object value) {
    Field fld = getField(name);
    if (fld == null)
      throw new IllegalArgumentException("no field named " + name);
    fld.setValue(value);
  }

  /** Add a button to the button panel */
  public void addButton(JComponent b) {
    auxButtons.add(b);
  }

  /**
   * Add a field created by the user.
   * 
   * @param fld add this field.
   */
  public Field addField(Field fld) {
    addField(fld, cursorCol, cursorRow, null);
    cursorRow++;
    return fld;
  }

  public Field addField(Field fld, int col, int row, String constraint) {
    if (null != flds.get(fld.getName()))
      throw new IllegalArgumentException("PrefPanel: already have field named " + fld.getName());

    // currentComps.add( fld);
    flds.put(fld.getName(), fld);
    layoutComponents.add(new LayoutComponent(fld, col, row, constraint));

    fld.addPropertyChangeListener(e -> revalidate());

    return fld;
  }

  public Field.BeanTableField addBeanTableField(String fldName, String label, java.util.ArrayList<?> beans,
      Class<?> beanClass, int col, int row, String constraint) {
    Field.BeanTableField fld =
        new Field.BeanTableField(fldName, label, beans, beanClass, (PreferencesExt) prefs, storeData);
    addField(fld, col, row, constraint);
    return fld;
  }

  /**
   * Add a boolean field as a checkbox.
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param defValue default value
   */
  public Field.CheckBox addCheckBoxField(String fldName, String label, boolean defValue) {
    Field.CheckBox fld = new Field.CheckBox(fldName, label, defValue, storeData);
    addField(fld);
    return fld;
  }

  public Field.CheckBox addCheckBoxField(String fldName, String label, boolean defValue, int col, int row) {
    Field.CheckBox fld = new Field.CheckBox(fldName, label, defValue, storeData);
    addField(fld, col, row, null);
    return fld;
  }

  /*
   * add a boolean field to turn a field on/off
   * 
   * @param fldName: the name to store the data in the PersistenceManagerData
   * 
   * @param defvalue: default value
   * 
   * @param enabledField: the InputField to enable/disable; must already be added
   *
   * public Field.BooleanEnabler addEnablerField(String fldName, boolean defValue, Field enabledField) {
   * Field.BooleanEnabler enabler = new Field.BooleanEnabler(fldName, defValue, enabledField, storeData);
   * // flds.add( enabledField);
   * enabledField.hasEnabler = true;
   * 
   * flds.add( enabler);
   * return enabler;
   * }
   */

  /**
   * Add a field that edits a date
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param defValue default value
   */
  public Field.Date addDateField(String fldName, String label, Date defValue) {
    Field.Date fld = new Field.Date(fldName, label, defValue, storeData);
    addField(fld);
    return fld;
  }

  public Field.Date addDateField(String fldName, String label, Date defValue, int col, int row, String constraint) {
    Field.Date fld = new Field.Date(fldName, label, defValue, storeData);
    addField(fld, col, row, constraint);
    return fld;
  }

  /**
   * Add a field that edits a double
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param defValue default value
   */
  public Field.Double addDoubleField(String fldName, String label, double defValue) {
    Field.Double fld = new Field.Double(fldName, label, defValue, -1, storeData);
    addField(fld);
    return fld;
  }

  public Field.Double addDoubleField(String fldName, String label, double defValue, int col, int row,
      String constraint) {
    Field.Double fld = new Field.Double(fldName, label, defValue, -1, storeData);
    addField(fld, col, row, constraint);
    return fld;
  }

  public Field.Double addDoubleField(String fldName, String label, double defValue, int nfracDig, int col, int row,
      String constraint) {
    Field.Double fld = new Field.Double(fldName, label, defValue, nfracDig, storeData);
    addField(fld, col, row, constraint);
    return fld;
  }


  public Field.EnumCombo addEnumComboField(String fldName, String label, java.util.Collection<Object> defValues,
      boolean editable, int col, int row, String constraint) {
    Field.EnumCombo fld = new Field.EnumCombo(fldName, label, defValues, storeData);
    addField(fld, col, row, constraint);
    fld.setEditable(editable);
    return fld;
  }

  public Field.EnumCombo addEnumComboField(String fldName, String label, java.util.Collection<Object> defValues,
      boolean editable) {
    Field.EnumCombo fld = new Field.EnumCombo(fldName, label, defValues, storeData);
    addField(fld);
    fld.setEditable(editable);
    return fld;
  }

  /*
   * Add a field that edits a formatted text field
   * NOTE: to use this directly, you must use a PersistenceManagerExt object.
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * 
   * @param label used as the label on the panel
   * 
   * @param defValue default value
   *
   * public Field.TextFormatted addTextFormattedField(String fldName, String label,
   * JFormattedTextField tf, Object defValue) {
   * Field.TextFormatted fld = new Field.TextFormatted(fldName, label, tf, defValue, storeData);
   * addField( new FieldResizable(fld, this));
   * return fld;
   * }
   */

  /**
   * Add a field that edits an integer
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param defValue default value
   */
  public Field.Int addIntField(String fldName, String label, int defValue) {
    Field.Int fld = new Field.Int(fldName, label, defValue, storeData);
    addField(fld);
    return fld;
  }

  /*
   * Add an integer field with units.
   * 
   * @param fldName: the name to store the data in the PersistenceManagerData
   * 
   * @param label: used as the label on the panel
   * 
   * @param defvalue: default value
   * 
   * @param units: optional unit label
   *
   * public Field.Int addIntField(String fldName, String label, int defValue, String units) {
   * Field.Int fld = new Field.IntUnits(fldName, label, units, defValue, storeData);
   * flds.add( fld);
   * return fld;
   * }
   */

  /**
   * Add a password text field.
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param defValue default value
   */
  public Field.Password addPasswordField(String fldName, String label, String defValue) {
    Field.Password fld = new Field.Password(fldName, label, defValue, storeData);
    addField(fld);
    return fld;
  }

  /**
   * Add a text field.
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param defValue default value
   * @return the Field.Text object that was added
   */
  public Field.Text addTextField(String fldName, String label, String defValue) {
    Field.Text fld = new Field.Text(fldName, label, defValue, storeData);
    addField(fld);
    return fld;
  }

  public Field.Text addTextField(String fldName, String label, String defValue, int col, int row, String constraint) {
    Field.Text fld = new Field.Text(fldName, label, defValue, storeData);
    addField(fld, col, row, constraint);
    return fld;
  }

  /**
   * Add a text combobox field.
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param defValues list of default values (Strings) to include in the comboBox. May be null.
   *        These are added to the combobox (at the end) no matter how many there are.
   * @param nKeep number of most recently used values to keep
   * @param editable whether the user can add new entries the list to select from.
   */
  public Field.TextCombo addTextComboField(String fldName, String label, java.util.Collection<Object> defValues,
      int nKeep, boolean editable) {
    Field.TextCombo fld = new Field.TextCombo(fldName, label, defValues, nKeep, storeData);
    addField(fld);
    fld.setEditable(editable);
    return fld;
  }

  public Field.TextCombo addTextComboField(String fldName, String label, java.util.Collection<Object> defValues,
      int nKeep, boolean editable, int col, int row, String constraint) {
    Field.TextCombo fld = new Field.TextCombo(fldName, label, defValues, nKeep, storeData);
    addField(fld, col, row, constraint);
    fld.setEditable(editable);
    return fld;
  }


  /**
   * Add a TextArea field.
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * @param label used as the label on the panel
   * @param def default value
   * @param nrows number of rows
   */
  public Field.TextArea addTextAreaField(String fldName, String label, String def, int nrows) {
    Field.TextArea fld = new Field.TextArea(fldName, label, def, nrows, storeData);
    addField(fld);
    return fld;
  }

  public Field.TextArea addTextAreaField(String fldName, String label, String def, int nrows, int col, int row,
      String constraint) {
    Field.TextArea fld = new Field.TextArea(fldName, label, def, nrows, storeData);
    addField(fld, col, row, constraint);
    return fld;
  }

  /*
   * Add a text combobox field.
   * 
   * @param fldName the name to store the data in the PersistenceManagerData
   * 
   * @param label used as the label on the panel
   * 
   * @param defValues list of default values to include in the comboBox. May be null.
   * These are added to the combobox (at the end) no matter how many there are.
   * 
   * @param nKeep number of most recently used values to keep
   *
   * public Field.Combo addComboField(String fldName, String label, java.util.Collection defValues, int nKeep) {
   * Field.Combo fld = new Field.Combo(fldName, label, defValues, nKeep, (PersistenceManagerExt) storeData);
   * addField( fld);
   * return fld;
   * }
   */

  /** Add a heading that takes no input */
  public void addHeading(String heading) {
    addHeading(heading, cursorRow);
    cursorRow++;
  }

  /** Add a heading at the specified row. this spans all columns */
  public void addHeading(String heading, int row) {
    layoutComponents.add(new LayoutComponent(heading, 0, row, null));
  }

  /** Add a Component. */
  public void addComponent(Component comp, int col, int row, String constraint) {
    layoutComponents.add(new LayoutComponent(comp, col, row, constraint));
  }

  /** Add a seperator after the last field added. */
  public void addSeparator() {
    addEmptyRow(cursorRow++, 15);
  }

  /** Add a seperator after the last field added. */
  public void addEmptyRow(int row, int size) {
    layoutComponents.add(new LayoutComponent(null, size, row, null));
  }

  /**
   * Start a new column.
   * Everything added goes into a vertical column until another call to newColumn().
   */
  public void setCursor(int col, int row) {
    // currentComps = new ArrayList(10);
    // colList.add( currentComps);
    cursorCol = col;
    cursorRow = row;
  }

  /** Call this when you have finish constructing the panel, adding buttons in default spot */
  public void finish() {
    finish(true);
  }

  /**
   * Call this when you have finish constructing the panel.
   * 
   * @param addButtons if true, add buttons in default spot
   */
  public void finish(boolean addButtons) {
    finish(addButtons, BorderLayout.SOUTH);
  }

  /**
   * Call when finished adding components to the PrefPanel.
   * 
   * @param addButtons if true, add buttons
   * @param where BorderLayout.NORTH, SOUTH, EAST, WEST
   */
  public void finish(boolean addButtons, String where) {
    if (finished)
      throw new IllegalStateException("PrefPanel " + name + ": already called finish()");

    StringBuilder sbuff = new StringBuilder();

    // column layout, first sort by col
    layoutComponents.sort(Comparator.comparingInt(o -> o.col));

    // now create column layout spec and x cell constraint
    sbuff.setLength(0);
    int currCol = -1;
    Iterator<LayoutComponent> iter = layoutComponents.iterator();
    while (iter.hasNext()) {
      LayoutComponent lc = iter.next();
      if (lc.col > currCol) {
        if (currCol >= 0)
          sbuff.append(", 5dlu, ");
        else
          sbuff.append("3dlu, ");
        sbuff.append("right:default, 3dlu, default:grow");
        currCol += 2;
      }
      lc.ccLabel.gridX = 2 * lc.col + 2;
      lc.cc.gridX = 2 * lc.col + 4;
    }
    String colSpec = sbuff.toString();
    if (debugLayout)
      System.out.println(" column layout = " + colSpec);
    int ncols = 2 * currCol;

    // row layout, first sort by row
    layoutComponents.sort(Comparator.comparingInt(o -> o.row));

    // now adjust for any headings, put into y cell constraint
    int incr = 0;
    iter = layoutComponents.iterator();
    while (iter.hasNext()) {
      LayoutComponent lc = iter.next();
      if ((lc.comp instanceof String) && (lc.row > 0)) // its a header, not in first position
        incr++; // leave space by adding a row

      lc.cc.gridY = lc.row + incr + 1; // adjust downward
      lc.ccLabel.gridY = lc.cc.gridY;
      if (debugLayout)
        System.out.println(lc + " constraint = " + lc.cc);
    }

    // now create row layout spec
    sbuff.setLength(0);
    int currRow = -1;
    iter = layoutComponents.iterator();
    while (iter.hasNext()) {
      LayoutComponent lc = iter.next();
      while (lc.row > currRow) {
        if ((lc.comp instanceof String) && (lc.row > 0)) {
          sbuff.append(", 5dlu, default");
        } else if ((lc.comp == null)) {
          sbuff.append(", ").append(lc.col).append("dlu");
        } else {
          if (currRow >= 0)
            sbuff.append(", ");
          sbuff.append("default");
        }
        currRow++;
      }
    }
    String rowSpec = sbuff.toString();
    if (debugLayout)
      System.out.println(" row layout = " + rowSpec);

    // the jgoodies form layout
    FormLayout layout = new FormLayout(colSpec, rowSpec);

    PanelBuilder builder = new PanelBuilder(layout);
    builder.setDefaultDialogBorder();

    CellConstraints cc = new CellConstraints();

    // now add each component with correct constraint
    iter = layoutComponents.iterator();
    while (iter.hasNext()) {
      LayoutComponent lc = iter.next();

      if (lc.comp instanceof Field) {
        Field fld = (Field) lc.comp;
        builder.addLabel(fld.getLabel() + ":", lc.ccLabel);
        Component comp = fld.getEditComponent();
        if (lc.comp instanceof Field.TextArea)
          comp = new JScrollPane(comp);
        builder.add(comp, lc.cc);
      } else if (lc.comp instanceof String) {
        String header = (String) lc.comp;
        builder.addSeparator(header, cc.xyw(1, lc.cc.gridY, ncols));
      } else if (lc.comp instanceof Component) {
        builder.add((Component) lc.comp, lc.cc);
      }
    }

    JPanel mainPanel = builder.getPanel();

    // button panel
    JPanel buttPanel = new JPanel();
    JButton acceptButton = new JButton("Apply");
    buttPanel.add(acceptButton, null);
    for (JComponent auxButton : auxButtons)
      buttPanel.add(auxButton, null);

    // button listeners
    acceptButton.addActionListener(evt -> accept());

    setLayout(new BorderLayout());
    add(mainPanel, BorderLayout.CENTER);

    if (addButtons) {
      if (where.equals(BorderLayout.SOUTH)) {
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(new JSeparator(SwingConstants.HORIZONTAL));
        south.add(buttPanel);
        add(south, BorderLayout.SOUTH);
      } else
        add(buttPanel, where);
    }

    finished = true;
  }

  // helper class to use jgoodies form to do layout.
  // Each field gets one of these
  private static class LayoutComponent {
    Object comp;
    int row, col;
    CellConstraints cc, ccLabel;

    LayoutComponent(Object comp, int col, int row, String constraint) {
      this.comp = comp;
      this.row = row;
      this.col = col;

      this.cc = new CellConstraints(1 + ", " + 1 + " " + (constraint == null ? "" : constraint));
      if (cc.gridWidth > 1)
        cc.gridWidth = cc.gridWidth * 2 - 1;
      this.ccLabel = new CellConstraints();
    }

    public String toString() {
      if (comp == null)
        return "empty row";
      if (comp instanceof Field)
        return ((Field) comp).getName();
      return comp.getClass().getName();
    }

  }

  /**
   * A convenience class for constructing a standalone JDialog window that has a PrefPanel inside it.
   * To show it on screen, call dialog.show().
   * Example:
   * 
   * <pre>
   *
   * PrefPanel.Dialog d = new PrefPanel.Dialog(frame, true, "testDialogue", (PersistenceManagerExt) store.node("dialog"));
   * PrefPanel pp2 = d.getPrefPanel();
   * pp2.addHeading("This is Not Your Life:");
   * pp2.addTextField("name", "name", "defValue");
   * pp2.addTextField("name2", "name2", "defValue22");
   * pp2.addTextField("name3", "name3", "defValue22 asd jalskdjalksjd");
   * pp2.addSeparator();
   * pp2.addHeading("Part Two:");
   * pp2.addPasswordField("password", "password", "secret");
   * pp2.addIntField("testInt", "testInt", 1234);
   * pp2.addDoubleField("testD", "testD", 1234.45);
   * pp2.addCheckBoxField("testB", "testB", true);
   * pp2.newColumn();
   * pp2.addHeading("Another Column:");
   * pp2.addDateField("date", "date", new Date());
   * try {
   *   pp2.addTextFormattedField("ff", "ff", new javax.swing.text.MaskFormatter("(###) ###-####"), "(303) 497-1234");
   * } catch (java.text.ParseException e) {
   * }
   * ArrayList list = new ArrayList(5);
   * list.add("this");
   * list.add("is");
   * list.add("new");
   * list.add("but");
   * list.add("really too longs");
   * pp2.addTextComboField("combo", "combo", list, 5);
   * 
   * d.finish();
   * d.show();
   * 
   * </pre>
   */
  public static class Dialog extends JDialog {
    private final PrefPanel pp;
    private PreferencesExt substore;

    /**
     * constructor
     * 
     * @param parent JFrame (application) or JApplet (applet)
     * @param modal true is modal (must finish editing before can do anything else)
     * @param title title of window
     * @param prefs PersistenceManagerExt store: keep values in here; may be null.
     */
    public Dialog(RootPaneContainer parent, boolean modal, String title, PreferencesExt prefs) {
      this(parent, modal, title, prefs, prefs);
    }

    /**
     * constructor
     * 
     * @param parent JFrame (application) or JApplet (applet)
     * @param modal true is modal (must finish editing before can do anything else)
     * @param title title of window
     * @param prefs PersistenceManagerExt store: keep values in here; may be null.
     */
    public Dialog(RootPaneContainer parent, boolean modal, String title, Preferences prefs,
        PersistenceManager storeData) {
      super((parent instanceof JFrame) ? (JFrame) parent : findActiveFrame());
      setModal(modal);
      if (title != null)
        setTitle(title);
      if (prefs != null)
        substore = (PreferencesExt) prefs.node("Dialog");

      if (substore != null) {
        Rectangle r = (Rectangle) substore.getBean("Bounds", null);
        if (r != null)
          setBounds(r);
      }

      // L&F may change
      UIManager.addPropertyChangeListener(e -> {
        if (e.getPropertyName().equals("lookAndFeel"))
          SwingUtilities.updateComponentTreeUI(Dialog.this);
      });

      Container cp = getContentPane();
      pp = new PrefPanel(title, prefs, storeData);
      cp.add(pp, BorderLayout.CENTER);

      // add a dismiss button
      JButton dismiss = new JButton("Cancel");
      dismiss.addActionListener(evt -> setVisible(false));
      pp.addButton(dismiss);

      // watch for accept
      pp.addActionListener(e -> setVisible(false));

      // catch move, resize events
      addComponentListener(new ComponentAdapter() {
        public void componentMoved(ComponentEvent e) {
          if (substore != null)
            substore.putBeanObject("Bounds", getBounds());
        }

        public void componentResized(ComponentEvent e) {
          if (substore != null)
            substore.putBeanObject("Bounds", getBounds());
        }
      });

    }

    /** Get the PrefPanel */
    public PrefPanel getPrefPanel() {
      return pp;
    }

    /**
     * Find the field with the specified name.
     * 
     * @param name of Field
     * @return Field or null if not found
     */
    public Field getField(String name) {
      return pp.getField(name);
    }

    /**
     * Call this when done adding Fields to the prefPanel, instead of calling
     * pp.finish().
     */
    public void finish() {
      pp.finish();
      pack();

      // persistent state
      if (substore != null) {
        Rectangle b = (Rectangle) substore.getBean("Bounds", null);
        if (b != null)
          setBounds(b);
        substore.putBeanObject("Bounds", getBounds());
      }
    }
  }

  // thanks to Heinz M. Kabutz
  public static Frame findActiveFrame() {
    Frame[] frames = JFrame.getFrames();
    for (Frame frame : frames) {
      if (frame.isVisible())
        return frame;
    }
    return null;
  }

}
