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
package org.citydb.core.operation.importer.database.content;

import org.citydb.config.Config;
import org.citydb.config.geometry.GeometryObject;
import org.citydb.config.project.database.DatabaseType;
import org.citydb.core.database.adapter.AbstractDatabaseAdapter;
import org.citydb.core.operation.common.util.AffineTransformer;
import org.citydb.core.operation.importer.CityGMLImportException;
import org.citydb.core.operation.importer.util.RingValidator;
import org.citygml4j.model.gml.GMLClass;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.GeometryProperty;
import org.citygml4j.model.gml.geometry.aggregates.*;
import org.citygml4j.model.gml.geometry.complexes.CompositeSolid;
import org.citygml4j.model.gml.geometry.complexes.CompositeSurface;
import org.citygml4j.model.gml.geometry.complexes.GeometricComplex;
import org.citygml4j.model.gml.geometry.complexes.GeometricComplexProperty;
import org.citygml4j.model.gml.geometry.primitives.*;
import org.citygml4j.util.child.ChildInfo;
import org.citygml4j.util.walker.GeometryWalker;

import java.util.*;

public class GeometryConverter {
	private AffineTransformer affineTransformer;
	private final RingValidator ringValidator;
	private final int dbSrid;
	private final boolean hasSolidSupport;

	private boolean affineTransformation;

	public GeometryConverter(AbstractDatabaseAdapter databaseAdapter) {
		ringValidator = new RingValidator();
		dbSrid = databaseAdapter.getConnectionMetaData().getReferenceSystem().getSrid();

		// solid geometries are only supported in Oracle 11g or higher
		hasSolidSupport = databaseAdapter.getDatabaseType() != DatabaseType.ORACLE
				|| databaseAdapter.getConnectionMetaData().getDatabaseMajorVersion() > 10;
	}

	public GeometryConverter(AbstractDatabaseAdapter databaseAdapter, AffineTransformer affineTransformer, Config config) {
		this(databaseAdapter);

		affineTransformation = config.getImportConfig().getAffineTransformation().isEnabled();
		if (affineTransformation) {
			this.affineTransformer = affineTransformer;
		}
	}

	public boolean isSurfaceGeometry(AbstractGeometry geometry) {
		boolean hasUnsupportedGeometry = false;

		switch (geometry.getGMLClass()) {
			case POLYGON:
			case ORIENTABLE_SURFACE:
			case _TEXTURED_SURFACE:
			case COMPOSITE_SURFACE:
			case SURFACE:
			case TRIANGULATED_SURFACE:
			case TIN:
			case SOLID:
			case COMPOSITE_SOLID:
			case MULTI_POLYGON:
			case MULTI_SURFACE:
			case MULTI_SOLID:
				return true;
			case GEOMETRIC_COMPLEX:
				GeometricComplex complex = (GeometricComplex) geometry;
				for (GeometricPrimitiveProperty property : complex.getElement()) {
					if (property.isSetGeometricPrimitive()) {
						if (!isSurfaceGeometry(property.getGeometricPrimitive())) {
							hasUnsupportedGeometry = true;
							break;
						}
					}
				}

				return !hasUnsupportedGeometry;
			case MULTI_GEOMETRY:
				MultiGeometry multiGeometry = (MultiGeometry) geometry;
				for (GeometryProperty<?> property : multiGeometry.getGeometryMember()) {
					if (property.isSetGeometry()) {
						if (!isSurfaceGeometry(property.getGeometry())) {
							hasUnsupportedGeometry = true;
							break;
						}
					}
				}

				if (!hasUnsupportedGeometry && multiGeometry.isSetGeometryMembers()) {
					for (AbstractGeometry member : multiGeometry.getGeometryMembers().getGeometry()) {
						if (!isSurfaceGeometry(member)) {
							hasUnsupportedGeometry = true;
							break;
						}
					}
				}

				return !hasUnsupportedGeometry;
			default:
				return false;
		}
	}

	public boolean isPointOrLineGeometry(AbstractGeometry abstractGeometry) {
		switch (abstractGeometry.getGMLClass()) {
			case POINT:
			case MULTI_POINT:
			case LINE_STRING:
			case CURVE:
			case COMPOSITE_CURVE:
			case ORIENTABLE_CURVE:
			case MULTI_CURVE:
				return true;
			case GEOMETRIC_COMPLEX:
				GeometricComplex complex = (GeometricComplex) abstractGeometry;
				return containsPointPrimitives(complex) || containsCurvePrimitives(complex);
			case MULTI_GEOMETRY:
				MultiGeometry multiGeometry = (MultiGeometry) abstractGeometry;
				return containsPointPrimitives(multiGeometry) || containsCurvePrimitives(multiGeometry);
			default:
				return false;
		}
	}

	public GeometryObject getPoint(Point point) {
		if (point != null) {
			List<Double> coords = point.toList3d();
			if (!coords.isEmpty()) {
				return GeometryObject.createPoint(convertPrimitive(coords), 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getPointGeometry(GeometricComplex geometricComplex) {
		if (geometricComplex != null && geometricComplex.isSetElement()) {
			List<List<Double>> pointList = new ArrayList<>();

			for (GeometricPrimitiveProperty primitiveProperty : geometricComplex.getElement()) {
				if (primitiveProperty.isSetGeometricPrimitive()) {
					AbstractGeometricPrimitive primitive = primitiveProperty.getGeometricPrimitive();
					if (primitive.getGMLClass() == GMLClass.POINT) {
						List<Double> coords = ((Point)primitive).toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}
				}
			}

			if (!pointList.isEmpty()) {				
				double[][] pointArray = convertAggregate(pointList);
				return pointList.size() > 1 ?
						GeometryObject.createMultiPoint(pointArray, 3, dbSrid) :
						GeometryObject.createPoint(pointArray[0], 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getPointGeometry(MultiGeometry multiGeometry) {
		List<List<Double>> pointList = new ArrayList<>();

		if (multiGeometry != null) {
			if (multiGeometry.isSetGeometryMember()) {
				for (GeometryProperty<?> property : multiGeometry.getGeometryMember()) {
					if (property.isSetGeometry() && property.getGeometry().getGMLClass() == GMLClass.POINT) {
						List<Double> coords = ((Point) property.getGeometry()).toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}
				}
			}

			if (multiGeometry.isSetGeometryMembers()) {
				for (AbstractGeometry member : multiGeometry.getGeometryMembers().getGeometry()) {
					if (member != null && member.getGMLClass() == GMLClass.POINT) {
						List<Double> coords = ((Point) member).toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}
				}
			}
		}

		if (!pointList.isEmpty()) {
			double[][] pointArray = convertAggregate(pointList);
			return pointList.size() > 1 ?
					GeometryObject.createMultiPoint(pointArray, 3, dbSrid) :
					GeometryObject.createPoint(pointArray[0], 3, dbSrid);
		}

		return null;
	}

	public GeometryObject getMultiPoint(MultiPoint multiPoint) {
		if (multiPoint != null) {
			List<List<Double>> pointList = new ArrayList<>();

			if (multiPoint.isSetPointMember()) {
				for (PointProperty property : multiPoint.getPointMember())
					if (property.isSetPoint()) {
						List<Double> coords = property.getPoint().toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}

			} else if (multiPoint.isSetPointMembers()) {
				PointArrayProperty property = multiPoint.getPointMembers();
				for (Point point : property.getPoint()) {
					if (point != null) {
						List<Double> coords = point.toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}
				}
			}

			if (!pointList.isEmpty()) {
				return GeometryObject.createMultiPoint(convertAggregate(pointList), 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getMultiPoint(ControlPoint controlPoint) {
		if (controlPoint != null) {
			List<List<Double>> pointList = new ArrayList<>();

			if (controlPoint.isSetPosList()) {
				List<Double> coords = controlPoint.getPosList().toList3d();
				if (!coords.isEmpty()) {
					for (int i = 0; i < coords.size(); i += 3) {
						pointList.add(coords.subList(i, i + 3));
					}
				}

			} else if (controlPoint.isSetGeometricPositionGroup()) {					
				for (GeometricPositionGroup posGroup : controlPoint.getGeometricPositionGroup()) {
					if (posGroup.isSetPos()) {
						List<Double> coords = posGroup.getPos().toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					} else if (posGroup.isSetPointProperty() && posGroup.getPointProperty().isSetPoint()) {
						List<Double> coords = posGroup.getPointProperty().getPoint().toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}
				}
			}

			if (!pointList.isEmpty()) {
				return GeometryObject.createMultiPoint(convertAggregate(pointList), 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getCurve(AbstractCurve curve) {
		if (curve != null) {
			List<Double> pointList = curve.toList3d();
			if (!pointList.isEmpty()) {
				return GeometryObject.createCurve(convertPrimitive(pointList), 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getMultiCurve(MultiCurve multiCurve) {
		if (multiCurve != null) {
			List<List<Double>> pointList = new ArrayList<>();

			if (multiCurve.isSetCurveMember()) {
				for (CurveProperty property : multiCurve.getCurveMember()) {
					if (property.isSetCurve()) {
						List<Double> points = property.getCurve().toList3d();
						if (!points.isEmpty()) {
							pointList.add(points);
						}
					}
				}
			} else if (multiCurve.isSetCurveMembers()) {
				CurveArrayProperty property = multiCurve.getCurveMembers();
				for (AbstractCurve curve : property.getCurve()) {
					if (curve != null) {
						List<Double> points = curve.toList3d();
						if (!points.isEmpty()) {
							pointList.add(points);
						}
					}
				}
			}

			if (!pointList.isEmpty()) {
				return GeometryObject.createMultiCurve(convertAggregate(pointList), 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getCurveGeometry(GeometricComplex geometricComplex) {
		if (geometricComplex != null && geometricComplex.isSetElement()) {
			List<List<Double>> pointList = new ArrayList<>();

			for (GeometricPrimitiveProperty primitiveProperty : geometricComplex.getElement()) {
				if (primitiveProperty.isSetGeometricPrimitive()) {
					AbstractGeometricPrimitive primitive = primitiveProperty.getGeometricPrimitive();
					if (primitive instanceof AbstractCurve) {
						List<Double> points = ((AbstractCurve) primitive).toList3d();
						if (!points.isEmpty()) {
							pointList.add(points);
						}
					}
				}
			}

			if (!pointList.isEmpty()) {
				double[][] pointArray = convertAggregate(pointList);
				return pointList.size() > 1 ?
						GeometryObject.createMultiCurve(pointArray, 3, dbSrid) :
						GeometryObject.createCurve(pointArray[0], 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getCurveGeometry(MultiGeometry multiGeometry) {
		List<List<Double>> pointList = new ArrayList<>();

		if (multiGeometry != null) {
			if (multiGeometry.isSetGeometryMember()) {
				for (GeometryProperty<?> property : multiGeometry.getGeometryMember()) {
					if (property.isSetGeometry() && property.getGeometry() instanceof AbstractCurve) {
						List<Double> coords = ((AbstractCurve) property.getGeometry()).toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}
				}
			}

			if (multiGeometry.isSetGeometryMembers()) {
				for (AbstractGeometry member : multiGeometry.getGeometryMembers().getGeometry()) {
					if (member instanceof AbstractCurve) {
						List<Double> coords = ((AbstractCurve) member).toList3d();
						if (!coords.isEmpty()) {
							pointList.add(coords);
						}
					}
				}
			}
		}

		if (!pointList.isEmpty()) {
			double[][] pointArray = convertAggregate(pointList);
			return pointList.size() > 1 ?
					GeometryObject.createMultiCurve(pointArray, 3, dbSrid) :
					GeometryObject.createCurve(pointArray[0], 3, dbSrid);
		}

		return null;
	}

	public GeometryObject getMultiCurve(List<LineStringSegmentArrayProperty> propertyList) {
		if (propertyList != null && !propertyList.isEmpty()) {
			List<List<Double>> pointList = new ArrayList<>();

			for (LineStringSegmentArrayProperty property : propertyList) {
				if (property.isSetLineStringSegment()) {
					List<Double> points = new ArrayList<>();

					for (LineStringSegment segment : property.getLineStringSegment()) {
						List<Double> coords = segment.toList3d();
						if (!coords.isEmpty()) {
							points.addAll(coords);
						}
					}

					if (!points.isEmpty()) {
						pointList.add(points);
					}
				}
			}

			if (!pointList.isEmpty()) {
				return GeometryObject.createMultiCurve(convertAggregate(pointList), 3, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getPoint(PointProperty pointProperty) {
		return pointProperty != null ?
				getPoint(pointProperty.getPoint()) :
				null;
	}

	public GeometryObject getMultiPoint(MultiPointProperty multiPointProperty) {
		return multiPointProperty != null ?
				getMultiPoint(multiPointProperty.getMultiPoint()) :
				null;
	}

	public GeometryObject getPointGeometry(GeometricComplexProperty complexProperty) {
		return complexProperty != null && complexProperty.isSetGeometricComplex() ?
				getPointGeometry(complexProperty.getGeometricComplex()) :
				null;
	}

	public GeometryObject getCurve(CurveProperty curveProperty) {
		return curveProperty != null ?
				getCurve(curveProperty.getCurve()) :
				null;
	}

	public GeometryObject getMultiCurve(MultiCurveProperty multiCurveProperty) {
		return multiCurveProperty != null ?
				getMultiCurve(multiCurveProperty.getMultiCurve()) :
				null;
	}

	public GeometryObject getCurveGeometry(GeometricComplexProperty complexProperty) {
		return complexProperty != null && complexProperty.isSetGeometricComplex() ?
				getCurveGeometry(complexProperty.getGeometricComplex()) :
				null;
	}

	public GeometryObject getPointOrCurveGeometry(AbstractGeometry abstractGeometry) {
		switch (abstractGeometry.getGMLClass()) {
			case POINT:
				return getPoint((Point) abstractGeometry);
			case MULTI_POINT:
				return getMultiPoint((MultiPoint) abstractGeometry);
			case LINE_STRING:
			case CURVE:
			case COMPOSITE_CURVE:
			case ORIENTABLE_CURVE:
				return getCurve((AbstractCurve) abstractGeometry);
			case MULTI_CURVE:
				return getMultiCurve((MultiCurve) abstractGeometry);
			case GEOMETRIC_COMPLEX:
				GeometricComplex complex = (GeometricComplex) abstractGeometry;
				if (containsPointPrimitives(complex)) {
					return getPointGeometry((GeometricComplex) abstractGeometry);
				} else if (containsCurvePrimitives(complex)) {
					return getCurveGeometry((GeometricComplex) abstractGeometry);
				} else {
					return null;
				}
			case MULTI_GEOMETRY:
				MultiGeometry multiGeometry = (MultiGeometry) abstractGeometry;
				if (containsPointPrimitives(multiGeometry)) {
					return getPointGeometry((MultiGeometry) abstractGeometry);
				} else if (containsCurvePrimitives(multiGeometry)) {
					return getCurveGeometry((MultiGeometry) abstractGeometry);
				} else {
					return null;
				}
			default:
				return null;
		}
	}

	private double[] convertPrimitive(List<Double> pointList) {
		if (affineTransformation) {
			affineTransformer.transformCoordinates(pointList);
		}

		double[] result = new double[pointList.size()];

		int i = 0;
		for (Double point : pointList) {
			result[i++] = point;
		}

		return result;
	}

	private double[][] convertAggregate(List<List<Double>> pointList) {
		double[][] result = new double[pointList.size()][];
		int i = 0;
		for (List<Double> points : pointList) {
			if (affineTransformation) {
				affineTransformer.transformCoordinates(points);
			}

			double[] coords = new double[points.size()];

			int j = 0;
			for (Double coord : points) {
				coords[j++] = coord;
			}

			result[i++] = coords;					
		}

		return result;
	}

	public GeometryObject get2DPolygon(Polygon polygon) {
		return getPolygon(polygon, true);
	}

	public GeometryObject getPolygon(Polygon polygon) throws CityGMLImportException {
		return getPolygon(polygon, false);
	}

	private GeometryObject getPolygon(Polygon polygon, boolean is2d) {
		GeometryObject polygonGeom = null;

		if (polygon != null) {
			List<List<Double>> pointList = generatePointList(polygon, is2d, false);
			if (pointList != null && !pointList.isEmpty()) {
				polygonGeom = GeometryObject.createPolygon(convertAggregate(pointList), is2d ? 2 : 3, dbSrid);
			}
		}

		return polygonGeom;
	}

	public GeometryObject get2DPolygon(PolygonProperty polygonProperty) {
		return polygonProperty != null ?
				get2DPolygon(polygonProperty.getPolygon()) :
				null;
	}

	public GeometryObject getPolygon(PolygonProperty polygonProperty) throws CityGMLImportException {
		return polygonProperty != null ?
				getPolygon(polygonProperty.getPolygon()) :
				null;
	}

	private List<List<Double>> generatePointList(Polygon polygon, boolean is2d, boolean reverse) {
		List<List<Double>> pointList = new ArrayList<>();

		if (polygon.isSetExterior()) {
			AbstractRing exteriorRing = polygon.getExterior().getRing();
			if (exteriorRing != null) {
				List<Double> coords = exteriorRing.toList3d(reverse);
				if (!ringValidator.validate(coords, exteriorRing)) {
					return null;
				}

				pointList.add(coords);

				if (polygon.isSetInterior()) {
					for (AbstractRingProperty abstractRingProperty : polygon.getInterior()) {
						AbstractRing interiorRing = abstractRingProperty.getRing();
						if (interiorRing != null) {
							coords = interiorRing.toList3d(reverse);
							if (!ringValidator.validate(coords, interiorRing)) {
								continue;
							}

							pointList.add(coords);
						}
					}
				}
			}

			if (is2d) {
				// if we have to return a 2d polygon we first have to correct the
				// double lists we retrieved from citygml4j as they are always 3d
				for (List<Double> coordsList : pointList) {							
					Iterator<Double> iter = coordsList.iterator();

					int count = 0;							
					while (iter.hasNext()) {
						iter.next();

						if (count++ == 2) {
							count = 0;	
							iter.remove();
						}
					}
				}						
			}
		}

		return pointList;
	}

	public GeometryObject getSolid(Solid solid) {
		if (!hasSolidSupport) {
			return null;
		}

		if (solid != null) {
			final List<List<Double>> pointList = new ArrayList<>();
			final List<Integer> rings = new ArrayList<>();

			solid.accept(new GeometryWalker() {
				boolean reverse = false;
				int ringNo = 0;

				public <T extends AbstractGeometry> void visit(GeometryProperty<T> geometryProperty) {
					if (geometryProperty.isSetHref()) {
						setShouldWalk(false);
						pointList.clear();
						return;
					}

					super.visit(geometryProperty);
				}

				public void visit(OrientableSurface orientableSurface) {
					if (orientableSurface.getOrientation() == Sign.MINUS) {
						reverse = !reverse;
						super.visit(orientableSurface);
						reverse = !reverse;
					} else {
						super.visit(orientableSurface);
					}
				}

				public void visit(Polygon polygon) {
					List<List<Double>> points = generatePointList(polygon, false, reverse);
					if (points == null || points.isEmpty()) {
						setShouldWalk(false);
						pointList.clear();
						return;
					}

					pointList.addAll(points);
					rings.add(ringNo);
					ringNo += points.size();
				}

				public void visit(PolygonPatch polygonPatch) {
					Polygon polygon = new Polygon();
					polygon.setExterior(polygonPatch.getExterior());
					polygon.setInterior(polygonPatch.getInterior());
					visit(polygon);
				}

				public void visit(AbstractRing ring) {
					// required to handle surface patches such as triangles and rectangles
					List<Double> points = ring.toList3d(reverse);
					if (ringValidator.validate(points, ring)) {
						pointList.add(points);
						rings.add(ringNo);
						ringNo++;
					}
				}

			});

			if (!pointList.isEmpty()) {
				int[] exteriorRings = new int[rings.size()];

				int i = 0;
				for (Integer ringNo : rings) {
					exteriorRings[i++] = ringNo;
				}

				return GeometryObject.createSolid(convertAggregate(pointList), exteriorRings, dbSrid);
			}
		}

		return null;
	}

	public GeometryObject getCompositeSolid(CompositeSolid compositeSolid) {
		if (!hasSolidSupport) {
			return null;
		}

		if (compositeSolid != null) {
			final List<GeometryObject> solidMembers = new ArrayList<>();

			compositeSolid.accept(new GeometryWalker() {
				public void visit(Solid solid) {
					GeometryObject solidMember = getSolid(solid);
					if (solidMember != null) {
						solidMembers.add(solidMember);
					} else {
						setShouldWalk(false);
						solidMembers.clear();
					}
				}
			});

			if (!solidMembers.isEmpty()) {
				GeometryObject[] tmp = new GeometryObject[solidMembers.size()];

				int i = 0;
				for (GeometryObject solidMember : solidMembers) {
					tmp[i++] = solidMember;
				}

				return GeometryObject.createCompositeSolid(tmp, dbSrid);
			}
		}

		return null;
	}

	public MultiSurface convertToMultiSurface(AbstractGeometry geometry) {
		if (geometry instanceof MultiSurface) {
			return (MultiSurface) geometry;
		}

		MultiSurface multiSurface = new MultiSurface();
		if (geometry instanceof AbstractSurface) {
			multiSurface.addSurfaceMember(new SurfaceProperty((AbstractSurface) geometry));
		} else {
			multiSurface.setId(geometry.getId());

			List<SurfaceProperty> properties = multiSurface.getSurfaceMember();
			Set<AbstractGeometry> visited = Collections.newSetFromMap(new IdentityHashMap<>());

			geometry.accept(new GeometryWalker() {
				final ChildInfo childInfo = new ChildInfo();

				public void visit(AbstractSurface surface) {
					if (!visited(surface)) {
						properties.add(new SurfaceProperty(surface));
					}
				}

				public void visit(MultiSurface multiSurface) {
					List<SurfaceProperty> tmp = new ArrayList<>(multiSurface.getSurfaceMember());
					if (multiSurface.isSetSurfaceMembers()) {
						for (AbstractSurface surface : multiSurface.getSurfaceMembers().getSurface()) {
							tmp.add(new SurfaceProperty(surface));
						}
					}

					if (multiSurface.isSetId()) {
						// mapping a MultiSurface to a CompositeSurface is not correct in terms
						// of spatial theory. However, a MultiSurface might be referenced by
						// appearance objects and thus the mapping is required to keep this information
						CompositeSurface compositeSurface = new CompositeSurface();
						compositeSurface.setId(multiSurface.getId());
						compositeSurface.setSurfaceMember(tmp);
						properties.add(new SurfaceProperty(compositeSurface));
					} else {
						properties.addAll(tmp);
					}
				}

				public <T extends AbstractGeometry> void visit(GeometryProperty<T> property) {
					if (!property.isSetGeometry() && property.isSetHref()) {
						properties.add(new SurfaceProperty(property.getHref()));
					} else {
						super.visit(property);
					}
				}

				private boolean visited(AbstractSurface surface) {
					if (visited.add(surface)) {
						while ((surface = childInfo.getParentGeometry(surface, AbstractSurface.class)) != null) {
							if (visited.contains(surface)) {
								return true;
							}
						}

						return false;
					} else {
						return true;
					}
				}
			});
		}

		return multiSurface;
	}

	private boolean containsPointPrimitives(GeometricComplex geometricComplex) {
		return containsPrimitives(geometricComplex, Point.class);
	}

	private boolean containsCurvePrimitives(GeometricComplex geometricComplex) {
		return containsPrimitives(geometricComplex, AbstractCurve.class);
	}

	private boolean containsPrimitives(GeometricComplex geometricComplex, Class<? extends AbstractGeometry> type) {
		if (geometricComplex != null && geometricComplex.isSetElement()) {
			for (GeometricPrimitiveProperty property : geometricComplex.getElement()) {
				if (!type.isInstance(property.getGeometricPrimitive())) {
					return false;
				}
			}
		}

		return true;
	}

	private boolean containsPointPrimitives(MultiGeometry multiGeometry) {
		return containsPrimitives(multiGeometry, Point.class);
	}

	private boolean containsCurvePrimitives(MultiGeometry multiGeometry) {
		return containsPrimitives(multiGeometry, AbstractCurve.class);
	}

	private boolean containsPrimitives(MultiGeometry multiGeometry, Class<? extends AbstractGeometry> type) {
		boolean hasUnsupportedGeometry = false;

		for (GeometryProperty<?> property : multiGeometry.getGeometryMember()) {
			if (property.isSetGeometry()) {
				if (!type.isInstance(property.getGeometry())) {
					hasUnsupportedGeometry = true;
					break;
				}
			}
		}

		if (!hasUnsupportedGeometry && multiGeometry.isSetGeometryMembers()) {
			for (AbstractGeometry member : multiGeometry.getGeometryMembers().getGeometry()) {
				if (!type.isInstance(member)) {
					hasUnsupportedGeometry = true;
					break;
				}
			}
		}

		return !hasUnsupportedGeometry;
	}
}
