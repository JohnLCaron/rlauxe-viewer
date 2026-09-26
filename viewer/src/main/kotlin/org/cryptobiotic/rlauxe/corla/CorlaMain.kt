/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import com.formdev.flatlaf.FlatLightLaf
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.belgium.BelgiumContests
import org.cryptobiotic.rlauxe.viewer.RlauxeAboutWindow

import ucar.ui.prefs.Debug
import ucar.ui.widget.BAMutil
import ucar.ui.widget.FontUtil
import ucar.ui.widget.FontUtil.StandardFont
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import ucar.util.prefs.XMLStore
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.IOException
import java.util.*
import javax.swing.*
import javax.swing.plaf.FontUIResource

/** ElectionRecord Viewer main program.  */
abstract class CorlaMain(val prefs: PreferencesExt, fontSize: Float) : JPanel() {
    var aboutWindow: RlauxeAboutWindow? = null

    val leftPanel = JPanel()
    val rightPanel = JPanel()
    val actionsPanel = JPanel()
    val fontu: StandardFont

    var infoTA: TextHistoryPane
    var infoWindow: IndependentWindow
    var topPanel: JPanel

    var statusLabel = JLabel("")

    var eventOk: Boolean = true
    var auditRecordDir: String = "none"

    var topTabs = JTabbedPane(JTabbedPane.TOP)
    var countyCvrTabs  = JTabbedPane(JTabbedPane.TOP)
    var activePanels = mutableListOf<SubPanelIF>()

    init {
        fontu = FontUtil.getStandardFont(fontSize)
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, fontSize + 5.0f));
        statusLabel.setHorizontalAlignment(JLabel.CENTER); // Centers text inside the label's area

        // Popup info window
        this.infoTA = TextHistoryPane(true)
        infoTA.setFontSize(fontSize)

        this.infoWindow = IndependentWindow("${name()} Details", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(infoTA))
        val bounds = prefs.getBean(INFO_BOUNDS, Rectangle(50, 50, 1000, 700)) as Rectangle
        this.infoWindow.setBounds(bounds)

        /////////////////////////////////////////////////////////////////////
        val mb = makeMenuBar()
        frame!!.setJMenuBar(mb)

        ////////////////////////////////////////////////////////////////
        // top layout
        this.topPanel = JPanel(BorderLayout())
        this.topPanel.add(leftPanel, BorderLayout.WEST)
        this.topPanel.add(statusLabel, BorderLayout.CENTER)
        this.topPanel.add(rightPanel, BorderLayout.EAST)

        // main layout
        setLayout(BorderLayout())
        add(topPanel, BorderLayout.NORTH)
        add(topTabs, BorderLayout.CENTER)

        logger.debug{"CorlaMain started"}
    }

    abstract fun name(): String
    abstract fun saveMine()

    // iterates over the keys stored in UIManager/UIDefaults, and for each key that's a Font,
    // derives a new font with the target point size, , and then puts that key and new font in UIManager.
    // Afterwards, the code calls SwingUtilities.updateComponentTreeUI() on the frame, and then packs the frame.
    // I believe you need to update the UIManager with a FontUIResource, not just a Font.
    fun resizeFonts(fontSize: Float) {
        for (vpanel in activePanels) {
            vpanel.setFontSize(fontSize)
        }
        infoTA.setFontSize(fontSize)
    }

    fun save() {
        logger.debug{"save"}
        saveMine()

        for (vpanel in activePanels) {
            vpanel.saveState()
        }

        if (infoWindow != null) {
            prefs.putBeanObject(INFO_BOUNDS, infoWindow.getBounds())
        }

        val bounds: Rectangle = frame!!.getBounds()
        prefs.putBeanObject(FRAME_SIZE, bounds)
        prefs.putBean(FONT_SIZE, fontu.getFontSize()) // TODO not working ??

        try {
            store!!.save()
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            logger.error(ioe){ "store.save() failed" }
        }
    }

    fun exit(save: Boolean) {
        logger.info { "------------- CorlaMain ${name()} exiting ----------------------" }
        if (save) save()
        System.exit(0)
    }

    // common with viewer
    private fun makeMenuBar(): JMenuBar {
        val mb = JMenuBar()
        val sysMenu = JMenu("System Menu")
        mb.add(sysMenu)

        //// TODO move to pulldown menu or something
        // font resizing
        val incrFontAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                resizeFonts(fontu.incrFontSize().getSize2D())
            }
        }
        BAMutil.setActionProperties(incrFontAction, "format-font-size-increase-icon.png", "Increase Font Size", false, '+'.code, -1)
        BAMutil.addActionToMenu(sysMenu, incrFontAction)

        val decrFontAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                resizeFonts(fontu.decrFontSize().getSize2D())
            }
        }
        BAMutil.setActionProperties(decrFontAction, "format-font-size-decrease-icon.png", "Decrease Font Size", false, '-'.code, -1)
        BAMutil.addActionToMenu(sysMenu, decrFontAction)

        val saveAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                Companion.ui!!.save()
            }
        }
        BAMutil.setActionProperties(saveAction, "Save", "Save Preferences to Disk", false, 'S'.code, -1)
        BAMutil.addActionToMenu(sysMenu, saveAction)

        val aboutAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(evt: ActionEvent) {
                if (aboutWindow == null) {
                    val parentFrame = Companion.ui!!.getTopLevelAncestor() as JFrame?
                    aboutWindow = RlauxeAboutWindow(parentFrame)
                }
                aboutWindow!!.setVisible(true)
            }
        }
        BAMutil.setActionProperties(aboutAction, null, "About", false, 'A'.code, 0)
        BAMutil.addActionToMenu(sysMenu, aboutAction)

        val exitAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                Companion.ui!!.exit(true)
            }
        }
        BAMutil.setActionProperties(exitAction, "Exit", "Exit Viewer", false, 'X'.code, -1)
        BAMutil.addActionToMenu(sysMenu, exitAction)

        val exitNoSaveAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                Companion.ui!!.exit(false)
            }
        }
        BAMutil.setActionProperties(exitNoSaveAction, "Exit", "Exit Viewer NO Save", false, 'X'.code, -1)
        BAMutil.addActionToMenu(sysMenu, exitNoSaveAction)

        return mb
    }

    companion object {
        private val logger = KotlinLogging.logger("CorlaMain")

        const val FRAME_SIZE: String = "FrameSize"
        const val INFO_BOUNDS: String = "InfoBounds"
        const val FONT_SIZE: String = "FontSize"

        var frame: JFrame? = null
        private var store: XMLStore? = null
        private var ui: CorlaMain? = null

        ///////////////////////\///////////////////////
         // iterates over the keys stored in UIManager/UIDefaults, and for each key that's a Font,
        // derives a new font with the target point size, , and then puts that key and new font in UIManager.
        // Afterwards, the code calls SwingUtilities.updateComponentTreeUI() on the frame, and then packs the frame.
        // I believe you need to update the UIManager with a FontUIResource, not just a Font.
        fun resizeDefaultFonts(fontSize: Float) {
            val uid = UIManager.getLookAndFeelDefaults()
            val copyKeys = HashSet<Any?>(uid.keys)
            for (key in copyKeys) { // concurrent modification
                val what = uid.get(key)
                if (what is FontUIResource) {
                    uid.put(key, FontUIResource(what.deriveFont(fontSize)))
                }
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            logger.info{"------------- CorlaMain starting ----------------------"}
            FlatLightLaf.setup();
            UIManager.put( "TabbedPane.showTabSeparators", true );
            UIManager.put( "TabbedPane.selectedBackground", Color.white );

            var type = "CountyAudit"
            var datadir = ""
            for (idx in args.indices) {
                val arg = args[idx]
                if (arg == "-datadir") datadir = args[idx + 1]
                if (arg == "-CountyAudit") type = "CountyAudit"
                if (arg == "-ColoradoInput") type = "ColoradoInput"
                if (arg == "-BelgiumContests") type = "BelgiumContests"
            }

            var preffs: PreferencesExt? = null
            try {
                val storeName = "CorlaInputData.xml"

                val prefStore = XMLStore.makeStandardFilename(".rlauxe", storeName)
                val storedDefaults = XMLStore.createFromResource("/resources/prefs/CorlaInputDataDefaults.xml", null)

                store = XMLStore.createFromFile(prefStore, storedDefaults)
                preffs = store!!.getPreferences()

            } catch (e: IOException) {
                logger.error(e) {"CorlaMain store.create() failed"}
                return
            }
            val prefsx = when (type) {
                "ColoradoInput" -> preffs!!.node("ColoradoInput") as PreferencesExt
                "CountyAudit" -> preffs!!.node("CountyAudit") as PreferencesExt
                "BelgiumContests" -> preffs!!.node("BelgiumContests") as PreferencesExt
                else -> throw RuntimeException()
            }

            Debug.setStore(prefsx.node("Debug"))
            val fontSize = prefsx.getBean(FONT_SIZE, 12.0f) as Float
            FontUtil.init()
            resizeDefaultFonts(fontSize)

            // put UI in a JFrame
            // JFrame.setDefaultLookAndFeelDecorated(true);
            frame = JFrame(type)
            ui = when (type) {
                "ColoradoInput" -> ColoradoInput(prefsx, fontSize)
                "CountyAudit" -> CountyAudit(prefsx, fontSize)
                "BelgiumContests" -> BelgiumContests(prefsx, fontSize)
                else -> throw RuntimeException()
            }

            frame!!.setIconImage(BAMutil.getImage("rlauxe-logo.png"))
            frame!!.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    ui!!.exit(true)
                }
            })

            frame!!.getContentPane().add(ui)
            val bounds = prefsx.getBean(FRAME_SIZE, Rectangle(50, 50, 800, 450)) as Rectangle
            frame!!.setBounds(bounds)

            frame!!.pack()
            frame!!.setBounds(bounds)
            frame!!.setVisible(true)
        }
    }
}

interface SubPanelIF {
    fun setFontSize(size: Float)
    fun saveState()
}
