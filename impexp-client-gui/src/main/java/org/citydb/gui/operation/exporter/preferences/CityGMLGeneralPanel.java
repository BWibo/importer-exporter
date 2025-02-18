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
package org.citydb.gui.operation.exporter.preferences;

import org.citydb.config.Config;
import org.citydb.config.i18n.Language;
import org.citydb.gui.components.TitledPanel;
import org.citydb.gui.operation.common.DefaultPreferencesComponent;
import org.citydb.gui.util.GuiUtil;

import javax.swing.*;
import java.awt.*;

public class CityGMLGeneralPanel extends DefaultPreferencesComponent {
	private TitledPanel formatOptionsPanel;
	private JCheckBox prettyPrint;

	public CityGMLGeneralPanel(Config config) {
		super(config);
		initGui();
	}

	@Override
	public boolean isModified() {
		if (prettyPrint.isSelected() != config.getExportConfig().getCityGMLOptions().isPrettyPrint()) return true;
		return false;
	}

	private void initGui() {
		prettyPrint = new JCheckBox();

		setLayout(new GridBagLayout());
		formatOptionsPanel = new TitledPanel()
				.withToggleButton(prettyPrint)
				.showSeparator(false)
				.buildWithoutContent();

		add(formatOptionsPanel, GuiUtil.setConstraints(0, 0, 1, 0, GridBagConstraints.BOTH, 0, 0, 0, 0));
	}
	
	@Override
	public void loadSettings() {
		prettyPrint.setSelected(config.getExportConfig().getCityGMLOptions().isPrettyPrint());
	}

	@Override
	public void setSettings() {
		config.getExportConfig().getCityGMLOptions().setPrettyPrint(prettyPrint.isSelected());
	}

	@Override
	public void doTranslation() {
		formatOptionsPanel.setTitle(Language.I18N.getString("pref.export.common.label.prettyPrint"));
	}

	@Override
	public String getTitle() {
		return Language.I18N.getString("pref.tree.export.cityGML.general");
	}
}
