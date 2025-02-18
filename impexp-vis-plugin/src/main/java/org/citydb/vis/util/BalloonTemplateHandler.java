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
package org.citydb.vis.util;

import org.citydb.core.ade.ADEExtension;
import org.citydb.core.ade.ADEExtensionManager;
import org.citydb.core.ade.visExporter.ADEBalloonException;
import org.citydb.core.ade.visExporter.ADEBalloonExtensionManager;
import org.citydb.core.ade.visExporter.ADEBalloonHandler;
import org.citydb.core.ade.visExporter.ADEBalloonManager;
import org.citydb.config.geometry.GeometryObject;
import org.citydb.core.database.adapter.AbstractDatabaseAdapter;
import org.citydb.util.log.Logger;
import org.citydb.core.registry.ObjectRegistry;
import org.citydb.core.util.Util;
import org.citygml4j.model.citygml.CityGMLClass;
import org.citygml4j.model.citygml.bridge.Bridge;
import org.citygml4j.model.citygml.building.Building;
import org.citygml4j.model.citygml.cityfurniture.CityFurniture;
import org.citygml4j.model.citygml.cityobjectgroup.CityObjectGroup;
import org.citygml4j.model.citygml.generics.GenericCityObject;
import org.citygml4j.model.citygml.landuse.LandUse;
import org.citygml4j.model.citygml.relief.ReliefFeature;
import org.citygml4j.model.citygml.transportation.AuxiliaryTrafficArea;
import org.citygml4j.model.citygml.transportation.TrafficArea;
import org.citygml4j.model.citygml.transportation.TransportationComplex;
import org.citygml4j.model.citygml.tunnel.Tunnel;
import org.citygml4j.model.citygml.vegetation.PlantCover;
import org.citygml4j.model.citygml.vegetation.SolitaryVegetationObject;
import org.citygml4j.model.citygml.waterbody.AbstractWaterBoundarySurface;
import org.citygml4j.model.citygml.waterbody.WaterBody;
import org.citygml4j.model.gml.base.AbstractGML;
import org.citygml4j.model.module.citygml.CityGMLVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.StringTokenizer;

@SuppressWarnings("serial")
public class BalloonTemplateHandler {
	private final Logger log = Logger.getInstance();

	// Constants
	public static final String START_TAG = "<3DCityDB>";
	public static final String END_TAG = "</3DCityDB>";
	public static final String FOREACH_TAG = "FOREACH";
	public static final String END_FOREACH_TAG = "END FOREACH";

	private static final String ADDRESS_TABLE = "ADDRESS";
	private static final LinkedHashSet<String> ADDRESS_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("STREET");
		add("HOUSE_NUMBER");
		add("PO_BOX");
		add("ZIP_CODE");
		add("CITY");
		add("STATE");
		add("COUNTRY");
		add("MULTI_POINT");
		add("XAL_SOURCE");
	}};

	private static final String ADDRESS_TO_BRIDGE_TABLE = "ADDRESS_TO_BRIDGE";
	private static final LinkedHashSet<String> ADDRESS_TO_BRIDGE_COLUMNS = new LinkedHashSet<String>() {{
		add("BRIDGE_ID");
		add("ADDRESS_ID");
	}};

	private static final String ADDRESS_TO_BUILDING_TABLE = "ADDRESS_TO_BUILDING";
	private static final LinkedHashSet<String> ADDRESS_TO_BUILDING_COLUMNS = new LinkedHashSet<String>() {{
		add("BUILDING_ID");
		add("ADDRESS_ID");
	}};

	private static final String APPEAR_TO_SURFACE_DATA_TABLE = "APPEAR_TO_SURFACE_DATA";
	private static final LinkedHashSet<String> APPEAR_TO_SURFACE_DATA_COLUMNS = new LinkedHashSet<String>() {{
		add("SURFACE_DATA_ID");
		add("APPEARANCE_ID");
	}};

	private static final String APPEARANCE_TABLE = "APPEARANCE";
	private static final LinkedHashSet<String> APPEARANCE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("GMLID");
		add("NAME");
		add("NAME_CODESPACE");
		add("DESCRIPTION");
		add("THEME");
		add("CITYMODEL_ID");
		add("CITYOBJECT_ID");
	}};
	/*
	private static final String BREAKLINE_RELIEF_TABLE = "BREAKLINE_RELIEF";
	private static final LinkedHashSet<String> BREAKLINE_RELIEF_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("RIDGE_OR_VALLEY_LINES");
		add("BREAK_LINES");
	}};
	 */
	private static final String BRIDGE_TABLE = "BRIDGE";
	private static final LinkedHashSet<String> BRIDGE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("BRIDGE_PARENT_ID");
		add("BRIDGE_ROOT_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("YEAR_OF_CONSTRUCTION");
		add("YEAR_OF_DEMOLITION");
		add("IS_MOVABLE");
		add("LOD1_TERRAIN_INTERSECTION");
		add("LOD2_TERRAIN_INTERSECTION");
		add("LOD3_TERRAIN_INTERSECTION");
		add("LOD4_TERRAIN_INTERSECTION");
		add("LOD2_MULTI_CURVE");
		add("LOD3_MULTI_CURVE");
		add("LOD4_MULTI_CURVE");
		add("LOD1_MULTI_SURFACE_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD1_SOLID_ID");
		add("LOD2_SOLID_ID");
		add("LOD3_SOLID_ID");
		add("LOD4_SOLID_ID");
	}};

	private static final String BRIDGE_CONSTR_ELEMENT_TABLE = "BRIDGE_CONSTR_ELEMENT";
	private static final LinkedHashSet<String> BRIDGE_CONSTR_ELEMENT_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("BRIDGE_ID");
		add("LOD1_TERRAIN_INTERSECTION");
		add("LOD2_TERRAIN_INTERSECTION");
		add("LOD3_TERRAIN_INTERSECTION");
		add("LOD4_TERRAIN_INTERSECTION");
		add("LOD1_BREP_ID");
		add("LOD2_BREP_ID");
		add("LOD3_BREP_ID");
		add("LOD4_BREP_ID");
		add("LOD1_OTHER_GEOM");
		add("LOD2_OTHER_GEOM");
		add("LOD3_OTHER_GEOM");
		add("LOD4_OTHER_GEOM");
		add("LOD1_IMPLICIT_REP_ID");
		add("LOD2_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD1_IMPLICIT_REF_POINT");
		add("LOD2_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD1_IMPLICIT_TRANSFORMATION");
		add("LOD2_IMPLICIT_TRANSFORMATION");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};

	private static final String BRIDGE_FURNITURE_TABLE = "BRIDGE_FURNITURE";
	private static final LinkedHashSet<String> BRIDGE_FURNITURE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("BRIDGE_ROOM_ID");
		add("LOD4_BREP_ID");
		add("LOD4_OTHER_GEOM");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};	

	private static final String BRIDGE_INSTALLATION_TABLE = "BRIDGE_INSTALLATION";
	private static final LinkedHashSet<String> BRIDGE_INSTALLATION_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("BRIDGE_ID");
		add("BRIDGE_ROOM_ID");
		add("LOD2_BREP_ID");
		add("LOD3_BREP_ID");
		add("LOD4_BREP_ID");
		add("LOD2_OTHER_GEOM");
		add("LOD3_OTHER_GEOM");
		add("LOD4_OTHER_GEOM");
		add("LOD2_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD2_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD2_IMPLICIT_TRANSFORMATION");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};	

	private static final String BRIDGE_OPEN_TO_THEM_SRF_TABLE = "BRIDGE_OPEN_TO_THEM_SRF";
	private static final LinkedHashSet<String> BRIDGE_OPEN_TO_THEM_SRF_COLUMNS = new LinkedHashSet<String>() {{
		add("BRIDGE_OPENING_ID");
		add("BRIDGE_THEMATIC_SURFACE_ID");
	}};	

	private static final String BRIDGE_OPENING_TABLE = "BRIDGE_OPENING";
	private static final LinkedHashSet<String> BRIDGE_OPENING_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("ADDRESS_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};	

	private static final String BRIDGE_ROOM_TABLE = "BRIDGE_ROOM";
	private static final LinkedHashSet<String> BRIDGE_ROOM_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("BRIDGE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD4_SOLID_ID");
	}};	

	private static final String BRIDGE_THEMATIC_SURFACE_TABLE = "BRIDGE_THEMATIC_SURFACE";
	private static final LinkedHashSet<String> BRIDGE_THEMATIC_SURFACE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("BRIDGE_ID");
		add("BRIDGE_ROOM_ID");
		add("BRIDGE_INSTALLATION_ID");
		add("BRIDGE_CONSTR_ELEMENT_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
	}};

	private static final String BUILDING_TABLE = "BUILDING";
	private static final LinkedHashSet<String> BUILDING_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("BUILDING_PARENT_ID");
		add("BUILDING_ROOT_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("YEAR_OF_CONSTRUCTION");
		add("YEAR_OF_DEMOLITION");
		add("ROOF_TYPE");
		add("ROOF_TYPE_CODESPACE");
		add("MEASURED_HEIGHT");
		add("MEASURED_HEIGHT_UNIT");
		add("STOREYS_ABOVE_GROUND");
		add("STOREYS_BELOW_GROUND");
		add("STOREY_HEIGHTS_ABOVE_GROUND");
		add("STOREY_HEIGHTS_AG_UNIT");
		add("STOREY_HEIGHTS_BELOW_GROUND");
		add("STOREY_HEIGHTS_BG_UNIT");
		add("LOD1_TERRAIN_INTERSECTION");
		add("LOD2_TERRAIN_INTERSECTION");
		add("LOD3_TERRAIN_INTERSECTION");
		add("LOD4_TERRAIN_INTERSECTION");
		add("LOD2_MULTI_CURVE");
		add("LOD3_MULTI_CURVE");
		add("LOD4_MULTI_CURVE");
		add("LOD0_FOOTPRINT_ID");
		add("LOD0_ROOFPRINT_ID");
		add("LOD1_MULTI_SURFACE_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD1_SOLID_ID");
		add("LOD2_SOLID_ID");
		add("LOD3_SOLID_ID");
		add("LOD4_SOLID_ID");
	}};

	private static final String BUILDING_FURNITURE_TABLE = "BUILDING_FURNITURE";
	private static final LinkedHashSet<String> BUILDING_FURNITURE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("ROOM_ID");
		add("LOD4_BREP_ID");
		add("LOD4_OTHER_GEOM");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};

	private static final String BUILDING_INSTALLATION_TABLE = "BUILDING_INSTALLATION";
	private static final LinkedHashSet<String> BUILDING_INSTALLATION_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("BUILDING_ID");
		add("ROOM_ID");
		add("LOD2_BREP_ID");
		add("LOD3_BREP_ID");
		add("LOD4_BREP_ID");
		add("LOD2_OTHER_GEOM");
		add("LOD3_OTHER_GEOM");
		add("LOD4_OTHER_GEOM");
		add("LOD2_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD2_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD2_IMPLICIT_TRANSFORMATION");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};

	private static final String CITY_FURNITURE_TABLE = "CITY_FURNITURE";
	private static final LinkedHashSet<String> CITY_FURNITURE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("LOD1_TERRAIN_INTERSECTION");
		add("LOD2_TERRAIN_INTERSECTION");
		add("LOD3_TERRAIN_INTERSECTION");
		add("LOD4_TERRAIN_INTERSECTION");
		add("LOD1_BREP_ID");
		add("LOD2_BREP_ID");
		add("LOD3_BREP_ID");
		add("LOD4_BREP_ID");
		add("LOD1_OTHER_GEOM");
		add("LOD2_OTHER_GEOM");
		add("LOD3_OTHER_GEOM");
		add("LOD4_OTHER_GEOM");
		add("LOD1_IMPLICIT_REP_ID");
		add("LOD2_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD1_IMPLICIT_REF_POINT");
		add("LOD2_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD1_IMPLICIT_TRANSFORMATION");
		add("LOD2_IMPLICIT_TRANSFORMATION");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};

	private static final String CITYMODEL_TABLE = "CITYMODEL";
	private static final LinkedHashSet<String> CITYMODEL_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("GMLID");
		add("NAME");
		add("NAME_CODESPACE");
		add("DESCRIPTION");
		add("ENVELOPE");
		add("CREATION_DATE");
		add("TERMINATION_DATE");
		add("LAST_MODIFICATION_DATE");
		add("UPDATING_PERSON");
		add("REASON_FOR_UPDATE");
		add("LINEAGE");
	}};

	private static final String CITYOBJECT_TABLE = "CITYOBJECT";
	private static final LinkedHashSet<String> CITYOBJECT_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("GMLID");
		add("NAME");
		add("NAME_CODESPACE");
		add("DESCRIPTION");
		add("ENVELOPE");
		add("CREATION_DATE");
		add("TERMINATION_DATE");
		add("RELATIVE_TO_TERRAIN");
		add("RELATIVE_TO_WATER");
		add("LAST_MODIFICATION_DATE");
		add("UPDATING_PERSON");
		add("REASON_FOR_UPDATE");
		add("LINEAGE");
		add("XML_SOURCE");
	}};

	private static final String CITYOBJECT_GENERICATTRIB_TABLE = "CITYOBJECT_GENERICATTRIB";
	private static final LinkedHashSet<String> CITYOBJECT_GENERICATTRIB_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("PARENT_GENATTRIB_ID");
		add("ROOT_GENATTRIB_ID");
		add("ATTRNAME");
		add("DATATYPE");
		add("STRVAL");
		add("INTVAL");
		add("REALVAL");
		add("URIVAL");
		add("DATEVAL");
		add("GEOMVAL");
		add("BLOBVAL");
		add("UNIT");
		add("GENATTRIBSET_CODESPACE");
		add("SURFACE_GEOMETRY_ID");
		add("CITYOBJECT_ID");		
	}};

	private static final String CITYOBJECT_MEMBER_TABLE = "CITYOBJECT_MEMBER";
	private static final LinkedHashSet<String> CITYOBJECT_MEMBER_COLUMNS = new LinkedHashSet<String>() {{
		add("CITYMODEL_ID");
		add("CITYOBJECT_ID");
	}};

	private static final String CITYOBJECTGROUP_TABLE = "CITYOBJECTGROUP";
	private static final LinkedHashSet<String> CITYOBJECTGROUP_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("BREP_ID");
		add("OTHER_GEOM");
		add("PARENT_CITYOBJECT_ID");
	}};

	private static final String DATABASE_SRS_TABLE = "DATABASE_SRS";
	private static final LinkedHashSet<String> DATABASE_SRS_COLUMNS = new LinkedHashSet<String>() {{
		add("SRID");
		add("GML_SRS_NAME");
	}};

	private static final String EXTERNAL_REFERENCE_TABLE = "EXTERNAL_REFERENCE";
	private static final LinkedHashSet<String> EXTERNAL_REFERENCE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("INFOSYS");
		add("NAME");
		add("URI");
		add("CITYOBJECT_ID");
	}};

	private static final String GENERALIZATION_TABLE = "GENERALIZATION";
	private static final LinkedHashSet<String> GENERALIZATION_COLUMNS = new LinkedHashSet<String>() {{
		add("CITYOBJECT_ID");
		add("GENERALIZES_TO_ID");
	}};

	private static final String GENERIC_CITYOBJECT_TABLE = "GENERIC_CITYOBJECT";
	private static final LinkedHashSet<String> GENERIC_CITYOBJECT_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("LOD0_TERRAIN_INTERSECTION");
		add("LOD1_TERRAIN_INTERSECTION");
		add("LOD2_TERRAIN_INTERSECTION");
		add("LOD3_TERRAIN_INTERSECTION");
		add("LOD4_TERRAIN_INTERSECTION");
		add("LOD0_BREP_ID");
		add("LOD1_BREP_ID");
		add("LOD2_BREP_ID");
		add("LOD3_BREP_ID");
		add("LOD4_BREP_ID");
		add("LOD0_OTHER_GEOM");
		add("LOD1_OTHER_GEOM");
		add("LOD2_OTHER_GEOM");
		add("LOD3_OTHER_GEOM");
		add("LOD4_OTHER_GEOM");
		add("LOD0_IMPLICIT_REP_ID");
		add("LOD1_IMPLICIT_REP_ID");
		add("LOD2_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD0_IMPLICIT_REF_POINT");
		add("LOD1_IMPLICIT_REF_POINT");
		add("LOD2_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD0_IMPLICIT_TRANSFORMATION");
		add("LOD1_IMPLICIT_TRANSFORMATION");
		add("LOD2_IMPLICIT_TRANSFORMATION");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};

	private static final String GROUP_TO_CITYOBJECT_TABLE = "GROUP_TO_CITYOBJECT";
	private static final LinkedHashSet<String> GROUP_TO_CITYOBJECT_COLUMNS = new LinkedHashSet<String>() {{
		add("CITYOBJECT_ID");
		add("CITYOBJECTGROUP_ID");
		add("ROLE");
	}};

	private static final String IMPLICIT_GEOMETRY_TABLE = "IMPLICIT_GEOMETRY";
	private static final LinkedHashSet<String> IMPLICIT_GEOMETRY_COLUMNS = new LinkedHashSet<String>() {{
		add("MIME_TYPE");
		add("REFERENCE_TO_LIBRARY");
		add("LIBRARY_OBJECT");
		add("RELATIVE_BREP_ID");
		add("RELATIVE_OTHER_GEOM");
	}};

	private static final String LAND_USE_TABLE = "LAND_USE";
	private static final LinkedHashSet<String> LAND_USE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("LOD0_MULTI_SURFACE_ID");
		add("LOD1_MULTI_SURFACE_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
	}};
	/*
	private static final String MASSPOINT_RELIEF_TABLE = "MASSPOINT_RELIEF";
	private static final LinkedHashSet<String> MASSPOINT_RELIEF_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("RELIEF_POINTS");
	}};
	 */
	private static final String OBJECTCLASS_TABLE = "OBJECTCLASS";
	private static final LinkedHashSet<String> OBJECTCLASS_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASSNAME");
		add("SUPERCLASS_ID");
	}};

	private static final String OPENING_TABLE = "OPENING";
	private static final LinkedHashSet<String> OPENING_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("ADDRESS_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};

	private static final String OPENING_TO_THEM_SURFACE_TABLE = "OPENING_TO_THEM_SURFACE";
	private static final LinkedHashSet<String> OPENING_TO_THEM_SURFACE_COLUMNS = new LinkedHashSet<String>() {{
		add("OPENING_ID");
		add("THEMATIC_SURFACE_ID");
	}};

	private static final String PLANT_COVER_TABLE = "PLANT_COVER";
	private static final LinkedHashSet<String> PLANT_COVER_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("AVERAGE_HEIGHT");
		add("AVERAGE_HEIGHT_UNIT");
		add("LOD1_MULTI_SURFACE_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD1_MULTI_SOLID_ID");
		add("LOD2_MULTI_SOLID_ID");
		add("LOD3_MULTI_SOLID_ID");
		add("LOD4_MULTI_SOLID_ID");
	}};
	/*
	private static final String RASTER_RELIEF_TABLE = "RASTER_RELIEF";
	private static final LinkedHashSet<String> RASTER_RELIEF_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("URI");
		add("COVERAGE_ID");
	}};
	 */
	/*	private static final String GRID_COVERAGE_TABLE = "GRID_COVERAGE";
	private static final LinkedHashSet<String> GRID_COVERAGE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("RASTERPROPERTY");
	}};*/

	private static final String RELIEF_COMPONENT_TABLE = "RELIEF_COMPONENT";
	private static final LinkedHashSet<String> RELIEF_COMPONENT_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("LOD");
		add("EXTENT");
	}};
	/*
	private static final String RELIEF_FEAT_TO_REL_COMP_TABLE = "RELIEF_FEAT_TO_REL_COMP";
	private static final LinkedHashSet<String> RELIEF_FEAT_TO_REL_COMP_COLUMNS = new LinkedHashSet<String>() {{
		add("RELIEF_COMPONENT_ID");
		add("RELIEF_FEATURE_ID");
	}};
	 */
	private static final String RELIEF_FEATURE_TABLE = "RELIEF_FEATURE";
	private static final LinkedHashSet<String> RELIEF_FEATURE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("LOD");
	}};

	private static final String ROOM_TABLE = "ROOM";
	private static final LinkedHashSet<String> ROOM_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("BUILDING_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD4_SOLID_ID");
	}};

	private static final String SOLITARY_VEGETAT_OBJECT_TABLE = "SOLITARY_VEGETAT_OBJECT";
	private static final LinkedHashSet<String> SOLITARY_VEGETAT_OBJECT_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("SPECIES");
		add("SPECIES_CODESPACE");
		add("HEIGHT");
		add("HEIGHT_UNIT");
		add("TRUNK_DIAMETER");
		add("TRUNK_DIAMETER_UNIT");
		add("CROWN_DIAMETER");
		add("CROWN_DIAMETER_UNIT");
		add("LOD1_BREP_ID");
		add("LOD2_BREP_ID");
		add("LOD3_BREP_ID");
		add("LOD4_BREP_ID");
		add("LOD1_OTHER_GEOM");
		add("LOD2_OTHER_GEOM");
		add("LOD3_OTHER_GEOM");
		add("LOD4_OTHER_GEOM");
		add("LOD1_IMPLICIT_REP_ID");
		add("LOD2_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD1_IMPLICIT_REF_POINT");
		add("LOD2_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD1_IMPLICIT_TRANSFORMATION");
		add("LOD2_IMPLICIT_TRANSFORMATION");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};

	private static final String SURFACE_DATA_TABLE = "SURFACE_DATA";
	private static final LinkedHashSet<String> SURFACE_DATA_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("GMLID");
		add("NAME");
		add("NAME_CODESPACE");
		add("DESCRIPTION");
		add("IS_FRONT");
		add("OBJECTCLASS_ID");
		add("X3D_SHININESS");
		add("X3D_TRANSPARENCY");
		add("X3D_AMBIENT_INTENSITY");
		add("X3D_SPECULAR_COLOR");
		add("X3D_DIFFUSE_COLOR");
		add("X3D_EMISSIVE_COLOR");
		add("X3D_IS_SMOOTH");
		add("TEX_IMAGE_ID");
		add("TEX_TEXTURE_TYPE");
		add("TEX_WRAP_MODE");
		add("TEX_BORDER_COLOR");
		add("GT_PREFER_WORLDFILE");
		add("GT_ORIENTATION");
		add("GT_REFERENCE_POINT");
	}};

	private static final String SURFACE_GEOMETRY_TABLE = "SURFACE_GEOMETRY";
	private static final LinkedHashSet<String> SURFACE_GEOMETRY_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("GMLID");
		add("PARENT_ID");
		add("ROOT_ID");
		add("IS_SOLID");
		add("IS_COMPOSITE");
		add("IS_TRIANGULATED");
		add("IS_XLINK");
		add("IS_REVERSE");
		add("GEOMETRY");
		add("SOLID_GEOMETRY");
		add("IMPLICIT_GEOMETRY");
		add("CITYOBJECT_ID");
	}};
	private static final String TEX_IMAGE_TABLE = "TEX_IMAGE";
	private static final LinkedHashSet<String> TEX_IMAGE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("TEX_IMAGE_URI");
		add("TEX_IMAGE_DATA");
		add("TEX_MIME_TYPE");
		add("TEX_MIME_TYPE_CODESPACE");
	}};

	private static final String TEXTUREPARAM_TABLE = "TEXTUREPARAM";
	private static final LinkedHashSet<String> TEXTUREPARAM_COLUMNS = new LinkedHashSet<String>() {{
		add("SURFACE_GEOMETRY_ID");
		add("IS_TEXTURE_PARAMETRIZATION");
		add("WORLD_TO_TEXTURE");
		add("TEXTURE_COORDINATES");
		add("SURFACE_DATA_ID");
	}};

	private static final String THEMATIC_SURFACE_TABLE = "THEMATIC_SURFACE";
	private static final LinkedHashSet<String> THEMATIC_SURFACE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("BUILDING_ID");
		add("ROOM_ID");
		add("BUILDING_INSTALLATION_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
	}};
	/*
	private static final String TIN_RELIEF_TABLE = "TIN_RELIEF";
	private static final LinkedHashSet<String> TIN_RELIEF_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("MAX_LENGTH");
		add("STOP_LINES");
		add("BREAK_LINES");
		add("CONTROL_POINTS");
		add("SURFACE_GEOMETRY_ID");
	}};
	 */
	private static final String TRAFFIC_AREA_TABLE = "TRAFFIC_AREA";
	private static final LinkedHashSet<String> TRAFFIC_AREA_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("SURFACE_MATERIAL");
		add("SURFACE_MATERIAL_CODESPACE");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("TRANSPORTATION_COMPLEX_ID");
	}};

	private static final String TRANSPORTATION_COMPLEX_TABLE = "TRANSPORTATION_COMPLEX";
	private static final LinkedHashSet<String> TRANSPORTATION_COMPLEX_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("LOD0_NETWORK");
		add("LOD1_MULTI_SURFACE_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
	}};

	private static final String TUNNEL_TABLE = "TUNNEL";
	private static final LinkedHashSet<String> TUNNEL_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("TUNNEL_PARENT_ID");
		add("TUNNEL_ROOT_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("YEAR_OF_CONSTRUCTION");
		add("YEAR_OF_DEMOLITION");
		add("LOD1_TERRAIN_INTERSECTION");
		add("LOD2_TERRAIN_INTERSECTION");
		add("LOD3_TERRAIN_INTERSECTION");
		add("LOD4_TERRAIN_INTERSECTION");
		add("LOD2_MULTI_CURVE");
		add("LOD3_MULTI_CURVE");
		add("LOD4_MULTI_CURVE");
		add("LOD1_MULTI_SURFACE_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD1_SOLID_ID");
		add("LOD2_SOLID_ID");
		add("LOD3_SOLID_ID");
		add("LOD4_SOLID_ID");
	}};

	private static final String TUNNEL_FURNITURE_TABLE = "TUNNEL_FURNITURE";
	private static final LinkedHashSet<String> TUNNEL_FURNITURE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("TUNNEL_HOLLOW_SPACE_ID");
		add("LOD4_BREP_ID");
		add("LOD4_OTHER_GEOM");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};	

	private static final String TUNNEL_HOLLOW_SPACE_TABLE = "TUNNEL_HOLLOW_SPACE";
	private static final LinkedHashSet<String> TUNNEL_HOLLOW_SPACE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("TUNNEL_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD4_SOLID_ID");
	}};	

	private static final String TUNNEL_INSTALLATION_TABLE = "TUNNEL_INSTALLATION";
	private static final LinkedHashSet<String> TUNNEL_INSTALLATION_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("TUNNEL_ID");
		add("TUNNEL_HOLLOW_SPACE_ID");
		add("LOD2_BREP_ID");
		add("LOD3_BREP_ID");
		add("LOD4_BREP_ID");
		add("LOD2_OTHER_GEOM");
		add("LOD3_OTHER_GEOM");
		add("LOD4_OTHER_GEOM");
		add("LOD2_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD2_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD2_IMPLICIT_TRANSFORMATION");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};	

	private static final String TUNNEL_OPEN_TO_THEM_SRF_TABLE = "TUNNEL_OPEN_TO_THEM_SRF";
	private static final LinkedHashSet<String> TUNNEL_OPEN_TO_THEM_SRF_COLUMNS = new LinkedHashSet<String>() {{
		add("TUNNEL_OPENING_ID");
		add("TUNNEL_THEMATIC_SURFACE_ID");
	}};	

	private static final String TUNNEL_OPENING_TABLE = "TUNNEL_OPENING";
	private static final LinkedHashSet<String> TUNNEL_OPENING_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
		add("LOD3_IMPLICIT_REP_ID");
		add("LOD4_IMPLICIT_REP_ID");
		add("LOD3_IMPLICIT_REF_POINT");
		add("LOD4_IMPLICIT_REF_POINT");
		add("LOD3_IMPLICIT_TRANSFORMATION");
		add("LOD4_IMPLICIT_TRANSFORMATION");
	}};	

	private static final String TUNNEL_THEMATIC_SURFACE_TABLE = "TUNNEL_THEMATIC_SURFACE";
	private static final LinkedHashSet<String> TUNNEL_THEMATIC_SURFACE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("TUNNEL_ID");
		add("TUNNEL_HOLLOW_SPACE_ID");
		add("TUNNEL_INSTALLATION_ID");
		add("LOD2_MULTI_SURFACE_ID");
		add("LOD3_MULTI_SURFACE_ID");
		add("LOD4_MULTI_SURFACE_ID");
	}};

	private static final String WATERBODY_TO_WATERBOUNDARY_SURFACE_TABLE = "WATERBOD_TO_WATERBND_SRF";
	private static final LinkedHashSet<String> WATERBODY_TO_WATERBOUNDARY_SURFACE_COLUMNS = new LinkedHashSet<String>() {{
		add("WATERBOUNDARY_SURFACE_ID");
		add("WATERBODY_ID");
	}};

	private static final String WATERBODY_TABLE = "WATERBODY";
	private static final LinkedHashSet<String> WATERBODY_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("CLASS");
		add("CLASS_CODESPACE");
		add("FUNCTION");
		add("FUNCTION_CODESPACE");
		add("USAGE");
		add("USAGE_CODESPACE");
		add("LOD0_MULTI_CURVE");
		add("LOD1_MULTI_CURVE");
		add("LOD0_MULTI_SURFACE_ID");
		add("LOD1_MULTI_SURFACE_ID");
		add("LOD1_SOLID_ID");
		add("LOD2_SOLID_ID");
		add("LOD3_SOLID_ID");
		add("LOD4_SOLID_ID");
	}};

	private static final String WATERBOUNDARY_SURFACE_TABLE = "WATERBOUNDARY_SURFACE";
	private static final LinkedHashSet<String> WATERBOUNDARY_SURFACE_COLUMNS = new LinkedHashSet<String>() {{
		add("ID");
		add("OBJECTCLASS_ID");
		add("WATER_LEVEL");
		add("WATER_LEVEL_CODESPACE");
		add("LOD2_SURFACE_ID");
		add("LOD3_SURFACE_ID");
		add("LOD4_SURFACE_ID");
	}};

	private static final String MAX = "MAX";
	private static final String MIN = "MIN";
	private static final String AVG = "AVG";
	private static final String COUNT = "COUNT";
	private static final String SUM = "SUM";
	private static final String FIRST = "FIRST";
	private static final String LAST = "LAST";

	private static final LinkedHashSet<String> AGGREGATION_FUNCTIONS = new LinkedHashSet<String>() {{
		add(MAX);
		add(MIN);
		add(AVG);
		add(COUNT);
		add(SUM);
		add(FIRST);
		add(LAST);
	}};

	public Set<String> getSupportedAggregationFunctions() {
		return AGGREGATION_FUNCTIONS;
	}

	private static final String SPECIAL_KEYWORDS = "SPECIAL_KEYWORDS";
	private static final String CENTROID_WGS84 = "CENTROID_WGS84";
	private static final String CENTROID_WGS84_LAT = "CENTROID_WGS84_LAT";
	private static final String CENTROID_WGS84_LON = "CENTROID_WGS84_LON";
	private static final String BBOX_WGS84_LAT_MIN = "BBOX_WGS84_LAT_MIN";
	private static final String BBOX_WGS84_LAT_MAX = "BBOX_WGS84_LAT_MAX";
	private static final String BBOX_WGS84_LON_MIN = "BBOX_WGS84_LON_MIN";
	private static final String BBOX_WGS84_LON_MAX = "BBOX_WGS84_LON_MAX";
	private static final String BBOX_WGS84_HEIGHT_MIN = "BBOX_WGS84_HEIGHT_MIN";
	private static final String BBOX_WGS84_HEIGHT_MAX = "BBOX_WGS84_HEIGHT_MAX";
	private static final String BBOX_WGS84_LAT_LON = "BBOX_WGS84_LAT_LON";
	private static final String BBOX_WGS84_LON_LAT = "BBOX_WGS84_LON_LAT";

	private static final LinkedHashSet<String> SPECIAL_KEYWORDS_SET = new LinkedHashSet<String>() {{
		add(CENTROID_WGS84);
		add(CENTROID_WGS84_LAT);
		add(CENTROID_WGS84_LON);
		add(BBOX_WGS84_LAT_MIN);
		add(BBOX_WGS84_LAT_MAX);
		add(BBOX_WGS84_LON_MIN);
		add(BBOX_WGS84_LON_MAX);
		add(BBOX_WGS84_HEIGHT_MIN);
		add(BBOX_WGS84_HEIGHT_MAX);
		add(BBOX_WGS84_LAT_LON);
		add(BBOX_WGS84_LON_LAT);
	}};

	private static HashMap<String, Set<String>> _3DCITYDB_TABLES_AND_COLUMNS = new HashMap<String, Set<String>>() {{
		put(ADDRESS_TABLE, ADDRESS_COLUMNS);
		put(ADDRESS_TO_BRIDGE_TABLE, ADDRESS_TO_BRIDGE_COLUMNS);
		put(ADDRESS_TO_BUILDING_TABLE, ADDRESS_TO_BUILDING_COLUMNS);
		put(APPEAR_TO_SURFACE_DATA_TABLE, APPEAR_TO_SURFACE_DATA_COLUMNS);
		put(APPEARANCE_TABLE, APPEARANCE_COLUMNS);
		//		put(BREAKLINE_RELIEF_TABLE, BREAKLINE_RELIEF_COLUMNS);
		put(BRIDGE_TABLE, BRIDGE_COLUMNS);
		put(BRIDGE_CONSTR_ELEMENT_TABLE,BRIDGE_CONSTR_ELEMENT_COLUMNS);
		put(BRIDGE_FURNITURE_TABLE, BRIDGE_FURNITURE_COLUMNS);
		put(BRIDGE_INSTALLATION_TABLE, BRIDGE_INSTALLATION_COLUMNS);
		put(BRIDGE_OPEN_TO_THEM_SRF_TABLE, BRIDGE_OPEN_TO_THEM_SRF_COLUMNS);
		put(BRIDGE_OPENING_TABLE, BRIDGE_OPENING_COLUMNS);
		put(BRIDGE_ROOM_TABLE, BRIDGE_ROOM_COLUMNS);
		put(BRIDGE_THEMATIC_SURFACE_TABLE, BRIDGE_THEMATIC_SURFACE_COLUMNS);

		put(BUILDING_TABLE, BUILDING_COLUMNS);
		put(BUILDING_FURNITURE_TABLE, BUILDING_FURNITURE_COLUMNS);
		put(BUILDING_INSTALLATION_TABLE, BUILDING_INSTALLATION_COLUMNS);

		put(CITY_FURNITURE_TABLE, CITY_FURNITURE_COLUMNS);
		put(CITYMODEL_TABLE, CITYMODEL_COLUMNS);
		put(CITYOBJECT_TABLE, CITYOBJECT_COLUMNS);
		put(CITYOBJECT_GENERICATTRIB_TABLE, CITYOBJECT_GENERICATTRIB_COLUMNS);
		put(CITYOBJECTGROUP_TABLE, CITYOBJECTGROUP_COLUMNS);
		put(CITYOBJECT_MEMBER_TABLE, CITYOBJECT_MEMBER_COLUMNS);
		put(DATABASE_SRS_TABLE, DATABASE_SRS_COLUMNS);
		put(EXTERNAL_REFERENCE_TABLE, EXTERNAL_REFERENCE_COLUMNS);
		put(GENERALIZATION_TABLE, GENERALIZATION_COLUMNS);
		put(GENERIC_CITYOBJECT_TABLE, GENERIC_CITYOBJECT_COLUMNS);
		put(GROUP_TO_CITYOBJECT_TABLE, GROUP_TO_CITYOBJECT_COLUMNS);
		put(IMPLICIT_GEOMETRY_TABLE, IMPLICIT_GEOMETRY_COLUMNS);
		put(LAND_USE_TABLE, LAND_USE_COLUMNS);
		//		put(MASSPOINT_RELIEF_TABLE, MASSPOINT_RELIEF_COLUMNS);
		put(OBJECTCLASS_TABLE, OBJECTCLASS_COLUMNS);
		put(OPENING_TABLE, OPENING_COLUMNS);
		put(OPENING_TO_THEM_SURFACE_TABLE, OPENING_TO_THEM_SURFACE_COLUMNS);
		put(PLANT_COVER_TABLE, PLANT_COVER_COLUMNS);
		//		put(RASTER_RELIEF_TABLE, RASTER_RELIEF_COLUMNS);
		//      put(GRID_COVERAGE_TABLE, GRID_COVERAGE_COLUMNS);
		put(RELIEF_COMPONENT_TABLE, RELIEF_COMPONENT_COLUMNS);
		put(RELIEF_FEATURE_TABLE, RELIEF_FEATURE_COLUMNS);
		//		put(RELIEF_FEAT_TO_REL_COMP_TABLE, RELIEF_FEAT_TO_REL_COMP_COLUMNS);
		put(ROOM_TABLE, ROOM_COLUMNS);
		put(SOLITARY_VEGETAT_OBJECT_TABLE, SOLITARY_VEGETAT_OBJECT_COLUMNS);
		put(SPECIAL_KEYWORDS, SPECIAL_KEYWORDS_SET);
		put(SURFACE_DATA_TABLE, SURFACE_DATA_COLUMNS);
		put(SURFACE_GEOMETRY_TABLE, SURFACE_GEOMETRY_COLUMNS);
		put(TEX_IMAGE_TABLE, TEX_IMAGE_COLUMNS);
		put(TEXTUREPARAM_TABLE, TEXTUREPARAM_COLUMNS);
		put(THEMATIC_SURFACE_TABLE, THEMATIC_SURFACE_COLUMNS);
		//		put(TIN_RELIEF_TABLE, TIN_RELIEF_COLUMNS);

		put(TRAFFIC_AREA_TABLE, TRAFFIC_AREA_COLUMNS);
		put(TRANSPORTATION_COMPLEX_TABLE, TRANSPORTATION_COMPLEX_COLUMNS);

		put(TUNNEL_TABLE, TUNNEL_COLUMNS);
		put(TUNNEL_FURNITURE_TABLE, TUNNEL_FURNITURE_COLUMNS);
		put(TUNNEL_HOLLOW_SPACE_TABLE, TUNNEL_HOLLOW_SPACE_COLUMNS);
		put(TUNNEL_INSTALLATION_TABLE, TUNNEL_INSTALLATION_COLUMNS);
		put(TUNNEL_OPEN_TO_THEM_SRF_TABLE, TUNNEL_OPEN_TO_THEM_SRF_COLUMNS);
		put(TUNNEL_OPENING_TABLE, TUNNEL_OPENING_COLUMNS);
		put(TUNNEL_THEMATIC_SURFACE_TABLE, TUNNEL_THEMATIC_SURFACE_COLUMNS);

		put(WATERBODY_TO_WATERBOUNDARY_SURFACE_TABLE, WATERBODY_TO_WATERBOUNDARY_SURFACE_COLUMNS);
		put(WATERBODY_TABLE, WATERBODY_COLUMNS);
		put(WATERBOUNDARY_SURFACE_TABLE, WATERBOUNDARY_SURFACE_COLUMNS);
	}};

	public HashMap<String, Set<String>> getSupportedTablesAndColumns() {
		HashMap<String, Set<String>> result = new HashMap<>();

		result.putAll(_3DCITYDB_TABLES_AND_COLUMNS);
		for (ADEExtension extension: ADEExtensionManager.getInstance().getExtensions()) {
			ADEBalloonManager balloonManager  = ADEBalloonExtensionManager.getInstance().getBalloonManager(extension);
			if (balloonManager != null) {
				result.putAll(balloonManager.getTablesAndColumns());
			}
		}

		return result;
	}

	/*
	public Set<String> getSpecialKeywords() {
		return SPECIAL_KEYWORDS;
	}
	 */
	public static final String balloonDirectoryName = "balloons";
	public static final String parentFrameStart =
			"<html>\n" +
					"  <body onload=\"resizeFrame(document.getElementById('childframe'))\">\n" +
					"    <script type=\"text/javascript\">\n" +
					"      function resizeFrame(f) {\n" +
					"        f.style.height = (f.contentWindow.document.body.scrollHeight + 20) + \"px\";\n" +
					"        f.style.width = (f.contentWindow.document.body.scrollWidth + 20) + \"px\";\n" +
					"      }\n" +
					"    </script>\n" +
					"    <iframe frameborder=0 border=0 src=\"";

	public static final String parentFrameEnd = ".html\" id=\"childframe\"></iframe>\n" +
			"  </body>\n" +
			"</html>";

	private final AbstractDatabaseAdapter databaseAdapter;
	private CityGMLClass cityGMLClassForBalloonHandler = null;
	private int objectClassId;

	List<BalloonStatement> statementList = null;
	List<String> htmlChunkList = null;

	public BalloonTemplateHandler(File templateFile, AbstractDatabaseAdapter databaseAdapter) {
		this.databaseAdapter = databaseAdapter;
		setTemplate(templateFile);
	}

	public BalloonTemplateHandler(String templateString, AbstractDatabaseAdapter databaseAdapter) {
		this.databaseAdapter = databaseAdapter;
		setTemplate(templateString);
	}

	private void setTemplate(File templateFile) {
		statementList = new ArrayList<BalloonStatement>();
		htmlChunkList = new ArrayList<String>();

		if (templateFile == null) return; // it was a dummy call

		// read file as String
		byte[] buffer = new byte[(int)templateFile.length()];
		FileInputStream f = null;
		try {
			f = new FileInputStream(templateFile);
			f.read(buffer);
		}
		catch (FileNotFoundException fnfe) {
			log.warn("Exception when trying to read file: " + templateFile.getAbsolutePath() + "\nFile not found.");
		} 
		catch (Exception e) {
			log.warn("Exception when trying to read file: " + templateFile.getAbsolutePath() + "\n");
			log.logStackTrace(e);
		} 
		finally {
			if (f != null) try { f.close(); } catch (Exception ignored) { }
		}
		String template = new String(buffer);
		try {
			fillStatementAndHtmlChunkList(template);
		}
		catch (Exception e) {
			log.warn("Following message applies to file: " + templateFile.getAbsolutePath());
			log.warn(e.getMessage());
		}
	}

	private void setTemplate(String templateString) {
		statementList = new ArrayList<BalloonStatement>();
		htmlChunkList = new ArrayList<String>();

		if (templateString == null) return; // it was a dummy call

		try {
			fillStatementAndHtmlChunkList(templateString);
		}
		catch (Exception e) {
			log.warn(e.getMessage());
		}
	}

	public String getBalloonContent(String template, long id, int lod, Connection connection, String schemaName) throws Exception {
		if (connection == null) throw new SQLException("Null or invalid connection");		

		if (schemaName == null)
			schemaName = databaseAdapter.getSchemaManager().getDefaultSchema();

		String balloonContent = "";
		List<BalloonStatement> statementListBackup = statementList;
		List<String> htmlChunkListBackup = htmlChunkList;
		statementList = new ArrayList<BalloonStatement>();
		htmlChunkList = new ArrayList<String>();
		try {
			fillStatementAndHtmlChunkList(template);
			balloonContent = getBalloonContent(id, lod, connection, schemaName);
		}
		catch (Exception e) {
			log.warn("Following message applies to generic attribute 'Balloon_Content' for cityobject with id = " + id);
			log.warn(e.getMessage());
		}
		statementList = statementListBackup;
		htmlChunkList = htmlChunkListBackup;
		return balloonContent;
	}

	public String getBalloonContent(String gmlId, int lod, Connection connection, String schemaName) throws Exception {
		if (connection == null) throw new SQLException("Null or invalid connection");
		if (statementList == null && htmlChunkList == null) throw new Exception("Invalid template file");

		if (schemaName == null)
			schemaName = databaseAdapter.getSchemaManager().getDefaultSchema();

		StringBuilder balloonContent = new StringBuilder();

		if (statementList != null) {
			// when properly initialized this happens only at the first object
			// otherwise it avoids problems from lousy initialization of BalloonTemplateHandlers in threads
			// at the cost of performance

			CityGMLClass cityObjectTypeForGmlId = null;
			long id = -1;

			ResultSet rs = null;
			PreparedStatement query = null;
			try {
				query = connection.prepareStatement(new StringBuilder("SELECT id, objectclass_id FROM ").append(schemaName).append(".CITYOBJECT WHERE gmlid = ?").toString());
				query.setString(1, gmlId);
				rs = query.executeQuery();

				if (rs.next()) {
					objectClassId = rs.getInt("objectclass_id");
					cityObjectTypeForGmlId = Util.getCityGMLClass(objectClassId);
					id = rs.getLong("id");
				}
			}
			catch (SQLException sqlEx) {}
			finally {
				if (rs != null) {
					try { rs.close(); }	catch (SQLException sqlEx) {}
					rs = null;
				}

				if (query != null) {
					try { query.close(); } catch (SQLException sqlEx) {}
					query = null;
				}
			}

			if (cityGMLClassForBalloonHandler != cityObjectTypeForGmlId) {
				for (BalloonStatement statement: statementList) {
					statement.setConversionTried(false);
				}
				cityGMLClassForBalloonHandler = cityObjectTypeForGmlId;
			}

			List<String> resultList = new ArrayList<String>();
			for (BalloonStatement statement: statementList) {
				resultList.add(executeStatement(statement, id, lod, connection, schemaName));
			}

			Iterator<String> htmlChunkIterator = htmlChunkList.iterator();
			Iterator<String> resultIterator = resultList.iterator();

			while (htmlChunkIterator.hasNext()) {
				balloonContent.append(htmlChunkIterator.next());
				if (resultIterator.hasNext()) {
					balloonContent.append(resultIterator.next());
				}
			}
		}
		return balloonContent.toString();
	}

	public String getBalloonContent(long id, int lod, Connection connection, String schemaName) throws Exception {
		if (connection == null) throw new SQLException("Null or invalid connection");
		if (statementList == null && htmlChunkList == null) throw new Exception("Invalid template file");

		if (schemaName == null)
			schemaName = databaseAdapter.getSchemaManager().getDefaultSchema();

		StringBuilder balloonContent = new StringBuilder();

		if (statementList != null) {
			// when properly initialized this happens only at the first object
			// otherwise it avoids problems from lousy initialization of BalloonTemplateHandlers in threads
			// at the cost of performance

			CityGMLClass cityObjectTypeForId = null;

			ResultSet rs = null;
			PreparedStatement query = null;
			try {
				query = connection.prepareStatement(new StringBuilder("SELECT gmlid, objectclass_id FROM ").append(schemaName).append(".CITYOBJECT WHERE id = ?").toString());
				query.setLong(1, id);
				rs = query.executeQuery();

				if (rs.next()) {
					objectClassId = rs.getInt("objectclass_id");
					cityObjectTypeForId = Util.getCityGMLClass(objectClassId);
				}
			}
			catch (SQLException sqlEx) {}
			finally {
				if (rs != null) {
					try { rs.close(); }	catch (SQLException sqlEx) {}
					rs = null;
				}

				if (query != null) {
					try { query.close(); } catch (SQLException sqlEx) {}
					query = null;
				}
			}

			if (cityGMLClassForBalloonHandler != cityObjectTypeForId) {
				for (BalloonStatement statement: statementList) {
					statement.setConversionTried(false);
				}
				cityGMLClassForBalloonHandler = cityObjectTypeForId;
			}

			List<String> resultList = new ArrayList<String>();
			for (BalloonStatement statement: statementList) {
				resultList.add(executeStatement(statement, id, lod, connection, schemaName));
			}

			Iterator<String> htmlChunkIterator = htmlChunkList.iterator();
			Iterator<String> resultIterator = resultList.iterator();

			while (htmlChunkIterator.hasNext()) {
				balloonContent.append(htmlChunkIterator.next());
				if (resultIterator.hasNext()) {
					balloonContent.append(resultIterator.next());
				}
			}
		}
		return balloonContent.toString();
	}

	private String executeStatement(BalloonStatement statement, long id, int lod, Connection connection, String schemaName) {
		String result = "";
		String query = "";
		if (statement != null) {
			PreparedStatement preparedStatement = null;
			ResultSet rs = null;
			try {
				if (statement.isForeach()) {
					return executeForeachStatement(statement, id, lod, connection, schemaName);
				}

				if (statement.isNested()) {
					String rawStatement = statement.getRawStatement();
					List<String> textBetweenNestedStatements = new ArrayList<String>();
					List<BalloonStatement> nestedStatementList = new ArrayList<BalloonStatement>();
					int nestingLevel = 0;
					int lastIndex = 0;
					int index = 0;
					int beginOfSubexpression = 0;

					while (nestingLevel > 0 || rawStatement.indexOf(END_TAG, index) > -1) {
						int indexOfNextStart = rawStatement.indexOf(START_TAG, index);
						int indexOfNextEnd = rawStatement.indexOf(END_TAG, index);
						if (indexOfNextStart != -1 && indexOfNextStart < indexOfNextEnd) {
							nestingLevel++;
							if (nestingLevel == 1) {
								textBetweenNestedStatements.add(rawStatement.substring(lastIndex, indexOfNextStart));
								beginOfSubexpression = indexOfNextStart + START_TAG.length();
							}
							index = indexOfNextStart + START_TAG.length();
						}
						else {
							nestingLevel--;
							index = indexOfNextEnd;
							if (nestingLevel == 0) {
								String originalNestedStatement = rawStatement.substring(beginOfSubexpression, index);
								BalloonStatement nestedStatement = new BalloonStatement(originalNestedStatement);
								nestedStatement.setNested(originalNestedStatement.contains(START_TAG));
								nestedStatementList.add(nestedStatement);
								lastIndex = index + END_TAG.length();
							}
							index = index + END_TAG.length();
						}
					}
					textBetweenNestedStatements.add(rawStatement.substring(index));

					StringBuilder notNestedAnymore = new StringBuilder();
					if (nestedStatementList != null) {
						List<String> resultList = new ArrayList<String>();
						for (BalloonStatement nestedStatement: nestedStatementList) {
							resultList.add(executeStatement(nestedStatement, id, lod, connection, schemaName));
						}

						Iterator<String> textIterator = textBetweenNestedStatements.iterator();
						Iterator<String> resultIterator = resultList.iterator();

						while (textIterator.hasNext()) {
							notNestedAnymore.append(textIterator.next());
							if (resultIterator.hasNext()) {
								notNestedAnymore.append(resultIterator.next());
							}
						}
					}

					BalloonStatement dummy = new BalloonStatement(notNestedAnymore.toString());
					query = dummy.getProperSQLStatement(lod, schemaName);
					preparedStatement = connection.prepareStatement(query);
				}
				else { // not nested
					if (statement.getProperSQLStatement(lod, schemaName) == null) {
						// malformed expression between proper START_TAG and END_TAG
						return result; // skip db call, rs and preparedStatement are currently null
					}
					query = statement.getProperSQLStatement(lod, schemaName);
					preparedStatement = connection.prepareStatement(query);
				}

				for (int i = 1; i <= getParameterCount(query); i++)
					preparedStatement.setLong(i, id);

				rs = preparedStatement.executeQuery();
				while (rs.next()) {
					if (rs.getRow() > 1) {
						result = result + ", ";
					}
					Object object = rs.getObject(1);
					if (!rs.wasNull() && object != null) {
						GeometryObject geomObj = databaseAdapter.getGeometryConverter().getGeometry(object);
						if (geomObj != null) {
							int dimension = geomObj.getDimension();

							result = result + "(";
							for (int ringNo = 0; ringNo < geomObj.getNumElements(); ringNo++) {
								double[] ring = geomObj.getCoordinates(ringNo);

								for (int i = 0; i < ring.length; i = i + dimension) {
									for (int j = 0; j < dimension; j++) {
										result = result + ring[i+j];
										if (j < dimension - 1) 
											result = result + ",";
									}

									if (i+dimension < ring.length)
										result = result + " ";
								}	

								if (ringNo < geomObj.getNumElements() - 1)
									result = result + " ";
							}
							result = result + ")";
						}
						else {
							String tmp = rs.getObject(1).toString();
							if (tmp.indexOf("oracle.sql.TIMESTAMPTZ") >= 0) {
								tmp =  rs.getTimestamp(1).toString();
							}
							result = result + tmp.replaceAll("\"", "&quot;"); // workaround, the JAXB KML marshaler does not escape " properly;
						}
					}
				}
			}
			catch (Exception e) {
				log.warn("Exception when executing balloon statement: " + statement.rawStatement + " --> " + e.getMessage());
			}
			finally {
				try {
					if (rs != null) rs.close();
					if (preparedStatement != null) preparedStatement.close();
				}
				catch (Exception e2) {}
			}
		}
		return result;
	}

	private String executeForeachStatement(BalloonStatement statement, long id, int lod, Connection connection, String schemaName) {
		String resultBody = "";

		PreparedStatement preparedStatement = null;
		ResultSet rs = null;
		try {
			if (statement != null && statement.getProperSQLStatement(lod, schemaName) != null) {
				String query = statement.getProperSQLStatement(lod, schemaName);
				preparedStatement = connection.prepareStatement(query);
				for (int i = 1; i <= getParameterCount(query); i++)
					preparedStatement.setLong(i, id);

				rs = preparedStatement.executeQuery();
				while (rs.next()) {
					String iterationBody = statement.getForeachBody();
					for (int n = 0; n <= statement.getColumnAmount(); n++) {
						String columnValue = "";
						if (n == 0) {
							columnValue = String.valueOf(rs.getRow());
						}
						else {
							Object object = rs.getObject(n);
							if (!rs.wasNull() && object != null) {
								GeometryObject geomObj = databaseAdapter.getGeometryConverter().getGeometry(object);
								if (geomObj != null) {
									int dimension = geomObj.getDimension();

									columnValue = columnValue + "(";
									for (int ringNo = 0; ringNo < geomObj.getNumElements(); ringNo++) {
										double[] ring = geomObj.getCoordinates(ringNo);

										for (int i = 0; i < ring.length; i = i + dimension) {
											for (int j = 0; j < dimension; j++) {
												columnValue = columnValue + ring[i+j];
												if (j < dimension - 1) 
													columnValue = columnValue + ",";
											}

											if (i+dimension < ring.length)
												columnValue = columnValue + " ";
										}	

										if (ringNo < geomObj.getNumElements() - 1)
											columnValue = columnValue + " ";
									}
									columnValue = columnValue + ")";
								}
								else {
									columnValue = rs.getObject(n).toString().replaceAll("\"", "&quot;"); // workaround, the JAXB KML marshaler does not escape " properly
								}
							}
						}
						iterationBody = iterationBody.replaceAll("%" + n, columnValue);
					}
					resultBody = resultBody + iterationBody;
				}
			}
		}
		catch (Exception e) {
			log.warn(e.getMessage());
		}
		finally {
			try {
				if (rs != null) rs.close();
				if (preparedStatement != null) preparedStatement.close();
			}
			catch (Exception e2) {}
		}
		return resultBody;
	}

	private void fillStatementAndHtmlChunkList(String template) throws Exception {
		// parse like it's 1999
		int lastIndex = 0;
		int index = 0;
		while (template.indexOf(START_TAG, lastIndex) != -1) {
			index = template.indexOf(START_TAG, lastIndex);
			int nestingLevel = 1;
			htmlChunkList.add(template.substring(lastIndex, index));
			index = index + START_TAG.length();
			int beginOfExpression = index;
			while (nestingLevel > 0) {
				int indexOfNextStart = template.indexOf(START_TAG, index);
				int indexOfNextEnd = template.indexOf(END_TAG, index);
				if (indexOfNextEnd == -1) {
					throw new Exception("Malformed balloon template. Please review nested " + START_TAG + " expressions.");
				}
				if (indexOfNextStart != -1 && indexOfNextStart < indexOfNextEnd) {
					nestingLevel++;
					index = indexOfNextStart + START_TAG.length();
				}
				else {
					nestingLevel--;
					index = indexOfNextEnd;
					if (nestingLevel == 0) {
						String originalStatement = template.substring(beginOfExpression, index).trim();
						BalloonStatement statement = new BalloonStatement(originalStatement);
						statement.setNested(originalStatement.contains(START_TAG));
						statement.setForeach(originalStatement.toUpperCase().startsWith(FOREACH_TAG));
						if (statement.isForeach()) {
							// look for END FOREACH statement
							index = index + END_TAG.length();
							indexOfNextStart = template.indexOf(START_TAG, index);
							indexOfNextEnd = template.indexOf(END_TAG, index);
							if (indexOfNextStart == -1 || indexOfNextEnd == -1 || indexOfNextEnd < indexOfNextStart) {
								throw new Exception("Malformed balloon template. Please review " + START_TAG + FOREACH_TAG + " expressions.");
							}
							String closingStatement = template.substring(indexOfNextStart + START_TAG.length(), indexOfNextEnd).trim();
							if (!END_FOREACH_TAG.equalsIgnoreCase(closingStatement)) {
								throw new Exception("Malformed balloon template. Please review " + START_TAG + FOREACH_TAG + " expressions.");
							}
							statement.setForeachBody(template.substring(index, indexOfNextStart));
							index = indexOfNextEnd;
						}
						statementList.add(statement);
						lastIndex = index + END_TAG.length();
					}
					index = index + END_TAG.length();
				}
			}
		}
		htmlChunkList.add(template.substring(index)); // last chunk
	}


	private class BalloonStatement {
		private String rawStatement;
		private boolean nested = false;
		private String properSQLStatement = null;
		private boolean conversionTried = false;
		private int columnAmount;
		private boolean foreach = false;
		private String foreachBody;

		private String tableShortId;
		private boolean orderByColumnAllowed = true;

		BalloonStatement (String rawStatement) {
			this.setRawStatement(rawStatement);
		}

		private void setRawStatement(String rawStatement) {
			this.rawStatement = rawStatement;
		}

		private String getRawStatement() {
			return rawStatement;
		}

		private void setNested(boolean nested) {
			this.nested = nested;
		}

		private boolean isNested() {
			return nested;
		}

		private void setProperSQLStatement(String properSQLStatement) {
			this.properSQLStatement = properSQLStatement;
		}

		private String getProperSQLStatement(int lod, String schemaName) throws Exception {
			if (!conversionTried && properSQLStatement == null) {
				this.convertStatementToProperSQL(lod, schemaName);
				conversionTried = true;
			}
			return properSQLStatement;
		}

		private void setConversionTried(boolean conversionTried) {
			this.conversionTried = conversionTried;
		}

		private boolean isForeach() {
			return foreach;
		}

		private void setForeach(boolean foreach) {
			this.foreach = foreach;
		}

		private String getForeachBody() {
			return foreachBody;
		}

		private void setForeachBody(String foreachBody) {
			this.foreachBody = foreachBody;
		}

		private int getColumnAmount() {
			return columnAmount;
		}

		private void setColumnAmount(int columnAmount) {
			this.columnAmount = columnAmount;
		}

		private void convertStatementToProperSQL(int lod, String schemaName) throws Exception {

			String sqlStatement = null;
			String table = null;
			String aggregateFunction = null;
			List<String> columns = null;
			String condition = null;

			int index = rawStatement.indexOf('/');
			if (index == -1) {
				throw new Exception("Invalid statement \"" + rawStatement + "\". Column name not set.");
			}

			if (isForeach()) {
				table = rawStatement.substring(FOREACH_TAG.length(), index).trim();
			}
			else {
				table = rawStatement.substring(0, index).trim();
			}

			index++;
			if (SPECIAL_KEYWORDS.equalsIgnoreCase(table)) {
				sqlStatement = checkForSpecialKeywords(rawStatement.substring(index), schemaName);
				if (sqlStatement != null) {
					setProperSQLStatement(sqlStatement);
					return;
				}
			}

			if (index >= rawStatement.length()) {
				throw new Exception("Invalid statement \"" + rawStatement + "\". Column name not set.");
			}
			if (rawStatement.charAt(index) == '[') { // beginning of aggregate function
				if (isForeach()) {
					throw new Exception("Invalid statement \"" + rawStatement + "\". No aggregation functions allowed here.");
				}
				index++;
				if (index >= rawStatement.length()) {
					throw new Exception("Invalid statement \"" + rawStatement + "\"");
				}
				if (rawStatement.indexOf(']', index) == -1) {
					throw new Exception("Invalid statement \"" + rawStatement + "\". Missing ']' character.");
				}
				aggregateFunction = rawStatement.substring(index, rawStatement.indexOf(']', index)).trim();
				index = rawStatement.indexOf(']', index) + 1;
				if (index >= rawStatement.length()) {
					throw new Exception("Invalid statement \"" + rawStatement + "\". Column name not set.");
				}
			}

			String columnsClauseString = null;
			if (rawStatement.indexOf('[', index) == -1) { // no condition
				columnsClauseString = rawStatement.substring(index).trim();
			}
			else {
				columnsClauseString = rawStatement.substring(index, rawStatement.indexOf('[', index)).trim();
				index = rawStatement.indexOf('[', index) + 1;
				if (index >= rawStatement.length()) {
					throw new Exception("Invalid statement \"" + rawStatement + "\"");
				}
				if (rawStatement.indexOf(']', index) == -1) {
					throw new Exception("Invalid statement \"" + rawStatement + "\". Missing ']' character.");
				}
				condition = rawStatement.substring(index, rawStatement.indexOf(']', index)).trim();
				try {
					if (Integer.parseInt(condition) < 0) {
						throw new Exception("Invalid condition \"" + condition + "\" in statement \"" + rawStatement);
					}
				}
				catch (NumberFormatException nfe) {
					ArrayList<Integer> indexOfPossibleSeparators = new ArrayList<Integer>();
					indexOfPossibleSeparators.add(condition.indexOf('='));
					indexOfPossibleSeparators.add(condition.indexOf("!="));
					indexOfPossibleSeparators.add(condition.indexOf("<>"));
					indexOfPossibleSeparators.add(condition.indexOf('<'));
					indexOfPossibleSeparators.add(condition.indexOf('>'));
					indexOfPossibleSeparators.add(condition.toUpperCase().indexOf(" IS NULL "));
					indexOfPossibleSeparators.add(condition.toUpperCase().indexOf(" IS NOT NULL "));
					indexOfPossibleSeparators.add(condition.toUpperCase().indexOf(" LIKE "));

					int indexOfSeparator = Collections.max(indexOfPossibleSeparators);

					if (indexOfSeparator < 1) {
						throw new Exception("Invalid condition \"" + condition + "\" in statement \"" + rawStatement);
					}
					String conditionColumnName = condition.substring(0, indexOfSeparator).trim();
					if (cityGMLClassForBalloonHandler != CityGMLClass.ADE_COMPONENT &&
							!_3DCITYDB_TABLES_AND_COLUMNS.get(table).contains(conditionColumnName)) {
						throw new Exception("Unsupported column \"" + conditionColumnName + "\" in statement \"" + rawStatement + "\"");
					}
				}
			}

			if (columnsClauseString == null) {
				throw new Exception("Invalid statement \"" + rawStatement + "\". Column name not set.");
			}
			else {
				columns = new ArrayList<String>();
				StringTokenizer columnTokenizer = new StringTokenizer(columnsClauseString, ",");
				while (columnTokenizer.hasMoreTokens()) {
					columns.add(columnTokenizer.nextToken().toUpperCase().trim());
				}
				setColumnAmount(columns.size());
			}

			String aggregateString = "";
			String aggregateClosingString = "";
			if (aggregateFunction != null) {
				if (MAX.equalsIgnoreCase(aggregateFunction) ||
						MIN.equalsIgnoreCase(aggregateFunction) ||
						AVG.equalsIgnoreCase(aggregateFunction) ||
						COUNT.equalsIgnoreCase(aggregateFunction) ||
						SUM.equalsIgnoreCase(aggregateFunction)) {
					aggregateString = aggregateFunction + "(";
					aggregateClosingString = ")";
				}
				else if (!FIRST.equalsIgnoreCase(aggregateFunction) &&
						!LAST.equalsIgnoreCase(aggregateFunction)) {
					throw new Exception("Unsupported aggregate function \"" + aggregateFunction + "\" in statement \"" + rawStatement + "\"");
				}
			}

			switch (cityGMLClassForBalloonHandler) {
				case ADE_COMPONENT:
					sqlStatement = sqlStatementForADEObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case CITY_FURNITURE:
					sqlStatement = sqlStatementForCityFurniture(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case PLANT_COVER:
					sqlStatement = sqlStatementForPlantCover(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case SOLITARY_VEGETATION_OBJECT:
					sqlStatement = sqlStatementForSolVegObj(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case TRAFFIC_AREA:
				case AUXILIARY_TRAFFIC_AREA:
					sqlStatement = sqlStatementForTrafficArea(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case TRANSPORTATION_COMPLEX:
				case TRACK:
				case RAILWAY:
				case ROAD:
				case SQUARE:
					sqlStatement = sqlStatementForTransportationComplex(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case LAND_USE:
					sqlStatement = sqlStatementForLandUse(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				/*
				case RASTER_RELIEF:
				case MASSPOINT_RELIEF:
				case BREAKLINE_RELIEF:
				case TIN_RELIEF:
				 */
				case RELIEF_FEATURE:
					sqlStatement = sqlStatementForRelief(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case WATER_BODY:
					sqlStatement = sqlStatementForWaterBody(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case WATER_CLOSURE_SURFACE:
				case WATER_GROUND_SURFACE:
				case WATER_SURFACE:
					sqlStatement = sqlStatementForWaterSurface(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case GENERIC_CITY_OBJECT:
					sqlStatement = sqlStatementForGenCityObj(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case BUILDING:
					sqlStatement = sqlStatementForBuilding(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case BRIDGE:
					sqlStatement = sqlStatementForBridge(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case TUNNEL:
					sqlStatement = sqlStatementForTunnel(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				case CITY_OBJECT_GROUP:
					sqlStatement = sqlStatementForCityObjectGroup(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
					break;
				default:
					sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, false);
					break;
			}

			if (sqlStatement != null) {
				int rownum = 0;
				if (condition != null) {
					try {
						rownum = Integer.parseInt(condition);
					}
					catch (Exception e) { // not a number, but a logical condition
						sqlStatement = sqlStatement + " AND " + tableShortId + "." + condition;
					}
				}
				if (aggregateFunction == null) {
					if (orderByColumnAllowed) {
						sqlStatement = sqlStatement + " ORDER by " + tableShortId + "." + columns.get(0);
					}
				}
				else {
					if ((!orderByColumnAllowed) && (rownum > 0
							|| (FIRST.equalsIgnoreCase(aggregateFunction)
									|| LAST.equalsIgnoreCase(aggregateFunction)
									|| AVG.equalsIgnoreCase(aggregateFunction)
									|| MAX.equalsIgnoreCase(aggregateFunction) || MIN
									.equalsIgnoreCase(aggregateFunction)))) {
						throw new Exception(columns.get(0) + " in " + table + " doesn't suppport aggregate function: " + aggregateFunction);
					}
					if (rownum > 0) {
						switch (databaseAdapter.getDatabaseType()) {
						case ORACLE:
							sqlStatement = "SELECT * FROM (SELECT a.*, ROWNUM rnum FROM (" + sqlStatement
							+ " ORDER by " + tableShortId + "." + columns.get(0)
							+ " ASC) a WHERE ROWNUM <= " + rownum + ") WHERE rnum >= " + rownum;
							break;
						case POSTGIS:
							sqlStatement = "SELECT * FROM "
									+ "(SELECT sqlstat.*, ROW_NUMBER() OVER() AS rnum FROM "
									+ "(" + sqlStatement + " ORDER BY "
									+ tableShortId + "." + columns.get(0) + " ASC) sqlstat) AS subq"
									+ " WHERE rnum = " + rownum;
							break;
						}
					}
					else if (FIRST.equalsIgnoreCase(aggregateFunction)) {
						switch (databaseAdapter.getDatabaseType()) {
						case ORACLE:
							sqlStatement = "SELECT * FROM (" + sqlStatement
							+ " ORDER by " + tableShortId + "." + columns.get(0)
							+ " ASC) WHERE ROWNUM = 1";
							break;
						case POSTGIS:
							sqlStatement = "SELECT * FROM "
									+ "(SELECT sqlstat.*, ROW_NUMBER() OVER() AS rnum FROM "
									+ "(" + sqlStatement + " ORDER BY "
									+ tableShortId + "." + columns.get(0) + " ASC) sqlstat) AS subq"
									+ " WHERE rnum = 1";
							break;
						}
					}
					else if (LAST.equalsIgnoreCase(aggregateFunction)) {
						switch (databaseAdapter.getDatabaseType()) {
						case ORACLE:
							sqlStatement = "SELECT * FROM (" + sqlStatement
							+ " ORDER by " + tableShortId + "." + columns.get(0)
							+ " DESC) WHERE ROWNUM = 1";
							break;
						case POSTGIS:
							sqlStatement = "SELECT * FROM "
									+ "(SELECT sqlstat.*, ROW_NUMBER() OVER() AS rnum FROM "
									+ "(" + sqlStatement + " ORDER BY "
									+ tableShortId + "." + columns.get(0) + " DESC) sqlstat) AS subq"
									+ " WHERE rnum = 1";
							break;
						}
					}
					// no ORDER by for MAX, MIN, AVG, COUNT, SUM
				}
			}

			setProperSQLStatement(sqlStatement);
		}

		private String sqlStatementForADEObject(String table,
		                                        List<String> columns,
		                                        String aggregateString,
		                                        String aggregateClosingString,
		                                        int lod,
		                                        String schemaName) throws Exception {
			String columnsClause = getColumnsClause(table, columns);
			ADEBalloonManager balloonManager = ADEBalloonExtensionManager.getInstance().getBalloonManager(objectClassId);
			ADEBalloonHandler adeBalloonHandler = balloonManager.getBalloonHandler(objectClassId);
			String aggregateColumnsClause = aggregateString + columnsClause + aggregateClosingString;
			String sqlStatement = adeBalloonHandler.getSqlStatement(table, tableShortId, aggregateColumnsClause, lod, schemaName);

			if (sqlStatement == null) {
				AbstractGML modelObject = Util.createObject(objectClassId, CityGMLVersion.v2_0_0);
				if (modelObject instanceof CityFurniture) {
					sqlStatement = sqlStatementForCityFurniture(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof PlantCover) {
					sqlStatement = sqlStatementForPlantCover(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof SolitaryVegetationObject) {
					sqlStatement = sqlStatementForSolVegObj(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof TrafficArea || modelObject instanceof AuxiliaryTrafficArea) {
					sqlStatement = sqlStatementForTrafficArea(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof TransportationComplex) {
					sqlStatement = sqlStatementForTransportationComplex(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof LandUse) {
					sqlStatement = sqlStatementForLandUse(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof ReliefFeature) {
					sqlStatement = sqlStatementForRelief(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof WaterBody) {
					sqlStatement = sqlStatementForWaterBody(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof AbstractWaterBoundarySurface) {
					sqlStatement = sqlStatementForWaterSurface(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof GenericCityObject) {
					sqlStatement = sqlStatementForGenCityObj(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof Building) {
					sqlStatement = sqlStatementForBuilding(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof Bridge) {
					sqlStatement = sqlStatementForBridge(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof Tunnel) {
					sqlStatement = sqlStatementForTunnel(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else if (modelObject instanceof CityObjectGroup) {
					sqlStatement = sqlStatementForCityObjectGroup(table, columns, aggregateString, aggregateClosingString, lod, schemaName);
				} else {
					sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, false);
				}
			}

			return sqlStatement;
		}

		private String sqlStatementForBuilding(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (ADDRESS_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".ADDRESS_TO_BUILDING a2b, " + schemaName + ".ADDRESS a" +
						" WHERE a2b.building_id = ?" +
						" AND a.id = a2b.address_id";
			}
			else if (ADDRESS_TO_BUILDING_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".ADDRESS_TO_BUILDING a2b" +
						" WHERE a2b.building_id = ?";
			}
			else if (BUILDING_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BUILDING b" +
						" WHERE b.id = ?";
			}
			else if (BUILDING_FURNITURE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BUILDING b, " + schemaName + ".BUILDING_FURNITURE bf, " + schemaName + ".ROOM r" +
						" WHERE b.building_root_id = ?" +
						" AND b.id = r.building_id" +
						" AND bf.room_id = r.id";
			}
			else if (BUILDING_INSTALLATION_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BUILDING b, " + schemaName + ".BUILDING_INSTALLATION bi" +
						" WHERE b.building_root_id = ?" +
						" AND bi.building_id = b.id";
			}
			else if (OPENING_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BUILDING b, " + schemaName + ".THEMATIC_SURFACE ts, " + schemaName + ".OPENING_TO_THEM_SURFACE o2ts, " + schemaName + ".OPENING o" +
						" WHERE b.building_root_id = ?" +
						" AND ts.building_id = b.id" +
						" AND o2ts.thematic_surface_id = ts.id" +
						" AND o.id = o2ts.opening_id";
			}
			else if (OPENING_TO_THEM_SURFACE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BUILDING b, " + schemaName + ".THEMATIC_SURFACE ts, " + schemaName + ".OPENING_TO_THEM_SURFACE o2ts" +
						" WHERE b.building_root_id = ?" +
						" AND ts.building_id = b.id" +
						" AND o2ts.thematic_surface_id = ts.id";
			}
			else if (ROOM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BUILDING b, " + schemaName + ".ROOM r" +
						" WHERE b.building_root_id = ?" +
						" AND r.building_id = b.id";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				if (lod == 0) {
					sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
							" FROM " + schemaName + ".SURFACE_GEOMETRY sg, " + schemaName + ".BUILDING b" +
							" WHERE sg.id IN" +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod0_footprint_id" +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod0_roofprint_id)";
				}
				else {
					sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
							" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE sg.id IN" +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod" + lod + "_multi_surface_id" +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod" + lod + "_solid_id";
					if (lod > 1) {
						sqlStatement = sqlStatement +
								" UNION" +
								" SELECT sg.id" +
								" FROM " + schemaName + ".BUILDING b, " + schemaName + ".THEMATIC_SURFACE ts, " + schemaName + ".SURFACE_GEOMETRY sg" +
								" WHERE b.building_root_id = ?" +
								" AND ts.building_id = b.id" +
								" AND sg.root_id = ts.lod" + lod + "_multi_surface_id";
					}
					sqlStatement = sqlStatement + ") tmp )";
				}
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				if (lod == 0) {
					sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
							" FROM " + schemaName + ".TEXTUREPARAM tp" +
							" WHERE tp.surface_geometry_id IN" +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod0_footprint_id" +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod0_roofprint_id)";
				}
				else {
					sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
							" FROM " + schemaName + ".TEXTUREPARAM tp" +
							" WHERE tp.surface_geometry_id IN" +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod" + lod + "_multi_surface_id" +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".BUILDING b, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.building_root_id = ?" +
							" AND sg.root_id = b.lod" + lod + "_solid_id";
					if (lod > 1) {
						sqlStatement = sqlStatement +
								" UNION" +
								" SELECT sg.id" +
								" FROM " + schemaName + ".BUILDING b, " + schemaName + ".THEMATIC_SURFACE ts, " + schemaName + ".SURFACE_GEOMETRY sg" +
								" WHERE b.building_root_id = ?" +
								" AND ts.building_id = b.id" +
								" AND sg.root_id = ts.lod" + lod + "_multi_surface_id";
					}
					sqlStatement = sqlStatement + ") tmp )";
				}

			}
			else if (THEMATIC_SURFACE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BUILDING b, " + schemaName + ".THEMATIC_SURFACE ts" +
						" WHERE b.building_root_id = ?" +
						" AND ts.building_id = b.id";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForLandUse(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (LAND_USE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".LAND_USE lu" +
						" WHERE lu.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg, " + schemaName + ".LAND_USE lu" +
						" WHERE lu.id = ?" +
						" AND sg.root_id = lu.lod" + lod + "_multi_surface_id";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp, " + schemaName + ".SURFACE_GEOMETRY sg, " + schemaName + ".LAND_USE lu" +
						" WHERE lu.id = ?" +
						" AND sg.root_id = lu.lod" + lod + "_multi_surface_id" +
						" AND tp.surface_geometry_id = sg.id";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}
			return sqlStatement;
		}

		private String sqlStatementForSolVegObj(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (SOLITARY_VEGETAT_OBJECT_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SOLITARY_VEGETAT_OBJECT svo" +
						" WHERE svo.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.root_id IN" +
						" (SELECT tmp.id from (SELECT ig.relative_brep_id as id" +
						" FROM " + schemaName + ".SOLITARY_VEGETAT_OBJECT svo, " + schemaName + ".IMPLICIT_GEOMETRY ig" +
						" WHERE svo.id = ?" +
						" AND ig.id = svo.lod" + lod + "_implicit_rep_id" +
						" UNION" +
						" SELECT svo.lod" + lod + "_brep_id as id" +
						" FROM " + schemaName + ".SOLITARY_VEGETAT_OBJECT svo" +
						" WHERE svo.id = ?";
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (SELECT sg.id" +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.root_id IN" +
						" (SELECT tmp.id from (SELECT ig.relative_brep_id as id" +
						" FROM " + schemaName + ".SOLITARY_VEGETAT_OBJECT svo, " + schemaName + ".IMPLICIT_GEOMETRY ig" +
						" WHERE svo.id = ?" +
						" AND ig.id = svo.lod" + lod + "_implicit_rep_id" +
						" UNION" +
						" SELECT svo.lod" + lod + "_brep_id as id" +
						" FROM " + schemaName + ".SOLITARY_VEGETAT_OBJECT svo" +
						" WHERE svo.id = ?";
				sqlStatement = sqlStatement + ") tmp))";
			}
			else if (PLANT_COVER_TABLE.equalsIgnoreCase(table)) { } // tolerate but do nothing
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForPlantCover(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (PLANT_COVER_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".PLANT_COVER pc" +
						" WHERE pc.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".PLANT_COVER pc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE pc.id = ?" +
						" AND sg.root_id = pc.lod" + lod + "_multi_surface_id" +
						" UNION" +
						" SELECT sg.id" +
						" FROM " + schemaName + ".PLANT_COVER pc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE pc.id = ?" +
						" AND sg.root_id = pc.lod" + lod + "_multi_solid_id";
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".PLANT_COVER pc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE pc.id = ?" +
						" AND sg.root_id = pc.lod" + lod + "_multi_surface_id" +
						" UNION" +
						" SELECT sg.id" +
						" FROM " + schemaName + ".PLANT_COVER pc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE pc.id = ?" +
						" AND sg.root_id = pc.lod" + lod + "_multi_solid_id";
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (SOLITARY_VEGETAT_OBJECT_TABLE.equalsIgnoreCase(table)) { } // tolerate but do nothing
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForWaterBody(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (WATERBODY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".WATERBODY wb" +
						" WHERE wb.id = ?";
			}
			else if (WATERBOUNDARY_SURFACE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBOUNDARY_SURFACE wbs" +
						" WHERE wb2wbs.waterbody_id = ?" +
						" AND wbs.id = wb2wbs.waterboundary_surface_id";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE wb.id = ?" +
						" AND sg.root_id = wb.lod" + lod + "_solid_id";
				if (lod < 2) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb.id = ?" +
							" AND sg.root_id = wb.lod" + lod + "_multi_surface_id";
				}
				else {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBOUNDARY_SURFACE wbs, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb2wbs.waterbody_id = ?" +
							" AND wbs.id = wb2wbs.waterboundary_surface_id" +
							" AND sg.root_id = wbs.lod" + lod + "_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE wb.id = ?" +
						" AND sg.root_id = wb.lod" + lod + "_solid_id";
				if (lod < 2) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb.id = ?" +
							" AND sg.root_id = wb.lod" + lod + "_multi_surface_id";
				}
				else {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBOUNDARY_SURFACE wbs, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb2wbs.waterbody_id = ?" +
							" AND wbs.id = wb2wbs.waterboundary_surface_id" +
							" AND sg.root_id = wbs.lod" + lod + "_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}

			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForWaterSurface(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (WATERBODY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBODY wb" +
						" WHERE wb2wbs.waterboundary_surface_id = ?" +
						" AND wb2wbs.waterbody_id = wb.id";
			}
			else if (WATERBOUNDARY_SURFACE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".WATERBOUNDARY_SURFACE wbs" +
						" WHERE wbs.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN ";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOUNDARY_SURFACE wbs, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wbs.id = ?" +
							" AND sg.root_id = wbs.lod" + lod + "_surface_id" +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb2wbs.waterboundary_surface_id = ?" +
							" AND wb2wbs.waterbody_id = wb.id" +
							" AND sg.root_id = wb.lod" + lod + "_solid_id";
				}
				else {
					sqlStatement = sqlStatement +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb2wbs.waterboundary_surface_id = ?" +
							" AND wb2wbs.waterbody_id = wb.id" +
							" AND sg.root_id = wb.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN ";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOUNDARY_SURFACE wbs, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wbs.id = ?" +
							" AND sg.root_id = wbs.lod" + lod + "_surface_id" +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb2wbs.waterboundary_surface_id = ?" +
							" AND wb2wbs.waterbody_id = wb.id" +
							" AND sg.root_id = wb.lod" + lod + "_solid_id";
				}
				else {
					sqlStatement = sqlStatement +
							" (select tmp.id from (SELECT sg.id" +
							" FROM " + schemaName + ".WATERBOD_TO_WATERBND_SRF wb2wbs, " + schemaName + ".WATERBODY wb, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE wb2wbs.waterboundary_surface_id = ?" +
							" AND wb2wbs.waterbody_id = wb.id" +
							" AND sg.root_id = wb.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForTrafficArea(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (TRAFFIC_AREA_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TRAFFIC_AREA ta" +
						" WHERE ta.id = ?";
			}
			else if (TRANSPORTATION_COMPLEX_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TRAFFIC_AREA ta, " + schemaName + ".TRANSPORTATION_COMPLEX tc" +
						" WHERE ta.id = ?" +
						" AND tc.id = ta.transportation_complex_id";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table) && (lod > 0)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".TRAFFIC_AREA ta, " + schemaName + ".TRANSPORTATION_COMPLEX tc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE ta.id = ?" +
						" AND tc.id = ta.transportation_complex_id" +
						" AND sg.root_id = tc.lod" + lod + "_multi_surface_id";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".TRAFFIC_AREA ta, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE ta.id = ?" +
							" AND sg.root_id = ta.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table) && (lod > 0)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".TRAFFIC_AREA ta, " + schemaName + ".TRANSPORTATION_COMPLEX tc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE ta.id = ?" +
						" AND tc.id = ta.transportation_complex_id" +
						" AND sg.root_id = tc.lod" + lod + "_multi_surface_id";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".TRAFFIC_AREA ta, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE ta.id = ?" +
							" AND sg.root_id = ta.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForTransportationComplex(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (TRAFFIC_AREA_TABLE.equalsIgnoreCase(table)) { } // tolerate but do nothing
			else if (TRANSPORTATION_COMPLEX_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TRANSPORTATION_COMPLEX tc" +
						" WHERE tc.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table) && (lod > 0)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN " +
						"(SELECT sg.id" +
						" FROM " + schemaName + ".TRANSPORTATION_COMPLEX tc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE tc.id = ?" +
						" AND sg.root_id = tc.lod" + lod + "_multi_surface_id)";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table) && (lod > 0)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN " +
						"(select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".TRANSPORTATION_COMPLEX tc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE tc.id = ?" +
						" AND sg.root_id = tc.lod" + lod + "_multi_surface_id) tmp )";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForRelief(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;


			if (RELIEF_COMPONENT_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".RELIEF_FEATURE rf, " + schemaName + ".RELIEF_FEAT_TO_REL_COMP rf2rc, " + schemaName + ".RELIEF_COMPONENT rc" +
						" WHERE rf.id = ?" +
						" AND rf.lod = " + lod +
						" AND rf2rc.relief_feature_id = rf.id" +
						" AND rc.id = rf2rc.relief_component_id";
			}
			else if (RELIEF_FEATURE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".RELIEF_FEATURE rf" +
						" WHERE rf.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table) && (lod > 0)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN " +
						"(SELECT sg.id" +
						" FROM " + schemaName + ".TIN_RELIEF tr, " + schemaName + ".RELIEF_FEATURE rf, " + schemaName + ".RELIEF_FEAT_TO_REL_COMP rftrc, " + schemaName + ".RELIEF_COMPONENT rc, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE rf.id = ?" +
						" AND rc.id = rc.id" +
						" AND rf.id = rftrc.relief_feature_id" +
						" AND rftrc.relief_component_id = rc.id" +
						" AND sg.root_id = tr.surface_geometry_id)";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table) && (lod > 0)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN " +
						"(select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".TIN_RELIEF tr, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE tr.id = ?" +
						" AND sg.root_id = tr.surface_geometry_id) tmp )";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForGenCityObj(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (GENERIC_CITYOBJECT_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".GENERIC_CITYOBJECT gco" +
						" WHERE gco.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.root_id IN" +
						" (SELECT tmp.id from (SELECT ig.relative_brep_id as id" +
						" FROM " + schemaName + ".GENERIC_CITYOBJECT gco, " + schemaName + ".IMPLICIT_GEOMETRY ig" +
						" WHERE gco.id = ?" +
						" AND ig.id = gco.lod" + lod + "_implicit_rep_id" +
						" UNION" +
						" SELECT gco.lod" + lod + "_brep_id as id" +
						" FROM " + schemaName + ".GENERIC_CITYOBJECT gco" +
						" WHERE gco.id = ?) tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (SELECT sg.id" +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.root_id IN" +
						" (SELECT tmp.id from (SELECT ig.relative_brep_id as id" +
						" FROM " + schemaName + ".GENERIC_CITYOBJECT gco, " + schemaName + ".IMPLICIT_GEOMETRY ig" +
						" WHERE gco.id = ?" +
						" AND ig.id = gco.lod" + lod + "_implicit_rep_id" +
						" UNION" +
						" SELECT gco.lod" + lod + "_brep_id as id" +
						" FROM " + schemaName + ".GENERIC_CITYOBJECT gco" +
						" WHERE gco.id = ?) tmp ))";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		private String sqlStatementForCityFurniture(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (CITY_FURNITURE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".CITY_FURNITURE cf" +
						" WHERE cf.id = ?";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.root_id IN" +
						" (SELECT tmp.id from (SELECT ig.relative_brep_id as id" +
						" FROM " + schemaName + ".CITY_FURNITURE cf, " + schemaName + ".IMPLICIT_GEOMETRY ig" +
						" WHERE cf.id = ?" +
						" AND ig.id = cf.lod" + lod + "_implicit_rep_id" +
						" UNION" +
						" SELECT cf.lod" + lod + "_brep_id" +
						" FROM " + schemaName + ".CITY_FURNITURE cf" +
						" WHERE cf.id = ?) tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (SELECT sg.id" +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.root_id IN" +
						" (SELECT tmp.id from (SELECT ig.relative_brep_id as id" +
						" FROM " + schemaName + ".CITY_FURNITURE cf, " + schemaName + ".IMPLICIT_GEOMETRY ig" +
						" WHERE cf.id = ?" +
						" AND ig.id = cf.lod" + lod + "_implicit_rep_id" +
						" UNION" +
						" SELECT cf.lod" + lod + "_brep_id" +
						" FROM " + schemaName + ".CITY_FURNITURE cf" +
						" WHERE cf.id = ?) tmp ))";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		// Bridge
		private String sqlStatementForBridge(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;

			if (ADDRESS_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".ADDRESS_TO_BRIDGE a2b, " + schemaName + ".ADDRESS a" +
						" WHERE a2b.bridge_id = ?" +
						" AND a.id = a2b.address_id";
			}
			else if (ADDRESS_TO_BRIDGE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".ADDRESS_TO_BRIDGE a2b" +
						" WHERE a2b.bridge_id = ?";
			}
			else if (BRIDGE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b" +
						" WHERE b.id = ?";
			}
			else if (BRIDGE_FURNITURE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_FURNITURE bf, " + schemaName + ".BRIDGE_ROOM br" +
						" WHERE b.bridge_root_id = ?" +
						" AND b.id = br.bridge_id" +
						" AND bf.bridge_room_id = br.id";
			}
			else if (BRIDGE_INSTALLATION_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_INSTALLATION bi" +
						" WHERE b.bridge_root_id = ?" +
						" AND bi.bridge_id = b.id";
			}
			else if (BRIDGE_CONSTR_ELEMENT_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_CONSTR_ELEMENT bce" +
						" WHERE b.bridge_root_id = ?" +
						" AND bce.bridge_id = b.id";
			}
			else if (BRIDGE_OPENING_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_THEMATIC_SURFACE bts, " + schemaName + ".BRIDGE_OPEN_TO_THEM_SRF bo2ts, " + schemaName + ".BRIDGE_OPENING bo" +
						" WHERE b.bridge_root_id = ?" +
						" AND bts.bridge_id = b.id" +
						" AND bo2ts.bridge_thematic_surface_id = bts.id" +
						" AND bo.id = bo2ts.bridge_opening_id";
			}
			else if (BRIDGE_OPEN_TO_THEM_SRF_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_THEMATIC_SURFACE bts, " + schemaName + ".BRIDGE_OPEN_TO_THEM_SRF bo2ts" +
						" WHERE b.bridge_root_id = ?" +
						" AND bts.bridge_id = b.id" +
						" AND bo2ts.bridge_thematic_surface_id = bts.id";
			}
			else if (BRIDGE_ROOM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_ROOM br" +
						" WHERE b.bridge_root_id = ?" +
						" AND br.bridge_id = b.id";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE b.bridge_root_id = ?" +
						" AND sg.root_id = b.lod" + lod + "_multi_surface_id" +
						" UNION" +
						" SELECT sg.id" +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE b.bridge_root_id = ?" +
						" AND sg.root_id = b.lod" + lod + "_solid_id";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_THEMATIC_SURFACE bts, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.bridge_root_id = ?" +
							" AND bts.bridge_id = b.id" +
							" AND sg.root_id = bts.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE b.bridge_root_id = ?" +
						" AND sg.root_id = b.lod" + lod + "_multi_surface_id" +
						" UNION" +
						" SELECT sg.id" +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE b.bridge_root_id = ?" +
						" AND sg.root_id = b.lod" + lod + "_solid_id";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_THEMATIC_SURFACE ts, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE b.bridge_root_id = ?" +
							" AND bts.bridge_id = b.id" +
							" AND sg.root_id = bts.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (BRIDGE_THEMATIC_SURFACE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".BRIDGE b, " + schemaName + ".BRIDGE_THEMATIC_SURFACE bts" +
						" WHERE b.bridge_root_id = ?" +
						" AND bts.bridge_id = b.id";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		// TUNNEL
		private String sqlStatementForTunnel(String table,
				List<String> columns,
				String aggregateString,
				String aggregateClosingString,
				int lod,
				String schemaName) throws Exception {
			String sqlStatement = null;


			if (TUNNEL_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TUNNEL t" +
						" WHERE t.id = ?";
			}
			else if (TUNNEL_FURNITURE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_FURNITURE tf, " + schemaName + ".TUNNEL_HOLLOW_SPACE ths" +
						" WHERE t.tunnel_root_id = ?" +
						" AND t.id = ths.tunnel_id" +
						" AND tf.TUNNEL_HOLLOW_SPACE_id = ths.id";
			}
			else if (TUNNEL_INSTALLATION_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_INSTALLATION ti" +
						" WHERE t.tunnel_root_id = ?" +
						" AND ti.tunnel_id = t.id";
			}
			else if (TUNNEL_OPENING_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_THEMATIC_SURFACE tts, " + schemaName + ".TUNNEL_OPEN_TO_THEM_SRF to2ts, " + schemaName + ".TUNNEL_OPENING ot" +
						" WHERE t.tunnel_root_id = ?" +
						" AND tts.tunnel_id = t.id" +
						" AND to2ts.tunnel_thematic_surface_id = tts.id" +
						" AND ot.id = to2ts.tunnel_opening_id";
			}
			else if (TUNNEL_OPEN_TO_THEM_SRF_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_THEMATIC_SURFACE tts, " + schemaName + ".TUNNEL_OPEN_TO_THEM_SRF to2ts" +
						" WHERE t.tunnel_root_id = ?" +
						" AND tts.tunnel_id = t.id" +
						" AND to2ts.tunnel_thematic_surface_id = tts.id";
			}
			else if (TUNNEL_HOLLOW_SPACE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_HOLLOW_SPACE ths" +
						" WHERE t.tunnel_root_id = ?" +
						" AND ths.tunnel_id = t.id";
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE sg.id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE t.tunnel_root_id = ?" +
						" AND sg.root_id = t.lod" + lod + "_multi_surface_id" +
						" UNION" +
						" SELECT sg.id" +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE t.tunnel_root_id = ?" +
						" AND sg.root_id = t.lod" + lod + "_solid_id";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_THEMATIC_SURFACE tts, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE t.tunnel_root_id = ?" +
							" AND tts.tunnel_id = t.id" +
							" AND sg.root_id = tts.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TEXTUREPARAM tp" +
						" WHERE tp.surface_geometry_id IN" +
						" (select tmp.id from (SELECT sg.id" +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE t.tunnel_root_id = ?" +
						" AND sg.root_id = t.lod" + lod + "_multi_surface_id" +
						" UNION" +
						" SELECT sg.id" +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".SURFACE_GEOMETRY sg" +
						" WHERE t.tunnel_root_id = ?" +
						" AND sg.root_id = t.lod" + lod + "_solid_id";
				if (lod > 1) {
					sqlStatement = sqlStatement +
							" UNION" +
							" SELECT sg.id" +
							" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_THEMATIC_SURFACE tts, " + schemaName + ".SURFACE_GEOMETRY sg" +
							" WHERE t.tunnel_root_id = ?" +
							" AND tts.tunnel_id = t.id" +
							" AND sg.root_id = tts.lod" + lod + "_multi_surface_id";
				}
				sqlStatement = sqlStatement + ") tmp )";
			}
			else if (TUNNEL_THEMATIC_SURFACE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".TUNNEL t, " + schemaName + ".TUNNEL_THEMATIC_SURFACE tts" +
						" WHERE t.tunnel_root_id = ?" +
						" AND tts.tunnel_id = t.id";
			}
			else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}

		// TUNNEL
		private String sqlStatementForCityObjectGroup(String table,
		                                     List<String> columns,
		                                     String aggregateString,
		                                     String aggregateClosingString,
		                                     int lod,
		                                     String schemaName) throws Exception {
			String sqlStatement = null;


			if (CITYOBJECTGROUP_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".CITYOBJECTGROUP cog" +
						" WHERE cog.id = ?";
			}
			else if (CITYOBJECT_MEMBER_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".CITYOBJECT_MEMBER com" +
						" WHERE com.citymodel_id = ?";
			} else {
				sqlStatement = sqlStatementForAnyObject(table, columns, aggregateString, aggregateClosingString, lod, schemaName, true);
			}

			return sqlStatement;
		}


		private String sqlStatementForAnyObject(String table,
												List<String> columns,
												String aggregateString,
												String aggregateClosingString,
												int lod,
												String schemaName,
												boolean checkADEHooks) throws Exception {
			String sqlStatement = null;

			if (APPEAR_TO_SURFACE_DATA_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".APPEARANCE a, " + schemaName + ".APPEAR_TO_SURFACE_DATA a2sd" +
						" WHERE a.cityobject_id = ?" +
						" AND a2sd.appearance_id = a.id";
			}
			else if (APPEARANCE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".APPEARANCE a" +
						" WHERE a.cityobject_id = ?";
			}
			else if (CITYMODEL_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".CITYOBJECT_MEMBER com, " + schemaName + ".CITYMODEL cm" +
						" WHERE com.cityobject_id = ?" +
						" AND cm.id = com.citymodel_id";
			}
			else if (CITYOBJECT_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".CITYOBJECT co" +
						" WHERE co.id = ?";
			}
			else if (CITYOBJECT_GENERICATTRIB_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".CITYOBJECT_GENERICATTRIB coga" +
						" WHERE coga.cityobject_id = ?";
			}
			else if (DATABASE_SRS_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".DATABASE_SRS dbsrs"; // unrelated to object
			}
			else if (EXTERNAL_REFERENCE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".EXTERNAL_REFERENCE er" +
						" WHERE er.cityobject_id = ?";
			}
			else if (GENERALIZATION_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".GENERALIZATION g" +
						" WHERE g.cityobject_id = ?";
			}
			else if (GROUP_TO_CITYOBJECT_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".GROUP_TO_CITYOBJECT g2co" +
						" WHERE g2co.cityobjectgroup_id = ?";
			}
			else if (OBJECTCLASS_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".CITYOBJECT co, OBJECTCLASS oc" +
						" WHERE co.id = ?" +
						" AND oc.id = co.objectclass_id";
			}
			else if (SURFACE_DATA_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM " + schemaName + ".APPEARANCE a, " + schemaName + ".APPEAR_TO_SURFACE_DATA a2sd, " + schemaName + ".SURFACE_DATA sd" +
						" WHERE a.cityobject_id = ?" +
						" AND a2sd.appearance_id = a.id" +
						" AND sd.id = a2sd.surface_data_id";
			}
			else if (TEX_IMAGE_TABLE.equalsIgnoreCase(table)) {
				sqlStatement = "SELECT " + aggregateString + getColumnsClause(table, columns) + aggregateClosingString +
						" FROM APPEARANCE a, APPEAR_TO_SURFACE_DATA a2sd, SURFACE_DATA sd, TEX_IMAGE ti" +
						" WHERE a.cityobject_id = ?" +
						" AND a2sd.appearance_id = a.id" +
						" AND sd.id = a2sd.surface_data_id" +
						" AND ti.id = sd.tex_image_id";
			}
			else if (checkADEHooks && !_3DCITYDB_TABLES_AND_COLUMNS.containsKey(table)) {
				String columnsClause = getColumnsClause(table, columns);
				ADEBalloonManager balloonManager = ADEBalloonExtensionManager.getInstance().getBalloonManager(table);
				ADEBalloonHandler adeBalloonHandler = balloonManager.getBalloonHandler(objectClassId);
				if (adeBalloonHandler != null) {
					String aggregateColumnsClause = aggregateString + columnsClause + aggregateClosingString;
					sqlStatement = adeBalloonHandler.getSqlStatement(table, tableShortId, aggregateColumnsClause, lod, schemaName);
					if (sqlStatement != null)
						return sqlStatement;
				}
			}

			if (sqlStatement == null)
				throw new Exception("Unsupported table \"" + table + "\" for CityGML type " +
						ObjectRegistry.getInstance().getSchemaMapping().getFeatureType(objectClassId).getPath());

			return sqlStatement;
		}

		private void setTableShortIdAndOrderByColumnAllowed(String tablename, List<String> columns) throws Exception {
			if (ADDRESS_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "a";
				orderByColumnAllowed = (!columns.get(0).equals("MULTI_POINT") && !columns.get(0).equals("XAL_SOURCE"));
			}
			else if (ADDRESS_TO_BRIDGE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "a2b";
			}
			else if (ADDRESS_TO_BUILDING_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "a2b";
			}
			else if (APPEAR_TO_SURFACE_DATA_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "a2sd";
			}
			else if (APPEARANCE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "a";
			}
			/* Bridge */
			else if (BRIDGE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "b";
				orderByColumnAllowed = (!columns.get(0).equals("LOD1_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD3_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD4_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_MULTI_CURVE") &&
						!columns.get(0).equals("LOD3_MULTI_CURVE") &&
						!columns.get(0).equals("LOD4_MULTI_CURVE"));
			}
			else if (BRIDGE_CONSTR_ELEMENT_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bce";
				orderByColumnAllowed = (!columns.get(0).equals("LOD1_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD3_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD4_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD1_OTHER_GEOM") &&
						!columns.get(0).equals("LOD2_OTHER_GEOM") &&
						!columns.get(0).equals("LOD3_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD1_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD2_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (BRIDGE_FURNITURE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bf";
				orderByColumnAllowed = (!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (BRIDGE_INSTALLATION_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bi";
				orderByColumnAllowed = (!columns.get(0).equals("LOD2_OTHER_GEOM") &&
						!columns.get(0).equals("LOD3_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD2_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (BRIDGE_OPEN_TO_THEM_SRF_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bo2ts";
			}
			else if (BRIDGE_OPENING_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bo";
				orderByColumnAllowed = (!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (BRIDGE_ROOM_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "br";
			}
			else if (BRIDGE_THEMATIC_SURFACE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bts";
			}
			/* Building */
			else if (BUILDING_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "b";
				orderByColumnAllowed = (!columns.get(0).equals("LOD1_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD3_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD4_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_MULTI_CURVE") &&
						!columns.get(0).equals("LOD3_MULTI_CURVE") &&
						!columns.get(0).equals("LOD4_MULTI_CURVE"));
			}
			else if (BUILDING_FURNITURE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bf";
				orderByColumnAllowed = (!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (BUILDING_INSTALLATION_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "bi";
				orderByColumnAllowed = (!columns.get(0).equals("LOD2_OTHER_GEOM") &&
						!columns.get(0).equals("LOD3_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD2_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			/**/
			else if (CITY_FURNITURE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "cf";
				orderByColumnAllowed = (!columns.get(0).equals("LOD1_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD3_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD4_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD1_OTHER_GEOM") &&
						!columns.get(0).equals("LOD2_OTHER_GEOM") &&
						!columns.get(0).equals("LOD3_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD1_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD2_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (CITYMODEL_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "cm";
				orderByColumnAllowed = (!columns.get(0).equals("ENVELOPE"));
			}
			else if (CITYOBJECT_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "co";
				orderByColumnAllowed = (!columns.get(0).equals("ENVELOPE") && !columns.get(0).equals("XML_SOURCE"));
			}
			else if (CITYOBJECT_GENERICATTRIB_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "coga";
				orderByColumnAllowed = (!columns.get(0).equals("GEOMVAL") && !columns.get(0).equals("BLOBVAL"));
			}
			else if (CITYOBJECTGROUP_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "cog";
				orderByColumnAllowed = (!columns.get(0).equals("OTHER_GEOM"));
			}
			else if (CITYOBJECT_MEMBER_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "com";
			}
			else if (DATABASE_SRS_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "dbsrs";
			}
			else if (EXTERNAL_REFERENCE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "er";
			}
			else if (GENERALIZATION_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "g";
			}
			else if (GENERIC_CITYOBJECT_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "gco";
				orderByColumnAllowed = (!columns.get(0).equals("LOD0_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD1_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD3_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD4_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD0_OTHER_GEOM") &&
						!columns.get(0).equals("LOD1_OTHER_GEOM") &&
						!columns.get(0).equals("LOD2_OTHER_GEOM") &&
						!columns.get(0).equals("LOD3_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD0_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD1_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD2_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (GROUP_TO_CITYOBJECT_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "g2co";
			}
			else if (IMPLICIT_GEOMETRY_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "ig";
				orderByColumnAllowed = (!columns.get(0).equals("LIBRARY_OBJECT") && !columns.get(0).equals("RELATIVE_OTHER_GEOM"));
			}
			else if (LAND_USE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "lu";
			}
			else if (OBJECTCLASS_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "oc";
			}
			else if (OPENING_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "o";
				orderByColumnAllowed = (!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (OPENING_TO_THEM_SURFACE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "o2ts";
			}
			else if (PLANT_COVER_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "pc";
			}
			/*			else if (RASTER_RELIEF_GEORASTER_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "rrg";
				orderByColumnAllowed = (!columns.get(0).equals("RASTERPROPERTY"));
			}*/
			else if (RELIEF_COMPONENT_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "rc";
				orderByColumnAllowed = (!columns.get(0).equals("EXTENT"));
			}
			else if (RELIEF_FEATURE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "rf";
			}
			else if (ROOM_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "r";
			}
			else if (SOLITARY_VEGETAT_OBJECT_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "svo";
				orderByColumnAllowed = (!columns.get(0).equals("LOD1_OTHER_GEOM") &&
						!columns.get(0).equals("LOD2_OTHER_GEOM") &&
						!columns.get(0).equals("LOD3_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD1_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD2_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (SURFACE_DATA_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "sd";
				orderByColumnAllowed = (!columns.get(0).equals("GT_REFERENCE_POINT"));
			}
			else if (SURFACE_GEOMETRY_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "sg";
				orderByColumnAllowed = (!columns.get(0).equals("GEOMETRY") &&
						!columns.get(0).equals("SOLID_GEOMETRY") &&
						!columns.get(0).equals("IMPLICIT_GEOMETRY"));
			}
			else if (TEX_IMAGE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "ti";
				orderByColumnAllowed = (!columns.get(0).equals("TEX_IMAGE_DATA"));
			}
			else if (TEXTUREPARAM_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "tp";
				orderByColumnAllowed = (!columns.get(0).equals("TEXTURE_COORDINATES"));
			}
			else if (THEMATIC_SURFACE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "ts";
			}
			/*
			else if (TIN_RELIEF_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "tr";
				orderByColumnAllowed = (!columns.get(0).equals("STOP_LINES") &&
										!columns.get(0).equals("BREAK_LINES") &&
										!columns.get(0).equals("CONTROL_POINTS"));
			}
			 */
			else if (TRAFFIC_AREA_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "ta";
			}
			else if (TRANSPORTATION_COMPLEX_TABLE.equalsIgnoreCase(tablename)) {
				orderByColumnAllowed = !columns.get(0).equals("LOD0_NETWORK");
				tableShortId = "tc";
			}

			/* TUNNEL */
			else if (TUNNEL_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "t";
				orderByColumnAllowed = (!columns.get(0).equals("LOD1_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD3_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD4_TERRAIN_INTERSECTION") &&
						!columns.get(0).equals("LOD2_MULTI_CURVE") &&
						!columns.get(0).equals("LOD3_MULTI_CURVE") &&
						!columns.get(0).equals("LOD4_MULTI_CURVE"));
			}
			else if (TUNNEL_FURNITURE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "tf";
				orderByColumnAllowed = (!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (TUNNEL_HOLLOW_SPACE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "ths";
			}
			else if (TUNNEL_INSTALLATION_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "ti";
				orderByColumnAllowed = (!columns.get(0).equals("LOD2_OTHER_GEOM") &&
						!columns.get(0).equals("LOD3_OTHER_GEOM") &&
						!columns.get(0).equals("LOD4_OTHER_GEOM") &&
						!columns.get(0).equals("LOD2_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (TUNNEL_OPEN_TO_THEM_SRF_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "to2ts";
			}
			else if (TUNNEL_OPENING_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "ot";
				orderByColumnAllowed = (!columns.get(0).equals("LOD3_IMPLICIT_REF_POINT") &&
						!columns.get(0).equals("LOD4_IMPLICIT_REF_POINT"));
			}
			else if (TUNNEL_THEMATIC_SURFACE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "tts";
			}
			/* */
			else if (WATERBODY_TO_WATERBOUNDARY_SURFACE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "wb2wbs";
			}
			else if (WATERBODY_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "wb";
				orderByColumnAllowed = (!columns.get(0).equals("LOD0_MULTI_CURVE") &&
						!columns.get(0).equals("LOD1_MULTI_CURVE"));
			}
			else if (WATERBOUNDARY_SURFACE_TABLE.equalsIgnoreCase(tablename)) {
				tableShortId = "wbs";
			}
			else {
				orderByColumnAllowed = true;
				tableShortId = "ade_";
				if (tableShortId == null)
					throw new Exception("Unsupported table \"" + tablename + "\" in statement \"" + rawStatement + "\"");
			}
		}

		private String getColumnsClause(String tableName, List<String> statementColumns) throws Exception {
			Set<String> tableColumns = _3DCITYDB_TABLES_AND_COLUMNS.get(tableName);
			String columnsClause = "";
			if (tableColumns == null) {
				ADEBalloonManager balloonManager = ADEBalloonExtensionManager.getInstance().getBalloonManager(tableName);
				tableColumns = balloonManager.getTablesAndColumns().get(tableName);
				if (tableColumns == null)
					throw new ADEBalloonException("Unsupported columns in the ADE table '" + tableName + "' .");
			}

			if (!tableColumns.containsAll(statementColumns)) {
				for (String column: statementColumns) {
					if (!tableColumns.contains(column)) {
						throw new Exception("Unsupported column \"" + column + "\" in statement \"" + rawStatement + "\"");
					}
				}
			}
			else {
				ListIterator<String> statementColumnIterator = statementColumns.listIterator();
				while (statementColumnIterator.hasNext()) {
					setTableShortIdAndOrderByColumnAllowed(tableName, statementColumns);
					columnsClause = columnsClause + tableShortId + "." + statementColumnIterator.next();
					if (statementColumnIterator.hasNext()) {
						columnsClause = columnsClause + ", ";
					}
				}
			}
			return columnsClause;
		}

		private String checkForSpecialKeywords(String keyword, String schemaName) throws Exception {
			String query = null;
			if (CENTROID_WGS84.equalsIgnoreCase(keyword)) {
				query = getCentroidInWGS84ById(schemaName);
			}
			else if (CENTROID_WGS84_LAT.equalsIgnoreCase(keyword)) {
				query = getCentroidLatInWGS84ById(schemaName);
			}
			else if (CENTROID_WGS84_LON.equalsIgnoreCase(keyword)) {
				query = getCentroidLonInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_LAT_MIN.equalsIgnoreCase(keyword)) {
				query = getEnvelopeLatMinInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_LAT_MAX.equalsIgnoreCase(keyword)) {
				query = getEnvelopeLatMaxInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_LON_MIN.equalsIgnoreCase(keyword)) {
				query = getEnvelopeLonMinInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_LON_MAX.equalsIgnoreCase(keyword)) {
				query = getEnvelopeLonMaxInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_HEIGHT_MIN.equalsIgnoreCase(keyword)) {
				query = getEnvelopeHeightMinInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_HEIGHT_MAX.equalsIgnoreCase(keyword)) {
				query = getEnvelopeHeightMaxInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_LAT_LON.equalsIgnoreCase(keyword)) {
				query = getEnvelopeLatMinInWGS84ById(schemaName) + " UNION ALL " +
						getEnvelopeLonMinInWGS84ById(schemaName) + " UNION ALL " +
						getEnvelopeLatMaxInWGS84ById(schemaName) + " UNION ALL " +
						getEnvelopeLonMaxInWGS84ById(schemaName);
			}
			else if (BBOX_WGS84_LON_LAT.equalsIgnoreCase(keyword)) {
				query = getEnvelopeLonMinInWGS84ById(schemaName) + " UNION ALL " +
						getEnvelopeLatMinInWGS84ById(schemaName) + " UNION ALL " +
						getEnvelopeLonMaxInWGS84ById(schemaName) + " UNION ALL " +
						getEnvelopeLatMaxInWGS84ById(schemaName);
			}
			else {
				throw new Exception("Unsupported keyword \"" + keyword + "\" in statement \"" + rawStatement + "\"");
			}

			return query;
		}

	}

	private String getCentroidInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT SDO_CS.TRANSFORM(SDO_GEOM.SDO_CENTROID(co.envelope, 0.001), ")
					.append(srid).append(") ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_Transform(ST_Centroid(co.envelope), ")
					.append(srid).append(") ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}		
	}

	private String getCentroidLatInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT v.Y FROM TABLE(")
					.append("SELECT SDO_UTIL.GETVERTICES(SDO_CS.TRANSFORM(SDO_GEOM.SDO_CENTROID(co.envelope, 0.001), ").append(srid).append(")) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?) v").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_Y(ST_Transform(ST_Centroid(co.envelope), ")
					.append(srid).append(")) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private String getCentroidLonInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT v.X FROM TABLE(")
					.append("SELECT SDO_UTIL.GETVERTICES(SDO_CS.TRANSFORM(SDO_GEOM.SDO_CENTROID(co.envelope, 0.001), ").append(srid).append(")) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?) v").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_X(ST_Transform(ST_Centroid(co.envelope), ")
					.append(srid).append(")) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private String getEnvelopeLatMinInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT SDO_GEOM.SDO_MIN_MBR_ORDINATE(SDO_CS.TRANSFORM(co.envelope, ").append(srid).append("), 2) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_YMin(Box3D(ST_Transform(co.envelope, ").append(srid).append("))) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private String getEnvelopeLatMaxInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT SDO_GEOM.SDO_MAX_MBR_ORDINATE(SDO_CS.TRANSFORM(co.envelope, ").append(srid).append("), 2) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_YMax(Box3D(ST_Transform(co.envelope, ").append(srid).append("))) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private String getEnvelopeLonMinInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT SDO_GEOM.SDO_MIN_MBR_ORDINATE(SDO_CS.TRANSFORM(co.envelope, ").append(srid).append("), 1) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_XMin(Box3D(ST_Transform(co.envelope, ").append(srid).append("))) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private String getEnvelopeLonMaxInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT SDO_GEOM.SDO_MAX_MBR_ORDINATE(SDO_CS.TRANSFORM(co.envelope, ").append(srid).append("), 1) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_XMax(Box3D(ST_Transform(co.envelope, ").append(srid).append("))) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private String getEnvelopeHeightMinInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT SDO_GEOM.SDO_MIN_MBR_ORDINATE(SDO_CS.TRANSFORM(co.envelope, ").append(srid).append("), 3) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_ZMin(Box3D(ST_Transform(co.envelope, ").append(srid).append("))) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private String getEnvelopeHeightMaxInWGS84ById(String schemaName) {
		int srid = databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D() ? databaseAdapter.getUtil().getWGS843D().getSrid() : 4326;
		switch (databaseAdapter.getDatabaseType()) {
		case ORACLE:
			return new StringBuilder("SELECT SDO_GEOM.SDO_MAX_MBR_ORDINATE(SDO_CS.TRANSFORM(co.envelope, ").append(srid).append("), 3) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		case POSTGIS:
			return new StringBuilder("SELECT ST_ZMax(Box3D(ST_Transform(co.envelope, ").append(srid).append("))) ")
					.append("FROM ").append(schemaName).append(".CITYOBJECT co ")
					.append("WHERE co.id = ?").toString();
		default:
			return null;
		}
	}

	private int getParameterCount(String query) {
		// due to a bug in the Oracle JDBC driver, we cannot use getParameterMetaData().getParameterCount() to
		// get the number of parameters in a prepared statement having a long query string...
		int parameters = 0;
		for (int i = 0; i < query.length(); i++)
			if (query.charAt(i) == '?')
				parameters++;

		return parameters;
	}
}
