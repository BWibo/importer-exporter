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
package org.citydb.gui.operation.database.view;

import org.citydb.config.Config;
import org.citydb.config.i18n.Language;
import org.citydb.gui.plugin.view.View;
import org.citydb.gui.plugin.view.ViewController;

import javax.swing.*;
import java.awt.*;

public class DatabaseView extends View {
	private final DatabasePanel component;
	
	public DatabaseView(ViewController viewController, Config config) {
		component = new DatabasePanel(viewController, config);
		addViewListener(component);
	}
	
	@Override
	public String getLocalizedTitle() {
		return Language.I18N.getString("main.tabbedPane.database");
	}

	@Override
	public Component getViewComponent() {
		return component;
	}

	@Override
	public String getToolTip() {
		return null;
	}

	@Override
	public Icon getIcon() {
		return null;
	}
	
	public void loadSettings() {
		component.loadSettings();
	}
	
	public void setSettings() {
		component.setSettings();
	}
	
	public void doTranslation() {
		component.doTranslation();
	}

}
