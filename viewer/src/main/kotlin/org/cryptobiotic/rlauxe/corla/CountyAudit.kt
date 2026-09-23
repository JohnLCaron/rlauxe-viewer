/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.checkExists
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.verify.VerifyContests
import org.cryptobiotic.rlauxe.viewer.CorlaContestsTable
import org.cryptobiotic.rlauxe.viewer.RlauxeContestsTable
import org.cryptobiotic.rlauxe.viewer.SamplingTable
import org.cryptobiotic.rlauxe.viewer.ViewerPanelIF
import ucar.ui.prefs.ComboBox
import ucar.ui.widget.BAMutil
import ucar.ui.widget.FileManager
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel

/** County oriented auditing  */
class CountyAudit(prefs: PreferencesExt, fontSize: Float) : CorlaMain(prefs, fontSize) {
    var fileChooser: FileManager
    var auditRecordDirCB: ComboBox<String>

    var contestTable: CountySampling
    // var samplingTable: SamplingTable

    init {
        ////////////////////////////////////////////
        // topTabs
        /* corlaInputPanel = ColoradoInputTable((prefs.node("ColoradoInputTable") as PreferencesExt),
            infoTA, infoWindow, fontSize) { setInput(it) }
        corlaInputPanel.getActions(actionsPanel)
        topTabs.addTab("Colorado Input", corlaInputPanel)
        activePanels.add(corlaInputPanel) */

        contestTable =
            CountySampling((prefs.node("CountySampling") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        contestTable.getActions(actionsPanel)
        topTabs.addTab("CountySampling", contestTable)
        activePanels.add(contestTable)

        /* samplingTable =
            SamplingTable((prefs.node("SamplingTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        samplingTable.getActions(actionsPanel)
        topTabs.addTab("Sampling", samplingTable)
        activePanels.add(samplingTable) */

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
        this.leftPanel.add(JLabel("Audit Record: "), BorderLayout.WEST)

        ////////////////////////////////////////////////////////////////
        // top layout
        this.topPanel = JPanel(BorderLayout())
        this.topPanel.add(leftPanel, BorderLayout.WEST)
        this.topPanel.add(auditRecordDirCB, BorderLayout.CENTER)
        this.topPanel.add(rightPanel, BorderLayout.EAST)

        // main layout
        setLayout(BorderLayout())
        add(topPanel, BorderLayout.NORTH)
        add(topTabs, BorderLayout.CENTER)

        logger.debug{"ColoradoInput started"}
    }

    override fun name() = "Colorado County Auditor"
    override fun saveMine() {
        fileChooser.save()
        auditRecordDirCB.save()
    }

    fun setAuditRecord(): Boolean {
        try {
            val auditRecord = read(auditRecordDir)
            if (auditRecord == null) return false
            contestTable.setAuditRecord(auditRecordDir)
            for (vpanel in activePanels) {
                if (vpanel is ViewerPanelIF) vpanel.setAuditRecord(auditRecordDir)
            }
            logger.info{"CountyAudit.setAuditRecord to $auditRecordDir"}
            return true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) {"CountyAudit.setAuditRecord failed"}
        }
        return false
    }

    fun showInfo() = buildString {
        /* if (currentCountyInput != null) {
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

        } */
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyAudit")
    }

}
