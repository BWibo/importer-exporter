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
import org.citydb.core.database.schema.XlinkType;
import org.citydb.core.operation.exporter.CityGMLExportException;
import org.citydb.core.operation.exporter.util.DefaultGeometrySetterHandler;
import org.citydb.core.operation.exporter.util.GeometrySetter;
import org.citydb.core.operation.exporter.util.GeometrySetterHandler;
import org.citydb.sqlbuilder.expression.LiteralSelectExpression;
import org.citydb.sqlbuilder.expression.PlaceHolder;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.select.Select;
import org.citydb.sqlbuilder.select.operator.comparison.ComparisonFactory;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.gml.GMLClass;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.aggregates.MultiSolid;
import org.citygml4j.model.gml.geometry.aggregates.MultiSurface;
import org.citygml4j.model.gml.geometry.complexes.CompositeSolid;
import org.citygml4j.model.gml.geometry.complexes.CompositeSurface;
import org.citygml4j.model.gml.geometry.primitives.AbstractSolid;
import org.citygml4j.model.gml.geometry.primitives.AbstractSurface;
import org.citygml4j.model.gml.geometry.primitives.DirectPositionList;
import org.citygml4j.model.gml.geometry.primitives.Exterior;
import org.citygml4j.model.gml.geometry.primitives.Interior;
import org.citygml4j.model.gml.geometry.primitives.LinearRing;
import org.citygml4j.model.gml.geometry.primitives.OrientableSurface;
import org.citygml4j.model.gml.geometry.primitives.Polygon;
import org.citygml4j.model.gml.geometry.primitives.Sign;
import org.citygml4j.model.gml.geometry.primitives.Solid;
import org.citygml4j.model.gml.geometry.primitives.SolidProperty;
import org.citygml4j.model.gml.geometry.primitives.SurfaceProperty;
import org.citygml4j.model.gml.geometry.primitives.Triangle;
import org.citygml4j.model.gml.geometry.primitives.TrianglePatchArrayProperty;
import org.citygml4j.model.gml.geometry.primitives.TriangulatedSurface;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBSurfaceGeometry implements DBExporter, SurfaceGeometryExporter {
	private final CityGMLExportManager exporter;
	private final PreparedStatement psBulk;
	private final PreparedStatement psSelect;
	private final List<SurfaceGeometryContext> batches;
	private final int batchSize;
	private final boolean exportAppearance;
	private final boolean useXLink;
	private final boolean affineTransformation;

	public DBSurfaceGeometry(Connection connection, CityGMLExportManager exporter) throws SQLException {
		this.exporter = exporter;

		batches = new ArrayList<>();
		batchSize = exporter.getGeometryBatchSize();
		exportAppearance = exporter.getInternalConfig().isExportGlobalAppearances();
		useXLink = exporter.getInternalConfig().isExportGeometryReferences();
		affineTransformation = exporter.getExportConfig().getAffineTransformation().isEnabled();
		String schema = exporter.getDatabaseAdapter().getConnectionDetails().getSchema();

		Table table = new Table(TableEnum.SURFACE_GEOMETRY.getName(), schema);
		Select select = new Select().addProjection(table.getColumn("id"), table.getColumn("gmlid"), table.getColumn("parent_id"),
				table.getColumn("is_solid"), table.getColumn("is_composite"), table.getColumn("is_triangulated"),
				table.getColumn("is_xlink"), table.getColumn("is_reverse"),
				exporter.getGeometryColumn(table.getColumn("geometry")), table.getColumn("implicit_geometry"));

		String placeHolders = String.join(",", Collections.nCopies(batchSize, "?"));
		psBulk = connection.prepareStatement(new Select(select)
				.addProjection(table.getColumn("root_id"))
				.addSelection(ComparisonFactory.in(table.getColumn("root_id"), new LiteralSelectExpression(placeHolders))).toString());

		psSelect = connection.prepareStatement(new Select(select)
				.addSelection(ComparisonFactory.equalTo(table.getColumn("root_id"), new PlaceHolder<>())).toString());
	}

	@Override
	public void addBatch(long id, GeometrySetterHandler handler) throws CityGMLExportException, SQLException {
		batches.add(new SurfaceGeometryContext(id, handler, false));
		if (batches.size() == batchSize)
			executeBatch();
	}

	@Override
	public void addBatch(long id, GeometrySetter.AbstractGeometry setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.Surface setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.CompositeSurface setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.MultiSurface setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.Polygon setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.MultiPolygon setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.Solid setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.CompositeSolid setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.MultiSolid setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	@Override
	public void addBatch(long id, GeometrySetter.Tin setter) throws CityGMLExportException, SQLException {
		addBatch(id, new DefaultGeometrySetterHandler(setter));
	}

	protected void addImplicitGeometryBatch(long id, ImplicitGeometry geometry) throws CityGMLExportException, SQLException {
		batches.add(new SurfaceGeometryContext(id,
				new DefaultGeometrySetterHandler((GeometrySetter.AbstractGeometry) geometry::setRelativeGeometry),
				true));

		if (batches.size() == batchSize)
			executeBatch();
	}

	public void executeBatch() throws CityGMLExportException, SQLException {
		if (batches.isEmpty())
			return;

		try {
			if (batches.size() == 1) {
				SurfaceGeometryContext context = batches.get(0);
				SurfaceGeometry geometry = doExport(context.id, context.isImplicit);
				if (geometry != null)
					context.handler.handle(geometry);
			} else {
				Map<Long, GeometryTree> geomTrees = new HashMap<>();
				for (SurfaceGeometryContext batch : batches)
					geomTrees.putIfAbsent(batch.id, new GeometryTree(batch.isImplicit));

				Long[] ids = geomTrees.keySet().toArray(new Long[0]);
				for (int i = 0; i < batchSize; i++)
					psBulk.setLong(i + 1, i < ids.length ? ids[i] : 0);

				try (ResultSet rs = psBulk.executeQuery()) {
					while (rs.next()) {
						GeometryTree geomTree = geomTrees.get(rs.getLong(11));
						if (geomTree != null)
							addSurfaceGeometry(geomTree, rs);
					}
				}

				for (SurfaceGeometryContext batch : batches) {
					GeometryTree geomTree = geomTrees.get(batch.id);
					if (geomTree != null && geomTree.root != 0) {
						SurfaceGeometry geometry = rebuildGeometry(geomTree.getNode(geomTree.root), false, false, geomTree.isImplicit);
						if (geometry != null)
							batch.handler.handle(geometry);
					} else
						exporter.logOrThrowErrorMessage("Failed to read surface geometry for root id " + batch.id + ".");
				}
			}
		} finally {
			batches.clear();
		}
	}

	protected SurfaceGeometry doExport(long rootId) throws CityGMLExportException, SQLException {
		return doExport(rootId, false);
	}

	protected SurfaceGeometry doExportImplicitGeometry(long rootId) throws CityGMLExportException, SQLException {
		return doExport(rootId, true);
	}

	private SurfaceGeometry doExport(long rootId, boolean isImplicit) throws CityGMLExportException, SQLException {
		psSelect.setLong(1, rootId);

		try (ResultSet rs = psSelect.executeQuery()) {
			GeometryTree geomTree = new GeometryTree(isImplicit);
			while (rs.next())
				addSurfaceGeometry(geomTree, rs);

			if (geomTree.root != 0)
				return rebuildGeometry(geomTree.getNode(geomTree.root), false, false, isImplicit);
			else {
				exporter.logOrThrowErrorMessage("Failed to read surface geometry for root id " + rootId + ".");
				return null;
			}
		}
	}

	private void addSurfaceGeometry(GeometryTree geomTree, ResultSet rs) throws CityGMLExportException, SQLException {
		long id = rs.getLong(1);

		// constructing a geometry node
		GeometryNode geomNode = new GeometryNode();
		geomNode.id = id;
		geomNode.gmlId = rs.getString(2);
		geomNode.parentId = rs.getLong(3);
		geomNode.isSolid = rs.getBoolean(4);
		geomNode.isComposite = rs.getBoolean(5);
		geomNode.isTriangulated = rs.getBoolean(6);
		geomNode.isXlink = rs.getInt(7);
		geomNode.isReverse = rs.getBoolean(8);

		GeometryObject geometry = null;
		Object object = rs.getObject(!geomTree.isImplicit ? 9 : 10);
		if (!rs.wasNull()) {
			try {
				geometry = exporter.getDatabaseAdapter().getGeometryConverter().getPolygon(object);
			} catch (Exception e) {
				exporter.logOrThrowErrorMessage("Skipping " + exporter.getGeometrySignature(GMLClass.POLYGON, id) +
						": " + e.getMessage());
				return;
			}
		}

		geomNode.geometry = geometry;

		// put polygon into the geometry tree
		geomTree.insertNode(geomNode, geomNode.parentId);
	}

	private SurfaceGeometry rebuildGeometry(GeometryNode geomNode, boolean isSetOrientableSurface, boolean wasXlink, boolean isImplicit) {
		SurfaceGeometry result = null;

		// try and determine the geometry type
		GMLClass surfaceGeometryType;
		if (geomNode.geometry != null) {
			surfaceGeometryType = GMLClass.POLYGON;
		} else {
			if (geomNode.childNodes == null || geomNode.childNodes.size() == 0)
				return null;

			if (!geomNode.isTriangulated) {
				if (!geomNode.isSolid && geomNode.isComposite)
					surfaceGeometryType = GMLClass.COMPOSITE_SURFACE;
				else if (geomNode.isSolid && !geomNode.isComposite)
					surfaceGeometryType = GMLClass.SOLID;
				else if (geomNode.isSolid)
					surfaceGeometryType = GMLClass.COMPOSITE_SOLID;
				else {
					boolean isMultiSolid = true;
					for (GeometryNode childNode : geomNode.childNodes) {
						if (!childNode.isSolid){
							isMultiSolid = false;
							break;
						}
					}

					if (isMultiSolid)
						surfaceGeometryType = GMLClass.MULTI_SOLID;
					else
						surfaceGeometryType = GMLClass.MULTI_SURFACE;
				}
			} else
				surfaceGeometryType = GMLClass.TRIANGULATED_SURFACE;
		}

		// check for xlinks
		if (geomNode.gmlId != null
				&& geomNode.isXlink > 0
				&& exporter.lookupAndPutGeometryId(geomNode.gmlId, geomNode.id, geomNode.isXlink == XlinkType.LOCAL.value())) {
			if (useXLink) {
				// check whether we have to embrace the geometry with an orientableSurface
				return geomNode.isReverse != isSetOrientableSurface ?
						new SurfaceGeometry(reverseSurface("#" + geomNode.gmlId)) :
						new SurfaceGeometry("#" + geomNode.gmlId, surfaceGeometryType);
			} else {
				geomNode.isXlink = XlinkType.NONE.value();
				geomNode.gmlId = exporter.generateGeometryGmlId(geomNode.gmlId);
				return rebuildGeometry(geomNode, isSetOrientableSurface, true, isImplicit);
			}
		}

		// check whether we have to initialize an orientableSurface
		boolean initOrientableSurface = false;
		if (geomNode.isReverse && !isSetOrientableSurface) {
			isSetOrientableSurface = true;
			initOrientableSurface = true;
		}

		// deal with geometry according to the identified type
		// Polygon
		if (surfaceGeometryType == GMLClass.POLYGON) {
			// try and interpret geometry object from database
			Polygon polygon = new Polygon();
			boolean forceRingIds = false;

			if (geomNode.gmlId != null) {
				polygon.setId(geomNode.gmlId);
				forceRingIds = true;
			}

			for (int ringIndex = 0; ringIndex < geomNode.geometry.getNumElements(); ringIndex++) {
				List<Double> values;

				// check whether we have to reverse the coordinate order
				if (!geomNode.isReverse) {
					values = geomNode.geometry.getCoordinatesAsList(ringIndex);
				} else {
					values = new ArrayList<>(geomNode.geometry.getCoordinates(ringIndex).length);
					double[] coordinates = geomNode.geometry.getCoordinates(ringIndex);
					for (int i = coordinates.length - 3; i >= 0; i -= 3) {
						values.add(coordinates[i]);
						values.add(coordinates[i + 1]);
						values.add(coordinates[i + 2]);
					}
				}

				if (affineTransformation && !isImplicit) {
					exporter.getAffineTransformer().transformCoordinates(values);
				}

				LinearRing linearRing = new LinearRing();
				DirectPositionList posList = new DirectPositionList();
				if (forceRingIds)
					linearRing.setId(polygon.getId() + '_' + ringIndex + '_');

				posList.setValue(values);
				posList.setSrsDimension(3);
				linearRing.setPosList(posList);

				if (ringIndex == 0)
					polygon.setExterior(new Exterior(linearRing));
				else
					polygon.addInterior(new Interior(linearRing));
			}

			// check whether we have to embrace the polygon with an orientableSurface
			result = initOrientableSurface || (isSetOrientableSurface && !geomNode.isReverse) ?
					new SurfaceGeometry(reverseSurface(polygon)) :
					new SurfaceGeometry(polygon);
		}

		// compositeSurface
		else if (surfaceGeometryType == GMLClass.COMPOSITE_SURFACE) {
			CompositeSurface compositeSurface = new CompositeSurface();

			if (geomNode.gmlId != null)
				compositeSurface.setId(geomNode.gmlId);

			for (GeometryNode childNode : geomNode.childNodes) {
				SurfaceGeometry member = rebuildGeometry(childNode, isSetOrientableSurface, wasXlink, isImplicit);
				if (member != null) {
					AbstractGeometry geometry = member.getGeometry();
					SurfaceProperty property = null;

					if (geometry instanceof AbstractSurface)
						property = new SurfaceProperty((AbstractSurface) geometry);
					else if (member.isSetReference())
						property = new SurfaceProperty(member.getReference());

					if (property != null)
						compositeSurface.addSurfaceMember(property);
				}
			}

			if (compositeSurface.isSetSurfaceMember()) {
				// check whether we have to embrace the compositeSurface with an orientableSurface
				result = initOrientableSurface || (isSetOrientableSurface && !geomNode.isReverse) ?
						new SurfaceGeometry(reverseSurface(compositeSurface)) :
						new SurfaceGeometry(compositeSurface);
			}
		}

		// compositeSolid
		else if (surfaceGeometryType == GMLClass.COMPOSITE_SOLID) {
			CompositeSolid compositeSolid = new CompositeSolid();

			if (geomNode.gmlId != null)
				compositeSolid.setId(geomNode.gmlId);

			for (GeometryNode childNode : geomNode.childNodes) {
				SurfaceGeometry member = rebuildGeometry(childNode, isSetOrientableSurface, wasXlink, isImplicit);
				if (member != null) {
					AbstractGeometry geometry = member.getGeometry();
					SolidProperty property = null;

					if (geometry instanceof AbstractSolid)
						property = new SolidProperty((AbstractSolid) geometry);
					else if (member.isSetReference())
						property = new SolidProperty(member.getReference());

					if (property != null)
						compositeSolid.addSolidMember(property);
				}
			}

			result = compositeSolid.isSetSolidMember() ? new SurfaceGeometry(compositeSolid) : null;
		}

		// a simple solid
		else if (surfaceGeometryType == GMLClass.SOLID) {
			Solid solid = new Solid();

			if (geomNode.gmlId != null)
				solid.setId(geomNode.gmlId);

			if (geomNode.childNodes.size() == 1) {
				SurfaceGeometry exterior = rebuildGeometry(geomNode.childNodes.get(0), isSetOrientableSurface, wasXlink, isImplicit);
				if (exterior != null) {
					AbstractGeometry geometry = exterior.getGeometry();
					SurfaceProperty property = null;

					if (geometry instanceof AbstractSurface)
						property = new SurfaceProperty((AbstractSurface) geometry);
					else if (exterior.isSetReference())
						property = new SurfaceProperty(exterior.getReference());

					if (property != null)
						solid.setExterior(property);
				}
			}

			result = solid.isSetExterior() ? new SurfaceGeometry(solid) : null;
		}

		// multiSolid
		else if (surfaceGeometryType == GMLClass.MULTI_SOLID) {
			MultiSolid multiSolid = new MultiSolid();

			if (geomNode.gmlId != null)
				multiSolid.setId(geomNode.gmlId);

			for (GeometryNode childNode : geomNode.childNodes) {
				SurfaceGeometry member = rebuildGeometry(childNode, isSetOrientableSurface, wasXlink, isImplicit);
				if (member != null) {
					AbstractGeometry geometry = member.getGeometry();
					SolidProperty property = null;

					if (geometry instanceof AbstractSolid)
						property = new SolidProperty((AbstractSolid) geometry);
					else if (member.isSetReference())
						property = new SolidProperty(member.getReference());

					if (property != null)
						multiSolid.addSolidMember(property);
				}
			}

			result = multiSolid.isSetSolidMember() ? new SurfaceGeometry(multiSolid) : null;
		}

		// multiSurface
		else if (surfaceGeometryType == GMLClass.MULTI_SURFACE){
			MultiSurface multiSurface = new MultiSurface();

			if (geomNode.gmlId != null)
				multiSurface.setId(geomNode.gmlId);

			for (GeometryNode childNode : geomNode.childNodes) {
				SurfaceGeometry member = rebuildGeometry(childNode, isSetOrientableSurface, wasXlink, isImplicit);
				if (member != null) {
					AbstractGeometry geometry = member.getGeometry();
					SurfaceProperty property = null;

					if (geometry instanceof AbstractSurface)
						property = new SurfaceProperty((AbstractSurface) geometry);
					else if (member.isSetReference())
						property = new SurfaceProperty(member.getReference());

					if (property != null)
						multiSurface.addSurfaceMember(property);
				}
			}

			result = multiSurface.isSetSurfaceMember() ? new SurfaceGeometry(multiSurface) : null;
		}

		// triangulatedSurface
		else if (surfaceGeometryType == GMLClass.TRIANGULATED_SURFACE) {
			TriangulatedSurface triangulatedSurface = new TriangulatedSurface();

			if (geomNode.gmlId != null)
				triangulatedSurface.setId(geomNode.gmlId);

			TrianglePatchArrayProperty property = new TrianglePatchArrayProperty();
			for (GeometryNode childNode : geomNode.childNodes) {
				SurfaceGeometry member = rebuildGeometry(childNode, isSetOrientableSurface, wasXlink, isImplicit);
				if (member != null) {
					AbstractGeometry geometry = member.getGeometry();

					if (geometry instanceof Polygon) {
						// we only expect polygons
						Polygon polygon = (Polygon) geometry;
						Triangle triangle = new Triangle();

						if (polygon.isSetExterior()) {
							triangle.setExterior(polygon.getExterior());
							property.addTriangle(triangle);
						}
					}
				}
			}

			if (property.isSetTriangle() && !property.getTriangle().isEmpty()) {
				triangulatedSurface.setTrianglePatches(property);

				// check whether we have to embrace the compositeSurface with an orientableSurface
				result = initOrientableSurface || (isSetOrientableSurface && !geomNode.isReverse) ?
						new SurfaceGeometry(reverseSurface(triangulatedSurface)) :
						new SurfaceGeometry(triangulatedSurface);
			}
		}

		// cache database id in case we have to export global appearances
		if (exportAppearance
				&& !wasXlink
				&& result != null
				&& (result.getGeometry() instanceof AbstractSurface
				|| result.getGeometry() instanceof MultiSurface)) {
			result.getGeometry().setLocalProperty("global_app_cache_id", geomNode.id);
		}

		return result;
	}

	private OrientableSurface reverseSurface(AbstractSurface surface) {
		OrientableSurface orientableSurface = new OrientableSurface();
		orientableSurface.setBaseSurface(new SurfaceProperty(surface));
		orientableSurface.setOrientation(Sign.MINUS);
		return orientableSurface;
	}

	private OrientableSurface reverseSurface(String reference) {
		OrientableSurface orientableSurface = new OrientableSurface();
		orientableSurface.setBaseSurface(new SurfaceProperty(reference));
		orientableSurface.setOrientation(Sign.MINUS);
		return orientableSurface;
	}

	@Override
	public void close() throws SQLException {
		psBulk.close();
		psSelect.close();
	}

	private static class GeometryNode {
		long id;
		String gmlId;
		long parentId;
		boolean isSolid;
		boolean isComposite;
		boolean isTriangulated;
		int isXlink;
		boolean isReverse;
		GeometryObject geometry;
		List<GeometryNode> childNodes;

		GeometryNode() {
			childNodes = new ArrayList<>();
		}
	}

	private static class GeometryTree {
		final Map<Long, GeometryNode> geometryTree = new HashMap<>();
		final boolean isImplicit;
		long root;

		GeometryTree(boolean isImplicit) {
			this.isImplicit = isImplicit;
		}

		void insertNode(GeometryNode geomNode, long parentId) {
			if (parentId == 0)
				root = geomNode.id;

			if (geometryTree.containsKey(geomNode.id)) {
				// we have inserted a pseudo node previously
				// so fill that one with life...
				GeometryNode pseudoNode = geometryTree.get(geomNode.id);
				pseudoNode.id = geomNode.id;
				pseudoNode.gmlId = geomNode.gmlId;
				pseudoNode.parentId = geomNode.parentId;
				pseudoNode.isSolid = geomNode.isSolid;
				pseudoNode.isComposite = geomNode.isComposite;
				pseudoNode.isTriangulated = geomNode.isTriangulated;
				pseudoNode.isXlink = geomNode.isXlink;
				pseudoNode.isReverse = geomNode.isReverse;
				pseudoNode.geometry = geomNode.geometry;

				geomNode = pseudoNode;
			} else {
				// identify hierarchy nodes and place them
				// into the tree
				if (geomNode.geometry == null || parentId == 0)
					geometryTree.put(geomNode.id, geomNode);
			}

			// make the node known to its parent...
			if (parentId != 0) {
				GeometryNode parentNode = geometryTree.get(parentId);
				if (parentNode == null) {
					// there is no entry so far, so lets create a
					// pseudo node
					parentNode = new GeometryNode();
					geometryTree.put(parentId, parentNode);
				}

				parentNode.childNodes.add(geomNode);
			}
		}

		GeometryNode getNode(long entryId) {
			return geometryTree.get(entryId);
		}
	}

	private static class SurfaceGeometryContext {
		final long id;
		final GeometrySetterHandler handler;
		final boolean isImplicit;

		SurfaceGeometryContext(long id, GeometrySetterHandler handler, boolean isImplicit) {
			this.id = id;
			this.handler = handler;
			this.isImplicit = isImplicit;
		}
	}
}
