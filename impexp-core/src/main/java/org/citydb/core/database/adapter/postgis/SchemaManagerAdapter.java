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
package org.citydb.core.database.adapter.postgis;

import org.citydb.core.database.adapter.AbstractDatabaseAdapter;
import org.citydb.core.database.adapter.AbstractSchemaManagerAdapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SchemaManagerAdapter extends AbstractSchemaManagerAdapter {
	private final String defaultSchema = "citydb";

	protected SchemaManagerAdapter(AbstractDatabaseAdapter databaseAdapter) {
		super(databaseAdapter);
	}

	@Override
	public String getDefaultSchema() {
		return defaultSchema;
	}

	@Override
	public boolean equalsDefaultSchema(String schema) {
		return schema == null || schema.trim().isEmpty() || defaultSchema.equals(schema);
	}

	@Override
	public boolean existsSchema(Connection connection, String schema) {
		if (schema == null)
			throw new IllegalArgumentException("Schema name may not be null.");

		if (schema.trim().isEmpty())
			schema = defaultSchema;

		try (PreparedStatement stmt = connection.prepareStatement("select exists(select schema_name from information_schema.schemata where schema_name = ?)")) {
			stmt.setString(1, schema);
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next() && rs.getBoolean(1);
			}
		} catch (SQLException e) {
			return false;
		}
	}

	@Override
	public List<String> fetchSchemasFromDatabase(Connection connection) throws SQLException {
		try (Statement stmt = connection.createStatement();
				ResultSet rs = stmt.executeQuery("select s.schema_name from information_schema.schemata s " +
						"where exists (select 1 from information_schema.tables t " +
						"where t.table_name='database_srs' and t.table_schema=s.schema_name) " +
						"order by s.schema_name")) {
			List<String> schemas = new ArrayList<>();
			while (rs.next())
				schemas.add(rs.getString(1));

			return schemas;
		}
	}

	@Override
	public String formatSchema(String schema) {
		return schema;
	}

}