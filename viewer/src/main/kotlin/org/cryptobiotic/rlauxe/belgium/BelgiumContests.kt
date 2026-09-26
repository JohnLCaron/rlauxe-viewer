/*
* Copyright (c) 2026 John L. Caron
* See LICENSE for license information.
*/
package org.cryptobiotic.rlauxe.belgium
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.corla.CorlaMain
import org.cryptobiotic.rlauxe.dhondt.*
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.checkExists
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.viewer.LogsTable
import org.cryptobiotic.rlauxe.viewer.ViewerMain
import org.cryptobiotic.rlauxe.viewer.ViewerPanelIF
import ucar.ui.prefs.ComboBox
import ucar.ui.widget.BAMutil
import ucar.ui.widget.FileManager
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*

class BelgiumContests(prefs: PreferencesExt, fontSize: Float) : CorlaMain(prefs, fontSize) {
    var fileChooser: FileManager
    var auditRecordDirCB: ComboBox<String>

    val contestTable: BelgiumContestTable
    val logsTable: LogsTable

    private var auditRecordLocation: String? = "none"
    private var auditRecord: CompositeAuditRecord? = null
    private var config: Config? = null
    private var lastAuditRound: AuditRoundIF? = null // may be null

    private val assertTA = TextHistoryPane()
    private val assertWindow  = IndependentWindow("Assertion", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(assertTA))

    init {
        val bounds = prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(50, 50, 1000, 700)) as Rectangle
        this.assertWindow.setBounds(bounds)

        contestTable = BelgiumContestTable((prefs.node("CountyContests") as PreferencesExt), infoTA, infoWindow, fontSize,
            statusLabel)  { setLogsForContest(it) }
        topTabs.addTab("CountyContests", contestTable)
        activePanels.add(contestTable)

        logsTable = LogsTable((prefs.node("LogsTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        topTabs.addTab("Logs", logsTable)
        activePanels.add(logsTable)

        topTabs.setSelectedIndex(0)

        ////////////////////////////////////////////
        this.fileChooser = FileManager(CorlaMain.frame, "", null, prefs.node("FileManager") as PreferencesExt?)
        this.auditRecordDirCB = ComboBox<String>(prefs.node("auditRecordDirCB") as PreferencesExt?)
        this.auditRecordDirCB.addChangeListener {
            if (this.eventOk) {
                val checkAuditDir = auditRecordDirCB.getSelectedItem() as String
                if (checkExists(checkAuditDir)) {
                    this.auditRecordDir = checkAuditDir
                    this.eventOk = false
                    this.auditRecordDirCB.addItem(checkAuditDir)
                    this.eventOk = true
                    setAuditRecord()
                } else {
                    JOptionPane.showMessageDialog(null, String.format("No AuditRecord in %s", checkAuditDir))
                }
            }
        }

        // choose the audit record
        val fileAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val dirName = fileChooser.chooseDirectory()
                if (dirName != null) {
                    auditRecordDirCB.setSelectedItem(dirName)
                }
            }
        }
        BAMutil.setActionProperties(fileAction, "Open-File-Folder-icon.png", "Audit Record chooser...", false, 'L'.code, -1)
        BAMutil.addActionToContainer(leftPanel, fileAction)
        this.leftPanel.add(JLabel("Audit Record: "))
        this.leftPanel.add(auditRecordDirCB)

        ////////////////////////////////////////////////////////////////
        // top layout
        this.topPanel = JPanel(BorderLayout())
        this.topPanel.add(leftPanel, BorderLayout.WEST)
        // this.topPanel.add(auditRecordDirCB, BorderLayout.CENTER)
        this.topPanel.add(rightPanel, BorderLayout.EAST)

        // main layout
        setLayout(BorderLayout())
        add(topPanel, BorderLayout.NORTH)
        add(topTabs, BorderLayout.CENTER)

        logger.debug { "BelgiumAuditPanel init" }
    }


    override fun name() = "Belgium Contests Viewer"

    fun setLogsForContest(contest: String) {
        logger.debug { "setLogsForContest $contest" }
        logsTable.setContest(contest)
        topTabs.setSelectedIndex(1)
    }

    fun setAuditRecord(): Boolean {
        try {
            val auditRecord = read(auditRecordDir)
            if (auditRecord == null) {
                logger.debug{ "read $auditRecordDir failed "}
                return false
            }

            contestTable.setAuditRecord(auditRecordDir)
            logsTable.setAuditRecord(auditRecordDir)
            logger.info{"setAuditRecord to $auditRecordDir"}
            return true

        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) {"setAuditRecord failed"}
        }
        return false
    }

    override fun saveMine() {
        fileChooser.save()
        auditRecordDirCB.save()
        prefs.putBeanObject(ViewerMain.INFO_BOUNDS, assertWindow.getBounds())
    }

    companion object {
        private val logger = KotlinLogging.logger("BelgiumContests")
    }
}

   
