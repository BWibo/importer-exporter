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
package org.citydb.core.operation.common.cache.model;

import org.citydb.core.database.adapter.AbstractSQLAdapter;


public class CacheTableTextureParam extends AbstractCacheTableModel {
	public static CacheTableTextureParam instance = null;
	
	public synchronized static CacheTableTextureParam getInstance() {
		if (instance == null)
			instance = new CacheTableTextureParam();
		
		return instance;
	}

	@Override
	public CacheTableModel getType() {
		return CacheTableModel.TEXTUREPARAM;
	}
	
	@Override
	protected String getColumns(AbstractSQLAdapter sqlAdapter) {
		return "(" +
				"ID " + sqlAdapter.getInteger() + ", " +
				"GMLID " + sqlAdapter.getCharacterVarying(256) + ", " +
				"TYPE " + sqlAdapter.getNumeric(3) + ", " +
				"IS_TEXTURE_PARAMETERIZATION " + sqlAdapter.getNumeric(1, 0) + ", " +
				"TEXPARAM_GMLID " + sqlAdapter.getCharacterVarying(256) + ", " +
				"WORLD_TO_TEXTURE " + sqlAdapter.getCharacterVarying(1000) +
				")";
	}

}
