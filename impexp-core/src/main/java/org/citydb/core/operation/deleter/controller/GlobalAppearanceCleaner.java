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

package org.citydb.core.operation.deleter.controller;

import org.citydb.core.database.adapter.AbstractDatabaseAdapter;
import org.citydb.core.database.connection.ConnectionManager;
import org.citydb.core.operation.deleter.DeleteException;

import java.sql.Connection;
import java.sql.SQLException;

public class GlobalAppearanceCleaner {
	private final ConnectionManager connectionManager;
	private final AbstractDatabaseAdapter databaseAdapter;
	private volatile boolean isDeleting = false;

	public GlobalAppearanceCleaner(ConnectionManager connectionManager, AbstractDatabaseAdapter databaseAdapter) {
		this.connectionManager = connectionManager;
		this.databaseAdapter = databaseAdapter;
	}

	public int doCleanup() throws DeleteException {
		try {
			isDeleting = true;
			Connection connection = connectionManager.getConnection();
			String schema = databaseAdapter.getConnectionDetails().getSchema();
			return databaseAdapter.getUtil().cleanupGlobalAppearances(schema, connection);
		} catch (SQLException e) {
			throw new DeleteException("Failed to delete global appearances.", e);
		} finally {
			isDeleting = false;
		}
	}

	public void interrupt() {
		if (isDeleting) {
			databaseAdapter.getUtil().interruptDatabaseOperation();
		}
	}
}
