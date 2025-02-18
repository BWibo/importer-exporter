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

package org.citydb.config.gui.exporter;

import org.citydb.config.gui.components.SQLExportFilterComponent;

import javax.xml.bind.annotation.XmlType;

@XmlType(name = "ExportGuiType", propOrder = {
        "collapseFeatureVersionFilter",
        "collapseAttributeFilter",
        "collapseSQLFilter",
        "collapseLodFilter",
        "collapseCounterFilter",
        "collapseBoundingBoxFilter",
        "collapseFeatureTypeFilter",
        "showAffineTransformationWarning",
        "showCityJSONTilingWarning",
        "sqlFilter"
})
public class ExportGuiConfig {
    private boolean collapseFeatureVersionFilter = false;
    private boolean collapseAttributeFilter = true;
    private boolean collapseSQLFilter = true;
    private boolean collapseLodFilter = true;
    private boolean collapseCounterFilter = true;
    private boolean collapseBoundingBoxFilter = true;
    private boolean collapseFeatureTypeFilter = true;
    private boolean showAffineTransformationWarning = true;
    private boolean showCityJSONTilingWarning = true;
    private SQLExportFilterComponent sqlFilter;

    public ExportGuiConfig() {
        sqlFilter = new SQLExportFilterComponent();
    }

    public boolean isCollapseFeatureVersionFilter() {
        return collapseFeatureVersionFilter;
    }

    public void setCollapseFeatureVersionFilter(boolean collapseFeatureVersionFilter) {
        this.collapseFeatureVersionFilter = collapseFeatureVersionFilter;
    }

    public boolean isCollapseAttributeFilter() {
        return collapseAttributeFilter;
    }

    public void setCollapseAttributeFilter(boolean collapseAttributeFilter) {
        this.collapseAttributeFilter = collapseAttributeFilter;
    }

    public boolean isCollapseSQLFilter() {
        return collapseSQLFilter;
    }

    public void setCollapseSQLFilter(boolean collapseSQLFilter) {
        this.collapseSQLFilter = collapseSQLFilter;
    }

    public boolean isCollapseLodFilter() {
        return collapseLodFilter;
    }

    public void setCollapseLodFilter(boolean collapseLodFilter) {
        this.collapseLodFilter = collapseLodFilter;
    }

    public boolean isCollapseCounterFilter() {
        return collapseCounterFilter;
    }

    public void setCollapseCounterFilter(boolean collapseCounterFilter) {
        this.collapseCounterFilter = collapseCounterFilter;
    }

    public boolean isCollapseBoundingBoxFilter() {
        return collapseBoundingBoxFilter;
    }

    public void setCollapseBoundingBoxFilter(boolean collapseBoundingBoxFilter) {
        this.collapseBoundingBoxFilter = collapseBoundingBoxFilter;
    }

    public boolean isCollapseFeatureTypeFilter() {
        return collapseFeatureTypeFilter;
    }

    public void setCollapseFeatureTypeFilter(boolean collapseFeatureTypeFilter) {
        this.collapseFeatureTypeFilter = collapseFeatureTypeFilter;
    }

    public boolean isShowAffineTransformationWarning() {
        return showAffineTransformationWarning;
    }

    public void setShowAffineTransformationWarning(boolean showAffineTransformationWarning) {
        this.showAffineTransformationWarning = showAffineTransformationWarning;
    }

    public boolean isShowCityJSONTilingWarning() {
        return showCityJSONTilingWarning;
    }

    public void setShowCityJSONTilingWarning(boolean showCityJSONTilingWarning) {
        this.showCityJSONTilingWarning = showCityJSONTilingWarning;
    }

    public SQLExportFilterComponent getSQLExportFilterComponent() {
        return sqlFilter;
    }

    public void setSQLExportFilterComponent(SQLExportFilterComponent sqlExportFilter) {
        if (sqlExportFilter != null) {
            this.sqlFilter = sqlExportFilter;
        }
    }
}
