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
package org.citydb.core.query.builder.sql;

import org.citydb.ade.model.module.CityDBADE200Module;
import org.citydb.core.database.adapter.AbstractSQLAdapter;
import org.citydb.core.database.schema.mapping.AbstractExtension;
import org.citydb.core.database.schema.mapping.AbstractJoin;
import org.citydb.core.database.schema.mapping.AbstractObjectType;
import org.citydb.core.database.schema.mapping.AbstractPathElement;
import org.citydb.core.database.schema.mapping.AbstractProperty;
import org.citydb.core.database.schema.mapping.AbstractType;
import org.citydb.core.database.schema.mapping.AbstractTypeProperty;
import org.citydb.core.database.schema.mapping.ComplexAttribute;
import org.citydb.core.database.schema.mapping.Condition;
import org.citydb.core.database.schema.mapping.FeatureType;
import org.citydb.core.database.schema.mapping.GeometryProperty;
import org.citydb.core.database.schema.mapping.InjectedProperty;
import org.citydb.core.database.schema.mapping.Join;
import org.citydb.core.database.schema.mapping.JoinTable;
import org.citydb.core.database.schema.mapping.MappingConstants;
import org.citydb.core.database.schema.mapping.PathElementType;
import org.citydb.core.database.schema.mapping.ReverseJoin;
import org.citydb.core.database.schema.mapping.SimpleAttribute;
import org.citydb.core.database.schema.mapping.SimpleType;
import org.citydb.core.database.schema.mapping.TableRole;
import org.citydb.core.database.schema.path.AbstractNode;
import org.citydb.core.database.schema.path.AbstractNodePredicate;
import org.citydb.core.database.schema.path.FeatureTypeNode;
import org.citydb.core.database.schema.path.SchemaPath;
import org.citydb.core.database.schema.path.predicate.comparison.ComparisonPredicateName;
import org.citydb.core.database.schema.path.predicate.comparison.EqualToPredicate;
import org.citydb.core.database.schema.path.predicate.logical.BinaryLogicalPredicate;
import org.citydb.core.database.schema.path.predicate.logical.LogicalPredicateName;
import org.citydb.core.query.builder.QueryBuildException;
import org.citydb.core.query.builder.sql.SQLQueryContext.BuildContext;
import org.citydb.core.query.filter.selection.expression.LiteralType;
import org.citydb.core.query.filter.selection.expression.TimestampLiteral;
import org.citydb.sqlbuilder.expression.AbstractSQLLiteral;
import org.citydb.sqlbuilder.expression.DoubleLiteral;
import org.citydb.sqlbuilder.expression.Expression;
import org.citydb.sqlbuilder.expression.IntegerLiteral;
import org.citydb.sqlbuilder.expression.LiteralList;
import org.citydb.sqlbuilder.expression.PlaceHolder;
import org.citydb.sqlbuilder.expression.StringLiteral;
import org.citydb.sqlbuilder.schema.AliasGenerator;
import org.citydb.sqlbuilder.schema.DefaultAliasGenerator;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.select.PredicateToken;
import org.citydb.sqlbuilder.select.Select;
import org.citydb.sqlbuilder.select.join.JoinName;
import org.citydb.sqlbuilder.select.operator.comparison.ComparisonFactory;
import org.citydb.sqlbuilder.select.operator.comparison.ComparisonName;
import org.citydb.sqlbuilder.select.operator.logical.BinaryLogicalOperator;
import org.citydb.sqlbuilder.select.operator.logical.LogicalOperationName;
import org.citydb.sqlbuilder.select.projection.Function;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class SchemaPathBuilder {
	private final AbstractSQLAdapter sqlAdapter;
	private final String schemaName;
	private final DefaultAliasGenerator aliasGenerator;

	private Map<String, Table> tableContext;
	private Table currentTable;
	private AbstractNode<?> currentNode;

	protected SchemaPathBuilder(AbstractSQLAdapter sqlAdapter, String schemaName, BuildProperties buildProperties) {
		this.sqlAdapter = sqlAdapter;
		this.schemaName = schemaName;
		aliasGenerator = buildProperties.aliasGenerator;
	}

	protected AliasGenerator getAliasGenerator() {
		return aliasGenerator;
	}

	protected SQLQueryContext createQueryContext(FeatureType featureType) {
		return new SQLQueryContext(featureType, new Table(featureType.getTable(), schemaName, aliasGenerator));
	}

	protected void addSchemaPath(SchemaPath schemaPath, SQLQueryContext queryContext, boolean useLeftJoins) throws QueryBuildException {
		addSchemaPath(queryContext, schemaPath, true, useLeftJoins);
	}

	protected void addSchemaPath(SchemaPath schemaPath, SQLQueryContext queryContext, boolean matchCase, boolean useLeftJoins) throws QueryBuildException {
		addSchemaPath(queryContext, schemaPath, matchCase, useLeftJoins);
	}

	private void addSchemaPath(SQLQueryContext queryContext, SchemaPath schemaPath, boolean matchCase, boolean useLeftJoins) throws QueryBuildException {
		BuildContext buildContext = queryContext.getBuildContext();

		FeatureTypeNode head = schemaPath.getFirstNode();
		if (!buildContext.getNode().isEqualTo(head, false))
			throw new QueryBuildException("The root node " + head + " of the schema path does not match the query context.");

		// initialize build context
		Select select = queryContext.getSelect();
		queryContext.unsetPredicates();
		currentNode = head;

		// iterate through schema path
		while (currentNode != null) {
			AbstractPathElement pathElement = currentNode.getPathElement();

			BuildContext subContext = currentNode == head ?
					buildContext :
					buildContext.findSubContext(currentNode);

			if (subContext != null) {
				// restore build context
				tableContext = subContext.getTableContext();
				currentTable = subContext.getCurrentTable();
			} else {
				processNode(pathElement, head, select, useLeftJoins);

				// remember build context
				subContext = buildContext.addSubContext(currentNode, currentTable, tableContext);
			}

			// translate predicate to where-conditions
			if (currentNode.isSetPredicate()) {
				PredicateToken predicate = evaluatePredicatePath(select, pathElement, currentNode.getPredicate(), matchCase, useLeftJoins);
				queryContext.addPredicate(predicate);
			}

			buildContext = subContext;
			currentNode = currentNode.child();
		}

		// copy results to query context
		updateQueryContext(queryContext, currentTable, schemaPath);
	}

	protected Table joinCityObjectTable(SQLQueryContext queryContext) throws QueryBuildException {
		if (queryContext.getCityObjectTable() != null)
			return queryContext.getCityObjectTable();

		BuildContext buildContext = queryContext.getBuildContext();
		FeatureType featureType = queryContext.getFeatureType();
		Select select = queryContext.getSelect();

		// restore build context
		tableContext = buildContext.getTableContext();
		currentTable = buildContext.getCurrentTable();

		// retrieve table and column of id property
		AbstractProperty property = featureType.getProperty(MappingConstants.ID, CityDBADE200Module.v3_0.getNamespaceURI(), true);
		if (property == null)
			throw new QueryBuildException("Fatal database schema error: Failed to find '" + MappingConstants.ID + "' property.");

		evaluatePropertyPath(select, featureType, property, false);
		queryContext.setCityObjectTable(currentTable);

		return currentTable;
	}

	private void processNode(AbstractPathElement pathElement, FeatureTypeNode head, Select select, boolean useLeftJoins) throws QueryBuildException {
		switch (pathElement.getElementType()) {
			case SIMPLE_ATTRIBUTE:
			case COMPLEX_ATTRIBUTE:
			case FEATURE_PROPERTY:
			case OBJECT_PROPERTY:
			case COMPLEX_PROPERTY:
			case IMPLICIT_GEOMETRY_PROPERTY:
			case GEOMETRY_PROPERTY:
				evaluatePropertyPath(select, currentNode.parent().getPathElement(), (AbstractProperty) pathElement, useLeftJoins);
				break;
			case FEATURE_TYPE:
			case OBJECT_TYPE:
			case COMPLEX_TYPE:
				AbstractType<?> type = (AbstractType<?>) pathElement;
				if (type.isSetTable()) {
					tableContext = new HashMap<>();
					tableContext.put(currentTable.getName(), currentTable);

					// correct table context in case of ADE subtypes
					if (currentNode != head && !type.getTable().equals(currentTable.getName()))
						traverseTypeHierarchy(type, select);
				}
				break;
		}
	}

	private void evaluatePropertyPath(Select select, AbstractPathElement parent, AbstractProperty property, boolean useLeftJoins) throws QueryBuildException {
		boolean found = false;

		if (PathElementType.TYPES.contains(parent.getElementType())) {
			AbstractType<?> type = (AbstractType<?>)parent;

			while (!found && type != null) {
				for (AbstractProperty tmp : type.getProperties()) {
					if (tmp == property) {
						found = true;
						break;
					}

					// check whether a simple attribute is part of a complex attribute type definition
					if (property.getElementType() == PathElementType.SIMPLE_ATTRIBUTE &&
							tmp.getElementType() == PathElementType.COMPLEX_ATTRIBUTE &&
							((SimpleAttribute)property).hasParentAttributeType() &&
							((SimpleAttribute)property).getParentAttributeType() == ((ComplexAttribute)tmp).getType()) {
						property = tmp;
						found = true;
						break;
					}
				}

				if (!found) {
					if (type.isSetExtension()) {
						AbstractExtension<?> extension = type.getExtension();
						if (extension.isSetJoin())
							addJoin(select, extension.getJoin(), false);

						type = extension.getBase();
					} else
						type = null;
				}
			}
		}

		if (property instanceof InjectedProperty) {
			InjectedProperty injectedProperty = (InjectedProperty)property;
			if (injectedProperty.isSetBaseJoin())
				addJoin(select, injectedProperty.getBaseJoin(), useLeftJoins);
		}

		if (property.isSetJoin()) {
			tableContext = new HashMap<>();
			addJoin(select, property.getJoin(), useLeftJoins);
		}
	}

	private PredicateToken evaluatePredicatePath(Select select, AbstractPathElement parent, AbstractNodePredicate predicate, boolean matchCase, boolean useLeftJoins) throws QueryBuildException {
		if (predicate.getPredicateName() == ComparisonPredicateName.EQUAL_TO) {
			EqualToPredicate equalTo = (EqualToPredicate)predicate;
			Table fromTable = currentTable;

			if (PathElementType.TYPES.contains(parent.getElementType()))
				evaluatePropertyPath(select, parent, equalTo.getLeftOperand(), useLeftJoins);

			Expression leftOperand = currentTable.getColumn(equalTo.getLeftOperand().getColumn());
			Expression rightOperand = equalTo.getRightOperand().convertToSQLPlaceHolder();

			if (!matchCase && ((PlaceHolder<?>)rightOperand).getValue() instanceof String) {
				rightOperand = new Function(sqlAdapter.resolveDatabaseOperationName("string.upper"), rightOperand);
				leftOperand = new Function(sqlAdapter.resolveDatabaseOperationName("string.upper"), leftOperand);
			}

			// implicit conversion from timestamp to date
			if (equalTo.getRightOperand().getLiteralType() == LiteralType.TIMESTAMP
					&& ((TimestampLiteral)equalTo.getRightOperand()).isDate()) {
				leftOperand = new Function(sqlAdapter.resolveDatabaseOperationName("timestamp.to_date"), leftOperand);
			}

			PredicateToken token = ComparisonFactory.equalTo(
					leftOperand, 
					rightOperand);

			if (currentTable != fromTable)
				currentTable = fromTable;

			return token;
		}

		else {
			// recursively build predicate that is composed of AND and OR operators
			BinaryLogicalPredicate logicalPredicate = (BinaryLogicalPredicate)predicate;
			PredicateToken leftOperand = evaluatePredicatePath(select, parent, logicalPredicate.getLeftOperand(), matchCase, useLeftJoins);
			PredicateToken rightOperand = evaluatePredicatePath(select, parent, logicalPredicate.getRightOperand(), matchCase, useLeftJoins);

			LogicalOperationName op = logicalPredicate.getPredicateName() == LogicalPredicateName.AND ? 
					LogicalOperationName.AND : LogicalOperationName.OR;

			return new BinaryLogicalOperator(leftOperand, op, rightOperand);
		}
	}

	private void traverseTypeHierarchy(AbstractType<?> type, Select select) throws QueryBuildException {
		// ADE types are often defined as subtype of a CityGML parent type and stored in their
		// own child table. The join of a property type might however point to the table of the
		// CityGML parent. In this case, the table context must be corrected to point to the 
		// child table of the ADE type rather than to the parent table. This is done by traversing
		// up the inheritance relations and adding reversed joins.
		String parentTable = currentTable.getName();

		while (type != null) {
			if (type.isSetExtension()) {
				AbstractExtension<?> extension = type.getExtension();				
				if (extension.isSetJoin()) {
					Join join = type.getExtension().getJoin();
					addJoin(select, new Join(type.getTable(), join.getToColumn(), join.getFromColumn(), TableRole.PARENT), false);
					if (join.getTable().equals(parentTable))
						break;
				}

				type = extension.getBase();
			} else 
				type = null;
		}
	}
	
	private void addJoin(Select select, AbstractJoin abstractJoin, boolean useLeftJoins) throws QueryBuildException {
		if (abstractJoin instanceof Join) {
			Join join = (Join)abstractJoin;
			String toTable = resolveTableToken(join.getTable(), currentTable);
			addJoin(select, join.getFromColumn(), toTable, join.getToColumn(), join.getConditions(), useLeftJoins, false);
		}

		else if (abstractJoin instanceof JoinTable) {
			JoinTable joinTable = (JoinTable)abstractJoin;
			Table fromTable = currentTable;

			// join intermediate table
			Join join = joinTable.getJoin();
			addJoin(select, join.getToColumn(), joinTable.getTable(), join.getFromColumn(), join.getConditions(), useLeftJoins, true);

			// join target table
			Join inverseJoin = joinTable.getInverseJoin();
			String toTable = resolveTableToken(inverseJoin.getTable(), fromTable);
			addJoin(select, inverseJoin.getFromColumn(), toTable, inverseJoin.getToColumn(), inverseJoin.getConditions(), useLeftJoins, true);
		}

		else if (abstractJoin instanceof ReverseJoin) {
			ListIterator<org.citydb.sqlbuilder.select.join.Join> iter = select.getJoins().listIterator(select.getJoins().size());
			while (iter.hasPrevious()) {
				org.citydb.sqlbuilder.select.join.Join candidate = iter.previous();
				if (candidate.getToColumn().getTable() == currentTable) {
					currentTable = candidate.getFromColumn().getTable();
					tableContext.put(currentTable.getName(), currentTable);
					return;
				}
			}

			throw new QueryBuildException("Failed to find a reverse join for table '" + currentTable.getName() + "'.");
		}
	}

	private void addJoin(Select select, String fromColumn, String joinTable, String toColumn, List<Condition> conditions, boolean useLeftJoin, boolean force) throws QueryBuildException {
		// check whether we already have joined the target table
		if (!force && !currentTable.getName().equals(joinTable)) {
			Table toTable = tableContext.get(joinTable);
			if (toTable != null) {
				currentTable = toTable;
				return;
			}
		}

		Table toTable = new Table(joinTable, schemaName, aliasGenerator);
		org.citydb.sqlbuilder.select.join.Join join = new org.citydb.sqlbuilder.select.join.Join(
				useLeftJoin ? JoinName.LEFT_JOIN : JoinName.INNER_JOIN,
				toTable, toColumn, ComparisonName.EQUAL_TO, currentTable.getColumn(fromColumn));

		if (conditions != null) {
			for (Condition condition : conditions) {
				String value = condition.getValue();

				// resolve token in condition statement
				if (value.equals(MappingConstants.TARGET_OBJECTCLASS_ID_TOKEN)) {
					if (currentNode.child() == null)
						continue;

					AbstractPathElement target = currentNode.child().getPathElement();
					if (!PathElementType.OBJECT_TYPES.contains(target.getElementType()))
						throw new QueryBuildException("Failed to replace '" + MappingConstants.TARGET_OBJECTCLASS_ID_TOKEN + "' token due to missing target object type.");

					AbstractObjectType<?> objectType = (AbstractObjectType<?>)target;

					// we skip this condition if the table is not shared by multiple object types
					if (!objectType.hasSharedTable(true))
						continue;

					if (!objectType.isAbstract())
						value = String.valueOf(objectType.getObjectClassId());
					else {
						// we can skip the condition if the next element in the schema path is
						// the abstract object type that is the target of the property type
						if (PathElementType.TYPE_PROPERTIES.contains(currentNode.getPathElement().getElementType())) {
							AbstractTypeProperty<?> property = (AbstractTypeProperty<?>)currentNode.getPathElement();
							if (property.getType() == objectType)
								continue;
						}

						// otherwise create a literal list for an in operator
						List<? extends AbstractObjectType<?>> subTypes = objectType.listSubTypes(true);
						int[] ids = new int[subTypes.size()];
						for (int i = 0; i < subTypes.size(); i++)
							ids[i] = subTypes.get(i).getObjectClassId();

						join.addCondition(ComparisonFactory.in(toTable.getColumn(condition.getColumn()), new LiteralList(ids)));
						continue;
					}
				}

				else if (value.equals(MappingConstants.TARGET_ID_TOKEN)) {
					join.addCondition(ComparisonFactory.equalTo(toTable.getColumn(condition.getColumn()), toTable.getColumn(MappingConstants.ID)));
					continue;
				}

				AbstractSQLLiteral<?> literal = convertToSQLLiteral(value, condition.getType());
				join.addCondition(ComparisonFactory.equalTo(toTable.getColumn(condition.getColumn()), literal));
			}
		}

		select.addJoin(join);

		// update tableContext and current fromTable pointer
		tableContext.put(toTable.getName(), toTable);
		currentTable = toTable;
	}

	private void updateQueryContext(SQLQueryContext queryContext, Table toTable, SchemaPath schemaPath) {
		AbstractNode<?> tail = schemaPath.getLastNode();

		// copy results to query context
		queryContext.setToTable(toTable);

		if (tail.getPathElement().getElementType() == PathElementType.SIMPLE_ATTRIBUTE)
			queryContext.setTargetColumn(toTable.getColumn(((SimpleAttribute)tail.getPathElement()).getColumn()));
		else if (tail.getPathElement().getElementType() == PathElementType.GEOMETRY_PROPERTY) {
			GeometryProperty geometryProperty = (GeometryProperty)tail.getPathElement();
			queryContext.setTargetColumn(toTable.getColumn(geometryProperty.isSetRefColumn() ? geometryProperty.getRefColumn() : geometryProperty.getInlineColumn()));
		}
	}
	
	AbstractSQLLiteral<?> convertToSQLLiteral(String value, SimpleType type) throws QueryBuildException {
		AbstractSQLLiteral<?> literal = null;

		switch (type) {
			case INTEGER:
				try {
					literal = new IntegerLiteral(Integer.parseInt(value));
				} catch (NumberFormatException e) {
					throw new QueryBuildException("Failed to convert '" + value + "' to an integer literal.", e);
				}
				break;
			case DOUBLE:
				try {
					literal = new DoubleLiteral(Double.parseDouble(value));
				} catch (NumberFormatException e) {
					throw new QueryBuildException("Failed to convert '" + value + "' to a double literal.", e);
				}
				break;
			case STRING:
				literal = new StringLiteral(value);
				break;
			case BOOLEAN:
			case DATE:
			case TIMESTAMP:
			case CLOB:
				throw new QueryBuildException(type + " values are not supported as join conditions.");
		}
		
		return literal;
	}

	private String resolveTableToken(String toTable, Table fromTable) {
		if (toTable.equals(MappingConstants.TARGET_TABLE_TOKEN)) {
			if (currentNode.child() != null) {	
				AbstractPathElement target = currentNode.child().getPathElement();
				switch (target.getElementType()) {
				case FEATURE_TYPE:
				case OBJECT_TYPE:
				case COMPLEX_TYPE:
					AbstractType<?> type = (AbstractType<?>)target;
					toTable = type.isSetTable() ? type.getTable() : fromTable.getName();
					break;
				case COMPLEX_ATTRIBUTE:
				case SIMPLE_ATTRIBUTE:
				case FEATURE_PROPERTY:
				case OBJECT_PROPERTY:
				case COMPLEX_PROPERTY:
				case IMPLICIT_GEOMETRY_PROPERTY:
				case GEOMETRY_PROPERTY:
					toTable = fromTable.getName();
					break;
				}
			} else
				toTable = fromTable.getName();
		}

		return toTable;
	}

}
