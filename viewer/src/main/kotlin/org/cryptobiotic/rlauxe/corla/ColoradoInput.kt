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
import ucar.ui.widget.BAMutil
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

// obsolete
class ColoradoInput(prefs: PreferencesExt, fontSize: Float) : CorlaMain(prefs, fontSize) {

    var corlaInputPanel: ColoradoInputTable
    var countyTabPanel: Counties
    var mvrComparisonPanel: MvrComparisonTable

    var countyCvrsTable: CountyCvrsTable
    var countyRedactionTable: CountyRedactionTable
    var countySchemaTable: CountySchemaTable
    var countyMvrTable: CountyMvrTable

    var currentInput: ColoradoInput? = null
    var currentCountyInput: CorlaCountyInput? = null

    init {
        ////////////////////////////////////////////
        // topTabs
        corlaInputPanel = ColoradoInputTable((prefs.node("ColoradoInputTable") as PreferencesExt),
            infoTA, infoWindow, fontSize) { setInput(it) }
        corlaInputPanel.getActions(actionsPanel)
        topTabs.addTab("Colorado Input", corlaInputPanel)
        activePanels.add(corlaInputPanel)

        countyTabPanel = Counties((prefs.node("Counties") as PreferencesExt),
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

        // this.leftPanel.add(inputLabel)

        this.rightPanel.add(actionsPanel, BorderLayout.EAST)

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

        logger.debug{"ColoradoInput started"}
    }

    override fun name() = "Colorado Input Data"
    override fun saveMine() {
        logger.debug{"saveMine"}
    }

    fun setInput(input: ColoradoInput) {
        currentInput = input
        countyTabPanel.setColoradoInput(input)
        mvrComparisonPanel.setColoradoInput(input)
        statusLabel.setText(input.name)
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
        statusLabel.setText("${currentInput!!.name} county ${countyInput.countyName}")
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

    companion object {
        private val logger = KotlinLogging.logger("ColoradoInput")
    }

}
