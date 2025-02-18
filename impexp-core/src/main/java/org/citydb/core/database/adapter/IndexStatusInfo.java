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
package org.citydb.core.database.adapter;

import org.citydb.util.log.Logger;

import java.util.ArrayList;
import java.util.List;

public class IndexStatusInfo {
	private final Logger LOG = Logger.getInstance();
	private List<IndexInfoObject> indexes;
	private IndexType type;

	public enum IndexType {
		SPATIAL,
		NORMAL
	}

	public enum IndexStatus {
		VALID,
		DROPPED,
		INVALID,
		ERROR
	}
	
	private IndexStatusInfo() {
		// just to thwart instantiation
	}
	
	public static synchronized IndexStatusInfo createFromDatabaseQuery(String[] query, IndexType type) {
		IndexStatusInfo info = null;

		if (query != null) {
			info = new IndexStatusInfo();
			info.indexes = new ArrayList<IndexInfoObject>(query.length);
			info.type = type;
			
			for (String indexInfo : query) {
				String[] parts = indexInfo.split(":");

				if (parts.length > 4) {
					IndexInfoObject obj = new IndexInfoObject();

					switch (parts[0]) {
						case "VALID":
							obj.status = IndexStatus.VALID;
							break;
						case "DROPPED":
							obj.status = IndexStatus.DROPPED;
							break;
						case "INVALID":
							obj.status = IndexStatus.INVALID;
							break;
						default:
							obj.status = IndexStatus.ERROR;
							break;
					}

					obj.name = parts[1].toUpperCase();
					obj.schema = parts[2];
					obj.table = parts[3].toUpperCase();
					obj.column = parts[4].toUpperCase();

					if (parts.length > 5 && !parts[5].equals("0"))
						obj.errorMessage = parts[5];
					else
						obj.errorMessage = "";
					
					info.indexes.add(obj);
				}
			}
		}

		return info;
	}

	public List<IndexInfoObject> getIndexObjects() {
		return indexes;
	}
	
	public List<IndexInfoObject> getIndexObjects(IndexStatus status) {
		List<IndexInfoObject> tmp = new ArrayList<IndexInfoObject>();		
		for (IndexInfoObject obj : indexes)
			if (obj.status == status)
				tmp.add(obj);
		
		return tmp;
	}
	
	public int getNumberOfIndexes() {
		return indexes.size();
	}
	
	public int getNumberOfValidIndexes() {
		return getIndexObjects(IndexStatus.VALID).size();
	}
	
	public int getNumberOfInvalidIndexes() {
		return getIndexObjects(IndexStatus.INVALID).size();
	}
	
	public int getNumberOfDroppedIndexes() {
		return getIndexObjects(IndexStatus.DROPPED).size();
	}
	
	public int getNumberOfFaultyIndexes() {
		return getIndexObjects(IndexStatus.ERROR).size();
	}
	
	public void printStatusToConsole() {
		int on = getNumberOfValidIndexes();
		int all = getIndexObjects().size();
		
		StringBuilder msg = new StringBuilder();
		
		msg.append(type == IndexType.SPATIAL ? "Spatial" : "Normal").append(" indexes are ");
		
		if (on == 0)
			msg.append("disabled.");
		else if (on == all)
			msg.append("enabled.");
		else
			msg.append("partly enabled (").append(on).append(" / ").append(all).append(").");
		
		LOG.info(msg.toString());
	}
	
	public static final class IndexInfoObject {
		private String name;
		private String schema;
		private String table;
		private String column;
		private IndexStatus status;
		private String errorMessage;

		public String getName() {
			return name;
		}

		public String getSchema() {
			return schema;
		}
		
		public String getTable() {
			return table;
		}
		
		public String getColumn() {
			return column;
		}

		public IndexStatus getStatus() {
			return status;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
		
		public boolean hasErrorMessage() {
			return errorMessage != null && errorMessage.length() > 0;
		}
		
		public String toString() {
			return name + " on " + schema + "." + table + "(" + column + ")";
		}
	}

}
