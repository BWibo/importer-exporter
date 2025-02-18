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
package org.citydb.gui.components.checkboxtree;

import javax.swing.tree.TreePath;

/**
 * SingleTreeCheckingMode defines a TreeCheckingMode without recursion. In this
 * simple mode the check state always changes only the current node: no
 * recursion. Also, only a single node of the tree is allowed to have a check at
 * a given time.
 *
 * @author Boldrini
 */
public class SingleTreeCheckingMode extends TreeCheckingMode {

    SingleTreeCheckingMode(DefaultTreeCheckingModel model) {
        super(model);
    }

    @Override
    public void checkPath(TreePath path) {
        this.model.clearChecking();
        this.model.addToCheckedPathsSet(path);
        this.model.updatePathGreyness(path);
        this.model.updateAncestorsGreyness(path);
    }

    @Override
    public void uncheckPath(TreePath path) {
        this.model.removeFromCheckedPathsSet(path);
        this.model.updatePathGreyness(path);
        this.model.updateAncestorsGreyness(path);
    }

    /*
     * (non-Javadoc)
     *
     * @seeit.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingMode#
     * updateCheckAfterChildrenInserted(javax.swing.tree.TreePath)
     */
    @Override
    public void updateCheckAfterChildrenInserted(TreePath parent) {
        this.model.updatePathGreyness(parent);
        this.model.updateAncestorsGreyness(parent);
    }

    /*
     * (non-Javadoc)
     *
     * @seeit.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingMode#
     * updateCheckAfterChildrenRemoved(javax.swing.tree.TreePath)
     */
    @Override
    public void updateCheckAfterChildrenRemoved(TreePath parent) {
        this.model.updatePathGreyness(parent);
        this.model.updateAncestorsGreyness(parent);
    }

    /*
     * (non-Javadoc)
     *
     * @seeit.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingMode#
     * updateCheckAfterStructureChanged(javax.swing.tree.TreePath)
     */
    @Override
    public void updateCheckAfterStructureChanged(TreePath parent) {
        this.model.updatePathGreyness(parent);
        this.model.updateAncestorsGreyness(parent);
    }

}