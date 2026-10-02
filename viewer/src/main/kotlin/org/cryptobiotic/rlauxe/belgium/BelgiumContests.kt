/*
* Copyright (c) 2026 John L. Caron
* See LICENSE for license information.
*/
package org.cryptobiotic.rlauxe.belgium
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.dhondt.DhondtContest
import org.cryptobiotic.rlauxe.dhondt.RelaxedAssertionsIF
import org.cryptobiotic.rlauxe.viewer.RlauxeViewerMain
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.checkExists
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.viewer.LogsTable
import org.cryptobiotic.rlauxe.viewer.ViewerMain
import ucar.ui.prefs.ComboBox
import ucar.ui.widget.BAMutil
import ucar.ui.widget.FileManager
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*

class BelgiumContests(prefs: PreferencesExt, fontSize: Float) : RlauxeViewerMain(prefs, fontSize) {
    var fileChooser: FileManager
    var auditRecordDirCB: ComboBox<String>
    val statusButton = JButton("status")

    val contestTable: BelgiumContestTable
    val altContestTable: BelgiumAltContestTable
    val logsTable: LogsTable
    var county: String?= null

    private var auditRecordLocation: String? = "none"
    private var auditRecord: CompositeAuditRecord? = null
    private var config: Config? = null
    private var lastAuditRound: AuditRoundIF? = null // may be null

    private val assertTA = TextHistoryPane()
    private val assertWindow  = IndependentWindow("Assertion", BAMutil.getImage("rlauxe-logo.png"), JScrollPane(assertTA))

    init {
        val bounds = prefs.getBean(ViewerMain.INFO_BOUNDS, Rectangle(50, 50, 1000, 700)) as Rectangle
        this.assertWindow.setBounds(bounds)

        contestTable = BelgiumContestTable((prefs.node("BelgiumContestTable") as PreferencesExt), infoTA, infoWindow, fontSize,
            headerLabel, statusButton,
            setCounty = { county: String -> setCountyComponent(county) },
            setAltContest = { relax: RelaxedAssertionsIF, sampleLimit: Int -> setAltContest(relax, sampleLimit) },
        )
        topTabs.addTab("Constituency", contestTable)
        activePanels.add(contestTable)

        altContestTable = BelgiumAltContestTable((prefs.node("BelgiumAltContestTable") as PreferencesExt), infoTA, infoWindow, fontSize,
            headerLabel)
        topTabs.addTab("AltContest", altContestTable)
        activePanels.add(altContestTable)

        logsTable = LogsTable((prefs.node("LogsTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        topTabs.addTab("Logs", logsTable)
        activePanels.add(logsTable)

        // default
        topTabs.setSelectedIndex(0)
        contestTable.getActions(actionsPanel)

        this.rightPanel.add(actionsPanel, BorderLayout.EAST)
        topTabs.addChangeListener {
            val c: Component = topTabs.getSelectedComponent()
            actionsPanel.removeAll()

            // actions on right side of Audit record chooser
            when {
                c is BelgiumContestTable -> c.getActions(actionsPanel)
                else -> {}
            }
            validate()
        }

        ////////////////////////////////////////////
        this.fileChooser = FileManager(frame, "", null, prefs.node("FileManager") as PreferencesExt?)
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

        val infoAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                infoTA.setFont(infoTA.getFont().deriveFont(fontSize))
                infoTA.setText(contestTable.showInfo(county))
                infoWindow.show()
            }
        }
        BAMutil.setActionProperties(infoAction, "Info-icon.png", "info on Election Record", false, 'I'.code, -1)
        BAMutil.addActionToContainer(leftPanel, infoAction)

        val refreshAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                setAuditRecord()
            }
        }
        BAMutil.setActionProperties(refreshAction, "refresh-icon.png", "Reread Audit Record", false, '-'.code, -1)
        BAMutil.addActionToContainer(leftPanel, refreshAction)

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

        rightPanel.add(statusButton, BorderLayout.WEST)

        logger.debug { "BelgiumAuditPanel init" }
    }

    override fun name() = "Belgium d'Hondt Audit"


    fun setAltContest(relax: RelaxedAssertionsIF, sampleLimit: Int) {
        logger.debug { "setAltContest ${relax.altContest().name}" }
        altContestTable.setAltContest(relax, sampleLimit)
        topTabs.setSelectedIndex(1)
    }

    fun setCountyComponent(county: String) {
        logger.debug { "setLogsForContest $county" }
        this.county = county
        logsTable.setContest(county)
        topTabs.setSelectedIndex(2)
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

   
