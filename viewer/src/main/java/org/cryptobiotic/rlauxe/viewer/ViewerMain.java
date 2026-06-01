/*
 * Copyright (c) 2025 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.cryptobiotic.rlauxe.persist.AuditRecord;
import org.slf4j.Logger;
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
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashSet;

import org.slf4j.LoggerFactory;

/** ElectionRecord Viewer main program. */
public class ViewerMain extends JPanel {
  static private final Logger logger = LoggerFactory.getLogger(ViewerMain.class);

  public static final String FRAME_SIZE = "FrameSize";
  public static final String INFO_BOUNDS = "InfoBounds";
  public static final String FONT_SIZE = "FontSize";

  private static JFrame frame;
  private static PreferencesExt prefs;
  private static XMLStore store;
  private static ViewerMain ui;
  private RlauxeAboutWindow aboutWindow;

  private final JPanel leftPanel = new JPanel();
  private final JPanel rightPanel = new JPanel();
  private final JPanel actionsPanel = new JPanel();
  private final FontUtil.StandardFont fontu;

  TextHistoryPane infoTA;
  IndependentWindow infoWindow;
  ComboBox<String> auditRecordDirCB;
  JPanel topPanel;
  JButton statusButton = new JButton("status");

  MvrAction mvrAction = new MvrAction();
  FileManager fileChooser;
  boolean eventOk = true;

  String auditRecordDir = "none";

  JTabbedPane tabbedPane;
  private BelgiumAuditPanel belgiumPanel;
  private ContestsPanel contestsPanel;
  private AuditRoundsTable auditRoundsPanel = null;
  private PoolTable poolPanel;
  private StyleTable stylePanel;
  private CardTable cardPanel;
  private MvrTable mvrPanel;
  private ContestPoolsTable contestPoolPanel;
  private CorlaAuditPanel corlaPanel = null;

  java.util.ArrayList<ViewerPanelIF> activePanels = new ArrayList<ViewerPanelIF>();

  public ViewerMain(PreferencesExt prefs, float fontSize, ViewerProfile profile, String datadir) {
    fontu = FontUtil.getStandardFont(fontSize);

    // Popup info window
    this.infoTA = new TextHistoryPane(true);
    infoTA.setFontSize(fontSize);

    this.infoWindow = new IndependentWindow("Details", BAMutil.getImage("rlauxe-logo.png"), new JScrollPane(infoTA));
    Rectangle bounds = (Rectangle) prefs.getBean(ViewerMain.INFO_BOUNDS, new Rectangle(50, 50, 1000, 700));
    // Rectangle bounds = new Rectangle(50, 50, 1000, 700);
    this.infoWindow.setBounds(bounds);

    ////////////////////////////////////////////
    // the tabbed panels
    tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    if (profile.isBelgium()) {

      belgiumPanel = new BelgiumAuditPanel((PreferencesExt) prefs.node("BelgiumAuditTable"), infoTA, infoWindow, fontSize, statusButton, profile);
      belgiumPanel.getActions(actionsPanel);
      tabbedPane.addTab("BelgiumAudit", belgiumPanel);
      activePanels.add(belgiumPanel);

    } else if (profile.isCorla()) {
      logger.debug("start CorlaAuditPanel");

      corlaPanel = new CorlaAuditPanel((PreferencesExt) prefs.node("CorlaAuditTable"), infoTA, infoWindow, fontSize);
      corlaPanel.getActions(actionsPanel);
      tabbedPane.addTab("CorlaAudit", corlaPanel);
      activePanels.add(corlaPanel);
      logger.debug("end CorlaAuditPanel");

    } else {
        contestsPanel = new ContestsPanel((PreferencesExt) prefs.node("AuditTable"), infoTA, infoWindow, fontSize, profile);
        tabbedPane.addTab("Contests", contestsPanel);
        activePanels.add(contestsPanel);

        auditRoundsPanel = new AuditRoundsTable((PreferencesExt) prefs.node("AuditStateTable"), infoTA, infoWindow, fontSize, profile, mvrAction);
        tabbedPane.addTab("AuditRounds", auditRoundsPanel);
        activePanels.add(auditRoundsPanel);

        stylePanel = new StyleTable((PreferencesExt) prefs.node("Styles"), infoTA, infoWindow, fontSize);
        tabbedPane.addTab("Styles", stylePanel);
        activePanels.add(stylePanel);

        poolPanel = new PoolTable((PreferencesExt) prefs.node("PoolTable"), infoTA, infoWindow, fontSize);
        tabbedPane.addTab("Pools", poolPanel);
        activePanels.add(poolPanel);

        contestPoolPanel = new ContestPoolsTable((PreferencesExt) prefs.node("ContestPoolTable"), infoTA, infoWindow, fontSize);
        tabbedPane.addTab("ContestPools", contestPoolPanel);
        activePanels.add(contestPoolPanel);

        cardPanel = new CardTable((PreferencesExt) prefs.node("CardTable"), infoTA, infoWindow, fontSize);
        tabbedPane.addTab("Cards", cardPanel);
        activePanels.add(cardPanel);

        mvrPanel = new MvrTable((PreferencesExt) prefs.node("MvrTable"), fontSize);
        tabbedPane.addTab("Mvrs", mvrPanel);
        activePanels.add(mvrPanel);
    }

    tabbedPane.setSelectedIndex(0);

    tabbedPane.addChangeListener(e -> {
      Component c = tabbedPane.getSelectedComponent();
      if (this.auditRecordDir.equals("none")) return;

      if (c instanceof CardTable cardTable) {
          cardTable.setSelectedTab();

      } else if (c instanceof MvrTable mvrTable) {
         mvrTable.setSelectedTab();

      } else if (c instanceof CountyPanel counties) {
        counties.setSelectedTab();
      }

      // actions on right side
      actionsPanel.removeAll();
      if (c instanceof BelgiumAuditPanel belgium) {
        belgium.getActions(actionsPanel);
      } else if (c instanceof CorlaAuditPanel corla) {
        corla.getActions(actionsPanel);
      } else if (c instanceof ContestsPanel contests) {
        contests.getActions(actionsPanel);
      } else if (c instanceof AuditRoundsTable auditRound) {
        auditRound.getActions(actionsPanel, contestsPanel);
      } else if (c instanceof CountyPanel counties) {
        counties.getActions(actionsPanel);
      }
      validate();
    });

    ////// layout, left to right

    //// buttons to the left of auditRecordDir ComboBox

    // TODO put into seperate thread
    AbstractAction verifyAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        var verifier = new org.cryptobiotic.rlauxe.verify.VerifyContests(auditRecordDir, true);
        infoTA.setText(verifier.verify().toString());
        infoWindow.show();
      }
    };
    // Verify-icon.png
    BAMutil.setActionProperties(verifyAction, "Verify-icon.png", "Verify Audit Record", false, 'V', -1);
    BAMutil.addActionToContainer(leftPanel, verifyAction);

    AbstractAction infoAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Formatter f = new Formatter();
        showInfo(f);
        infoTA.setFont(infoTA.getFont().deriveFont(fontSize));
        infoTA.setText(f.toString());
        infoWindow.show();
      }
    };
    BAMutil.setActionProperties(infoAction, "Info-icon.png", "info on Election Record", false, 'I', -1);
    BAMutil.addActionToContainer(leftPanel, infoAction);

    AbstractAction refreshAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) { setAuditRecord(); }
    };
    BAMutil.setActionProperties(refreshAction, "refresh-icon.png", "Reread Audit Record", false, '-', -1);
    BAMutil.addActionToContainer(leftPanel, refreshAction);

    // choose the audit record
    AbstractAction fileAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String dirName = fileChooser.chooseDirectory();
        if (dirName != null) {
            auditRecordDirCB.setSelectedItem(dirName);
        }
      }
    };
    BAMutil.setActionProperties(fileAction, "Open-File-Folder-icon.png", "Audit Record chooser...", false, 'L', -1);
    BAMutil.addActionToContainer(leftPanel, fileAction);
    this.leftPanel.add(new JLabel("Audit Record: "), BorderLayout.WEST);

    // the right Panel
    if (profile.isBelgium()) {
      this.rightPanel.add(statusButton, BorderLayout.WEST);
    }
    this.rightPanel.add(actionsPanel, BorderLayout.EAST);


    ////////////////////////////////////////////

    this.fileChooser = new FileManager(frame, datadir, null, (PreferencesExt) prefs.node("FileManager"));
    this.auditRecordDirCB = new ComboBox<>((PreferencesExt) prefs.node("auditRecordDirCB"));
    this.auditRecordDirCB.addChangeListener(e -> {
      if (!this.eventOk) {
        return;
      }
      var checkAuditDir = (String) auditRecordDirCB.getSelectedItem();
      if (AuditRecord.Companion.checkExists(checkAuditDir)) {
        this.auditRecordDir = checkAuditDir;
        this.eventOk = false;
        this.auditRecordDirCB.addItem(checkAuditDir);
        this.eventOk = true;
        setAuditRecord();
      } else {
        JOptionPane.showMessageDialog(null, String.format("No AuditRecord in %s", checkAuditDir));
      }
    });

    /////////////////////////////////////////////////////////////////////
    JMenuBar mb = makeMenuBar();
    frame.setJMenuBar(mb);

    ////////////////////////////////////////////////////////////////
    // top layout
    this.topPanel = new JPanel(new BorderLayout());
    this.topPanel.add(leftPanel, BorderLayout.WEST);
    this.topPanel.add(auditRecordDirCB, BorderLayout.CENTER);
    this.topPanel.add(rightPanel, BorderLayout.EAST);

    // main layout
    setLayout(new BorderLayout());
    add(topPanel, BorderLayout.NORTH);
    add(tabbedPane, BorderLayout.CENTER);

    logger.debug("ViewerMain started");
  }

  void showInfo(Formatter f) {
    if (belgiumPanel != null) belgiumPanel.showInfo(f);
    else if (corlaPanel != null) corlaPanel.showInfo(f);
    else contestsPanel.showInfo(f);
  }

  // iterates over the keys stored in UIManager/UIDefaults, and for each key that's a Font,
  // derives a new font with the target point size, , and then puts that key and new font in UIManager.
  // Afterwards, the code calls SwingUtilities.updateComponentTreeUI() on the frame, and then packs the frame.
  // I believe you need to update the UIManager with a FontUIResource, not just a Font.
  void resizeFonts(float fontSize) {
    for (var vpanel : activePanels) {
      vpanel.setFontSize(fontSize);
    }
    infoTA.setFontSize(fontSize);
  }

  void setAuditRecord() {
    for (var vpanel : activePanels) {
      vpanel.setAuditRecord(auditRecordDir);
    }
  }

  public void save() {
    logger.debug("save");

    for (var vpanel : activePanels) {
      vpanel.saveState();
    }

    fileChooser.save();
    auditRecordDirCB.save();

    if (infoWindow != null) {
      prefs.putBeanObject(ViewerMain.INFO_BOUNDS, infoWindow.getBounds());
    }

    Rectangle bounds = frame.getBounds();
    prefs.putBeanObject(FRAME_SIZE, bounds);
    prefs.putBean(FONT_SIZE, fontu.getFontSize());

    try {
      store.save();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      logger.error("ViewerMain store.save() failed", ioe);
    }
  }

  public void exit() {
    logger.info("------------- ViewerMain exiting ----------------------");
    save();
    System.exit(0);
  }

  private JMenuBar makeMenuBar() {
    JMenuBar mb = new JMenuBar();
    JMenu sysMenu = new JMenu("System Menu");
    mb.add(sysMenu);

    //// TODO move to pulldown menu or something
    // font resizing
    AbstractAction incrFontAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        resizeFonts( fontu.incrFontSize().getSize2D());
      }
    };
    BAMutil.setActionProperties(incrFontAction, "format-font-size-increase-icon.png", "Increase Font Size", false, '+', -1);
    BAMutil.addActionToMenu(sysMenu, incrFontAction);

    AbstractAction decrFontAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        resizeFonts(fontu.decrFontSize().getSize2D());
      }
    };
    BAMutil.setActionProperties(decrFontAction, "format-font-size-decrease-icon.png", "Decrease Font Size", false, '-', -1);
    BAMutil.addActionToMenu(sysMenu, decrFontAction);

    AbstractAction saveAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui.save();
      }
    };
    BAMutil.setActionProperties(saveAction, "Save", "Save Preferences to Disk", false, 'S', -1);
    BAMutil.addActionToMenu(sysMenu, saveAction);

    AbstractAction aboutAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent evt) {
        if (aboutWindow == null) {
          JFrame parentFrame = (JFrame) ui.getTopLevelAncestor();
          aboutWindow = new RlauxeAboutWindow(parentFrame);
        }
        aboutWindow.setVisible(true);
      }
    };
    BAMutil.setActionProperties(aboutAction, null, "About", false, 'A', 0);
    BAMutil.addActionToMenu(sysMenu, aboutAction);

    AbstractAction exitAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui.exit();
      }
    };
    BAMutil.setActionProperties(exitAction, "Exit", "Exit Viewer", false, 'X', -1);
    BAMutil.addActionToMenu(sysMenu, exitAction);

    return mb;
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

  public class MvrAction extends AbstractAction {
    public int roundIdx;
    public void actionPerformed(ActionEvent e) {
      mvrPanel.setAuditRecord(auditRecordDir, roundIdx);
      tabbedPane.setSelectedIndex(4);
    }
  }

  public enum ViewerProfile {
    BelgiumViewer, CorlaViewer, RlauxeViewer;

    boolean isBelgium() { return this == BelgiumViewer; }
    boolean isCorla() { return this == CorlaViewer; }
  }

  public static void main(String[] args) {
      logger.info("------------- ViewerMain starting ----------------------");

    ViewerProfile profile = ViewerProfile.RlauxeViewer;
    String datadir = null;
    for (int idx=0; idx < args.length; idx++) {
      String arg = args[idx];
      if (arg.equals("-corlaAudit")) profile = ViewerProfile.CorlaViewer;
      if (arg.equals("-belgiumAudit")) profile = ViewerProfile.BelgiumViewer;
      if (arg.equals("-datadir")) datadir = args[idx+1];
    }

    // prefs storage
    try {
      String storeName = profile.isCorla() ? "CorlaViewer.xml" : profile.isBelgium() ? "BelgiumViewer.xml" : "RlauxeViewer.xml";
      String prefStore = XMLStore.makeStandardFilename(".rlauxe", storeName);
      XMLStore storedDefaults = profile.isBelgium() ? XMLStore.createFromResource("/resources/prefs/BelgiumViewerDefaults.xml", null) : null;

      store = XMLStore.createFromFile(prefStore, storedDefaults);
      prefs = store.getPreferences();
      Debug.setStore(prefs.node("Debug"));
    } catch (IOException e) {
      logger.error("ViewerMain store.create() failed", e);
    }

    var fontSize = (Float) prefs.getBean(ViewerMain.FONT_SIZE, 12.0f); // getFloat() ??
    ucar.ui.widget.FontUtil.init();
    resizeDefaultFonts(fontSize);

    // put UI in a JFrame
    // JFrame.setDefaultLookAndFeelDecorated(true);
    frame = new JFrame(profile.toString());
    ui = new ViewerMain(prefs, fontSize, profile, datadir);

    frame.setIconImage(BAMutil.getImage("rlauxe-logo.png"));
    frame.addWindowListener(new WindowAdapter() {
      /* @Override
      public void windowActivated(WindowEvent e) {
        RlauxeSplashScreen.getSharedInstance().setVisible(false);
      } */

      @Override
      public void windowClosing(WindowEvent e) {
        ui.exit();
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
