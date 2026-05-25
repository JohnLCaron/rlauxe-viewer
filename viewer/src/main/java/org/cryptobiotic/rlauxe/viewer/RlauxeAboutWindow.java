/*
 * Copyright (c) 1998-2025 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package org.cryptobiotic.rlauxe.viewer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ui.util.Resource;
import ucar.ui.widget.BAMutil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.invoke.MethodHandles;

public class RlauxeAboutWindow extends JWindow {
  static private final Logger logger = LoggerFactory.getLogger(RlauxeAboutWindow.class);
  private static String splashImage = "/resources/ui/icons/rlauxe-logo.png";

  public RlauxeAboutWindow(JFrame parent) {
    super(parent);

    JLabel about = new JLabel(
"""
<html> <body bgcolor="#FFECEC">
<center>
A library for Risk Limiting Audits, based on the published papers of Philip Stark and collaborators,
<br>and on his SHANGRLA framework and related code.
<br>Source available at <a href="https://github.com/JohnLCaron/rlauxe/" title="icons">https://github.com/JohnLCaron/rlauxe/</a>.
<br>
<br>The SHANGRLA Python library is the work of Philip Stark and collaborators, released under the AGPL-3.0 license.
<br>
<br>Rlauxe uses the Raire-Java library for Instant Runoff Voting.
<br>Raire-Java is Copyright 2023-2025 Democracy Developers,
<br>based on software (c) Michelle Blom in C++, released under the GNU General Public License v3.0.
<br>
<br>
<br>Thanks to Kotlin for life after Java. Also thanks to 
<br>Neal McBurnett, Jake Spertus, Philip Stark, Vanessa Teague, and Dan Wallach 
<br>for technical help over the years.
<br>
<br>Icons from <a href="https://www.flaticon.com/" title="icons">www.flaticon.com</a>
<br>
<br>
<br>
</center>
</body></html>""");

    JPanel main = new JPanel(new BorderLayout());
    main.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
    main.setBackground(new Color(0xFFECEC));

    JLabel logo = new JLabel(Resource.getIcon(splashImage, false));
    logo.setBackground(new Color(0xFFECEC));
    logo.setOpaque(true);

    main.add(logo, BorderLayout.NORTH);
    main.add(about, BorderLayout.CENTER);
    getContentPane().add(main);
    pack();

    // show();
    Dimension labelSize = getPreferredSize();

    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice gd = ge.getDefaultScreenDevice();
    GraphicsConfiguration gc = gd.getDefaultConfiguration();

    Point location;

    if (gc != null) {
      Rectangle gcrect = gc.getBounds();
      location = new Point(gcrect.x + gcrect.width / 2 - (labelSize.width / 2), gcrect.y + gcrect.height / 2 - (labelSize.height / 2));
    } else {
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      location = new Point(screenSize.width / 2 - (labelSize.width / 2), screenSize.height / 2 - (labelSize.height / 2));
    }

    setLocation(location);

    // Any click on the window hides it.
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        setVisible(false);
      }
    });
    setVisible(true);
  }
}

/*
A library for Risk Limiting Audits (RLA), based on Philip Stark's SHANGRLA framework and related code. The Rlauxe library is an independent implementation of the SHANGRLA framework, based on the published papers of Stark et al.

The SHANGRLA python library is the work of Philip Stark and collaborators, released under the AGPL-3.0 license.

Rlauxe uses the Raire Java library for Instant runoff Voting (IRV) contests. Raire-Java is Copyright 2023-2025 Democracy Developers. It is based on software (c) Michelle Blom in C++ https://github.com/michelleblom/audit-irv-cp/tree/raire-branch, and released under the GNU General Public License v3.0.
 */
