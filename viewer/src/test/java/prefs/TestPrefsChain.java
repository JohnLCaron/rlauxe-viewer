package prefs;

import org.cryptobiotic.rlauxe.viewer.ViewerMain;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.util.prefs.PreferencesExt;
import ucar.util.prefs.XMLStore;

import java.io.IOException;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class TestPrefsChain {
    static private final Logger logger = LoggerFactory.getLogger(TestPrefsChain.class);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testPrefsChain() {
        try {
            String storeName = "BelgiumViewer.xml";
            String prefStore = XMLStore.makeStandardFilename(tempFolder.toString(), storeName);
            XMLStore storedDefaults = XMLStore.createFromResource("/resources/prefs/BelgiumViewerDefaults.xml", null);

            XMLStore store = XMLStore.createFromFile(prefStore, storedDefaults);
            PreferencesExt prefs = store.getPreferences();

            // from the default
            var fontSize = prefs.getBean(ViewerMain.FONT_SIZE, 12.0f);
            assertThat(fontSize).isEqualTo(28.0f);

            prefs.putBean(ViewerMain.FONT_SIZE, 30.0f);
            var fontSize2 = prefs.getBean(ViewerMain.FONT_SIZE, 12.0f);
            assertThat(fontSize2).isEqualTo(30.0f); // sensitive to FLoat or Double

        } catch (IOException e) {
            System.out.println("XMLStore Creation failed " + e);
            logger.error("ViewerMain store.create() failed", e);
        }
    }

    @Test
    public void testPrefsChainStore() {
        try {
            String storeName = "BelgiumViewer.xml";
            String prefStore = XMLStore.makeStandardFilename(".rlauxeTest", storeName);
            XMLStore storedDefaults = XMLStore.createFromResource("/resources/prefs/BelgiumViewerDefaults.xml", null);

            XMLStore store = XMLStore.createFromFile(prefStore, storedDefaults);
            PreferencesExt prefs = store.getPreferences();

            // from the default
            var random = new Random();
            var randomThing = "randomThing" + random.nextInt();
            var fontSize = prefs.getBean(randomThing, 12.0f);
            assertThat(fontSize).isEqualTo(12.0f);

            prefs.putBean(randomThing, 30.0f);
            var fontSize2 = prefs.getBean(randomThing, 12.0f);
            assertThat(fontSize2).isEqualTo(30.0f); // sensitive to FLoat or Double

            try {
                store.save();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                logger.error("ViewerMain store.save() failed", ioe);
            }

            String prefStore2 = XMLStore.makeStandardFilename(".rlauxeTest", storeName);
            XMLStore storedDefaults2 = XMLStore.createFromResource("/resources/prefs/BelgiumViewerDefaults.xml", null);
            XMLStore store2 = XMLStore.createFromFile(prefStore2, storedDefaults2);
            PreferencesExt prefs2 = store2.getPreferences();

            var fontSize3 = prefs2.getBean(randomThing, 12.0f);
            assertThat(fontSize3).isEqualTo(30.0f);

        } catch (IOException e) {
            System.out.println("XMLStore Creation failed " + e);
            logger.error("ViewerMain store.create() failed", e);
        }
    }

}
