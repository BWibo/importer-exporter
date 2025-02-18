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
package org.citydb.core.operation.common.xlink;

import org.citydb.config.geometry.GeometryObject;

public class DBXlinkTextureCoordList implements DBXlink {
	private long id;
	private String gmlId;
	private String texParamGmlId;
	private GeometryObject textureCoord;
	private long targetId;
	
	private long surfaceGeometryId;
	private boolean isReverse;

	public DBXlinkTextureCoordList(long id, 
			String gmlId, 
			String texParamGmlId, 
			long targetId) {
		this.id = id;
		this.gmlId = gmlId;
		this.texParamGmlId = texParamGmlId;
		this.targetId = targetId;
	}
	
	public DBXlinkTextureCoordList(long id, 
			String gmlId, 
			String texParamGmlId, 
			GeometryObject textureCoord,
			long targetId) {
		this.id = id;
		this.gmlId = gmlId;
		this.texParamGmlId = texParamGmlId;
		this.textureCoord = textureCoord;
		this.targetId = targetId;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	@Override
	public String getGmlId() {
		return gmlId;
	}

	@Override
	public void setGmlId(String gmlId) {
		this.gmlId = gmlId;
	}

	public String getTexParamGmlId() {
		return texParamGmlId;
	}

	public void setTexParamGmlId(String texParamGmlId) {
		this.texParamGmlId = texParamGmlId;
	}

	public GeometryObject getTextureCoord() {
		return textureCoord;
	}

	public void setTextureCoord(GeometryObject textureCoord) {
		this.textureCoord = textureCoord;
	}

	public long getTargetId() {
		return targetId;
	}

	public void setTargetId(long targetId) {
		this.targetId = targetId;
	}

	public long getSurfaceGeometryId() {
		return surfaceGeometryId;
	}

	public void setSurfaceGeometryId(long surfaceGeometryId) {
		this.surfaceGeometryId = surfaceGeometryId;
	}

	public boolean isReverse() {
		return isReverse;
	}

	public void setReverse(boolean isReverse) {
		this.isReverse = isReverse;
	}

	@Override
	public DBXlinkEnum getXlinkType() {
		return DBXlinkEnum.TEXTURE_COORD_LIST;
	}
}
