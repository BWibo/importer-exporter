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
package org.citydb.gui.components.popup;

import org.citydb.config.i18n.Language;
import org.citydb.util.event.Event;
import org.citydb.util.event.EventHandler;
import org.citydb.util.event.global.EventType;
import org.citydb.core.registry.ObjectRegistry;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class TreePopupMenu extends AbstractPopupMenu implements EventHandler {
	private JMenuItem expand;
	private JMenuItem expandAll;
	private JMenuItem collapse;
	private JMenuItem collapseAll;

	private JTree tree;
	private TreePath path;

	public TreePopupMenu() {
		ObjectRegistry.getInstance().getEventDispatcher().addEventHandler(EventType.SWITCH_LOCALE, this);
	}

	public void init() {
		expand = new JMenuItem();
		expandAll = new JMenuItem();		
		collapse = new JMenuItem();
		collapseAll = new JMenuItem();

		expand.addActionListener(e -> {
			if (path != null) {
				performActionOnNodes(path, true, false);
			}
		});

		expandAll.addActionListener(e -> {
			if (path != null) {
				performActionOnNodes(path, true, true);
			}
		});

		collapse.addActionListener(e -> {
			if (path != null) {
				performActionOnNodes(path, false, false);
			}
		});

		collapseAll.addActionListener(e -> {
			if (path != null) {
				performActionOnNodes(path, false, true);
			}
		});

		add(expand);
		add(expandAll);
		addSeparator();
		add(collapse);
		add(collapseAll);
	}

	public void prepare(JTree tree, TreePath path) {
		this.tree = tree;
		this.path = path;

		TreeNode node = (TreeNode)path.getLastPathComponent();
		boolean hasNestedChildren = hasNestedChildren(path);		
		boolean isCollapsed = tree.isCollapsed(path);
		boolean isLeaf = node.isLeaf();

		expand.setVisible(isLeaf || isCollapsed);
		expandAll.setEnabled(!isLeaf && hasNestedChildren && (isCollapsed || showAll(path, path, true)));
		collapse.setVisible(!isLeaf && !isCollapsed);
		collapseAll.setEnabled(!isLeaf && !isCollapsed && hasNestedChildren && showAll(path, path, false));
	}

	public void doTranslation() {
		expand.setText(Language.I18N.getString("common.popup.expand"));
		expandAll.setText(Language.I18N.getString("common.popup.expandAll"));
		collapse.setText(Language.I18N.getString("common.popup.collapse"));
		collapseAll.setText(Language.I18N.getString("common.popup.collapseAll"));
	}

	private void performActionOnNodes(TreePath parent, boolean expand, boolean recursive) {
		TreeNode node = (TreeNode)parent.getLastPathComponent();

		if (recursive) {
			for (int i = 0; i < node.getChildCount(); i++) {
				performActionOnNodes(parent.pathByAddingChild(node.getChildAt(i)), expand, recursive);
			}
		}

		if (expand) {
			tree.expandPath(parent);
		} else {
			tree.collapsePath(parent);
		}
	}

	private boolean showAll(TreePath root, TreePath sub, boolean expand) {
		TreeNode node = (TreeNode)sub.getLastPathComponent();
		
		for (int i = 0; i < node.getChildCount(); ++i) {
			TreeNode child = node.getChildAt(i);
			if (child.isLeaf()) {
				continue;
			}
			
			if (showAll(root, sub.pathByAddingChild(child), expand)) {
				return true;
			}
		}
		
		if (root == sub) {
			return false;
		}
		
		return expand ? tree.isCollapsed(sub) : tree.isExpanded(sub);
	}

	private boolean hasNestedChildren(TreePath parent) {
		TreeNode node = (TreeNode)parent.getLastPathComponent();
		boolean hasNestedChildren = false;

		for (int i = 0; i < node.getChildCount(); ++i) {
			TreeNode child = (TreeNode)parent.pathByAddingChild(node.getChildAt(i)).getLastPathComponent();
			if (!child.isLeaf()) {
				hasNestedChildren = true;
				break;
			}
		}

		return hasNestedChildren;
	}

	@Override
	public void handleEvent(Event event) throws Exception {
		doTranslation();
	}
}
