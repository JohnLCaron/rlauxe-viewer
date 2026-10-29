/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */
package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.verify.VerifyContests
import org.cryptobiotic.rlauxe.viewer.RlauxeViewerMain
import ucar.ui.widget.BAMutil
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

class ColoradoInput(prefs: PreferencesExt, fontSize: Float) : RlauxeViewerMain(prefs, fontSize) {
    var corlaInputPanel: ColoradoInputTable
    var countiesTab: CiCounties
    var mvrSamplePanel: MvrSampleTable
    var mvrComparisonPanel: MvrComparisonTable

    var countyCvrTabs  = JTabbedPane(JTabbedPane.TOP)
    var countyCvrsTable: CiCvrs
    var countyStylesTable: CiStyles
    var countyRedactionTable: CiRedactions
    var countySchemaTable: CiSchema
    var countyMvrTable: MvrSampleTable

    var currentStateInput: StatewideInput? = null
    var currentCountyInput: CountyInput? = null

    init {
        ////////////////////////////////////////////
        // topTabs
        corlaInputPanel = ColoradoInputTable((prefs.node("ColoradoInputTable") as PreferencesExt),
            infoTA, infoWindow, fontSize) { setInput(it) }
        corlaInputPanel.getActions(actionsPanel)
        topTabs.addTab("Auditcenter Input", corlaInputPanel)
        activePanels.add(corlaInputPanel)

        countiesTab = CiCounties((prefs.node("CiCounties") as PreferencesExt),
            infoTA, infoWindow, fontSize, { setCountyInput(it)}, { setCounty(it)})
        topTabs.addTab("CiCounties", countiesTab)
        activePanels.add(countiesTab)

        mvrSamplePanel = MvrSampleTable((prefs.node("mvrSamplePanel") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        topTabs.addTab("Mvr Samples", mvrSamplePanel)
        activePanels.add(mvrSamplePanel)

        mvrComparisonPanel = MvrComparisonTable((prefs.node("mvrComparisonPanel") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        topTabs.addTab("Mvr Comparisons", mvrComparisonPanel)
        activePanels.add(mvrComparisonPanel)

        topTabs.addTab("CountyCvrs", countyCvrTabs);
        topTabs.setSelectedIndex(0)

        // countyCvrs
        countyCvrsTable = CiCvrs((prefs.node("countyCvrsTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("Cvrs", countyCvrsTable)
        activePanels.add(countyCvrsTable)

        countyStylesTable = CiStyles((prefs.node("cyStyles") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("Styles", countyStylesTable)
        activePanels.add(countyStylesTable)

        countyRedactionTable = CiRedactions((prefs.node("countyRedactionTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("Redactions", countyRedactionTable)
        activePanels.add(countyRedactionTable)

        countySchemaTable = CiSchema((prefs.node("countySchemaTable") as PreferencesExt),
            infoTA, infoWindow, fontSize)
        countyCvrTabs.addTab("CountySchema", countySchemaTable)
        activePanels.add(countySchemaTable)

        countyMvrTable = MvrSampleTable((prefs.node("countyMvrTable") as PreferencesExt),
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
        headerLabel.setText("headerLabel from ${name()}")

        logger.debug{"ColoradoInput started"}
    }

    override fun name() = "Colorado Input Data"
    override fun saveMine() {
        logger.debug{"saveMine"}
    }

    fun setInput(input: StatewideInput) {
        currentStateInput = input
        countiesTab.setColoradoInput(input)
        mvrSamplePanel.setColoradoInput(input, null)
        mvrComparisonPanel.setColoradoInput(input)
        headerLabel.setText(input.name())
        logger.info{"ColoradoInput.setAuditRecord to $auditRecordDir"}
    }

    fun setCountyInput(countyInput: CountyInput) {
        currentCountyInput = countyInput

        countyCvrsTable.setCountyInput(countyInput)
        countySchemaTable.setCorlaInput(countyInput, currentStateInput!!, countyCvrsTable.corlaCvrs)
        countyRedactionTable.setCorlaCvrs(countyCvrsTable.corlaCvrs)
        countyMvrTable.setColoradoInput(currentStateInput!!, countyInput.countyName)

        topTabs.setSelectedComponent(countyCvrTabs)
        countyCvrTabs.setSelectedComponent(countyCvrsTable)
        headerLabel.setText("${currentStateInput!!.name()} : ${countyInput.countyName} County")
    }

    fun setCounty(countyName: String) {
        mvrSamplePanel.setColoradoInput(currentStateInput!!, countyName)
        mvrComparisonPanel.setColoradoInput(currentStateInput!!, countyName)
        headerLabel.setText("${currentStateInput!!.name()} : ${countyName} County")
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
            appendLine("         cvrs = ${nfn(corlaCvrs.nrows(), 6)}")
            val nredactedCvrs = corlaCvrs.redaction().nredactedCvrs()
            appendLine("redacted cvrs = ${nfn(nredactedCvrs, 6)}")
            appendLine("   total cvrs = ${nfn(corlaCvrs.nrows() + nredactedCvrs, 6)}")
            appendLine()
            appendLine("# contests = ${corlaCvrs.schema.contests.size}")
            appendLine("# redactedGroups = ${corlaCvrs.redaction().groups().size}")
            appendLine("# cardStyles = ${corlaCvrs.cardStyles().size}")
            val sumCardStyles = corlaCvrs.cardStyles().sumOf { it.ncards }
            appendLine("sum cardStyles.count = ${sumCardStyles}")

            appendLine()
            appendLine("cvr file election name = ${corlaCvrs.electionName}")
            appendLine("cvr file version = ${corlaCvrs.versionName}")

        }
    }

    companion object {
        private val logger = KotlinLogging.logger("ColoradoInput")
    }

}
