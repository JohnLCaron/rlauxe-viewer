/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.ui.widget;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;

/**
 * font utilities.
 * Example of use:
 * 
 * <pre>
 * FontUtil.StandardFont fontu = FontUtil.getStandardFont(20);
 * g2.setFont(fontu.getFont());
 * </pre>
 * 
 * @author John Caron
 */
public class FontUtil {
  private static final boolean debug = false;

  private static final int MAX_FONTS = 15;
  private static final int fontType = Font.PLAIN;
  // standard
  private static final Font[] stdFont = new Font[MAX_FONTS]; // list of fonts to use to make text bigger/smaller
  private static final FontMetrics[] stdMetrics = new FontMetrics[MAX_FONTS]; // fontMetric for each font
  // mono
  private static final Font[] monoFont = new Font[MAX_FONTS]; // list of fonts to use to make text bigger/smaller
  private static final FontMetrics[] monoMetrics = new FontMetrics[MAX_FONTS]; // fontMetric for each font

  private static boolean isInit;

  public static void init() {
    if (isInit)
      return;
    initFontFamily("SansSerif", stdFont, stdMetrics);
    initFontFamily("Monospaced", monoFont, monoMetrics);
    isInit = true;
  }

  private static void initFontFamily(String name, Font[] fonts, FontMetrics[] fontMetrics) {
    for (int i = 0; i < MAX_FONTS; i++) {
      int fontSize = i < 6 ? 5 + i : (i < 11 ? 10 + 2 * (i - 5) : 20 + 4 * (i - 10));
      fonts[i] = new Font(name, fontType, fontSize);
      fontMetrics[i] = Toolkit.getDefaultToolkit().getFontMetrics(fonts[i]);

      if (debug)
        System.out.println("TextSymbol font " + fonts[i] + " " + fontSize + " " + fontMetrics[i].getAscent());
    }
  }

  private static float defaultFontSize = 12.0f;
  public static float getStandardFontSize() {
    if (defaultFontSize == 0.0) {
      var font = UIManager.getLookAndFeelDefaults().getFont("defaultFont");
      if (font != null) defaultFontSize = font.getSize2D();
    }
    return defaultFontSize;
  }

  public static FontUtil.StandardFont getStandardFont(float size) {
    init();
    return new StandardFont(stdFont, size);
  }

  public static FontUtil.StandardFont getMonoFont(float size) {
    init();
    return new StandardFont(monoFont, size);
  }

  public static class StandardFont {
    private int currFontNo;
    // private int height;
    private final Font[] fonts;
    // private final FontMetrics[] fontMetrics;

    StandardFont(Font[] fonts, float size) {
      this.fonts = fonts;
      // this.fontMetrics = fontMetrics;
      currFontNo = findClosest(size);
      // height = fontMetrics[currFontNo].getAscent();
    }

    public Font getFont() {
      return fonts[currFontNo];
    }

    public float getFontSize() {
      return getFont().getSize2D();
    }

    /** increment the font size one "increment" */
    public Font incrFontSize() {
      if (currFontNo < MAX_FONTS - 1) {
        currFontNo++;
        //this.height = fontMetrics[currFontNo].getAscent();
      }
      return getFont();
    }

    /** decrement the font size one "increment" */
    public Font decrFontSize() {
      if (currFontNo > 0) {
        currFontNo--;
        //this.height = fontMetrics[currFontNo].getAscent();
      }
      return getFont();
    }

    /* public Dimension getBoundingBox(String s) {
      return new Dimension(fontMetrics[currFontNo].stringWidth(s), height);
    } */

    // finds font closest to size
    private int findClosest(float size) {
      var minDistance = Float.MAX_VALUE;
      for (int i = 0; i < fonts.length; i++) {
        minDistance = Math.min(minDistance, Math.abs(size - fonts[i].getSize2D()));
      }
      for (int i = 0; i < fonts.length; i++) {
        if (Math.abs(size - fonts[i].getSize2D()) == minDistance) {
         return i;
        }
      }
      return 0;
    }

  } // inner class StandardFont
}
