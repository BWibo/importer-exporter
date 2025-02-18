/*
 * 3D City Database - The Open Source CityGML Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2013 - 2021
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.lrg.tum.de/gis/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * Virtual City Systems, Berlin <https://vc.systems/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Taufkirchen <http://www.moss.de/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.citydb.gui.console;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

public class ConsoleTextPane extends JTextPane {
    private boolean lineWrap = false;

    public ConsoleTextPane() {
        DefaultCaret caret = (DefaultCaret) getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        if (!lineWrap)
            return getUI().getPreferredSize(this).width <= getParent().getSize().width;
        else
            return super.getScrollableTracksViewportWidth();
    };

    @Override
    public Dimension getPreferredSize() {
        if (!lineWrap)
            return getUI().getPreferredSize(this);
        else
            return super.getPreferredSize();
    };

    public void setLineWrap(boolean lineWrap) {
        this.lineWrap = lineWrap;
    }

}
