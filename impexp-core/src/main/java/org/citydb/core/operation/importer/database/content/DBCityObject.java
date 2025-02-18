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
package org.citydb.core.operation.importer.database.content;

import org.citydb.ade.model.LineageProperty;
import org.citydb.ade.model.ReasonForUpdateProperty;
import org.citydb.ade.model.UpdatingPersonProperty;
import org.citydb.config.Config;
import org.citydb.config.geometry.GeometryObject;
import org.citydb.config.project.global.UpdatingPersonMode;
import org.citydb.config.project.importer.CreationDateMode;
import org.citydb.config.project.importer.TerminationDateMode;
import org.citydb.core.database.connection.DatabaseConnectionPool;
import org.citydb.core.database.schema.SequenceEnum;
import org.citydb.core.database.schema.TableEnum;
import org.citydb.core.database.schema.mapping.AbstractObjectType;
import org.citydb.core.operation.common.xlink.DBXlinkBasic;
import org.citydb.core.operation.importer.CityGMLImportException;
import org.citydb.core.operation.importer.util.AttributeValueJoiner;
import org.citydb.core.operation.importer.util.LocalAppearanceHandler;
import org.citydb.core.operation.importer.util.LocalGeometryXlinkResolver;
import org.citydb.core.util.CoreConstants;
import org.citydb.core.util.Util;
import org.citygml4j.geometry.BoundingBox;
import org.citygml4j.model.citygml.ade.ADEComponent;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.citygml.core.ExternalObject;
import org.citygml4j.model.citygml.core.ExternalReference;
import org.citygml4j.model.citygml.core.GeneralizationRelation;
import org.citygml4j.model.citygml.generics.AbstractGenericAttribute;
import org.citygml4j.model.gml.base.AbstractGML;
import org.citygml4j.model.gml.basicTypes.Code;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.model.gml.feature.BoundingShape;
import org.citygml4j.util.bbox.BoundingBoxOptions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class DBCityObject implements DBImporter {
	private final Connection batchConn;
	private final CityGMLImportManager importer;

	private PreparedStatement psCityObject;
	private DBCityObjectGenericAttrib genericAttributeImporter;
	private DBExternalReference externalReferenceImporter;
	private LocalGeometryXlinkResolver resolver;
	private AttributeValueJoiner valueJoiner;
	private int batchCounter;

	private String importFileName;
	private int dbSrid;
	private boolean replaceGmlId;
	private boolean rememberGmlId;
	private boolean importAppearance;
	private boolean affineTransformation;

	private boolean importCityDBMetadata;
	private String updatingPerson;
	private String reasonForUpdate;
	private String lineage;
	private CreationDateMode creationDateMode;
	private TerminationDateMode terminationDateMode;
	private BoundingBoxOptions bboxOptions;

	public DBCityObject(Connection batchConn, Config config, CityGMLImportManager importer) throws CityGMLImportException, SQLException {
		this.batchConn = batchConn;	
		this.importer = importer;

		affineTransformation = config.getImportConfig().getAffineTransformation().isEnabled();
		dbSrid = DatabaseConnectionPool.getInstance().getActiveDatabaseAdapter().getConnectionMetaData().getReferenceSystem().getSrid();
		importAppearance = config.getImportConfig().getAppearances().isSetImportAppearance();
		creationDateMode = config.getImportConfig().getContinuation().getCreationDateMode();
		terminationDateMode = config.getImportConfig().getContinuation().getTerminationDateMode();

		importCityDBMetadata = config.getImportConfig().getContinuation().isImportCityDBMetadata();
		reasonForUpdate = importer.getInternalConfig().getReasonForUpdate();
		lineage = importer.getInternalConfig().getLineage();
		updatingPerson = importer.getInternalConfig().getUpdatingPersonMode() == UpdatingPersonMode.USER ?
				importer.getInternalConfig().getUpdatingPerson() :
				importer.getDatabaseAdapter().getConnectionDetails().getUser();

		String gmlIdCodespace = importer.getInternalConfig().getCurrentGmlIdCodespace();
		if (gmlIdCodespace != null)
			gmlIdCodespace = "'" + gmlIdCodespace + "', ";

		replaceGmlId = config.getImportConfig().getResourceId().isUUIDModeReplace();
		rememberGmlId = config.getImportConfig().getResourceId().isSetKeepIdAsExternalReference();
		if (replaceGmlId && rememberGmlId && importer.getInternalConfig().getInputFile() != null)
			importFileName = importer.getInternalConfig().getInputFile().getFile().toString();

		String schema = importer.getDatabaseAdapter().getConnectionDetails().getSchema();
		bboxOptions = BoundingBoxOptions.defaults()
				.useExistingEnvelopes(true)
				.assignResultToFeatures(true)
				.useReferencePointAsFallbackForImplicitGeometries(true);

		String stmt = "insert into " + schema + ".cityobject (id, objectclass_id, gmlid, " + (gmlIdCodespace != null ? "gmlid_codespace, " : "") +
				"name, name_codespace, description, envelope, creation_date, termination_date, relative_to_terrain, relative_to_water, " +
				"last_modification_date, updating_person, reason_for_update, lineage) values " +
				"(?, ?, ?, " + (gmlIdCodespace != null ? gmlIdCodespace : "") + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		psCityObject = batchConn.prepareStatement(stmt);

		genericAttributeImporter = importer.getImporter(DBCityObjectGenericAttrib.class);
		externalReferenceImporter = importer.getImporter(DBExternalReference.class);
		resolver = new LocalGeometryXlinkResolver(importer);
		valueJoiner = importer.getAttributeValueJoiner();
	}

	protected long doImport(AbstractGML object) throws CityGMLImportException, SQLException {
		AbstractObjectType<?> objectType = importer.getAbstractObjectType(object);
		if (objectType == null)
			throw new SQLException("Failed to retrieve object type.");

		long objectId = doImport(object, objectType);

		// ADE-specific extensions
		if (importer.hasADESupport())
			importer.delegateToADEImporter(object, objectId, objectType);
		
		return objectId;
	}

	protected long doImport(AbstractGML object, AbstractObjectType<?> objectType) throws CityGMLImportException, SQLException {
		boolean isFeature = object instanceof AbstractFeature;
		boolean isCityObject = object instanceof AbstractCityObject;
		boolean isGlobal = !object.isSetParent();
		ZonedDateTime now = ZonedDateTime.now();

		// primary id
		long objectId = importer.getNextSequenceValue(SequenceEnum.CITYOBJECT_ID_SEQ.getName());
		psCityObject.setLong(1, objectId);

		// object class id
		psCityObject.setInt(2, objectType.getObjectClassId());

		// gml:id
		String origGmlId = object.getId();
		if (origGmlId != null)
			object.setLocalProperty(CoreConstants.OBJECT_ORIGINAL_GMLID, origGmlId);

		if (replaceGmlId) {
			String gmlId = importer.generateNewGmlId();

			// mapping entry
			if (object.isSetId()) {
				importer.putObjectId(object.getId(), objectId, gmlId, objectType.getObjectClassId());

				if (rememberGmlId && isCityObject) {	
					ExternalReference externalReference = new ExternalReference();
					externalReference.setInformationSystem(importFileName);

					ExternalObject externalObject = new ExternalObject();
					externalObject.setName(object.getId());

					externalReference.setExternalObject(externalObject);
					((AbstractCityObject)object).addExternalReference(externalReference);
				}
			}

			object.setId(gmlId);
		} else {
			if (object.isSetId())
				importer.putObjectId(object.getId(), objectId, objectType.getObjectClassId());
			else
				object.setId(importer.generateNewGmlId());
		}

		psCityObject.setString(3, object.getId());

		// gml:name
		if (object.isSetName()) {
			valueJoiner.join(object.getName(), Code::getValue, Code::getCodeSpace);
			psCityObject.setString(4, valueJoiner.result(0));
			psCityObject.setString(5, valueJoiner.result(1));
		} else {
			psCityObject.setNull(4, Types.VARCHAR);
			psCityObject.setNull(5, Types.VARCHAR);
		}

		// gml:description
		if (object.isSetDescription()) {
			String description = object.getDescription().getValue();
			if (description != null)
				description = description.trim();

			psCityObject.setString(6, description);
		} else {
			psCityObject.setNull(6, Types.VARCHAR);
		}

		// gml:boundedBy
		BoundingShape boundedBy = null;
		if (isFeature)
			boundedBy = ((AbstractFeature)object).calcBoundedBy(bboxOptions);

		if (boundedBy != null && boundedBy.isSetEnvelope()) {
			BoundingBox bbox = boundedBy.getEnvelope().toBoundingBox();
			List<Double> points = bbox.toList();

			if (affineTransformation)
				importer.getAffineTransformer().transformCoordinates(points);

			double[] coordinates = new double[]{
					points.get(0), points.get(1), points.get(2),
					points.get(3), points.get(1), points.get(2),
					points.get(3), points.get(4), points.get(5),
					points.get(0), points.get(4), points.get(5),
					points.get(0), points.get(1), points.get(2)
			};

			GeometryObject envelope = GeometryObject.createPolygon(coordinates, 3, dbSrid);
			psCityObject.setObject(7, importer.getDatabaseAdapter().getGeometryConverter().getDatabaseObject(envelope, batchConn));
		} else {
			psCityObject.setNull(7, importer.getDatabaseAdapter().getGeometryConverter().getNullGeometryType(), 
					importer.getDatabaseAdapter().getGeometryConverter().getNullGeometryTypeName());
		}

		// core:creationDate
		ZonedDateTime creationDate = null;
		if (isCityObject && (creationDateMode == CreationDateMode.INHERIT || creationDateMode == CreationDateMode.COMPLEMENT)) {
			creationDate = Util.getCreationDate((AbstractCityObject) object, creationDateMode == CreationDateMode.INHERIT);
			if (creationDate != null)
				creationDate = creationDate.withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
		}

		if (creationDate == null)
			creationDate = now;

		psCityObject.setObject(8, creationDate.toOffsetDateTime());

		// core:terminationDate
		ZonedDateTime terminationDate = null;
		if (isCityObject && (terminationDateMode == TerminationDateMode.INHERIT || terminationDateMode == TerminationDateMode.COMPLEMENT)) {
			terminationDate = Util.getTerminationDate((AbstractCityObject) object, terminationDateMode == TerminationDateMode.INHERIT);
			if (terminationDate != null)
				terminationDate = terminationDate.withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
		}

		if (terminationDate == null)
			psCityObject.setNull(9, Types.TIMESTAMP);
		else
			psCityObject.setObject(9, terminationDate.toOffsetDateTime());

		// core:relativeToTerrain
		if (isCityObject && ((AbstractCityObject)object).isSetRelativeToTerrain())
			psCityObject.setString(10, ((AbstractCityObject)object).getRelativeToTerrain().getValue());
		else
			psCityObject.setNull(10, Types.VARCHAR);

		// core:relativeToWater
		if (isCityObject && ((AbstractCityObject)object).isSetRelativeToWater())
			psCityObject.setString(11, ((AbstractCityObject)object).getRelativeToWater().getValue());
		else
			psCityObject.setNull(11, Types.VARCHAR);

		// 3DCityDB metadata
		String updatingPerson = this.updatingPerson;
		String reasonForUpdate = this.reasonForUpdate;
		String lineage = this.lineage;

		if (isCityObject && importCityDBMetadata) {
			for (ADEComponent adeComponent : ((AbstractCityObject) object).getGenericApplicationPropertyOfCityObject()) {
				if (adeComponent instanceof UpdatingPersonProperty)
					updatingPerson = ((UpdatingPersonProperty) adeComponent).getValue();
				else if (adeComponent instanceof ReasonForUpdateProperty)
					reasonForUpdate = ((ReasonForUpdateProperty) adeComponent).getValue();
				else if (adeComponent instanceof LineageProperty)
					lineage = ((LineageProperty) adeComponent).getValue();
			}
		}

		// citydb:lastModificationDate
		psCityObject.setObject(12, now.toOffsetDateTime());

		// citydb:updatingPerson
		psCityObject.setString(13, updatingPerson);

		// citydb:reasonForUpdate
		psCityObject.setString(14, reasonForUpdate);

		// citydb:lineage
		psCityObject.setString(15, lineage);

		// resolve local xlinks to geometry objects
		if (isGlobal) {
			boolean success = resolver.resolveGeometryXlinks(object);
			if (!success) {
				importer.logOrThrowErrorMessage(importer.getObjectSignature(object, origGmlId) +
						": Skipping import due to circular reference of the following geometry XLinks:\n" +
						String.join("\n", resolver.getCircularReferences()));
				return 0;
			}
		}

		psCityObject.addBatch();
		if (++batchCounter == importer.getDatabaseAdapter().getMaxBatchSize())
			importer.executeBatch(TableEnum.CITYOBJECT);

		// work on city object related information
		if (isCityObject) {
			AbstractCityObject cityObject = (AbstractCityObject)object;

			// core:_genericAttribute
			if (cityObject.isSetGenericAttribute()) {
				for (AbstractGenericAttribute genericAttribute : cityObject.getGenericAttribute())
					genericAttributeImporter.doImport(genericAttribute, objectId);
			}

			// core:externalReferences
			if (cityObject.isSetExternalReference()) {
				for (ExternalReference externalReference : cityObject.getExternalReference())
					externalReferenceImporter.doImport(externalReference, objectId);
			}

			// core:generalizesTo
			if (cityObject.isSetGeneralizesTo()) {
				for (GeneralizationRelation generalizesTo : cityObject.getGeneralizesTo()) {
					if (generalizesTo.isSetCityObject()) {
						importer.logOrThrowErrorMessage(importer.getObjectSignature(object) +
								": Failed to correctly process generalizesTo element.");
					} else {
						String href = generalizesTo.getHref();
						if (href != null && href.length() != 0) {
							importer.propagateXlink(new DBXlinkBasic(
									TableEnum.GENERALIZATION.getName(),
									objectId,
									"CITYOBJECT_ID",
									href,
									"GENERALIZES_TO_ID"));
						}
					}
				}
			}		

			// handle local appearances
			if (importAppearance) {
				LocalAppearanceHandler handler = importer.getLocalAppearanceHandler();

				// reset handler for top-level features
				if (isGlobal)
					handler.reset();

				if (cityObject.isSetAppearance())
					handler.registerAppearances(cityObject, objectId);
			}
		}

		importer.updateObjectCounter(object, objectType, objectId);
		return objectId;
	}

	@Override
	public void executeBatch() throws CityGMLImportException, SQLException {
		if (batchCounter > 0) {
			psCityObject.executeBatch();
			batchCounter = 0;
		}
	}

	@Override
	public void close() throws CityGMLImportException, SQLException {
		psCityObject.close();
	}

}
