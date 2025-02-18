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
package org.citydb.vis.database;

import net.opengis.kml._2.AltitudeModeEnumType;
import net.opengis.kml._2.LineStringType;
import net.opengis.kml._2.PlacemarkType;
import net.opengis.kml._2.PointType;
import org.citydb.config.Config;
import org.citydb.config.geometry.GeometryObject;
import org.citydb.config.geometry.GeometryType;
import org.citydb.config.project.visExporter.Balloon;
import org.citydb.config.project.visExporter.ColladaOptions;
import org.citydb.config.project.visExporter.DisplayFormType;
import org.citydb.config.project.visExporter.Style;
import org.citydb.config.project.visExporter.Styles;
import org.citydb.core.database.adapter.AbstractDatabaseAdapter;
import org.citydb.core.database.adapter.BlobExportAdapter;
import org.citydb.util.event.EventDispatcher;
import org.citydb.util.event.global.GeometryCounterEvent;
import org.citydb.util.log.Logger;
import org.citydb.core.query.Query;
import org.citydb.vis.util.BalloonTemplateHandler;
import org.citydb.vis.util.ElevationServiceHandler;

import javax.vecmath.Point3d;
import javax.xml.bind.JAXBException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Transportation extends AbstractVisObject {
	private final Logger log = Logger.getInstance();

	public static final String STYLE_BASIS_NAME = "Transportation";

	public Transportation(Connection connection,
			Query query,
			VisExporterManager visExporterManager,
			net.opengis.kml._2.ObjectFactory kmlFactory,
			AbstractDatabaseAdapter databaseAdapter,
			BlobExportAdapter textureExportAdapter,
			ElevationServiceHandler elevationServiceHandler,
			BalloonTemplateHandler balloonTemplateHandler,
			EventDispatcher eventDispatcher,
			Config config) {

		super(connection,
				query,
                visExporterManager,
				kmlFactory,
				databaseAdapter,
				textureExportAdapter,
				elevationServiceHandler,
				balloonTemplateHandler,
				eventDispatcher,
				config);
	}

	protected Styles getStyles() {
		return config.getVisExportConfig().getTransportationStyles();
	}

	public Balloon getBalloonSettings() {
		return config.getVisExportConfig().getTransportationBalloon();
	}

	public String getStyleBasisName() {
		return STYLE_BASIS_NAME;
	}

	public void read(DBSplittingResult work) {
		PreparedStatement psQuery = null;
		ResultSet rs = null;

		try {
			int lodToExportFrom = currentLod = config.getVisExportConfig().getLodToExportFrom();
			currentLod = lodToExportFrom == 5 ? 4 : lodToExportFrom;
			int minLod = lodToExportFrom == 5 ? 0 : lodToExportFrom;
			boolean found = false;

			while (!found && currentLod >= minLod) {
				if (!work.getDisplayForm().isAchievableFromLoD(currentLod)) 
					break;

				try {
					String query = queries.getTransportationQuery(currentLod, work.getDisplayForm(), work.getObjectClassId());
					psQuery = connection.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
					for (int i = 1; i <= getParameterCount(query); i++)
						psQuery.setLong(i, work.getId());

					rs = psQuery.executeQuery();
					if (rs.isBeforeFirst())
						found = true; // result set not empty
					else
						currentLod--;
				} catch (Exception e) {
					log.error("SQL error while querying the highest available LOD.", e);
					try { connection.commit(); } catch (SQLException sqle) {}
				} finally {
					if (!found) {
						try { rs.close(); } catch (SQLException sqle) {} 
						try { psQuery.close(); } catch (SQLException sqle) {}
						rs = null;
					}
				}
			}

			if (rs == null) { // result empty, give up
				String fromMessage = " from LoD" + lodToExportFrom;
				if (lodToExportFrom == 5) {
					if (work.getDisplayForm().getType() == DisplayFormType.COLLADA)
						fromMessage = ". LoD1 or higher required";
					else
						fromMessage = " from any LoD";
				}
				log.info("Could not display object " + work.getGmlId() + " as " + work.getDisplayForm().getName() + fromMessage + ".");
			}
			else { // result not empty
				visExporterManager.updateFeatureTracker(work);

				if (currentLod == 0) { // LoD0_Network
					visExporterManager.print(createPlacemarksForLoD0Network(rs, work),
							work,
							getBalloonSettings().isBalloonContentInSeparateFile());
				}
				else {
					switch (work.getDisplayForm().getType()) {
					case FOOTPRINT:
						visExporterManager.print(createPlacemarksForFootprint(rs, work),
								work,
								getBalloonSettings().isBalloonContentInSeparateFile());
						break;

					case EXTRUDED:
						PreparedStatement psQuery2 = null;
						ResultSet rs2 = null;

						try {
							String query = queries.getExtrusionHeight();
							psQuery2 = connection.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							for (int i = 1; i <= getParameterCount(query); i++)
								psQuery2.setLong(i, work.getId());

							rs2 = psQuery2.executeQuery();
							rs2.next();

							double measuredHeight = rs2.getDouble("envelope_measured_height");
							visExporterManager.print(createPlacemarksForExtruded(rs, work, measuredHeight, false), work, getBalloonSettings().isBalloonContentInSeparateFile());
							break;
						} finally {
							try { if (rs2 != null) rs2.close(); } catch (SQLException e) {}
							try { if (psQuery2 != null) psQuery2.close(); } catch (SQLException e) {}
						}

					case GEOMETRY:
						setGmlId(work.getGmlId());
						setId(work.getId());
						visExporterManager.print(createPlacemarksForGeometry(rs, work), work, getBalloonSettings().isBalloonContentInSeparateFile());
						if (getStyle(work.getDisplayForm().getType()).isHighlightingEnabled())
							visExporterManager.print(createPlacemarksForHighlighting(rs, work), work, getBalloonSettings().isBalloonContentInSeparateFile());
						break;

					case COLLADA:
						ColladaOptions colladaOptions = config.getVisExportConfig().getColladaOptions();

						fillGenericObjectForCollada(rs, colladaOptions.isGenerateTextureAtlases());
						String currentgmlId = getGmlId();
						setGmlId(work.getGmlId());
						setId(work.getId());

						if (currentgmlId != null && !currentgmlId.equals(work.getGmlId()) && getGeometryAmount() > GEOMETRY_AMOUNT_WARNING)
							log.info("Object " + work.getGmlId() + " has more than " + GEOMETRY_AMOUNT_WARNING + " geometries. This may take a while to process...");

						List<Point3d> anchorCandidates = getOrigins(); // setOrigins() called mainly for the side-effect
						double zOffset = getZOffsetFromConfigOrDB(work.getId());
						if (zOffset == Double.MAX_VALUE) {
							zOffset = getZOffsetFromGEService(work.getId(), anchorCandidates);
						}
						setZOffset(zOffset);

						setIgnoreSurfaceOrientation(colladaOptions.isIgnoreSurfaceOrientation());
						try {
							if (getStyle(work.getDisplayForm().getType()).isHighlightingEnabled())
								visExporterManager.print(createPlacemarksForHighlighting(rs, work), work, getBalloonSettings().isBalloonContentInSeparateFile());
						} catch (Exception ioe) {
							log.logStackTrace(ioe);
						}

						break;
					}
				}				
			}
		} catch (SQLException sqlEx) {
			log.error("SQL error while querying city object " + work.getGmlId() + ": " + sqlEx.getMessage());
		} catch (JAXBException jaxbEx) {
			log.error("XML error while working on city object " + work.getGmlId() + ": " + jaxbEx.getMessage());
		} finally {
			if (rs != null)
				try { rs.close(); } catch (SQLException e) {}
			if (psQuery != null)
				try { psQuery.close(); } catch (SQLException e) {}
		}
	}

	public PlacemarkType createPlacemarkForColladaModel() throws SQLException {
		double[] originInWGS84 = convertPointCoordinatesToWGS84(new double[] {getOrigin().x,
				getOrigin().y,
				getOrigin().z});
		setLocation(reducePrecisionForXorY(originInWGS84[0]),
				reducePrecisionForXorY(originInWGS84[1]),
				reducePrecisionForZ(originInWGS84[2]));

		return super.createPlacemarkForColladaModel();
	}

	private List<PlacemarkType> createPlacemarksForLoD0Network(ResultSet rs,
			DBSplittingResult work) throws SQLException {

		Style style = getStyles().getOrDefault(DisplayFormType.FOOTPRINT);

		List<PlacemarkType> placemarkList= new ArrayList<>();
		
		double zOffset = getZOffsetFromConfigOrDB(work.getId());
		List<Point3d> lowestPointCandidates = getLowestPointsCoordinates(rs, null, (zOffset == Double.MAX_VALUE));
		rs.beforeFirst(); // return cursor to beginning
		if (zOffset == Double.MAX_VALUE) {
			zOffset = getZOffsetFromGEService(work.getId(), lowestPointCandidates);
		}

		while (rs.next()) {
			PlacemarkType placemark = kmlFactory.createPlacemarkType();
			placemark.setName(work.getGmlId());
			placemark.setId(/* DisplayForm.FOOTPRINT_PLACEMARK_ID + */ placemark.getName());
			if (style.isHighlightingEnabled())
				placemark.setStyleUrl("#" + getStyleBasisName() + DisplayFormType.FOOTPRINT.getName() + "Style");
			else
				placemark.setStyleUrl("#" + getStyleBasisName() + DisplayFormType.FOOTPRINT.getName() + "Normal");

			Object buildingGeometryObj = rs.getObject(1); 
			if (!rs.wasNull() && buildingGeometryObj != null) {
				GeometryObject pointOrCurveGeometry = geometryConverterAdapter.getGeometry(buildingGeometryObj);
				eventDispatcher.triggerEvent(new GeometryCounterEvent(null, this));

				if (pointOrCurveGeometry.getGeometryType() == GeometryType.POINT) { // point
					double[] ordinatesArray = pointOrCurveGeometry.getCoordinates(0);
					ordinatesArray = super.convertPointCoordinatesToWGS84(ordinatesArray);

					PointType point = kmlFactory.createPointType();
					point.getCoordinates().add(reducePrecisionForXorY(ordinatesArray[0]) + ","
							+ reducePrecisionForXorY(ordinatesArray[1]) + ","
							+ reducePrecisionForZ(ordinatesArray[2]) + zOffset);

					placemark.setAbstractGeometryGroup(kmlFactory.createPoint(point));
				}
				else { // curve
					pointOrCurveGeometry = super.convertToWGS84(pointOrCurveGeometry);
					LineStringType lineString = kmlFactory.createLineStringType();
					
					for (int i = 0; i < pointOrCurveGeometry.getNumElements(); i++) {
						double[] ordinatesArray = pointOrCurveGeometry.getCoordinates(i);

						// order points clockwise
						for (int j = 0; j < ordinatesArray.length; j = j+3) {
							lineString.getCoordinates().add(reducePrecisionForXorY(ordinatesArray[j]) + ","
									+ reducePrecisionForXorY(ordinatesArray[j + 1]) + ","
									+ reducePrecisionForZ(ordinatesArray[j + 2] + zOffset));
						}
					}
					
					switch (config.getVisExportConfig().getElevation().getAltitudeMode()) {
					case ABSOLUTE:
						lineString.setAltitudeModeGroup(kmlFactory.createAltitudeMode(AltitudeModeEnumType.ABSOLUTE));
						break;
					case RELATIVE:
						lineString.setAltitudeModeGroup(kmlFactory.createAltitudeMode(AltitudeModeEnumType.RELATIVE_TO_GROUND));
						break;
					case CLAMP_TO_GROUND:
						lineString.setAltitudeModeGroup(kmlFactory.createAltitudeMode(AltitudeModeEnumType.CLAMP_TO_GROUND));
						break;
					}
					
					placemark.setAbstractGeometryGroup(kmlFactory.createLineString(lineString));
				}
			}
			// replace default BalloonTemplateHandler with a brand new one, this costs resources!
			if (getBalloonSettings().isIncludeDescription()) {
				addBalloonContents(placemark, work.getId());
			}
			placemarkList.add(placemark);
		}
		return placemarkList;
	}

}
