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

package org.citydb.cli.option;

import org.citydb.config.project.query.filter.type.FeatureTypeFilter;
import picocli.CommandLine;

import javax.xml.namespace.NamespaceContext;
import java.util.Map;

public class TypeNamesOption implements CliOption {
    @CommandLine.Option(names = {"-t", "--type-name"}, split = ",", paramLabel = "<[prefix:]name>",
            description = "Names of the top-level features to process.")
    private String[] typeNames;

    @CommandLine.Option(names = "--namespace", split = ",", paramLabel = "<prefix=name>",
            description = "Prefix-to-namespace mappings.")
    private Map<String, String> namespaces;

    private FeatureTypeFilter featureTypeFilter;
    private NamespaceContext namespaceContext;

    public String[] getTypeNames() {
        return typeNames;
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    private NamespaceContext toNamespaceContext() {
        return namespaceContext;
    }

    public FeatureTypeFilter toFeatureTypeFilter() {
        return featureTypeFilter;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        namespaceContext = CliOptionBuilder.namespaceContext(namespaces);
        featureTypeFilter = CliOptionBuilder.featureTypeFilter(typeNames, namespaceContext, commandLine);
    }
}
