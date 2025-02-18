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
package org.citydb.core.database.adapter.h2;

import org.citydb.config.geometry.GeometryObject;
import org.citydb.core.database.adapter.AbstractDatabaseAdapter;
import org.citydb.core.database.adapter.AbstractSQLAdapter;
import org.citydb.core.database.adapter.BlobExportAdapter;
import org.citydb.core.database.adapter.BlobImportAdapter;
import org.citydb.core.database.adapter.BlobType;
import org.citydb.core.query.filter.selection.operator.spatial.SpatialOperatorName;
import org.citydb.sqlbuilder.schema.Column;
import org.citydb.sqlbuilder.select.PredicateToken;
import org.citydb.sqlbuilder.select.projection.Function;

import java.sql.Connection;
import java.sql.SQLException;

public class SQLAdapter extends AbstractSQLAdapter {
	
	protected SQLAdapter(AbstractDatabaseAdapter databaseAdapter) {
		super(databaseAdapter);
	}

	@Override
	public String getInteger() {
		return "BIGINT";
	}

	@Override
	public String getSmallInt() {
		return "SMALLINT";
	}

	@Override
	public String getBigInt() {
		return "BIGINT";
	}

	@Override
	public String getNumeric() {
		return "BIGINT";
	}

	@Override
	public String getNumeric(int precision) {
		if (precision <= 3)
			return "SMALLINT";

		return "BIGINT";
	}

	@Override
	public String getNumeric(int precision, int scale) {
		if (precision == 1 && scale == 0)
			return "TINYINT";

		return "DECIMAL";
	}

	@Override
	public String getReal() {
		return "REAL";
	}

	@Override
	public String getDoublePrecision() {
		return "DOUBLE";
	}

	@Override
	public String getCharacter(int nrOfChars) {
		return "VARCHAR(" + nrOfChars + ")";
	}

	@Override
	public String getCharacterVarying(int nrOfChars) {
		return "VARCHAR(" + nrOfChars + ")";
	}

	@Override
	public String getPolygon2D() {
		return "GEOMETRY";
	}

	@Override
	public String getCreateUnloggedTable(String tableName, String columns) {
		return "create table " + tableName + " " + columns;
	}

	@Override
	public String getCreateUnloggedTableAsSelect(String tableName, String select) {
		return "create table " + tableName + " as " + select;
	}

	@Override
	public String getUnloggedIndexProperty() {
		return "";
	}

	@Override
	public boolean requiresPseudoTableInSelect() {
		return false;
	}

	@Override
	public String getPseudoTableName() {
		return "";
	}
	
	@Override
	public int getMaximumNumberOfItemsForInOperator() {
		// not required for cache tables
		return 0;
	}
	
	@Override
	public boolean spatialPredicateRequiresNoIndexHint() {
		return false;
	}

	@Override
	public boolean supportsFetchFirstClause() {
		return true;
	}

	@Override
	public String getHierarchicalGeometryQuery() {
		// not required for cache tables
		return "";
	}

	@Override
	public String getNextSequenceValue(String sequence) {
		// not required for cache tables
		return "";
	}

	@Override
	public String getCurrentSequenceValue(String sequence) {
		// not required for cache tables
		return "";
	}

	@Override
	public String getNextSequenceValuesQuery(String sequence) {
		// not required for cache tables
		return "";
	}

	@Override
	public BlobImportAdapter getBlobImportAdapter(Connection connection, BlobType type) throws SQLException {
		// not required for cache tables
		return null;
	}

	@Override
	public BlobExportAdapter getBlobExportAdapter(Connection connection, BlobType type) {
		// not required for cache tables
		return null;
	}
	
	@Override
	public PredicateToken getBinarySpatialPredicate(SpatialOperatorName operator, Column targetColumn, GeometryObject geometry, boolean negate) {
		// not required for cache tables
		return null;
	}

	@Override
	public PredicateToken getDistancePredicate(SpatialOperatorName operator, Column targetColumn, GeometryObject geometry, double distance, boolean negate) {
		// not required for cache tables
		return null;
	}

	@Override
	public Function getAggregateExtentFunction(Column envelope) {
		// not required for cache tables
		return null;
	}

}
