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

package org.citydb.core.operation.exporter;

public class CityGMLExportException extends Exception {
	private final ErrorCode errorCode;

	public enum ErrorCode {
		SPATIAL_INDEXES_NOT_ACTIVATED,
		UNKNOWN
	}

	public CityGMLExportException(ErrorCode errorCode) {
		super();
		this.errorCode = errorCode;
	}

	public CityGMLExportException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public CityGMLExportException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public CityGMLExportException(ErrorCode errorCode, Throwable cause) {
		super(cause);
		this.errorCode = errorCode;
	}

	public CityGMLExportException() {
		this(ErrorCode.UNKNOWN);
	}

	public CityGMLExportException(String message) {
		this(ErrorCode.UNKNOWN, message);
	}

	public CityGMLExportException(String message, Throwable cause) {
		this(ErrorCode.UNKNOWN, message, cause);
	}

	public CityGMLExportException(Throwable cause) {
		this(ErrorCode.UNKNOWN, cause);
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
