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
package org.citydb.gui.components.dialog;

import org.citydb.config.i18n.Language;
import org.citydb.util.event.Event;
import org.citydb.util.event.EventDispatcher;
import org.citydb.util.event.EventHandler;
import org.citydb.util.event.global.CounterEvent;
import org.citydb.util.event.global.CounterType;
import org.citydb.util.event.global.EventType;
import org.citydb.util.event.global.ProgressBarEventType;
import org.citydb.util.event.global.StatusDialogMessage;
import org.citydb.util.event.global.StatusDialogProgressBar;
import org.citydb.util.event.global.StatusDialogTitle;
import org.citydb.gui.util.GuiUtil;
import org.citydb.core.registry.ObjectRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ImportStatusDialog extends JDialog implements EventHandler {
	private final EventDispatcher eventDispatcher;

	private JLabel fileName;
	private JLabel messageLabel;
	private JLabel featureCounterLabel;
	private JLabel appearanceCounterLabel;
	private JLabel textureCounterLabel;
	private JLabel fileCounterLabel;
	private JProgressBar progressBar;
	public JButton cancelButton;
	
	private long featureCounter;
	private long appearanceCounter;
	private long textureCounter;
	private int progressBarCounter;
	private volatile boolean acceptStatusUpdate = true;

	public ImportStatusDialog(JFrame frame, 
			String title,
			String message) {
		super(frame, title, true);

		eventDispatcher = ObjectRegistry.getInstance().getEventDispatcher();
		eventDispatcher.addEventHandler(EventType.COUNTER, this);
		eventDispatcher.addEventHandler(EventType.STATUS_DIALOG_PROGRESS_BAR, this);
		eventDispatcher.addEventHandler(EventType.STATUS_DIALOG_MESSAGE, this);
		eventDispatcher.addEventHandler(EventType.STATUS_DIALOG_TITLE, this);
		eventDispatcher.addEventHandler(EventType.INTERRUPT, this);

		initGUI(message);
	}

	private void initGUI(String message) {
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		Object arc = UIManager.get("ProgressBar.arc");
		UIManager.put("ProgressBar.arc", 0);

		fileName = new JLabel(message);
		fileName.setFont(fileName.getFont().deriveFont(Font.BOLD));
		messageLabel = new JLabel(" ");
		cancelButton = new JButton(Language.I18N.getString("common.button.cancel"));
		JLabel featureLabel = new JLabel(Language.I18N.getString("common.status.dialog.featureCounter"));
		featureLabel.setFont(featureLabel.getFont().deriveFont(Font.BOLD));
		JLabel appearanceLabel = new JLabel(Language.I18N.getString("common.status.dialog.appearanceCounter"));
		JLabel textureLabel = new JLabel(Language.I18N.getString("common.status.dialog.textureCounter"));
		JLabel fileCounter = new JLabel(Language.I18N.getString("common.status.dialog.fileCounter"));

		featureCounterLabel = new JLabel("0", SwingConstants.TRAILING);
		featureCounterLabel.setFont(featureCounterLabel.getFont().deriveFont(Font.BOLD));
		appearanceCounterLabel = new JLabel("0", SwingConstants.TRAILING);
		textureCounterLabel = new JLabel("0", SwingConstants.TRAILING);
		featureCounterLabel.setPreferredSize(new Dimension(100, featureLabel.getPreferredSize().height));
		appearanceCounterLabel.setPreferredSize(new Dimension(100, appearanceLabel.getPreferredSize().height));
		textureCounterLabel.setPreferredSize(new Dimension(100, textureLabel.getPreferredSize().height));
		fileCounterLabel = new JLabel("n/a", SwingConstants.TRAILING);
		fileCounterLabel.setPreferredSize(new Dimension(100, fileCounterLabel.getPreferredSize().height));

		progressBar = new JProgressBar();

		setLayout(new GridBagLayout());
		JPanel main = new JPanel();
		main.setLayout(new GridBagLayout());
		{
			JPanel counterPanel = new JPanel();
			counterPanel.setBackground(UIManager.getColor("TextField.background"));
			counterPanel.setLayout(new GridBagLayout());
			{
				counterPanel.add(featureLabel, GuiUtil.setConstraints(0, 0, 0, 0, GridBagConstraints.HORIZONTAL, 5, 5, 0, 5));
				counterPanel.add(featureCounterLabel, GuiUtil.setConstraints(1, 0, 1, 0, GridBagConstraints.HORIZONTAL, 5, 5, 0, 5));
				counterPanel.add(appearanceLabel, GuiUtil.setConstraints(0, 1, 0, 0, GridBagConstraints.HORIZONTAL, 3, 5, 0, 5));
				counterPanel.add(appearanceCounterLabel, GuiUtil.setConstraints(1, 1, 1, 0, GridBagConstraints.HORIZONTAL, 3, 5, 0, 5));
				counterPanel.add(textureLabel, GuiUtil.setConstraints(0, 2, 0, 0, GridBagConstraints.HORIZONTAL, 3, 5, 5, 5));
				counterPanel.add(textureCounterLabel, GuiUtil.setConstraints(1, 2, 1, 0, GridBagConstraints.HORIZONTAL, 3, 5, 5, 5));
				counterPanel.add(fileCounter, GuiUtil.setConstraints(0, 3, 0, 0, GridBagConstraints.HORIZONTAL, 5, 5, 5, 5));
				counterPanel.add(fileCounterLabel, GuiUtil.setConstraints(1, 3, 1, 0, GridBagConstraints.HORIZONTAL, 5, 5, 5, 5));
			}

			main.add(fileName, GuiUtil.setConstraints(0, 0, 0, 0, GridBagConstraints.HORIZONTAL, 0, 0, 5, 0));
			main.add(messageLabel, GuiUtil.setConstraints(0, 1, 0, 0, GridBagConstraints.HORIZONTAL, 5, 0, 5, 0));
			main.add(progressBar, GuiUtil.setConstraints(0, 2, 1, 0, GridBagConstraints.HORIZONTAL, 5, 0, 0, 0));
			main.add(counterPanel, GuiUtil.setConstraints(0, 3, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, 0, 0, 0, 0));
		}

		add(main, GuiUtil.setConstraints(0, 0, 1, 1, GridBagConstraints.BOTH, 10, 10, 0, 10));
		add(cancelButton, GuiUtil.setConstraints(0, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, 15, 10, 10, 10));

		setMinimumSize(new Dimension(300, 100));
		pack();

		UIManager.put("ProgressBar.arc", arc);
		progressBar.setIndeterminate(true);

		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				eventDispatcher.removeEventHandler(ImportStatusDialog.this);
			}
		});
	}

	public JButton getCancelButton() {
		return cancelButton;
	}

	@Override
	public void handleEvent(Event e) throws Exception {
		if (e.getEventType() == EventType.COUNTER) {
			CounterEvent counter = (CounterEvent) e;
			if (counter.getType() == CounterType.TOPLEVEL_FEATURE) {
				featureCounter += counter.getCounter();
				featureCounterLabel.setText(String.valueOf(featureCounter));
			} else if (counter.getType() == CounterType.GLOBAL_APPEARANCE) {
				appearanceCounter += counter.getCounter();
				appearanceCounterLabel.setText(String.valueOf(appearanceCounter));
			} else if (counter.getType() == CounterType.TEXTURE_IMAGE) {
				textureCounter += counter.getCounter();
				textureCounterLabel.setText(String.valueOf(textureCounter));
			} else if (counter.getType() == CounterType.FILE) {
				fileCounterLabel.setText(String.valueOf(counter.getCounter()));
			}
		} else if (e.getEventType() == EventType.INTERRUPT) {
			acceptStatusUpdate = false;
			messageLabel.setText(Language.I18N.getString("common.dialog.msg.abort"));
			progressBar.setIndeterminate(true);
		} else if (e.getEventType() == EventType.STATUS_DIALOG_PROGRESS_BAR && acceptStatusUpdate) {
			StatusDialogProgressBar progressBarEvent = (StatusDialogProgressBar) e;
			if (progressBarEvent.getType() == ProgressBarEventType.INIT) {
				SwingUtilities.invokeLater(() -> progressBar.setIndeterminate(progressBarEvent.isSetIntermediate()));
				if (!progressBarEvent.isSetIntermediate()) {
					progressBar.setMaximum(progressBarEvent.getValue());
					progressBar.setValue(0);
					progressBarCounter = 0;
				}
			} else {
				progressBarCounter += progressBarEvent.getValue();
				progressBar.setValue(progressBarCounter);
			}
		} else if (e.getEventType() == EventType.STATUS_DIALOG_MESSAGE && acceptStatusUpdate) {
			messageLabel.setText(((StatusDialogMessage) e).getMessage());
		} else if (e.getEventType() == EventType.STATUS_DIALOG_TITLE && acceptStatusUpdate) {
			fileName.setText(((StatusDialogTitle) e).getTitle());
		}
	}
}
