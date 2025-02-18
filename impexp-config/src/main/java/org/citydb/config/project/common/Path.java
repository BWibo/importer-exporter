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
package org.citydb.config.project.common;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "PathType", propOrder = {
        "mode",
        "standardPath",
        "lastUsedPath"
})
public class Path {
    @XmlElement(required = true)
    private PathMode mode = PathMode.LASTUSED;
    private String standardPath = "";
    private String lastUsedPath = "";

    public Path() {
    }

    public boolean isSetLastUsedMode() {
        return mode == PathMode.LASTUSED;
    }

    public boolean isSetStandardMode() {
        return mode == PathMode.STANDARD;
    }

    public PathMode getPathMode() {
        return mode;
    }

    public void setPathMode(PathMode mode) {
        this.mode = mode;
    }

    public String getStandardPath() {
        return standardPath;
    }

    public void setStandardPath(String standardPath) {
        this.standardPath = standardPath;
    }

    public String getLastUsedPath() {
        return lastUsedPath;
    }

    public void setLastUsedPath(String lastUsedPath) {
        this.lastUsedPath = lastUsedPath;
    }

}
