/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.verify.VerifyContests
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
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.IOException
import java.util.*
import javax.swing.*
import javax.swing.plaf.FontUIResource

/** ElectionRecord Viewer main program.  */
class ColoradoInputMain(prefs: PreferencesExt, fontSize: Float) : JPanel() {
    private var aboutWindow: RlauxeAboutWindow? = null

    private val leftPanel = JPanel()
    private val rightPanel = JPanel()
    private val actionsPanel = JPanel()
    private val fontu: StandardFont

    // var fileChooser: FileManager
    var infoTA: TextHistoryPane
    var infoWindow: IndependentWindow
    // var auditRecordDirCB: ComboBox<String>
    var topPanel: JPanel
    var inputLabel: JButton = JButton("")

    var eventOk: Boolean = true
    var auditRecordDir: String = "none"

    var topTabs = JTabbedPane(JTabbedPane.TOP)
    var countyCvrTabs  = JTabbedPane(JTabbedPane.TOP)
    var activePanels = mutableListOf<SubPanelIF>()

    var corlaInputPanel: ColoradoInputTable
    var countyTabPanel: ColoradoCounties
    var mvrComparisonPanel: MvrComparisonTable

    var countyCvrsTable: CountyCvrsTable
    var countyRedactionTable: CountyRedactionTable
    var countySchemaTable: CountySchemaTable
    var countyMvrTable: CountyMvrTable

    var currentInput: ColoradoInput? = null
    var currentCountyInput: CorlaCountyInput? = null

    init {
        fontu = FontUtil.getStandardFont(fontSize)

        // Popup info window
        this.infoTA = TextHistoryPane(true)
        infoTA.setFontSize(fontSize)

        this.infoWindow = IndependentWindow("Details", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(infoTA))
        val bounds = prefs.getBean(INFO_BOUNDS, Rectangle(50, 50, 1000, 700)) as Rectangle
        // Rectangle bounds = new Rectangle(50, 50, 1000, 700);
        this.infoWindow.setBounds(bounds)

        ////////////////////////////////////////////
        // topTabs
        corlaInputPanel = ColoradoInputTable((prefs.node("ColoradoInputTable") as PreferencesExt),
            infoTA, infoWindow, fontSize) { setInput(it) }
        corlaInputPanel.getActions(actionsPanel)
        topTabs.addTab("Colorado Input", corlaInputPanel)
        activePanels.add(corlaInputPanel)

        countyTabPanel = ColoradoCounties((prefs.node("ColoradoCounties") as PreferencesExt),
            infoTA, infoWindow, fontSize) { setCountyInput(it) }
        topTabs.addTab("Counties", countyTabPanel)
        activePanels.add(countyTabPanel)

        mvrComparisonPanel = MvrComparisonTable((prefs.node("mvrComparisonPanel") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        topTabs.addTab("Mvr Comparisons", mvrComparisonPanel)
        activePanels.add(mvrComparisonPanel)

        topTabs.addTab("CountyCvrs", countyCvrTabs);
        topTabs.setSelectedIndex(0)

        // countyCvrs
        countyCvrsTable = CountyCvrsTable((prefs.node("countyCvrsTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("Cvrs", countyCvrsTable)
        activePanels.add(countyCvrsTable)

        countyRedactionTable = CountyRedactionTable((prefs.node("countyRedactionTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("Redactions", countyRedactionTable)
        activePanels.add(countyRedactionTable)

        countySchemaTable = CountySchemaTable((prefs.node("countySchemaTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("CountySchema", countySchemaTable)
        activePanels.add(countySchemaTable)

        countyMvrTable = CountyMvrTable((prefs.node("countyMvrTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("CountyMvrs", countyMvrTable)
        activePanels.add(countyMvrTable)

        // TODO put into seperate thread
        val verifyAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val verifier = VerifyContests(auditRecordDir, false)
                infoTA.setText(verifier.verify().toString())
                infoWindow!!.show()
            }
        }
        // Verify-icon.png
        BAMutil.setActionProperties(verifyAction, "Verify-icon.png", "Verify Audit Record", false, 'V'.code, -1)
        BAMutil.addActionToContainer(leftPanel, verifyAction)

        val infoAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                infoTA.setFont(infoTA.getFont().deriveFont(fontSize))
                infoTA.setText(showInfo())
                infoWindow!!.show()
            }
        }
        BAMutil.setActionProperties(infoAction, "Info-icon.png", "info on Election Record", false, 'I'.code, -1)
        BAMutil.addActionToContainer(leftPanel, infoAction)

        val refreshAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                // setAuditRecord()
            }
        }
        BAMutil.setActionProperties(refreshAction, "refresh-icon.png", "Reread Audit Record", false, '-'.code, -1)
        BAMutil.addActionToContainer(leftPanel, refreshAction)

        this.rightPanel.add(actionsPanel, BorderLayout.EAST)

        /////////////////////////////////////////////////////////////////////
        val mb = makeMenuBar()
        frame!!.setJMenuBar(mb)

        ////////////////////////////////////////////////////////////////
        // top layout
        this.topPanel = JPanel(BorderLayout())
        this.topPanel.add(leftPanel, BorderLayout.WEST)
        this.topPanel.add(inputLabel, BorderLayout.CENTER)
        this.topPanel.add(rightPanel, BorderLayout.EAST)

        // main layout
        setLayout(BorderLayout())
        add(topPanel, BorderLayout.NORTH)
        add(topTabs, BorderLayout.CENTER)

        logger.debug{"ColoradoInputMain started"}
    }

    fun setInput(input: ColoradoInput) {
        currentInput = input
        countyTabPanel.setColoradoInput(input)
        mvrComparisonPanel.setColoradoInput(input)
        inputLabel.setText(input.name)
        logger.info{"ViewerMain.setAuditRecord to $auditRecordDir"}
    }

    fun setCountyInput(countyInput: CorlaCountyInput) {
        currentCountyInput = countyInput

        countyCvrsTable.setCountyInput(countyInput)
        countySchemaTable.setCorlaInput(countyInput.countyName, currentInput!!, countyCvrsTable.corlaCvrs)
        countyRedactionTable.setCorlaCvrs(countyCvrsTable.corlaCvrs)
        countyMvrTable.setColoradoInput(currentInput!!, countyInput.countyName)

        topTabs.setSelectedComponent(countyCvrTabs)
        countyCvrTabs.setSelectedComponent(countyCvrsTable)
        inputLabel.setText("${currentInput!!.name} county=${countyInput.countyName}")
    }

    fun showInfo() = buildString {
        if (currentCountyInput != null) {
            val cc = currentCountyInput!!
            appendLine("electionName = ${cc.electionName}")
            appendLine("countyName = ${cc.countyName}")
            appendLine("manifestSource = ${cc.manifestSource}")
            appendLine("cvrsSource = ${cc.cvrsSource}")
            appendLine()
            val corlaCvrs = countyCvrsTable.corlaCvrs!!
            appendLine("         cvrs = ${nfn(corlaCvrs.cvrs().size, 6)}")
            val nredactedCvrs = corlaCvrs.redaction().nredactedCvrs()
            appendLine("redacted cvrs = ${nfn(nredactedCvrs, 6)}")
            appendLine("   total cvrs = ${nfn(corlaCvrs.cvrs().size + nredactedCvrs, 6)}")
            appendLine()
            appendLine("# contests = ${corlaCvrs.schema.contests.size}")
            appendLine("# redactedGroups = ${corlaCvrs.redaction().groups().size}")
            appendLine("# cardStyles = ${corlaCvrs.cardStyles().size}")
            val sumCardStyles = corlaCvrs.cardStyles().sumOf { it.countCards}
            appendLine("sum cardStyles.count = ${sumCardStyles}")

            appendLine()
            appendLine("cvr file election name = ${corlaCvrs.electionName}")
            appendLine("cvr file version = ${corlaCvrs.versionName}")

        }
    }

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

        for (vpanel in activePanels) {
            vpanel.saveState()
        }

        //fileChooser.save()
        //auditRecordDirCB.save()

        if (infoWindow != null) {
            prefs!!.putBeanObject(INFO_BOUNDS, infoWindow!!.getBounds())
        }

        val bounds: Rectangle = frame!!.getBounds()
        prefs!!.putBeanObject(FRAME_SIZE, bounds)
        prefs!!.putBean(FONT_SIZE, fontu.getFontSize()) // TODO not working ??

        try {
            store!!.save()
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            logger.error(ioe){"store.save() failed"}
        }
    }

    fun exit(save: Boolean) {
        logger.info { "------------- ColoradoInputMain exiting ----------------------" }
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
        BAMutil.setActionProperties(
            incrFontAction,
            "format-font-size-increase-icon.png",
            "Increase Font Size",
            false,
            '+'.code,
            -1
        )
        BAMutil.addActionToMenu(sysMenu, incrFontAction)

        val decrFontAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                resizeFonts(fontu.decrFontSize().getSize2D())
            }
        }
        BAMutil.setActionProperties(
            decrFontAction,
            "format-font-size-decrease-icon.png",
            "Decrease Font Size",
            false,
            '-'.code,
            -1
        )
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
        private val logger = KotlinLogging.logger("ColoradoInputMain")

        const val FRAME_SIZE: String = "FrameSize"
        const val INFO_BOUNDS: String = "InfoBounds"
        const val FONT_SIZE: String = "FontSize"

        private var frame: JFrame? = null
        private var prefs: PreferencesExt? = null
        private var store: XMLStore? = null
        private var ui: ColoradoInputMain? = null

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
            logger.info{"------------- ColoradoInputMain starting ----------------------"}

            // prefs storage
            try {
                val storeName = "CorlaInputData.xml"

                val prefStore = XMLStore.makeStandardFilename(".rlauxe", storeName)
                val storedDefaults = XMLStore.createFromResource("/resources/prefs/CorlaInputDataDefaults.xml", null)

                store = XMLStore.createFromFile(prefStore, storedDefaults)
                prefs = store!!.getPreferences()
                Debug.setStore(prefs!!.node("Debug"))
            } catch (e: IOException) {
                logger.error(e) {"ColoradoInputMain store.create() failed"}
            }

            val fontSize = prefs!!.getBean(FONT_SIZE, 12.0f) as Float // getFloat() ??
            FontUtil.init()
            resizeDefaultFonts(fontSize)

            // put UI in a JFrame
            // JFrame.setDefaultLookAndFeelDecorated(true);
            frame = JFrame("Colorado Input Data")
            ui = ColoradoInputMain(prefs!!, fontSize)

            frame!!.setIconImage(BAMutil.getImage("rlauxe-logo.png"))
            frame!!.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    ui!!.exit(true)
                }
            })

            frame!!.getContentPane().add(ui)
            val bounds = prefs!!.getBean(FRAME_SIZE, Rectangle(50, 50, 800, 450)) as Rectangle
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
