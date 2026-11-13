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
import org.cryptobiotic.rlauxe.viewer.AuditRoundsTable
import org.cryptobiotic.rlauxe.viewer.LogsTable
import org.cryptobiotic.rlauxe.viewer.MvrsTable
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

    val stateContests: CvContests
    val countyTable: CvCounties
    val auditRoundsTable: AuditRoundsTable

    val stateStyles: StyleTable
    val poolTable: PoolTable
    val mvrsTable: MvrsTable
    val logsPanel: LogsTable

    val detailTabs  = JTabbedPane(JTabbedPane.TOP)
    // val countyCvrsTable: CiCvrs
    // val samplingTable: CvCountySampling // this uses CountyAuditRecord and county cvrs. but could just use the ac county subtotals ??
    // val countyStyles: StyleTable
    var countyName: String? = null

    init {
        ////////////////////////////////////////////
        // topTabs
        stateContests = CvContests(prefs.node("StateContests") as PreferencesExt, infoTA, infoWindow, fontSize, headerLabel)
        stateContests.getActions(actionsPanel)
        topTabs.addTab("Contests", stateContests)
        activePanels.add(stateContests)

        countyTable = CvCounties((prefs.node("CountyContests") as PreferencesExt), infoTA, infoWindow, fontSize,
            headerLabel) { county, nmvrs -> countyTable.setCountyNmvrs(county, nmvrs)}
        topTabs.addTab("County Sampling", countyTable)
        activePanels.add(countyTable)

        auditRoundsTable = AuditRoundsTable((prefs.node("AuditRoundsTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        topTabs.addTab("AuditRounds", auditRoundsTable)
        activePanels.add(auditRoundsTable)

        topTabs.addTab("Details", detailTabs)
        topTabs.setSelectedIndex(0)

        ///////////////////////////
        // detail tabs

        stateStyles = StyleTable((prefs.node("Styles") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        detailTabs.addTab("Styles", stateStyles)
        activePanels.add(stateStyles)

        poolTable = PoolTable((prefs.node("PoolTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        detailTabs.addTab("Pools", poolTable)
        activePanels.add(poolTable)

        mvrsTable = MvrsTable((prefs.node("MvrsTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        detailTabs.addTab("Mvrs", mvrsTable)
        activePanels.add(mvrsTable)

        logsPanel = LogsTable((prefs.node("LogsTable") as PreferencesExt?)!!, infoTA, infoWindow, fontSize)
        detailTabs.addTab("Logs", logsPanel)
        activePanels.add(logsPanel)

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

        topTabs.setSelectedIndex(0)
        topTabs.addChangeListener {
            val c: Component = topTabs.getSelectedComponent()
            actionsPanel.removeAll()
            when (c) {
                stateContests -> stateContests.getActions(actionsPanel)
                countyTable -> countyTable.getActions(actionsPanel)
                auditRoundsTable -> auditRoundsTable.getActions(actionsPanel)
                else -> {} // headerLabel.setText("")
            }
            validate()
        }
        /* countyTabs.addChangeListener {
            val c: Component = countyTabs.getSelectedComponent()
            actionsPanel.removeAll()
            when (c) {
                samplingTable -> samplingTable.getActions(actionsPanel)
                else -> {}
            }
            if (countyName != null) headerLabel.setText("$countyName County")
            validate()
        } */
        this.rightPanel.add(actionsPanel, BorderLayout.EAST)

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

            for (vpanel in activePanels) {
                if (vpanel is AuditRecordViewerIF) vpanel.setAuditRecord(auditRecord)
                else if (vpanel is ViewerPanelIF) vpanel.setAuditRecordLocation(auditRecordDir)
            }
            logger.info{"ColoradoAuditViewer.setAuditRecord to $auditRecordDir"}
            return true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, e.message)
            logger.error(e) {"ColoradoAuditViewer.setAuditRecord failed"}
        }
        return false
    }

    /*
    fun sampleCounty(county: String, sample: Boolean) {
        if (!sample) {
            setCounty(county)
        } else {
            samplingTable.setCounty(county)
            samplingTable.resample()
        }
    }

    fun setCounty(county: String) {
        samplingTable.setCounty(county)
        countyStyles.setCounty(county)

        topTabs.setSelectedComponent(countyTabs)
        headerLabel.setText("$county County")
        this.countyName = county
    } */

    fun showInfo() = stateContests.showInfo()

    companion object {
        private val logger = KotlinLogging.logger("ColoradoAuditViewer")
    }

}
