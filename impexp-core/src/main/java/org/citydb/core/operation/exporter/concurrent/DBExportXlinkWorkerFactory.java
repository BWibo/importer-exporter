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
package org.citydb.core.operation.exporter.concurrent;

import org.citydb.util.concurrent.Worker;
import org.citydb.util.concurrent.WorkerFactory;
import org.citydb.config.Config;
import org.citydb.core.database.adapter.AbstractDatabaseAdapter;
import org.citydb.core.database.connection.DatabaseConnectionPool;
import org.citydb.util.event.EventDispatcher;
import org.citydb.util.log.Logger;
import org.citydb.core.operation.common.xlink.DBXlink;
import org.citydb.core.operation.exporter.util.InternalConfig;

import java.sql.Connection;
import java.sql.SQLException;

public class DBExportXlinkWorkerFactory implements WorkerFactory<DBXlink> {
	private final Logger log = Logger.getInstance();

	private final InternalConfig internalConfig;
	private final Config config;
	private final EventDispatcher eventDispatcher;

	public DBExportXlinkWorkerFactory(InternalConfig internalConfig, Config config, EventDispatcher eventDispatcher) {
		this.internalConfig = internalConfig;
		this.config = config;
		this.eventDispatcher = eventDispatcher;
	}

	@Override
	public Worker<DBXlink> createWorker() {
		DBExportXlinkWorker dbWorker = null;

		try {
			AbstractDatabaseAdapter databaseAdapter = DatabaseConnectionPool.getInstance().getActiveDatabaseAdapter();
			Connection connection = DatabaseConnectionPool.getInstance().getConnection();
			connection.setAutoCommit(false);

			dbWorker = new DBExportXlinkWorker(connection, databaseAdapter, internalConfig, config, eventDispatcher);
		} catch (SQLException e) {
			log.error("Failed to create XLink export worker.", e);
		}

		return dbWorker;
	}
}
