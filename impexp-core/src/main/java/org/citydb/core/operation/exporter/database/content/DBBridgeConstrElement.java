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

import org.citydb.config.geometry.GeometryObject;
import org.citydb.core.database.schema.TableEnum;
import org.citydb.core.database.schema.mapping.FeatureType;
import org.citydb.core.operation.exporter.CityGMLExportException;
import org.citydb.core.operation.exporter.util.AttributeValueSplitter;
import org.citydb.core.operation.exporter.util.SplitValue;
import org.citydb.core.operation.exporter.util.DefaultGeometrySetterHandler;
import org.citydb.core.operation.exporter.util.GeometrySetter;
import org.citydb.core.operation.exporter.util.GeometrySetterHandler;
import org.citydb.core.query.filter.lod.LodFilter;
import org.citydb.core.query.filter.lod.LodIterator;
import org.citydb.core.query.filter.projection.CombinedProjectionFilter;
import org.citydb.core.query.filter.projection.ProjectionFilter;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.select.Select;
import org.citydb.sqlbuilder.select.join.JoinFactory;
import org.citydb.sqlbuilder.select.operator.comparison.ComparisonName;
import org.citygml4j.model.citygml.bridge.AbstractBoundarySurface;
import org.citygml4j.model.citygml.bridge.AbstractBridge;
import org.citygml4j.model.citygml.bridge.AbstractOpening;
import org.citygml4j.model.citygml.bridge.BoundarySurfaceProperty;
import org.citygml4j.model.citygml.bridge.BridgeConstructionElement;
import org.citygml4j.model.citygml.bridge.BridgeConstructionElementProperty;
import org.citygml4j.model.citygml.bridge.Door;
import org.citygml4j.model.citygml.bridge.OpeningProperty;
import org.citygml4j.model.citygml.core.AddressProperty;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.citygml.core.ImplicitRepresentationProperty;
import org.citygml4j.model.gml.basicTypes.Code;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.GeometryProperty;
import org.citygml4j.model.gml.geometry.aggregates.MultiCurveProperty;
import org.citygml4j.model.module.citygml.CityGMLModuleType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DBBridgeConstrElement extends AbstractFeatureExporter<BridgeConstructionElement> {
	private final Map<Long, AbstractBridge> batches;
	private final DBSurfaceGeometry geometryExporter;
	private final DBCityObject cityObjectExporter;
	private final DBBridgeThematicSurface thematicSurfaceExporter;
	private final DBBridgeOpening openingExporter;
	private final DBAddress addressExporter;
	private final DBImplicitGeometry implicitGeometryExporter;
	private final GMLConverter gmlConverter;

	private final int batchSize;
	private final String bridgeModule;
	private final LodFilter lodFilter;
	private final AttributeValueSplitter valueSplitter;
	private final boolean hasObjectClassIdColumn;
	private final boolean useXLink;
	private final List<Table> constructionElementADEHookTables;
	private List<Table> surfaceADEHookTables;
	private List<Table> openingADEHookTables;
	private List<Table> addressADEHookTables;

	public DBBridgeConstrElement(Connection connection, CityGMLExportManager exporter) throws CityGMLExportException, SQLException {
		super(BridgeConstructionElement.class, connection, exporter);

		batches = new LinkedHashMap<>();
		batchSize = exporter.getFeatureBatchSize();
		cityObjectExporter = exporter.getExporter(DBCityObject.class);
		thematicSurfaceExporter = exporter.getExporter(DBBridgeThematicSurface.class);
		openingExporter = exporter.getExporter(DBBridgeOpening.class);
		addressExporter = exporter.getExporter(DBAddress.class);
		geometryExporter = exporter.getExporter(DBSurfaceGeometry.class);
		implicitGeometryExporter = exporter.getExporter(DBImplicitGeometry.class);
		gmlConverter = exporter.getGMLConverter();
		valueSplitter = exporter.getAttributeValueSplitter();

		CombinedProjectionFilter projectionFilter = exporter.getCombinedProjectionFilter(TableEnum.BRIDGE_CONSTR_ELEMENT.getName());
		bridgeModule = exporter.getTargetCityGMLVersion().getCityGMLModule(CityGMLModuleType.BRIDGE).getNamespaceURI();
		lodFilter = exporter.getLodFilter();
		hasObjectClassIdColumn = exporter.getDatabaseAdapter().getConnectionMetaData().getCityDBVersion().compareTo(4, 0, 0) >= 0;
		useXLink = exporter.getInternalConfig().isExportFeatureReferences();
		String schema = exporter.getDatabaseAdapter().getConnectionDetails().getSchema();

		table = new Table(TableEnum.BRIDGE_CONSTR_ELEMENT.getName(), schema);
		select = new Select().addProjection(table.getColumn("id"));
		if (hasObjectClassIdColumn) select.addProjection(table.getColumn("objectclass_id"));
		if (projectionFilter.containsProperty("class", bridgeModule)) select.addProjection(table.getColumn("class"), table.getColumn("class_codespace"));
		if (projectionFilter.containsProperty("function", bridgeModule)) select.addProjection(table.getColumn("function"), table.getColumn("function_codespace"));
		if (projectionFilter.containsProperty("usage", bridgeModule)) select.addProjection(table.getColumn("usage"), table.getColumn("usage_codespace"));
		if (lodFilter.isEnabled(1)) {
			if (projectionFilter.containsProperty("lod1TerrainIntersection", bridgeModule)) select.addProjection(exporter.getGeometryColumn(table.getColumn("lod1_terrain_intersection")));
			if (projectionFilter.containsProperty("lod1Geometry", bridgeModule)) select.addProjection(table.getColumn("lod1_brep_id"), exporter.getGeometryColumn(table.getColumn("lod1_other_geom")));
			if (projectionFilter.containsProperty("lod1ImplicitRepresentation", bridgeModule)) select.addProjection(table.getColumn("lod1_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod1_implicit_ref_point")), table.getColumn("lod1_implicit_transformation"));
		}
		if (lodFilter.isEnabled(2)) {
			if (projectionFilter.containsProperty("lod2TerrainIntersection", bridgeModule)) select.addProjection(exporter.getGeometryColumn(table.getColumn("lod2_terrain_intersection")));
			if (projectionFilter.containsProperty("lod2Geometry", bridgeModule)) select.addProjection(table.getColumn("lod2_brep_id"), exporter.getGeometryColumn(table.getColumn("lod2_other_geom")));
			if (projectionFilter.containsProperty("lod2ImplicitRepresentation", bridgeModule)) select.addProjection(table.getColumn("lod2_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod2_implicit_ref_point")), table.getColumn("lod2_implicit_transformation"));
		}
		if (lodFilter.isEnabled(3)) {
			if (projectionFilter.containsProperty("lod3TerrainIntersection", bridgeModule)) select.addProjection(exporter.getGeometryColumn(table.getColumn("lod3_terrain_intersection")));
			if (projectionFilter.containsProperty("lod3Geometry", bridgeModule)) select.addProjection(table.getColumn("lod3_brep_id"), exporter.getGeometryColumn(table.getColumn("lod3_other_geom")));
			if (projectionFilter.containsProperty("lod3ImplicitRepresentation", bridgeModule)) select.addProjection(table.getColumn("lod3_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod3_implicit_ref_point")), table.getColumn("lod3_implicit_transformation"));
		}
		if (lodFilter.isEnabled(4)) {
			if (projectionFilter.containsProperty("lod4TerrainIntersection", bridgeModule)) select.addProjection(exporter.getGeometryColumn(table.getColumn("lod4_terrain_intersection")));
			if (projectionFilter.containsProperty("lod4Geometry", bridgeModule)) select.addProjection(table.getColumn("lod4_brep_id"), exporter.getGeometryColumn(table.getColumn("lod4_other_geom")));
			if (projectionFilter.containsProperty("lod4ImplicitRepresentation", bridgeModule)) select.addProjection(table.getColumn("lod4_implicit_rep_id"), exporter.getGeometryColumn(table.getColumn("lod4_implicit_ref_point")), table.getColumn("lod4_implicit_transformation"));
		}
		if (lodFilter.containsLodGreaterThanOrEuqalTo(2)
				&& projectionFilter.containsProperty("boundedBy", bridgeModule)) {
			CombinedProjectionFilter boundarySurfaceProjectionFilter = exporter.getCombinedProjectionFilter(TableEnum.BRIDGE_THEMATIC_SURFACE.getName());
			Table thematicSurface = new Table(TableEnum.BRIDGE_THEMATIC_SURFACE.getName(), schema);
			thematicSurfaceExporter.addProjection(select, thematicSurface, boundarySurfaceProjectionFilter, "ts")
					.addJoin(JoinFactory.left(thematicSurface, "bridge_constr_element_id", ComparisonName.EQUAL_TO, table.getColumn("id")));
			if (lodFilter.containsLodGreaterThanOrEuqalTo(3)
					&& boundarySurfaceProjectionFilter.containsProperty("opening", bridgeModule)) {
				CombinedProjectionFilter openingProjectionFilter = exporter.getCombinedProjectionFilter(TableEnum.BRIDGE_OPENING.getName());
				Table opening = new Table(TableEnum.BRIDGE_OPENING.getName(), schema);
				Table openingToThemSurface = new Table(TableEnum.BRIDGE_OPEN_TO_THEM_SRF.getName(), schema);
				Table cityObject = new Table(TableEnum.CITYOBJECT.getName(), schema);
				openingExporter.addProjection(select, opening, openingProjectionFilter, "op")
						.addProjection(cityObject.getColumn("gmlid", "opgmlid"))
						.addJoin(JoinFactory.left(openingToThemSurface, "bridge_thematic_surface_id", ComparisonName.EQUAL_TO, thematicSurface.getColumn("id")))
						.addJoin(JoinFactory.left(opening, "id", ComparisonName.EQUAL_TO, openingToThemSurface.getColumn("bridge_opening_id")))
						.addJoin(JoinFactory.left(cityObject, "id", ComparisonName.EQUAL_TO, opening.getColumn("id")));
				if (openingProjectionFilter.containsProperty("address", bridgeModule)) {
					Table address = new Table(TableEnum.ADDRESS.getName(), schema);
					addressExporter.addProjection(select, address, "oa")
							.addJoin(JoinFactory.left(address, "id", ComparisonName.EQUAL_TO, opening.getColumn("address_id")));
					addressADEHookTables = addJoinsToADEHookTables(TableEnum.ADDRESS, address);
				}
				openingADEHookTables = addJoinsToADEHookTables(TableEnum.BRIDGE_OPENING, opening);
			}
			surfaceADEHookTables = addJoinsToADEHookTables(TableEnum.BRIDGE_THEMATIC_SURFACE, thematicSurface);
		}
		constructionElementADEHookTables = addJoinsToADEHookTables(TableEnum.BRIDGE_CONSTR_ELEMENT, table);
	}

	protected void addBatch(long id, AbstractBridge parent) throws CityGMLExportException, SQLException {
		batches.put(id, parent);
		if (batches.size() == batchSize)
			executeBatch();
	}

	protected void executeBatch() throws CityGMLExportException, SQLException {
		if (batches.isEmpty())
			return;

		try {
			PreparedStatement ps;
			if (batches.size() == 1) {
				ps = getOrCreateStatement("id");
				ps.setLong(1, batches.keySet().iterator().next());
			} else {
				ps = getOrCreateBulkStatement(batchSize);
				prepareBulkStatement(ps, batches.keySet().toArray(new Long[0]), batchSize);
			}

			try (ResultSet rs = ps.executeQuery()) {
				Map<Long, BridgeConstructionElement> constructionElements = doExport(0, null, null, rs);
				for (Map.Entry<Long, BridgeConstructionElement> entry : constructionElements.entrySet()) {
					AbstractBridge bridge = batches.get(entry.getKey());
					if (bridge == null) {
						exporter.logOrThrowErrorMessage("Failed to assign bridge construction element with id " + entry.getKey() + " to a bridge.");
						continue;
					}

					bridge.addOuterBridgeConstructionElement(new BridgeConstructionElementProperty(entry.getValue()));
				}
			}
		} finally {
			batches.clear();
		}
	}

	protected Collection<BridgeConstructionElement> doExport(AbstractBridge parent, long parentId) throws CityGMLExportException, SQLException {
		return doExport(parentId, null, null, getOrCreateStatement("bridge_id"));
	}

	@Override
	protected Collection<BridgeConstructionElement> doExport(long id, BridgeConstructionElement root, FeatureType rootType, PreparedStatement ps) throws CityGMLExportException, SQLException {
		ps.setLong(1, id);

		try (ResultSet rs = ps.executeQuery()) {
			return doExport(id, root, rootType, rs).values();
		}
	}

	private Map<Long, BridgeConstructionElement> doExport(long id, BridgeConstructionElement root, FeatureType rootType, ResultSet rs) throws CityGMLExportException, SQLException {
		long currentConstructionElementId = 0;
		BridgeConstructionElement constructionElement = null;
		ProjectionFilter projectionFilter = null;
		Map<Long, BridgeConstructionElement> constructionElements = new HashMap<>();
		Map<Long, GeometrySetterHandler> geometries = new LinkedHashMap<>();
		Map<Long, List<String>> adeHookTables = constructionElementADEHookTables != null ? new HashMap<>() : null;

		long currentBoundarySurfaceId = 0;
		AbstractBoundarySurface boundarySurface = null;
		ProjectionFilter boundarySurfaceProjectionFilter = null;
		Map<Long, AbstractBoundarySurface> boundarySurfaces = new HashMap<>();

		while (rs.next()) {
			long constructionElementId = rs.getLong("id");

			if (constructionElementId != currentConstructionElementId || constructionElement == null) {
				currentConstructionElementId = constructionElementId;

				constructionElement = constructionElements.get(constructionElementId);
				if (constructionElement == null) {
					FeatureType featureType;
					if (constructionElementId == id && root != null) {
						constructionElement = root;
						featureType = rootType;
					} else {
						if (hasObjectClassIdColumn) {
							// create bridge construction element object
							int objectClassId = rs.getInt("objectclass_id");
							constructionElement = exporter.createObject(objectClassId, BridgeConstructionElement.class);
							if (constructionElement == null) {
								exporter.logOrThrowErrorMessage("Failed to instantiate " + exporter.getObjectSignature(objectClassId, constructionElementId) + " as bridge construction element object.");
								continue;
							}

							featureType = exporter.getFeatureType(objectClassId);
						} else {
							constructionElement = new BridgeConstructionElement();
							featureType = exporter.getFeatureType(constructionElement);
						}
					}

					// get projection filter
					projectionFilter = exporter.getProjectionFilter(featureType);

					// export city object information
					cityObjectExporter.addBatch(constructionElement, constructionElementId, featureType, projectionFilter);

					if (projectionFilter.containsProperty("class", bridgeModule)) {
						String clazz = rs.getString("class");
						if (!rs.wasNull()) {
							Code code = new Code(clazz);
							code.setCodeSpace(rs.getString("class_codespace"));
							constructionElement.setClazz(code);
						}
					}

					if (projectionFilter.containsProperty("function", bridgeModule)) {
						for (SplitValue splitValue : valueSplitter.split(rs.getString("function"), rs.getString("function_codespace"))) {
							Code function = new Code(splitValue.result(0));
							function.setCodeSpace(splitValue.result(1));
							constructionElement.addFunction(function);
						}
					}

					if (projectionFilter.containsProperty("usage", bridgeModule)) {
						for (SplitValue splitValue : valueSplitter.split(rs.getString("usage"), rs.getString("usage_codespace"))) {
							Code usage = new Code(splitValue.result(0));
							usage.setCodeSpace(splitValue.result(1));
							constructionElement.addUsage(usage);
						}
					}

					LodIterator lodIterator = lodFilter.iterator(1, 4);
					while (lodIterator.hasNext()) {
						int lod = lodIterator.next();

						if (!projectionFilter.containsProperty("lod" + lod + "TerrainIntersection", bridgeModule))
							continue;

						Object terrainIntersectionObj = rs.getObject("lod" + lod + "_terrain_intersection");
						if (rs.wasNull())
							continue;

						GeometryObject terrainIntersection = exporter.getDatabaseAdapter().getGeometryConverter().getMultiCurve(terrainIntersectionObj);
						if (terrainIntersection != null) {
							MultiCurveProperty multiCurveProperty = gmlConverter.getMultiCurveProperty(terrainIntersection, false);
							if (multiCurveProperty != null) {
								switch (lod) {
									case 1:
										constructionElement.setLod1TerrainIntersection(multiCurveProperty);
										break;
									case 2:
										constructionElement.setLod2TerrainIntersection(multiCurveProperty);
										break;
									case 3:
										constructionElement.setLod3TerrainIntersection(multiCurveProperty);
										break;
									case 4:
										constructionElement.setLod4TerrainIntersection(multiCurveProperty);
										break;
								}
							}
						}
					}

					lodIterator.reset();
					while (lodIterator.hasNext()) {
						int lod = lodIterator.next();

						if (!projectionFilter.containsProperty("lod" + lod + "Geometry", bridgeModule))
							continue;

						long geometryId = rs.getLong("lod" + lod + "_brep_id");
						if (!rs.wasNull()) {
							switch (lod) {
								case 1:
									geometries.put(geometryId, new DefaultGeometrySetterHandler((GeometrySetter.AbstractGeometry) constructionElement::setLod1Geometry));
									break;
								case 2:
									geometries.put(geometryId, new DefaultGeometrySetterHandler((GeometrySetter.AbstractGeometry) constructionElement::setLod2Geometry));
									break;
								case 3:
									geometries.put(geometryId, new DefaultGeometrySetterHandler((GeometrySetter.AbstractGeometry) constructionElement::setLod3Geometry));
									break;
								case 4:
									geometries.put(geometryId, new DefaultGeometrySetterHandler((GeometrySetter.AbstractGeometry) constructionElement::setLod4Geometry));
									break;
							}
						} else {
							Object geometryObj = rs.getObject("lod" + lod + "_other_geom");
							if (rs.wasNull())
								continue;

							GeometryObject geometry = exporter.getDatabaseAdapter().getGeometryConverter().getGeometry(geometryObj);
							if (geometry != null) {
								GeometryProperty<AbstractGeometry> property = new GeometryProperty<>(gmlConverter.getPointOrCurveGeometry(geometry, true));
								switch (lod) {
									case 1:
										constructionElement.setLod1Geometry(property);
										break;
									case 2:
										constructionElement.setLod2Geometry(property);
										break;
									case 3:
										constructionElement.setLod3Geometry(property);
										break;
									case 4:
										constructionElement.setLod4Geometry(property);
										break;
								}
							}
						}
					}

					lodIterator.reset();
					while (lodIterator.hasNext()) {
						int lod = lodIterator.next();

						if (!projectionFilter.containsProperty("lod" + lod + "ImplicitRepresentation", bridgeModule))
							continue;

						// get implicit geometry details
						long implicitGeometryId = rs.getLong("lod" + lod + "_implicit_rep_id");
						if (rs.wasNull())
							continue;

						GeometryObject referencePoint = null;
						Object referencePointObj = rs.getObject("lod" + lod + "_implicit_ref_point");
						if (!rs.wasNull())
							referencePoint = exporter.getDatabaseAdapter().getGeometryConverter().getPoint(referencePointObj);

						String transformationMatrix = rs.getString("lod" + lod + "_implicit_transformation");

						ImplicitGeometry implicit = implicitGeometryExporter.doExport(implicitGeometryId, referencePoint, transformationMatrix);
						if (implicit != null) {
							ImplicitRepresentationProperty implicitProperty = new ImplicitRepresentationProperty();
							implicitProperty.setObject(implicit);

							switch (lod) {
								case 1:
									constructionElement.setLod1ImplicitRepresentation(implicitProperty);
									break;
								case 2:
									constructionElement.setLod2ImplicitRepresentation(implicitProperty);
									break;
								case 3:
									constructionElement.setLod3ImplicitRepresentation(implicitProperty);
									break;
								case 4:
									constructionElement.setLod4ImplicitRepresentation(implicitProperty);
									break;
							}
						}
					}

					// get tables of ADE hook properties
					if (constructionElementADEHookTables != null) {
						List<String> tables = retrieveADEHookTables(constructionElementADEHookTables, rs);
						if (tables != null) {
							adeHookTables.put(constructionElementId, tables);
							constructionElement.setLocalProperty("type", featureType);
						}
					}

					constructionElement.setLocalProperty("projection", projectionFilter);
					constructionElements.put(constructionElementId, constructionElement);
				} else
					projectionFilter = (ProjectionFilter) constructionElement.getLocalProperty("projection");
			}

			if (!lodFilter.containsLodGreaterThanOrEuqalTo(2)
					|| !projectionFilter.containsProperty("boundedBy", bridgeModule))
				continue;

			// brid:boundedBy
			long boundarySurfaceId = rs.getLong("tsid");
			if (rs.wasNull())
				continue;

			if (boundarySurfaceId != currentBoundarySurfaceId || boundarySurface == null) {
				currentBoundarySurfaceId = boundarySurfaceId;

				boundarySurface = boundarySurfaces.get(boundarySurfaceId);
				if (boundarySurface == null) {
					int objectClassId = rs.getInt("tsobjectclass_id");
					FeatureType featureType = exporter.getFeatureType(objectClassId);

					boundarySurface = thematicSurfaceExporter.doExport(boundarySurfaceId, featureType, "ts", surfaceADEHookTables, rs);
					if (boundarySurface == null) {
						exporter.logOrThrowErrorMessage("Failed to instantiate " + exporter.getObjectSignature(objectClassId, boundarySurfaceId) + " as boundary surface object.");
						continue;
					}

					// get projection filter
					boundarySurfaceProjectionFilter = exporter.getProjectionFilter(featureType);
					boundarySurface.setLocalProperty("projection", boundarySurfaceProjectionFilter);

					constructionElement.getBoundedBySurface().add(new BoundarySurfaceProperty(boundarySurface));
					boundarySurfaces.put(boundarySurfaceId, boundarySurface);
				} else
					boundarySurfaceProjectionFilter = (ProjectionFilter) boundarySurface.getLocalProperty("projection");
			}

			// continue if openings shall not be exported
			if (!lodFilter.containsLodGreaterThanOrEuqalTo(3)
					|| !boundarySurfaceProjectionFilter.containsProperty("opening", bridgeModule))
				continue;

			long openingId = rs.getLong("opid");
			if (rs.wasNull())
				continue;

			int objectClassId = rs.getInt("opobjectclass_id");

			// check whether we need an XLink
			String gmlId = rs.getString("opgmlid");
			boolean generateNewGmlId = false;
			if (!rs.wasNull()) {
				if (exporter.lookupAndPutObjectId(gmlId, openingId, objectClassId)) {
					if (useXLink) {
						OpeningProperty openingProperty = new OpeningProperty();
						openingProperty.setHref("#" + gmlId);
						boundarySurface.addOpening(openingProperty);
						continue;
					} else
						generateNewGmlId = true;
				}
			}

			// create new opening object
			FeatureType featureType = exporter.getFeatureType(objectClassId);
			AbstractOpening opening = openingExporter.doExport(openingId, featureType, "op", openingADEHookTables, rs);
			if (opening == null) {
				exporter.logOrThrowErrorMessage("Failed to instantiate " + exporter.getObjectSignature(objectClassId, openingId) + " as bridge opening object.");
				continue;
			}

			if (generateNewGmlId)
				opening.setId(exporter.generateFeatureGmlId(opening, gmlId));

			// get projection filter
			ProjectionFilter openingProjectionFilter = exporter.getProjectionFilter(featureType);

			if (opening instanceof Door
					&& openingProjectionFilter.containsProperty("address", bridgeModule)) {
				long addressId = rs.getLong("oaid");
				if (!rs.wasNull()) {
					AddressProperty addressProperty = addressExporter.doExport(addressId, "oa", addressADEHookTables, rs);
					if (addressProperty != null)
						((Door) opening).addAddress(addressProperty);
				}
			}

			boundarySurface.getOpening().add(new OpeningProperty(opening));
		}

		// export postponed geometries
		for (Map.Entry<Long, GeometrySetterHandler> entry : geometries.entrySet())
			geometryExporter.addBatch(entry.getKey(), entry.getValue());

		// delegate export of generic ADE properties
		if (adeHookTables != null) {
			for (Map.Entry<Long, List<String>> entry : adeHookTables.entrySet()) {
				long constructionElementId = entry.getKey();
				constructionElement = constructionElements.get(constructionElementId);
				exporter.delegateToADEExporter(entry.getValue(), constructionElement, constructionElementId,
						(FeatureType) constructionElement.getLocalProperty("type"),
						(ProjectionFilter) constructionElement.getLocalProperty("projection"));
			}
		}

		return constructionElements;
	}
}
