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
package org.citydb.core.operation.exporter.database.content;

import org.citydb.core.database.schema.TableEnum;
import org.citydb.core.database.schema.mapping.FeatureType;
import org.citydb.core.operation.exporter.CityGMLExportException;
import org.citydb.core.operation.exporter.util.AttributeValueSplitter;
import org.citydb.core.operation.exporter.util.SplitValue;
import org.citydb.core.query.filter.lod.LodFilter;
import org.citydb.core.query.filter.lod.LodIterator;
import org.citydb.core.query.filter.projection.CombinedProjectionFilter;
import org.citydb.core.query.filter.projection.ProjectionFilter;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.select.Select;
import org.citygml4j.model.citygml.landuse.LandUse;
import org.citygml4j.model.gml.basicTypes.Code;
import org.citygml4j.model.module.citygml.CityGMLModuleType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DBLandUse extends AbstractFeatureExporter<LandUse> {
	private final DBSurfaceGeometry geometryExporter;
	private final DBCityObject cityObjectExporter;

	private final String landUseModule;
	private final LodFilter lodFilter;
	private final AttributeValueSplitter valueSplitter;
	private final boolean hasObjectClassIdColumn;
	private final List<Table> adeHookTables;

	public DBLandUse(Connection connection, CityGMLExportManager exporter) throws CityGMLExportException, SQLException {
		super(LandUse.class, connection, exporter);

		cityObjectExporter = exporter.getExporter(DBCityObject.class);
		geometryExporter = exporter.getExporter(DBSurfaceGeometry.class);
		valueSplitter = exporter.getAttributeValueSplitter();

		CombinedProjectionFilter projectionFilter = exporter.getCombinedProjectionFilter(TableEnum.LAND_USE.getName());
		landUseModule = exporter.getTargetCityGMLVersion().getCityGMLModule(CityGMLModuleType.LAND_USE).getNamespaceURI();
		lodFilter = exporter.getLodFilter();
		hasObjectClassIdColumn = exporter.getDatabaseAdapter().getConnectionMetaData().getCityDBVersion().compareTo(4, 0, 0) >= 0;
		String schema = exporter.getDatabaseAdapter().getConnectionDetails().getSchema();

		table = new Table(TableEnum.LAND_USE.getName(), schema);
		select = new Select().addProjection(table.getColumn("id"));
		if (hasObjectClassIdColumn) select.addProjection(table.getColumn("objectclass_id"));
		if (projectionFilter.containsProperty("class", landUseModule)) select.addProjection(table.getColumn("class"), table.getColumn("class_codespace"));
		if (projectionFilter.containsProperty("function", landUseModule)) select.addProjection(table.getColumn("function"), table.getColumn("function_codespace"));
		if (projectionFilter.containsProperty("usage", landUseModule)) select.addProjection(table.getColumn("usage"), table.getColumn("usage_codespace"));
		if (lodFilter.isEnabled(0) && projectionFilter.containsProperty("lod0MultiSurface", landUseModule)) select.addProjection(table.getColumn("lod0_multi_surface_id"));
		if (lodFilter.isEnabled(1) && projectionFilter.containsProperty("lod1MultiSurface", landUseModule)) select.addProjection(table.getColumn("lod1_multi_surface_id"));
		if (lodFilter.isEnabled(2) && projectionFilter.containsProperty("lod2MultiSurface", landUseModule)) select.addProjection(table.getColumn("lod2_multi_surface_id"));
		if (lodFilter.isEnabled(3) && projectionFilter.containsProperty("lod3MultiSurface", landUseModule)) select.addProjection(table.getColumn("lod3_multi_surface_id"));
		if (lodFilter.isEnabled(4) && projectionFilter.containsProperty("lod4MultiSurface", landUseModule)) select.addProjection(table.getColumn("lod4_multi_surface_id"));
		adeHookTables = addJoinsToADEHookTables(TableEnum.LAND_USE, table);
	}

	@Override
	protected Collection<LandUse> doExport(long id, LandUse root, FeatureType rootType, PreparedStatement ps) throws CityGMLExportException, SQLException {
		ps.setLong(1, id);

		try (ResultSet rs = ps.executeQuery()) {
			List<LandUse> landUses = new ArrayList<>();

			while (rs.next()) {
				long landUseId = rs.getLong("id");
				LandUse landUse;
				FeatureType featureType;

				if (landUseId == id && root != null) {
					landUse = root;
					featureType = rootType;
				} else {
					if (hasObjectClassIdColumn) {
						// create land use object
						int objectClassId = rs.getInt("objectclass_id");
						landUse = exporter.createObject(objectClassId, LandUse.class);
						if (landUse == null) {
							exporter.logOrThrowErrorMessage("Failed to instantiate " + exporter.getObjectSignature(objectClassId, landUseId) + " as land use object.");
							continue;
						}

						featureType = exporter.getFeatureType(objectClassId);
					} else {
						landUse = new LandUse();
						featureType = exporter.getFeatureType(landUse);
					}
				}

				// get projection filter
				ProjectionFilter projectionFilter = exporter.getProjectionFilter(featureType);				

				// export city object information
				cityObjectExporter.addBatch(landUse, landUseId, featureType, projectionFilter);

				if (projectionFilter.containsProperty("class", landUseModule)) {
					String clazz = rs.getString("class");
					if (!rs.wasNull()) {
						Code code = new Code(clazz);
						code.setCodeSpace(rs.getString("class_codespace"));
						landUse.setClazz(code);
					}
				}

				if (projectionFilter.containsProperty("function", landUseModule)) {
					for (SplitValue splitValue : valueSplitter.split(rs.getString("function"), rs.getString("function_codespace"))) {
						Code function = new Code(splitValue.result(0));
						function.setCodeSpace(splitValue.result(1));
						landUse.addFunction(function);
					}
				}

				if (projectionFilter.containsProperty("usage", landUseModule)) {
					for (SplitValue splitValue : valueSplitter.split(rs.getString("usage"), rs.getString("usage_codespace"))) {
						Code usage = new Code(splitValue.result(0));
						usage.setCodeSpace(splitValue.result(1));
						landUse.addUsage(usage);
					}
				}

				LodIterator lodIterator = lodFilter.iterator(0, 4);
				while (lodIterator.hasNext()) {
					int lod = lodIterator.next();

					if (!projectionFilter.containsProperty("lod" + lod + "MultiSurface", landUseModule))
						continue;

					long geometryId = rs.getLong("lod" + lod + "_multi_surface_id");
					if (rs.wasNull())
						continue;

					switch (lod) {
						case 0:
							geometryExporter.addBatch(geometryId, landUse::setLod0MultiSurface);
							break;
						case 1:
							geometryExporter.addBatch(geometryId, landUse::setLod1MultiSurface);
							break;
						case 2:
							geometryExporter.addBatch(geometryId, landUse::setLod2MultiSurface);
							break;
						case 3:
							geometryExporter.addBatch(geometryId, landUse::setLod3MultiSurface);
							break;
						case 4:
							geometryExporter.addBatch(geometryId, landUse::setLod4MultiSurface);
							break;
					}
				}
				
				// delegate export of generic ADE properties
				if (adeHookTables != null) {
					List<String> adeHookTables = retrieveADEHookTables(this.adeHookTables, rs);
					if (adeHookTables != null)
						exporter.delegateToADEExporter(adeHookTables, landUse, landUseId, featureType, projectionFilter);
				}
				
				landUses.add(landUse);
			}	

			return landUses;
		}
	}
}
