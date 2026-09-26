/*
 * Copyright (c) 2026 John L. Caron
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.beans.BeanTable
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.read
import org.cryptobiotic.rlauxe.persist.AuditRecordIF
import org.cryptobiotic.rlauxe.persist.Publisher
import ucar.ui.widget.IndependentWindow
import ucar.ui.widget.TextHistoryPane
import ucar.util.prefs.PreferencesExt
import java.awt.BorderLayout
import java.io.BufferedReader
import java.io.File
import javax.swing.JPanel
import javax.swing.JSplitPane

class LogsTable(
    val prefs: PreferencesExt,
    val infoTA: TextHistoryPane,
    val infoWindow: IndependentWindow,
    fontSize: Float,
) : JPanel(), ViewerPanelIF {
    
    private val logsTable: BeanTable<LogBean>
    var localInfo: TextHistoryPane = TextHistoryPane()

    private val split1: JSplitPane
    private var auditRecordLocation: String? = "none"
    var auditRecord: AuditRecordIF? = null

    init {
        logsTable = BeanTable(
            LogBean::class.java, prefs.node("logsTable") as PreferencesExt, false,
            "Logs", "Logs", null
        )
        logsTable.addListSelectionListener {
            val cardBean = logsTable.getSelectedBean()
            if (cardBean != null) setSelectedCard(cardBean)
        }

        //cardTable.addPopupOption("Show Population", cardTable.makeShowAction(localInfo,
        //    bean -> ((cardTable) bean).show()));
        setFontSize(fontSize)

        // layout of tables
        split1 = JSplitPane(JSplitPane.VERTICAL_SPLIT, false, logsTable, localInfo)
        split1.setDividerLocation(prefs.getInt("splitPos1", 200))

        setLayout(BorderLayout())
        add(split1, BorderLayout.CENTER)

        logger.debug { "logsTable init" }
    }

    override fun setFontSize(size: Float) {
        logsTable.setFontSize(size)
        localInfo.setFontSize(size)
    }

    override fun setAuditRecord(auditRecordLocation: String): Boolean {
        logger.debug { "LogsTable setAuditRecord $auditRecordLocation" }
        logsTable.setBeans(null)

        this.auditRecordLocation = auditRecordLocation
        /* val auditRecordTry = read(auditRecordLocation)
        if (auditRecordTry == null) {
            logger.info { "LogsTable failed on readFrom $auditRecordLocation" }
            return false
        }
        this.auditRecord = auditRecordTry
        return loadLogs(null) */
        return true
    }

    fun setContest(contest: String) {
        loadLogs(contest)
    }

    fun loadLogs(contest: String?): Boolean {
        logsTable.setBeans(null)

        val topdir = "${auditRecordLocation}/$contest"
        logger.debug { "read logs from $topdir" }

        val logsFile = Publisher(topdir).logsFile()
        println(logsFile)

        val logsBeans = mutableListOf<LogBean>()
        try {
            val reader: BufferedReader = File(logsFile).bufferedReader()
            reader.readLine() // skip header line

            var lastBean: LogBean? = null
            while (true) {
                val line = reader.readLine()
                if (line == null) break

                if (isContinuation(line)) {
                    if (lastBean != null) lastBean.addContinuation(line)
                } else {
                    lastBean = LogBean(line)
                    logsBeans.add(lastBean)
                }
            }
            reader.close()
        } catch (e: Exception) {
            logger.error(e) {"LogsTable exception"}
        }

        logsTable.setBeans(logsBeans)
        return true
    }

    // is this a msg continuation line ??
    fun isContinuation(line: String): Boolean {
        val tokens = line.split(" ".toRegex()).filter{ it.isNotEmpty() }
        if (tokens.size < 2) return true
        val level = tokens[1].trim()
        return !levels.contains(level)
    }

    fun setSelectedCard(bean: LogBean) {
        localInfo.setText(bean.show())
        localInfo.gotoTop()
    }

    override fun saveState() {
        logsTable.saveState(false)

        prefs.putInt("splitPos1", split1.getDividerLocation())
    }

    class LogBean(logLine: String) {
        var date: String = ""
        var level: String = ""
        var loggerName: String = ""
        var msg: String = ""

        init {
            try {
                // 2026-08-26T09:12:39 WARN  CountyPoolsSimCvrs: makeCardPoolsFromCountyStyles has (contestNc-sum) 6 > 5
                val tokens = logLine.split(" ".toRegex()).filter{ it.isNotEmpty() }
                if (tokens.size > 0) date = tokens[0]
                if (tokens.size > 1) level = tokens[1]
                if (tokens.size > 2) loggerName = tokens[2]
                if (tokens.size > 3) {
                    val remaining = logLine.indexOf(loggerName) + loggerName.length + 1
                    msg = logLine.substring(remaining).trim()
                }

            } catch (ex: Exception) {
                logger.error(ex) { logLine }
            }
        }

        fun addContinuation(contMsg: String) {
            msg += "\n"
            msg += contMsg
        }

        fun show() = buildString {
            appendLine("date = $date")
            appendLine("level = $level")
            appendLine("loggerName = $loggerName")
            appendLine("msg = $msg")
        }

    }

    companion object {
        private val logger = KotlinLogging.logger("BelgiumContestsTable")
        val levels = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
    }

}