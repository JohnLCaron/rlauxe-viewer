/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.viewer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.corla.CountyContests
import org.cryptobiotic.rlauxe.corla.CountySampling

import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.checkExists
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.verify.VerifyContests

import ucar.ui.prefs.ComboBox
import ucar.ui.widget.*
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.*

/** County oriented auditing  */
class RlauxeViewer(prefs: PreferencesExt, fontSize: Float) : RlauxeViewerMain(prefs, fontSize) {
    var fileChooser: FileManager
    var auditRecordDirCB: ComboBox<String>

    val contestTable: RlauxeContestsTable
    val stylePanel: StyleTable
    val poolPanel: PoolTable
    val cardPanel: CardTable
    val auditRoundsPanel: AuditRoundsTable
    val mvrsTable: MvrsTable
    val logsPanel: LogsTable

init {
    contestTable = RlauxeContestsTable((prefs.node("AuditTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
    contestTable.getActions(actionsPanel)
    topTabs.addTab("Contests", contestTable)
    activePanels.add(contestTable)

    poolPanel = PoolTable((prefs.node("PoolTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
    topTabs.addTab("Pools", poolPanel)
    activePanels.add(poolPanel)

    stylePanel = StyleTable((prefs.node("Styles") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
    topTabs.addTab("Styles", stylePanel)
    activePanels.add(stylePanel)

    cardPanel = CardTable((prefs.node("CardTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
    topTabs.addTab("Cards", cardPanel)
    activePanels.add(cardPanel)

    auditRoundsPanel = AuditRoundsTable((prefs.node("AuditStateTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize,
        null)
    topTabs.addTab("AuditRounds", auditRoundsPanel)
    activePanels.add(auditRoundsPanel)

    mvrsTable = MvrsTable((prefs.node("MvrsTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
    topTabs.addTab("Mvrs", mvrsTable)
    activePanels.add(mvrsTable)
    
    logsPanel = LogsTable((prefs.node("LogsTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
    topTabs.addTab("Logs", logsPanel)
    activePanels.add(logsPanel)

    topTabs.setSelectedIndex(0)

    topTabs.addChangeListener {
        val c: Component = topTabs.getSelectedComponent()
        actionsPanel.removeAll()

        // actions on right side of Audit record chooser
        when (c) {
            contestTable -> this@RlauxeViewer.contestTable.getActions(actionsPanel)
            auditRoundsPanel -> auditRoundsPanel.getActions(actionsPanel)
            else -> {}
        }
        validate()
    }


    // TODO put into seperate thread
        val verifyAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val verifier = VerifyContests(auditRecordDir, false)
                infoTA.setText(verifier.verify().toString())
                infoWindow.show()
            }
        }
        // Verify-icon.png
        BAMutil.setActionProperties(verifyAction, "Verify-icon.png", "Verify Audit Record", false, 'V'.code, -1)
        BAMutil.addActionToContainer(leftPanel, verifyAction)

        val infoAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                infoTA.setFont(infoTA.getFont().deriveFont(fontSize))
                infoTA.setText(showInfo())
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

        this.rightPanel.add(actionsPanel, BorderLayout.EAST)
        topTabs.addChangeListener {
            val c: Component = topTabs.getSelectedComponent()
            actionsPanel.removeAll()

            // actions on right side of Audit record chooser
            when {
                c is CountyContests -> c.getActions(actionsPanel)
                c is CountySampling -> c.getActions(actionsPanel)
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

        logger.debug{"RlauxeViewer started"}
    }

    override fun name() = "Rlauxe Viewer"


    fun setAuditRecord(): Boolean {
        try {
            val auditRecord = read(auditRecordDir)
            if (auditRecord == null) {
                logger.debug{ "read record failed "}
                return false
            }

            contestTable.setAuditRecord(auditRecordDir)
            for (vpanel in activePanels) {
                if (vpanel is ViewerPanelIF) vpanel.setAuditRecord(auditRecordDir)
            }
            logger.info{"RlauxeViewer.setAuditRecord to $auditRecordDir"}
            return true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) {"RlauxeViewer.setAuditRecord failed"}
        }
        return false
    }

    override fun saveMine() {
        fileChooser.save()
        auditRecordDirCB.save()
        logger.debug{"saveMine"}
    }

    fun showInfo() = buildString {
        /* if (currentCountyInput != null) {
            val cc = currentCountyInput
            appendLine("electionName = ${cc.electionName}")
            appendLine("countyName = ${cc.countyName}")
            appendLine("manifestSource = ${cc.manifestSource}")
            appendLine("cvrsSource = ${cc.cvrsSource}")
            appendLine()
            val corlaCvrs = countyCvrsTable.corlaCvrs
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

        } */
    }

    companion object {
        private val logger = KotlinLogging.logger("RlauxeViewer")
    }
}
