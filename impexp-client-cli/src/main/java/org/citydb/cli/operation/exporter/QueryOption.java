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

package org.citydb.cli.operation.exporter;

import org.citydb.config.project.query.QueryConfig;
import org.citydb.config.project.query.filter.selection.AbstractPredicate;
import org.citydb.config.project.query.filter.selection.SelectionFilter;
import org.citydb.config.project.query.filter.selection.id.DatabaseIdOperator;
import org.citydb.config.project.query.filter.selection.id.ResourceIdOperator;
import org.citydb.config.project.query.filter.selection.logical.AndOperator;
import org.citydb.config.project.query.filter.selection.spatial.AbstractSpatialOperator;
import org.citydb.config.project.query.filter.selection.sql.SelectOperator;
import org.citydb.config.project.query.simple.SimpleFeatureVersionFilter;
import org.citydb.cli.option.CliOption;
import org.citydb.cli.option.CounterOption;
import org.citydb.cli.option.DatabaseIdOption;
import org.citydb.cli.option.FeatureVersionOption;
import org.citydb.cli.option.ResourceIdOption;
import org.citydb.cli.option.SQLSelectOption;
import org.citydb.cli.option.TypeNamesOption;
import org.citydb.cli.option.XMLQueryOption;
import org.citydb.core.registry.ObjectRegistry;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

public class QueryOption implements CliOption {
    @CommandLine.ArgGroup(exclusive = false)
    private TypeNamesOption typeNamesOption;

    @CommandLine.ArgGroup(exclusive = false)
    private FeatureVersionOption featureVersionOption;

    @CommandLine.ArgGroup
    private ResourceIdOption resourceIdOption;

    @CommandLine.ArgGroup
    private DatabaseIdOption databaseIdOption;

    @CommandLine.ArgGroup(exclusive = false)
    private BoundingBoxOption boundingBoxOption;

    @CommandLine.ArgGroup(exclusive = false)
    private CounterOption counterOption;

    @CommandLine.ArgGroup(exclusive = false)
    private LodOption lodOption;

    @CommandLine.ArgGroup
    private AppearanceOption appearanceOption;

    @CommandLine.ArgGroup
    private SQLSelectOption sqlSelectOption;

    @CommandLine.ArgGroup
    private XMLQueryOption xmlQueryOption;

    public boolean isExportAppearances() {
        return appearanceOption == null || appearanceOption.isExportAppearances();
    }

    public QueryConfig toQueryConfig() {
        if (typeNamesOption != null
                || featureVersionOption != null
                || resourceIdOption != null
                || databaseIdOption != null
                || boundingBoxOption != null
                || counterOption != null
                || lodOption != null
                || (appearanceOption != null && appearanceOption.isSetAppearanceFilter())
                || sqlSelectOption != null) {
            QueryConfig queryConfig = new QueryConfig();
            List<AbstractPredicate> predicates = new ArrayList<>();

            if (typeNamesOption != null) {
                queryConfig.setFeatureTypeFilter(typeNamesOption.toFeatureTypeFilter());
            }

            SimpleFeatureVersionFilter versionFilter = featureVersionOption != null ?
                    featureVersionOption.toFeatureVersionFilter(ObjectRegistry.getInstance().getDatatypeFactory()) :
                    FeatureVersionOption.defaultFeatureVersionFilter();

            if (versionFilter != null) {
                AbstractPredicate predicate = versionFilter.toPredicate();
                if (predicate != null) {
                    predicates.add(predicate);
                }
            }

            if (resourceIdOption != null) {
                ResourceIdOperator idOperator = resourceIdOption.toResourceIdOperator();
                if (idOperator != null) {
                    predicates.add(idOperator);
                }
            }

            if (databaseIdOption != null) {
                DatabaseIdOperator idOperator = databaseIdOption.toDatabaseIdOperator();
                if (idOperator != null) {
                    predicates.add(idOperator);
                }
            }

            if (boundingBoxOption != null) {
                if (boundingBoxOption.isSetTiling()) {
                    queryConfig.setTiling(boundingBoxOption.toTiling());
                } else {
                    AbstractSpatialOperator spatialOperator = boundingBoxOption.toSpatialOperator();
                    if (spatialOperator != null) {
                        predicates.add(spatialOperator);
                    }
                }
            }

            if (counterOption != null) {
                queryConfig.setCounterFilter(counterOption.toCounterFilter());
            }

            if (lodOption != null) {
                queryConfig.setLodFilter(lodOption.toLodFilter());
            }

            if (appearanceOption != null && appearanceOption.isSetAppearanceFilter()) {
                queryConfig.setAppearanceFilter(appearanceOption.toAppearanceFilter());
            }

            if (sqlSelectOption != null) {
                SelectOperator selectOperator = sqlSelectOption.toSelectOperator();
                if (selectOperator != null) {
                    predicates.add(selectOperator);
                }
            }

            if (!predicates.isEmpty()) {
                AndOperator andOperator = new AndOperator();
                andOperator.setOperands(predicates);
                SelectionFilter selectionFilter = new SelectionFilter();
                selectionFilter.setPredicate(andOperator);
                queryConfig.setSelectionFilter(selectionFilter);
            }

            return queryConfig;
        } else if (xmlQueryOption != null) {
            return xmlQueryOption.toQueryConfig();
        }

        return new QueryConfig();
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (xmlQueryOption != null) {
            if (typeNamesOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --type-name and --xml-query are mutually exclusive (specify only one)");
            } else if (featureVersionOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --feature-version and --xml-query are mutually exclusive (specify only one)");
            } else if (resourceIdOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --gml-id and --xml-query are mutually exclusive (specify only one)");
            } else if (boundingBoxOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --bbox and --xml-query are mutually exclusive (specify only one)");
            } else if (counterOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: Counter options and --xml-query are mutually exclusive (specify only one)");
            } else if (lodOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --lod and --xml-query are mutually exclusive (specify only one)");
            } else if (appearanceOption != null && appearanceOption.isSetAppearanceFilter()) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --appearance-theme and --xml-query are mutually exclusive (specify only one)");
            } else if (sqlSelectOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --sql-select and --xml-query are mutually exclusive (specify only one)");
            }

            xmlQueryOption.preprocess(commandLine);
        }

        if (typeNamesOption != null) {
            typeNamesOption.preprocess(commandLine);
        }

        if (featureVersionOption != null) {
            featureVersionOption.preprocess(commandLine);
        }

        if (databaseIdOption != null) {
            databaseIdOption.preprocess(commandLine);
        }

        if (boundingBoxOption != null) {
            boundingBoxOption.preprocess(commandLine);
        }

        if (counterOption != null) {
            counterOption.preprocess(commandLine);
        }

        if (lodOption != null) {
            lodOption.preprocess(commandLine);
        }
    }
}
