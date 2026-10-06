/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.checkExists
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.verify.VerifyContests
import org.cryptobiotic.rlauxe.viewer.AuditRecordViewerIF
import org.cryptobiotic.rlauxe.viewer.CorlaContestsTable
import org.cryptobiotic.rlauxe.viewer.PoolTable
import org.cryptobiotic.rlauxe.viewer.RlauxeViewerMain
import org.cryptobiotic.rlauxe.viewer.StyleTable
import org.cryptobiotic.rlauxe.viewer.ViewerPanelIF
import ucar.ui.prefs.ComboBox
import ucar.ui.widget.BAMutil
import ucar.ui.widget.FileManager
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JTabbedPane

class ColoradoAuditViewer(prefs: PreferencesExt, fontSize: Float) : RlauxeViewerMain(prefs, fontSize) {
    val fileChooser: FileManager
    val auditRecordDirCB: ComboBox<String>

    val stateTable: StateContests
    val countyTable: CountyContests
    val stateStyles: StyleTable
    val poolTable: PoolTable

    val countyTabs  = JTabbedPane(JTabbedPane.TOP)
    // val countyCvrsTable: CountyCvrsTable
    val samplingTable: CountySampling // this uses CountyAuditRecord and county cvrs. but could just use the ac county subtotals ??
    val countyStyles: StyleTable

    init {
        ////////////////////////////////////////////
        // topTabs
        stateTable = StateContests(prefs.node("StateContests") as PreferencesExt, infoTA, infoWindow, fontSize);
        stateTable.getActions(actionsPanel);
        topTabs.addTab("Contests", stateTable);
        activePanels.add(stateTable);

        countyTable = CountyContests((prefs.node("CountyContests") as PreferencesExt), infoTA, infoWindow, fontSize) {
            setCounty(it)
        }
        topTabs.addTab("Counties", countyTable)
        activePanels.add(countyTable)

        stateStyles = StyleTable((prefs.node("Styles") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        topTabs.addTab("All Styles", stateStyles)
        activePanels.add(stateStyles)

        poolTable = PoolTable((prefs.node("PoolTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        topTabs.addTab("All Pools", poolTable)
        activePanels.add(poolTable)

        topTabs.addTab("CountyCvrs", countyTabs);
        topTabs.setSelectedIndex(0)

        ///////////////////////////
        // county tabs
        /* countyCvrsTable = CountyCvrsTable((prefs.node("countyCvrsTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyTabs.addTab("Cvrs", countyCvrsTable)
        activePanels.add(countyCvrsTable) */

        samplingTable = CountySampling((prefs.node("CountySampling") as PreferencesExt), infoTA, infoWindow, fontSize)
        countyTabs.addTab("County Sampling", samplingTable)
        activePanels.add(samplingTable)

        countyStyles = StyleTable((prefs.node("Styles") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        countyTabs.addTab("County Styles", countyStyles)
        activePanels.add(countyStyles)

        // TODO push up?; put into seperate thread
        val verifyAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val verifier = VerifyContests(auditRecordDir, false)
                infoTA.setText(verifier.verify().toString())
                infoWindow.show()
            }
        }
        BAMutil.setActionProperties(verifyAction, "Verify-icon.png", "Verify Audit Record", false, 'V'.code, -1)
        BAMutil.addActionToContainer(leftPanel, verifyAction)

        // TODO push up
        val infoAction: AbstractAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                infoTA.setFont(infoTA.getFont().deriveFont(fontSize))
                infoTA.setText(showInfo())
                infoWindow.show()
            }
        }
        BAMutil.setActionProperties(infoAction, "Info-icon.png", "info on Election Record", false, 'I'.code, -1)
        BAMutil.addActionToContainer(leftPanel, infoAction)

        // TODO push up ??
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
                c is CorlaContestsTable -> c.getActions(actionsPanel)
                c is CountyContests -> c.getActions(actionsPanel)
                c is CountySampling -> c.getActions(actionsPanel)
                else -> {}
            }
            validate()
        }

        ////////////////////////////////////////////
        this.fileChooser = FileManager(RlauxeViewerMain.frame, "", null, prefs.node("FileManager") as PreferencesExt?)
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

        logger.debug{"ColoradoAuditViewer started"}
    }

    override fun name() = "Colorado Audit Viewer"
    override fun saveMine() {
        fileChooser.save()
        auditRecordDirCB.save()
        logger.debug{"saveMine"}
    }

    // why dont you share AuditRecord
    fun setAuditRecord(): Boolean {
        try {
            val auditRecord = read(auditRecordDir)
            if (auditRecord == null) {
                logger.debug{ "read record failed "}
                return false
            }

            countyTable.setAuditRecordLocation(auditRecordDir)
            for (vpanel in activePanels) {
                if (vpanel is ViewerPanelIF) vpanel.setAuditRecordLocation(auditRecordDir)
                if (vpanel is AuditRecordViewerIF) vpanel.setAuditRecord(auditRecord)
            }
            logger.info{"ColoradoAuditViewer.setAuditRecord to $auditRecordDir"}
            return true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) {"ColoradoAuditViewer.setAuditRecord failed"}
        }
        return false
    }

    fun setCounty(county: String) {
        samplingTable.setCounty(county)
        countyStyles.setCounty(county)

        topTabs.setSelectedComponent(countyTabs)
        headerLabel.setText("$county County")
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
        private val logger = KotlinLogging.logger("ColoradoAuditViewer")
    }

}
