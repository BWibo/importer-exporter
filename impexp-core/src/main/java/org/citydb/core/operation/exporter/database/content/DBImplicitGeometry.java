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
import org.citydb.core.database.schema.mapping.MappingConstants;
import org.citydb.core.operation.common.xlink.DBXlinkLibraryObject;
import org.citydb.core.operation.exporter.CityGMLExportException;
import org.citydb.core.operation.exporter.util.AttributeValueSplitter;
import org.citydb.core.util.CoreConstants;
import org.citydb.sqlbuilder.expression.PlaceHolder;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.select.Select;
import org.citydb.sqlbuilder.select.join.JoinFactory;
import org.citydb.sqlbuilder.select.operator.comparison.ComparisonFactory;
import org.citydb.sqlbuilder.select.operator.comparison.ComparisonName;
import org.citydb.sqlbuilder.select.projection.Function;
import org.citygml4j.geometry.Matrix;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.citygml.core.TransformationMatrix4x4;
import org.citygml4j.model.gml.basicTypes.Code;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.GeometryProperty;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DBImplicitGeometry implements DBExporter {
	private final CityGMLExportManager exporter;
	private final PreparedStatement ps;
	private final DBSurfaceGeometry geometryExporter;
	private final GMLConverter gmlConverter;
	private final MessageDigest md5;
	private final AttributeValueSplitter valueSplitter;
	private final boolean affineTransformation;

	public DBImplicitGeometry(Connection connection, CityGMLExportManager exporter) throws CityGMLExportException, SQLException {
		this.exporter = exporter;

		geometryExporter = exporter.getExporter(DBSurfaceGeometry.class);
		gmlConverter = exporter.getGMLConverter();
		valueSplitter = exporter.getAttributeValueSplitter();
		affineTransformation = exporter.getExportConfig().getAffineTransformation().isEnabled();
		String getLength = exporter.getDatabaseAdapter().getSQLAdapter().resolveDatabaseOperationName("blob.get_length");
		String schema = exporter.getDatabaseAdapter().getConnectionDetails().getSchema();

		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new CityGMLExportException(e);
		}
		
		Table table = new Table(TableEnum.IMPLICIT_GEOMETRY.getName(), schema);
		Table surfaceGeometry = new Table(TableEnum.SURFACE_GEOMETRY.getName(), schema);

		Select select = new Select().addProjection(table.getColumns("id", "mime_type", "reference_to_library", "relative_brep_id", "relative_other_geom"))
				.addProjection(new Function(getLength, "db_library_object_length", table.getColumn("library_object")))
				.addProjection(surfaceGeometry.getColumn("gmlid"))
				.addJoin(JoinFactory.left(surfaceGeometry, "id", ComparisonName.EQUAL_TO, table.getColumn("relative_brep_id")))
				.addSelection(ComparisonFactory.equalTo(table.getColumn("id"), new PlaceHolder<>()));
		ps = connection.prepareStatement(select.toString());
	}

	protected ImplicitGeometry doExport(long id, GeometryObject referencePoint, String transformationMatrix) throws CityGMLExportException, SQLException {
		ps.setLong(1, id);
		
		try (ResultSet rs = ps.executeQuery()) {		
			ImplicitGeometry implicit = new ImplicitGeometry();
			boolean isValid = false;

			if (rs.next()) {
				// library object
				String blobURI = rs.getString(3);
				if (!rs.wasNull()) {
					isValid = true;

					long dbBlobSize = rs.getLong(6);
					if (dbBlobSize > 0) {
						String fileName = new File(blobURI).getName();
						implicit.setLibraryObject(CoreConstants.LIBRARY_OBJECTS_DIR + '/' + fileName);

						exporter.propagateXlink(new DBXlinkLibraryObject(
								id,
								fileName));
					} else
						implicit.setLibraryObject(blobURI);

					implicit.setMimeType(new Code(rs.getString(2)));
				}

				// geometry
				long geometryId = rs.getLong(4);
				if (!rs.wasNull()) {
					isValid = true;
					String gmlId = rs.getString(7);

					if (exporter.lookupGeometryId(gmlId)) {
						implicit.setRelativeGeometry(new GeometryProperty<>("#" + gmlId));
					} else {
						geometryExporter.addImplicitGeometryBatch(geometryId, implicit);
					}
				} else {
					Object otherGeomObj = rs.getObject(5);
					if (!rs.wasNull()) {
						isValid = true;
						long implicitId = rs.getLong(1);
						String uuid = toHexString(md5.digest(String.valueOf(implicitId).getBytes()));

						if (exporter.lookupAndPutObjectId(uuid, implicitId, MappingConstants.IMPLICIT_GEOMETRY_OBJECTCLASS_ID)) {
							implicit.setRelativeGeometry(new GeometryProperty<>("#UUID_" + uuid));
						} else {
							GeometryObject otherGeom = exporter.getDatabaseAdapter().getGeometryConverter().getGeometry(otherGeomObj);
							AbstractGeometry geometry = gmlConverter.getPointOrCurveGeometry(otherGeom, true);
							if (geometry != null) {
								geometry.setId("UUID_" + uuid);
								implicit.setRelativeGeometry(new GeometryProperty<>(geometry));
							} else
								isValid = false;
						}
					}
				}
			}

			if (!isValid)
				return null;

			// referencePoint
			if (referencePoint != null)
				implicit.setReferencePoint(gmlConverter.getPointProperty(referencePoint, false));

			// transformationMatrix
			if (transformationMatrix != null) {
				List<Double> m = valueSplitter.splitDoubleList(transformationMatrix);
				if (m.size() >= 16) {
					Matrix matrix = new Matrix(4, 4);
					matrix.setMatrix(m.subList(0, 16));

					if (affineTransformation) {
						matrix = exporter.getAffineTransformer().transformImplicitGeometryTransformationMatrix(matrix);
					}

					implicit.setTransformationMatrix(new TransformationMatrix4x4(matrix));
				}
			}

			return implicit;
		}
	}

	private String toHexString(byte[] bytes) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : bytes)
			hexString.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));

		return hexString.toString();
	}

	@Override
	public void close() throws SQLException {
		ps.close();
	}
}
