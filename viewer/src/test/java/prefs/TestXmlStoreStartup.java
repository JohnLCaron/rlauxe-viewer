package prefs;

import com.formdev.flatlaf.FlatLightLaf;
import org.cryptobiotic.rlauxe.viewer.ViewerMain;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import ucar.ui.prefs.Debug;
import ucar.ui.widget.FontUtil;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.XMLStore;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.io.IOException;
import java.util.HashSet;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class TestXmlStoreStartup {

    @Test
    public void testPrefsChain() {
        FlatLightLaf.setup();

        try {
            String storeName = "CorlaInputData.xml";
            String prefStore = XMLStore.makeStandardFilename(".rlauxe", storeName);
            XMLStore storedDefaults = XMLStore.createFromResource("/resources/prefs/CorlaInputDataDefaults.xml", null);

            XMLStore store = XMLStore.createFromFile(prefStore, storedDefaults);
            PreferencesExt prefs = store.getPreferences();

            var prefsx = (PreferencesExt) prefs.node("CountyAudit");
            Debug.setStore(prefsx.node("Debug"));

            var fontSize = (Float) prefsx.getBean(ViewerMain.FONT_SIZE, 12.0f);
            FontUtil.init();
            resizeDefaultFonts(fontSize);

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
