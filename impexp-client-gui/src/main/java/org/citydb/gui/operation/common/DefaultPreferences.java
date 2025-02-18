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
package org.citydb.gui.operation.common;

import org.citydb.gui.plugin.preferences.Preferences;
import org.citydb.gui.plugin.preferences.PreferencesEntry;

public class DefaultPreferences implements Preferences {
	protected DefaultPreferencesEntry root;
	
	protected DefaultPreferences(DefaultPreferencesEntry entry) {
		this.root = entry;
	}
	
	@Override
	public final DefaultPreferencesEntry getPreferencesEntry() {
		return root;
	}
	
	public final void doTranslation() {
		doTranslation(root);
	}

	private void doTranslation(DefaultPreferencesEntry node) {
		node.doTranslation();
		
		for (PreferencesEntry childEntry : node.getChildEntries())
			doTranslation((DefaultPreferencesEntry)childEntry);
	}

	public final void loadSettings() {
		loadSettings(root);
	}

	private void loadSettings(DefaultPreferencesEntry node) {
		node.getViewComponent().loadSettings();
		
		for (PreferencesEntry childEntry : node.getChildEntries())
			loadSettings((DefaultPreferencesEntry)childEntry);
	}

	public final void setSettings() {
		setSettings(root);
	}

	private void setSettings(DefaultPreferencesEntry node) {
		node.getViewComponent().setSettings();
		
		for (PreferencesEntry childEntry : node.getChildEntries())
			setSettings((DefaultPreferencesEntry)childEntry);
	}
}
