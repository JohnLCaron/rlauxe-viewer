/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import ucar.ui.prefs.ComboBox;
import ucar.ui.prefs.Debug;
import ucar.ui.widget.*;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.XMLStore;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Formatter;
import java.util.HashSet;

import static ucar.ui.widget.FontUtil.getStandardFontSize;

/** ElectionRecord Viewer main program. */
public class ViewerMain extends JPanel {
  public static final String FRAME_SIZE = "FrameSize";
  public static final String FONT_SIZE = "FontSize";

  private static JFrame frame;
  private static PreferencesExt prefs;
  private static XMLStore store;
  private static ViewerMain ui;
  private static boolean done;

  private final JPanel buttPanel = new JPanel();
  private final FontUtil.StandardFont fontu;

  TextHistoryPane infoTA;
  IndependentWindow infoWindow;
  ComboBox<String> auditRecordDirCB;
  JPanel topPanel;

  FileManager fileChooser;
  boolean eventOk = true;

  String auditRecordDir = "none";

  JTabbedPane tabbedPane;
  private final AuditTable auditPanel;
  private final AuditRoundsTable auditRoundsPanel;

  public ViewerMain(PreferencesExt prefs, float fontSize) {
    // the tabbed panels
    tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    auditPanel = new AuditTable((PreferencesExt) prefs.node("AuditTable"), infoTA, infoWindow, fontSize);
    auditRoundsPanel = new AuditRoundsTable((PreferencesExt) prefs.node("AuditStateTable"), infoTA, infoWindow, fontSize);

    tabbedPane.addTab("Audit", auditPanel);
    tabbedPane.addTab("AuditRounds", auditRoundsPanel);
    tabbedPane.setSelectedIndex(0);
    tabbedPane.addChangeListener(e -> {
      Component c = tabbedPane.getSelectedComponent();
      if (c instanceof AuditTable) {
        ((AuditTable)c).setSelected(this.auditRecordDir);
      } else if (c instanceof AuditRoundsTable) {
        ((AuditRoundsTable)c).setSelected(this.auditRecordDir);
      }
    });

    // choose the audit record
    this.fileChooser = new FileManager(frame, null, null, (PreferencesExt) prefs.node("FileManager"));
    this.auditRecordDirCB = new ComboBox<>((PreferencesExt) prefs.node("auditRecordDirCB"));
    this.auditRecordDirCB.addChangeListener(e -> {
      if (!this.eventOk) {
        return;
      }
      this.auditRecordDir = (String) auditRecordDirCB.getSelectedItem();
      this.eventOk = false;
      this.auditRecordDirCB.addItem(this.auditRecordDir);
      this.eventOk = true;
      int tab = tabbedPane.getSelectedIndex();
      if (tab == 0) auditPanel.setSelected(this.auditRecordDir);
      if (tab == 1) auditRoundsPanel.setSelected(this.auditRecordDir);
    });

    ///////////////////////////////////////////////////////////
    // ButtPanel
    AbstractAction fileAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String dirName = fileChooser.chooseDirectory("");
        if (dirName != null) {
          auditRecordDirCB.setSelectedItem(dirName);
        }
      }
    };
    BAMutil.setActionProperties(fileAction, "FileChooser", "open Local dataset...", false, 'L', -1);
    BAMutil.addActionToContainer(buttPanel, fileAction);

    // Popup info window
    this.infoTA = new TextHistoryPane(true);
    infoTA.setFontSize(fontSize);

    this.infoWindow = new IndependentWindow("Details", BAMutil.getImage("rlauxe-logo.png"), new JScrollPane(infoTA));
    Rectangle bounds = (Rectangle) prefs.getBean(ViewerMain.FRAME_SIZE, new Rectangle(200, 50, 500, 700));
    this.infoWindow.setBounds(bounds);
    AbstractAction infoAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Formatter f = new Formatter();
        showInfo(f);
        infoTA.setFont(infoTA.getFont().deriveFont(fontSize));
        infoTA.setText(f.toString());
        infoWindow.show();
      }
    };
    BAMutil.setActionProperties(infoAction, "Information", "info on Election Record", false, 'I', -1);
    BAMutil.addActionToContainer(buttPanel, infoAction);

    fontu = FontUtil.getStandardFont((int) fontSize);
    AbstractAction incrFontAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        resizeFonts(fontu.incrFontSize().getSize2D());
      }
    };
    BAMutil.setActionProperties(incrFontAction, "FontIncr", "Increase Font Size", false, '+', -1);
    BAMutil.addActionToContainer(buttPanel, incrFontAction);

    AbstractAction decrFontAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        resizeFonts(fontu.decrFontSize().getSize2D());
      }
    };
    BAMutil.setActionProperties(decrFontAction, "FontDecr", "Decrease Font Size", false, '-', -1);
    BAMutil.addActionToContainer(buttPanel, decrFontAction);

    ////////////////////////////////////////////////////////////////

    this.topPanel = new JPanel(new BorderLayout());
    this.topPanel.add(new JLabel("Audit Record Directory:"), BorderLayout.WEST);
    this.topPanel.add(auditRecordDirCB, BorderLayout.CENTER);
    this.topPanel.add(buttPanel, BorderLayout.EAST);
    setLayout(new BorderLayout());
    add(topPanel, BorderLayout.NORTH);
    add(tabbedPane, BorderLayout.CENTER);
  }

  void showInfo(Formatter f) {
    auditPanel.showInfo(f);
  }

  // iterates over the keys stored in UIManager/UIDefaults, and for each key that's a Font,
  // derives a new font with the target point size, , and then puts that key and new font in UIManager.
  // Afterwards, the code calls SwingUtilities.updateComponentTreeUI() on the frame, and then packs the frame.
  // I believe you need to update the UIManager with a FontUIResource, not just a Font.
  void resizeFonts(float fontSize) {
    auditPanel.setFontSize(fontSize);
    auditRoundsPanel.setFontSize(fontSize);
    infoTA.setFontSize(fontSize);
  }

  public void exit() {
    auditPanel.save();
    auditRoundsPanel.save();

    fileChooser.save();
    auditRecordDirCB.save();

    if (infoWindow != null) {
      prefs.putBeanObject(ViewerMain.FRAME_SIZE, infoWindow.getBounds());
    }
    // prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());

    Rectangle bounds = frame.getBounds();
    prefs.putBeanObject(FRAME_SIZE, bounds);
    prefs.putBean(FONT_SIZE, fontu.getFontSize());
    try {
      store.save();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

    done = true; // on some systems, still get a window close event
    System.exit(0);
  }

  ///////////////////////\///////////////////////

  static void showFonts(Formatter f) {
    UIDefaults uid = UIManager.getLookAndFeelDefaults();
    var copyKeys = new HashSet<Object>(uid.keySet());
    for (Object key : copyKeys) { // concurrent modification
      var what = uid.get(key);
      if (what instanceof FontUIResource) {
        f.format("key=%s class=%s what=%s class=%s%n", key, key.getClass(), what, what.getClass());
      }
    }
  }

  // iterates over the keys stored in UIManager/UIDefaults, and for each key that's a Font,
  // derives a new font with the target point size, , and then puts that key and new font in UIManager.
  // Afterwards, the code calls SwingUtilities.updateComponentTreeUI() on the frame, and then packs the frame.
  // I believe you need to update the UIManager with a FontUIResource, not just a Font.
  static void resizeDefaultFonts(float fontSize) {
    UIDefaults uid = UIManager.getLookAndFeelDefaults();
    var copyKeys = new HashSet<Object>(uid.keySet());
    for (Object key : copyKeys) { // concurrent modification
      var what = uid.get(key);
      if (what instanceof FontUIResource) {
        uid.put(key, new FontUIResource(((FontUIResource) what).deriveFont(fontSize)));
      }
    }
  }

  public static void main(String[] args) {

    // prefs storage
    try {
      String prefStore = XMLStore.makeStandardFilename(".rlauxe", "RlauxeViewer.xml");
      store = XMLStore.createFromFile(prefStore, null);
      prefs = store.getPreferences();
      Debug.setStore(prefs.node("Debug"));
    } catch (IOException e) {
      System.out.println("XMLStore Creation failed " + e);
    }

    var fontSize = (Float) prefs.getBean(ViewerMain.FONT_SIZE, 12.0f); // getFloat() ??
    ucar.ui.widget.FontUtil.init();
    resizeDefaultFonts(fontSize);

      // put UI in a JFrame
    // JFrame.setDefaultLookAndFeelDecorated(true);
    frame = new JFrame("Rlauxe Viewer");
    ui = new ViewerMain(prefs, fontSize);

    frame.setIconImage(BAMutil.getImage("rlauxe-logo.png"));
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        if (!done) {
          ui.exit();
        }
      }
    });

    frame.getContentPane().add(ui);
    Rectangle bounds = (Rectangle) prefs.getBean(FRAME_SIZE, new Rectangle(50, 50, 800, 450));
    frame.setBounds(bounds);

    frame.pack();
    frame.setBounds(bounds);
    frame.setVisible(true);
  }
}
