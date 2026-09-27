package prefs;

import com.formdev.flatlaf.FlatLightLaf;
import org.cryptobiotic.rlauxe.viewer.ViewerMain;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import ucar.ui.prefs.BeanTable;
import ucar.ui.prefs.Debug;
import ucar.ui.widget.FontUtil;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.XMLStore;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.IOException;
import java.util.HashSet;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class TestXmlStoreStartup {

    @Test
    public void testPrefsChain() {
        FlatLightLaf.setup();

        try {
            String storeName = "BelgiumContests.xml";
            String prefStore = XMLStore.makeStandardFilename(".rlauxe", storeName);
            // XMLStore storedDefaults = XMLStore.createFromResource("/resources/prefs/BelgiumContestsDefaults.xml", null);

            XMLStore store = XMLStore.createFromFile(prefStore, null);
            PreferencesExt prefs = store.getPreferences();

            var prefsx = (PreferencesExt) prefs.node("WhatTheHecIsaNode");
            Debug.setStore(prefsx.node("Debug"));

            var fontSize = (Float) prefsx.getBean(ViewerMain.FONT_SIZE, 12.0f);
            FontUtil.init();
            resizeDefaultFonts(fontSize);
            prefs.putBean(ViewerMain.FONT_SIZE, 21.0);

            BeanTable<String> logsTable = new BeanTable(String.class, (PreferencesExt) prefsx.node("BeanTable"), false);
            logsTable.saveState(false);

            //     val beanClass: Class<T>,
            //    val store: PreferencesExt,
            //    val canAddDelete: Boolean,
            //    val header: String,
            //    val tooltip: String,
            //    val innerbean: T? = null,
            org.cryptobiotic.rlauxe.beans.BeanTable<String> klogsTable = new org.cryptobiotic.rlauxe.beans.BeanTable<String>(String.class, (PreferencesExt) prefsx.node("KBeanTable"), false,
                    "header", "tool", null);
            klogsTable.saveState(false);

            store.save();

        } catch (IOException e) {
            System.out.println("XMLStore Creation failed " + e);
        }
    }


    static void resizeDefaultFonts(float fontSize) {
        UIDefaults uid = UIManager.getLookAndFeelDefaults();
        var copyKeys = new HashSet<>(uid.keySet());
        for (Object key : copyKeys) { // concurrent modification
            var what = uid.get(key);
            if (what instanceof FontUIResource) {
                uid.put(key, new FontUIResource(((FontUIResource) what).deriveFont(fontSize)));
            }
        }
    }
}
