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

import org.citydb.config.project.query.filter.selection.id.DatabaseIdOperator;
import picocli.CommandLine;

public class DatabaseIdOption implements CliOption {
    @CommandLine.Option(names = "--db-id", split = ",", paramLabel = "<id>",
            description = "Process top-level features with a matching database id.")
    private Long[] ids;

    private DatabaseIdOperator databaseIdOperator;

    public DatabaseIdOperator toDatabaseIdOperator() {
        return databaseIdOperator;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        databaseIdOperator = CliOptionBuilder.databaseIdOperator(ids, commandLine);
    }
}
