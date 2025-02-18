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

import org.citydb.config.Config;
import org.citydb.config.gui.window.WindowSize;
import org.citydb.config.i18n.Language;
import org.citydb.gui.ImpExpGui;
import org.citydb.gui.util.GuiUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.Dialog.ModalExclusionType;

public class ConsoleWindow extends JFrame {
	private final Component content;
	private final Config config;
	private final ImpExpGui topFrame;

	public ConsoleWindow(Component content, Config config, ImpExpGui topFrame) {
		this.content = content;
		this.config = config;
		this.topFrame = topFrame;

		init();
		doTranslation();
		loadSettings();
	}

	private void init() {
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);
		setIconImage(Toolkit.getDefaultToolkit().getImage(ConsoleWindow.class.getResource("/org/citydb/gui/logos/logo_small.png")));
		setLayout(new GridBagLayout());
	}

	public void activate() {
		if (getWidth() == 0 && getHeight() == 0) {
			// if the console window has not been opened before
			int width = content.getWidth();
			if (width == 0)
				width = topFrame.getWidth();

			setLocation(topFrame.getX() + topFrame.getWidth(), topFrame.getY());
			setSize(width, topFrame.getHeight());
		}

		add(content, GuiUtil.setConstraints(0,0,1.0,1.0,GridBagConstraints.BOTH,0,0,0,0));			
		doTranslation();
		setVisible(true);
	}

	public void loadSettings() {
		WindowSize size = config.getGuiConfig().getConsoleWindow().getSize();
		if (size.getX() != null && size.getY() != null && size.getWidth() != null & size.getHeight() != null) {
			setLocation(size.getX(), size.getY());
			setSize(size.getWidth(), size.getHeight());
		}
	}

	public void setSettings() {
		if (config.getGuiConfig().getConsoleWindow().isDetached()) {
			WindowSize size = config.getGuiConfig().getConsoleWindow().getSize();
			size.setX(getX());
			size.setY(getY());
			size.setWidth(getWidth());
			size.setHeight(getHeight());
		} else
			config.getGuiConfig().getConsoleWindow().setSize(new WindowSize());
	}

	public void doTranslation() {
		setTitle(Language.I18N.getString("main.window.title") + " - " + Language.I18N.getString("main.console.label"));
	}

}
