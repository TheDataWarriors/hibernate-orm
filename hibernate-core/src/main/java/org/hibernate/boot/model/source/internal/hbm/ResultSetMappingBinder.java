/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.model.source.internal.hbm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.boot.MappingException;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryCollectionLoadReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryJoinReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryPropertyReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryReturnType;
import org.hibernate.boot.jaxb.hbm.spi.JaxbHbmNativeQueryScalarReturnType;
import org.hibernate.boot.jaxb.hbm.spi.NativeQueryNonScalarRootReturn;
import org.hibernate.boot.jaxb.hbm.spi.ResultSetMappingBindingDefinition;
import org.hibernate.boot.spi.HbmResultSetMappingDefinition;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryCollectionReturn;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryJoinReturn;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryReturn;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryRootReturn;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryScalarReturn;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.ToOne;
import org.hibernate.mapping.Value;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.type.Type;

/**
 * Handles consuming a top-level SQL result-set mapping defined in `hbm.xnl`
 *
 * @author Steve Ebersole
 * @author Emmanuel Bernard
 */
public abstract class ResultSetMappingBinder {
	/**
	 * Create the mapping definition and register it
	 *
	 * @param resultSetMappingSource The XML data as a JAXB binding
	 * @param context The mapping state
	 *
	 * @return The ResultSet mapping descriptor
	 */
	public static HbmResultSetMappingDefinition bind(
			ResultSetMappingBindingDefinition resultSetMappingSource,
			HbmLocalMetadataBuildingContext context) {
		if ( resultSetMappingSource.getName() == null ) {
			throw new MappingException( "ResultSet mapping did not specify name", context.getOrigin() );
		}

		final HbmResultSetMappingDefinition.Builder builder = new HbmResultSetMappingDefinition.Builder( resultSetMappingSource.getName() );

		for ( Object valueMappingSource : resultSetMappingSource.getValueMappingSources() ) {
			if ( valueMappingSource instanceof JaxbHbmNativeQueryReturnType ) {
				builder.addReturn( (JaxbHbmNativeQueryReturnType) valueMappingSource );
			}
			else if ( valueMappingSource instanceof JaxbHbmNativeQueryCollectionLoadReturnType ) {
				builder.addReturn( (JaxbHbmNativeQueryCollectionLoadReturnType) valueMappingSource );
			}
			else if ( valueMappingSource instanceof JaxbHbmNativeQueryJoinReturnType ) {
				builder.addReturn( (JaxbHbmNativeQueryJoinReturnType) valueMappingSource );
			}
			else if ( valueMappingSource instanceof JaxbHbmNativeQueryScalarReturnType ) {
				builder.addReturn( (JaxbHbmNativeQueryScalarReturnType) valueMappingSource );
			}
		}

		final HbmResultSetMappingDefinition mappingDefinition = builder.build( context );
		context.getMetadataCollector().addResultSetMapping( mappingDefinition );
		return mappingDefinition;
	}

	// todo (6.0) : cannibalize this into the builder

	public static NativeSQLQueryScalarReturn extractReturnDescription(
			JaxbHbmNativeQueryScalarReturnType rtnSource,
			HbmLocalMetadataBuildingContext context) {
		final String column = rtnSource.getColumn();
		final String typeName = rtnSource.getType();
		Type type = null;
		if ( typeName != null ) {
			type = context.getMetadataCollector().getTypeConfiguration().getBasicTypeRegistry().getRegisteredType( typeName );
			if ( type == null ) {
				throw new MappingException(
						String.format( "Unable to resolve type [%s] specified for native query scalar return", typeName ),
						context.getOrigin()
				);
			}
		}
		return new NativeSQLQueryScalarReturn( column, (BasicDomainType) type );
	}


	public static NativeSQLQueryRootReturn extractReturnDescription(
			JaxbHbmNativeQueryReturnType rtnSource,
			HbmLocalMetadataBuildingContext context,
			int queryReturnPosition) {
		String alias = rtnSource.getAlias();
		if ( StringHelper.isEmpty( alias ) ) {
			// hack-around as sqlquery impl depend on having a key.
			alias = "alias_" + queryReturnPosition;
		}
		final String entityName = context.determineEntityName(
				rtnSource.getEntityName(),
				rtnSource.getClazz()
		);
		final PersistentClass pc = context.getMetadataCollector().getEntityBinding( entityName );

		return new NativeSQLQueryRootReturn(
				alias,
				entityName,
				extractPropertyResults( alias, rtnSource, pc, context ),
				rtnSource.getLockMode()
		);
	}

	public static NativeSQLQueryJoinReturn extractReturnDescription(
			JaxbHbmNativeQueryJoinReturnType rtnSource,
			HbmLocalMetadataBuildingContext context,
			int queryReturnPosition) {
		final int dot = rtnSource.getProperty().lastIndexOf( '.' );
		if ( dot == -1 ) {
			throw new MappingException(
					String.format(
							Locale.ENGLISH,
							"Role attribute for sql query return [%s] not formatted correctly {owningAlias.propertyName}",
							rtnSource.getAlias()
					),
					context.getOrigin()
			);
		}

		String roleOwnerAlias = rtnSource.getProperty().substring( 0, dot );
		String roleProperty = rtnSource.getProperty().substring( dot + 1 );

		return new NativeSQLQueryJoinReturn(
				rtnSource.getAlias(),
				roleOwnerAlias,
				roleProperty,
				//FIXME: get the PersistentClass
				extractPropertyResults( rtnSource.getAlias(), rtnSource, null, context ),
				rtnSource.getLockMode()
		);
	}

	public static NativeSQLQueryReturn extractReturnDescription(
			JaxbHbmNativeQueryCollectionLoadReturnType rtnSource,
			HbmLocalMetadataBuildingContext context,
			int queryReturnPosition) {
		final int dot = rtnSource.getRole().lastIndexOf( '.' );
		if ( dot == -1 ) {
			throw new MappingException(
					String.format(
							Locale.ENGLISH,
							"Collection attribute for sql query return [%s] not formatted correctly {OwnerClassName.propertyName}",
							rtnSource.getAlias()
					),
					context.getOrigin()
			);
		}

		String ownerClassName = context.findEntityBinding( null, rtnSource.getRole().substring( 0, dot ) )
				.getClassName();
		String ownerPropertyName = rtnSource.getRole().substring( dot + 1 );

		return new NativeSQLQueryCollectionReturn(
				rtnSource.getAlias(),
				ownerClassName,
				ownerPropertyName,
				// FIXME: get the PersistentClass
				extractPropertyResults( rtnSource.getAlias(), rtnSource, null, context ),
				rtnSource.getLockMode()
		);
	}

	/**
	 * return-property extraction/binding specific to an entity return to account for
	 * discriminator.
	 *
	 * @param alias The return alias
	 * @param rtnSource The entity return jaxb binding
	 * @param pc The located PersistentClass
	 * @param context The hbm context
	 *
	 * @return The extracted property mappings
	 */
	private static Map<String, String[]> extractPropertyResults(
			String alias,
			JaxbHbmNativeQueryReturnType rtnSource,
			PersistentClass pc,
			HbmLocalMetadataBuildingContext context) {
		Map<String, String[]> results = extractPropertyResults(
				alias,
				(NativeQueryNonScalarRootReturn) rtnSource,
				pc,
				context
		);

		if ( rtnSource.getReturnDiscriminator() != null ) {
			if ( results == null ) {
				results = new HashMap<>();
			}

			final String column = rtnSource.getReturnDiscriminator().getColumn();
			if ( column == null ) {
				throw new MappingException(
						String.format(
								Locale.ENGLISH,
								"return-discriminator [%s (%s)] did not specify column",
								pc.getEntityName(),
								alias
						),
						context.getOrigin()
				);
			}

			results.put( "class", new String[] {column} );
		}

		return results;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String[]> extractPropertyResults(
			String alias,
			NativeQueryNonScalarRootReturn rtnSource,
			PersistentClass pc,
			HbmLocalMetadataBuildingContext context) {
		if ( CollectionHelper.isEmpty( rtnSource.getReturnProperty() ) ) {
			return null;
		}

		final HashMap results = new HashMap();

		List<JaxbHbmNativeQueryPropertyReturnType> propertyReturnSources = new ArrayList<>();
		List<String> propertyNames = new ArrayList<>();


		for ( JaxbHbmNativeQueryPropertyReturnType propertyReturnSource : rtnSource.getReturnProperty() ) {
			final int dotPosition = propertyReturnSource.getName().lastIndexOf( '.' );
			if ( pc == null || dotPosition == -1 ) {
				propertyReturnSources.add( propertyReturnSource );
				propertyNames.add( propertyReturnSource.getName() );
			}
			else {
				final String reducedName = propertyReturnSource.getName().substring( 0, dotPosition );
				final Value value = pc.getRecursiveProperty( reducedName ).getValue();

				Iterator parentPropItr;
				if ( value instanceof Component ) {
					final Component comp = (Component) value;
					parentPropItr = comp.getPropertyIterator();
				}
				else if ( value instanceof ToOne ) {
					final ToOne toOne = (ToOne) value;
					final PersistentClass referencedPc = context.getMetadataCollector()
							.getEntityBinding( toOne.getReferencedEntityName() );
					if ( toOne.getReferencedPropertyName() != null ) {
						try {
							parentPropItr = ( (Component) referencedPc.getRecursiveProperty( toOne.getReferencedPropertyName() )
									.getValue() ).getPropertyIterator();
						}
						catch (ClassCastException e) {
							throw new MappingException(
									"dotted notation reference neither a component nor a many/one to one",
									e,
									context.getOrigin()
							);
						}
					}
					else {
						try {
							if ( referencedPc.getIdentifierMapper() == null ) {
								parentPropItr = ( (Component) referencedPc.getIdentifierProperty()
										.getValue() ).getPropertyIterator();
							}
							else {
								parentPropItr = referencedPc.getIdentifierMapper().getPropertyIterator();
							}
						}
						catch (ClassCastException e) {
							throw new MappingException(
									"dotted notation reference neither a component nor a many/one to one",
									e,
									context.getOrigin()
							);
						}
					}
				}
				else {
					throw new MappingException(
							"dotted notation reference neither a component nor a many/one to one",
							context.getOrigin()
					);
				}

				boolean hasFollowers = false;
				List<String> followers = new ArrayList<>();
				while ( parentPropItr.hasNext() ) {
					final Property parentProperty = (Property) parentPropItr.next();
					final String currentPropertyName = parentProperty.getName();
					final String currentName = reducedName + '.' + currentPropertyName;
					if ( hasFollowers ) {
						followers.add( currentName );
					}
					if ( propertyReturnSource.getName().equals( currentName ) ) {
						hasFollowers = true;
					}
				}

				int index = propertyNames.size();
				for ( String follower : followers ) {
					int currentIndex = getIndexOfFirstMatchingProperty( propertyNames, follower );
					index = currentIndex != -1 && currentIndex < index ? currentIndex : index;
				}
				propertyNames.add( index, propertyReturnSource.getName() );
				propertyReturnSources.add( index, propertyReturnSource );
			}
		}

		Set<String> uniqueReturnProperty = new HashSet<>();
		for ( JaxbHbmNativeQueryPropertyReturnType propertyReturnBinding : propertyReturnSources ) {
			final String name = propertyReturnBinding.getName();
			if ( "class".equals( name ) ) {
				throw new MappingException(
						"class is not a valid property name to use in a <return-property>, use <return-discriminator> instead",
						context.getOrigin()
				);
			}
			//TODO: validate existing of property with the chosen name. (secondpass )
			ArrayList allResultColumns = extractResultColumns( propertyReturnBinding );

			if ( allResultColumns.isEmpty() ) {
				throw new MappingException(
						String.format(
								Locale.ENGLISH,
								"return-property [alias=%s, property=%s] must specify at least one column or return-column name",
								alias,
								propertyReturnBinding.getName()
						),
						context.getOrigin()
				);
			}
			if ( uniqueReturnProperty.contains( name ) ) {
				throw new MappingException(
						String.format(
								Locale.ENGLISH,
								"Duplicate return-property [alias=%s] : %s",
								alias,
								propertyReturnBinding.getName()
						),
						context.getOrigin()
				);
			}
			uniqueReturnProperty.add( name );

			// the issue here is that for <return-join/> representing an entity collection,
			// the collection element values (the property values of the associated entity)
			// are represented as 'element.{propertyname}'.  Thus the StringHelper.root()
			// here puts everything under 'element' (which additionally has significant
			// meaning).  Probably what we need to do is to something like this instead:
			//      String root = StringHelper.root( name );
			//      String key = root; // by default
			//      if ( !root.equals( name ) ) {
			//	        // we had a dot
			//          if ( !root.equals( alias ) {
			//              // the root does not apply to the specific alias
			//              if ( "elements".equals( root ) {
			//                  // we specifically have a <return-join/> representing an entity collection
			//                  // and this <return-property/> is one of that entity's properties
			//                  key = name;
			//              }
			//          }
			//      }
			// but I am not clear enough on the intended purpose of this code block, especially
			// in relation to the "Reorder properties" code block above...
			//			String key = StringHelper.root( name );
			ArrayList intermediateResults = (ArrayList) results.get( name );
			if ( intermediateResults == null ) {
				results.put( name, allResultColumns );
			}
			else {
				intermediateResults.addAll( allResultColumns );
			}
		}

		for ( Object o : results.entrySet() ) {
			Map.Entry entry = (Map.Entry) o;
			if ( entry.getValue() instanceof ArrayList ) {
				ArrayList list = (ArrayList) entry.getValue();
				entry.setValue( list.toArray( new String[ 0 ] ) );
			}
		}
		return results.isEmpty() ? Collections.EMPTY_MAP : results;
	}

	private static int getIndexOfFirstMatchingProperty(List propertyNames, String follower) {
		int propertySize = propertyNames.size();
		for ( int propIndex = 0; propIndex < propertySize; propIndex++ ) {
			if ( ( (String) propertyNames.get( propIndex ) ).startsWith( follower ) ) {
				return propIndex;
			}
		}
		return -1;
	}

	private static ArrayList<String> extractResultColumns(JaxbHbmNativeQueryPropertyReturnType propertyReturnSource) {
		final String column = unquote( propertyReturnSource.getColumn() );
		ArrayList<String> allResultColumns = new ArrayList<>();
		if ( column != null ) {
			allResultColumns.add( column );
		}
		for ( JaxbHbmNativeQueryPropertyReturnType.JaxbHbmReturnColumn returnColumnSource : propertyReturnSource.getReturnColumn() ) {
			allResultColumns.add( unquote( returnColumnSource.getName() ) );
		}
		return allResultColumns;
	}

	private static String unquote(String name) {
		if ( name != null && name.charAt( 0 ) == '`' ) {
			name = name.substring( 1, name.length() - 1 );
		}
		return name;
	}
}
