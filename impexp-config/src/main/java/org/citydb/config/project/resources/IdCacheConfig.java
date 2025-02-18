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
package org.citydb.config.project.resources;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "IdCacheConfigType", propOrder = {
        "cacheSize",
        "pageFactor",
        "partitions"
})
public class IdCacheConfig {
    @XmlSchemaType(name = "positiveInteger")
    @XmlElement(required = true, defaultValue = "200000")
    private Integer cacheSize = 200000;
    @XmlElement(required = true, defaultValue = "0.85")
    private Float pageFactor = 0.85f;
    @XmlElement(required = true, defaultValue = "10")
    private Integer partitions = 10;

    public IdCacheConfig() {
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        if (cacheSize != null && cacheSize > 0)
            this.cacheSize = cacheSize;
    }

    public Float getPageFactor() {
        return pageFactor;
    }

    public void setPageFactor(Float pageFactor) {
        if (pageFactor != null && pageFactor > 0 && pageFactor <= 1)
            this.pageFactor = pageFactor;
    }

    public Integer getPartitions() {
        return partitions;
    }

    public void setPartitions(Integer concurrentTempTables) {
        if (concurrentTempTables != null &&
                concurrentTempTables > 0 &&
                concurrentTempTables <= 100)
            this.partitions = concurrentTempTables;
    }

}
