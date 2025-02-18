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
package org.citydb.gui.components.bbox;

import org.citydb.config.Config;
import org.citydb.config.geometry.BoundingBox;
import org.citydb.config.project.database.DatabaseSrs;
import org.citydb.util.log.Logger;
import org.citydb.core.registry.ObjectRegistry;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BoundingBoxClipboardHandler implements ClipboardOwner {
	private static BoundingBoxClipboardHandler instance;

	private final Logger log = Logger.getInstance();
	private final Config config;
	private final boolean isMac;
	private final Clipboard clipboard;

	private BoundingBoxClipboardHandler() {
		// just to thwart instantiation
		config = ObjectRegistry.getInstance().getConfig();
		isMac = System.getProperty("os.name").toLowerCase().contains("mac");
		clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	}

	public static synchronized BoundingBoxClipboardHandler getInstance() {
		if (instance == null) {
			instance = new BoundingBoxClipboardHandler();
		}

		return instance;
	}

	public void putBoundingBox(BoundingBox bbox) {
		StringBuilder content = new StringBuilder();
		content.append("bbox=")
		.append(bbox.getLowerCorner().getX()).append(",")
		.append(bbox.getLowerCorner().getY()).append(",")
		.append(bbox.getUpperCorner().getX()).append(",")
		.append(bbox.getUpperCorner().getY());

		if (bbox.isSetSrs()) {
			content.append("&3dcitydb_srs=")
			.append(bbox.getSrs().getId());
		}

		clipboard.setContents(new StringSelection(content.toString()), this);
	}

	public BoundingBox getBoundingBox() {
		if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
			try {
				Transferable content = clipboard.getContents(null);
				String bbox = (String)content.getTransferData(DataFlavor.stringFlavor);

				BoundingBox result = parseWebServiceRepresentation(bbox);
				if (result != null)
					return result;

				result = parseGMLEnvelopeRepresentation(bbox);
				if (result != null)
					return result;

			} catch (Exception e) {
				//
			}
		}

		log.error("Failed to interpret clipboard content as bounding box.");
		return null;
	}

	public boolean containsPossibleBoundingBox() {
		if (!isMac) {		
			try {
				return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor);
			} catch (Exception e) {
				// we face strange access issues on Windows sometimes
				// silently discard them...
			}
		}

		return true;
	}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
		// not necessary now
	}

	private BoundingBox parseWebServiceRepresentation(String candidate) {	
		BoundingBox bbox = new BoundingBox();
		String[] tokens = candidate.trim().split("&+");
		boolean success = false;

		for (String token : tokens) {
			String[] pair = token.trim().split("=");
			if (pair != null && pair.length == 2) {
				String key = pair[0].trim();
				String value = pair[1].trim();

				if (key != null && value != null && key.length() > 0 && value.length() > 0) {
					if ("bbox".equals(key.toLowerCase())) {
						String[] coords = value.split("[,|;|\\s]+");

						if (coords != null && coords.length == 4) {
							try {
								NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);
								bbox.getLowerCorner().setX(format.parse(coords[0].trim()).doubleValue());
								bbox.getLowerCorner().setY(format.parse(coords[1].trim()).doubleValue());
								bbox.getUpperCorner().setX(format.parse(coords[2].trim()).doubleValue());
								bbox.getUpperCorner().setY(format.parse(coords[3].trim()).doubleValue());

								success = true;
							} catch (Exception e) {
								//
							}
						}
					} else if ("3dcitydb_srs".equals(key.toLowerCase())) {
						for (DatabaseSrs srs : config.getDatabaseConfig().getReferenceSystems()) {
							if (value.equals(srs.getId())) {
								bbox.setSrs(srs);
								break;
							}
						}
					}
				}
			}
		}

		return success ? bbox : null;
	}

	private BoundingBox parseGMLEnvelopeRepresentation(String candidate) {	
		String lowerCorner = "</?(.*?:)?lowerCorner>";
		String upperCorner = "</?(.*?:)?upperCorner>";

		StringBuilder regex = new StringBuilder();
		regex.append(".*?")
		.append(lowerCorner)
		.append("(.*?)")
		.append(lowerCorner)
		.append("(.*?)")
		.append(upperCorner)
		.append("(.*?)")
		.append(upperCorner)
		.append(".*?");

		Pattern pattern = Pattern.compile(regex.toString(), Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(candidate.trim());

		if (matcher.matches()) {
			try {
				NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);	

				String lowerCornerCoords = matcher.group(2);
				String upperCornerCoords = matcher.group(6);

				String value = "([-|\\+]?\\d*?(\\.\\d+?)??)";			

				regex = new StringBuilder();
				regex.append("\\s*").append(value)
				.append("\\s+").append(value)
				.append("(\\s+").append(value).append(")?")
				.append("\\s*");

				pattern = Pattern.compile(regex.toString(), Pattern.MULTILINE | Pattern.DOTALL);
				Matcher lower = pattern.matcher(lowerCornerCoords.trim());
				Matcher upper = pattern.matcher(upperCornerCoords.trim());

				if (lower.matches() && upper.matches()) {
					BoundingBox bbox = new BoundingBox();

					bbox.getLowerCorner().setX(format.parse(lower.group(1)).doubleValue());
					bbox.getLowerCorner().setY(format.parse(lower.group(3)).doubleValue());
					bbox.getUpperCorner().setX(format.parse(upper.group(1)).doubleValue());
					bbox.getUpperCorner().setY(format.parse(upper.group(3)).doubleValue());

					return bbox;
				}
			} catch (Exception e) {
				//
			}
		}

		return null;
	}

}
