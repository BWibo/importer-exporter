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
package org.citydb.core.query.builder.config;

import org.citydb.core.query.builder.QueryBuildException;
import org.citydb.core.query.filter.apperance.AppearanceFilter;

public class AppearanceFilterBuilder {
	
	protected AppearanceFilterBuilder() {
		
	}

	protected AppearanceFilter buildAppearanceFilter(org.citydb.config.project.query.filter.appearance.AppearanceFilter appearanceFilterConfig) throws QueryBuildException {
		AppearanceFilter appearanceFilter = new AppearanceFilter();
		
		appearanceFilter.setIncludeNullTheme(appearanceFilterConfig.isIncludeNullTheme());
		
		for (String theme : appearanceFilterConfig.getThemes()) {
			if (theme != null && !theme.isEmpty())
				appearanceFilter.addTheme(theme);
		}
		
		if (!appearanceFilter.containsThemes())
			throw new QueryBuildException("No valid themes provided for appearance filter.");
		
		return appearanceFilter;
	}
	
}
