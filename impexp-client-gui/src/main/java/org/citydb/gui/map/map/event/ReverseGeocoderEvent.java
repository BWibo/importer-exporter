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
package org.citydb.gui.map.map.event;

import org.citydb.util.event.Event;
import org.citydb.gui.map.geocoder.Location;
import org.citydb.gui.map.geocoder.service.GeocodingServiceException;

public class ReverseGeocoderEvent extends Event {
	private final ReverseGeocoderStatus status;
	private final Location location;
	private final GeocodingServiceException exception;
	
	public enum ReverseGeocoderStatus {
		SEARCHING,
		NO_RESULT,
		RESULT,
		ERROR
	}	
	
	public ReverseGeocoderEvent(ReverseGeocoderStatus status, Object source) {
		super(MapEvents.REVERSE_GEOCODER, GLOBAL_CHANNEL, source);
		this.status = status == ReverseGeocoderStatus.SEARCHING || status == ReverseGeocoderStatus.NO_RESULT ?
				status : ReverseGeocoderStatus.NO_RESULT;

		location = null;
		exception = null;
	}
	
	public ReverseGeocoderEvent(Location location, Object source) {
		super(MapEvents.REVERSE_GEOCODER, GLOBAL_CHANNEL, source);
		this.status = ReverseGeocoderStatus.RESULT;
		this.location = location;
		exception = null;
	}
	
	public ReverseGeocoderEvent(GeocodingServiceException exception, Object source) {
		super(MapEvents.REVERSE_GEOCODER, GLOBAL_CHANNEL, source);
		this.status = ReverseGeocoderStatus.ERROR;
		location = null;
		this.exception = exception;
	}

	public ReverseGeocoderStatus getStatus() {
		return status;
	}

	public Location getLocation() {
		return location;
	}

	public GeocodingServiceException getException() {
		return exception;
	}
	
}
