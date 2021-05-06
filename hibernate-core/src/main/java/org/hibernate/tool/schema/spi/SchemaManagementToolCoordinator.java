/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tool.schema.spi;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.hibernate.Internal;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.config.spi.StandardConverters;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.DefaultSchemaFilter;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl;
import org.hibernate.tool.schema.internal.Helper;

import org.jboss.logging.Logger;

import static org.hibernate.cfg.AvailableSettings.HBM2DDL_AUTO;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_CREATE_SCRIPT_SOURCE;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_CREATE_SOURCE;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_DATABASE_ACTION;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_DROP_SCRIPT_SOURCE;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_DROP_SOURCE;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_SCRIPTS_ACTION;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_SCRIPTS_CREATE_TARGET;
import static org.hibernate.cfg.AvailableSettings.HBM2DDL_SCRIPTS_DROP_TARGET;

/**
 * Responsible for coordinating SchemaManagementTool execution(s) for auto-tooling whether
 * from JPA or hbm2ddl.auto.
 * <p/>
 * The main entry point is {@link #process}
 *
 * @author Steve Ebersole
 */
public class SchemaManagementToolCoordinator {
	private static final Logger log = Logger.getLogger( SchemaManagementToolCoordinator.class );

	public static void process(
			final Metadata metadata,
			final ServiceRegistry serviceRegistry,
			final Map<?,?> configurationValues,
			DelayedDropRegistry delayedDropRegistry) {
		final Set<ActionGrouping> groupings = ActionGrouping.interpret( metadata, configurationValues );

		if ( groupings.isEmpty() ) {
			// no actions specified
			log.debug( "No actions found; doing nothing" );
			return;
		}

		Map<Action,Set<String>> databaseActionMap = null;
		Map<Action,Set<String>> scriptActionMap = null;

		for ( ActionGrouping grouping : groupings ) {
			// for database action
			if ( grouping.databaseAction != Action.NONE ) {
				final Set<String> contributors;
				if ( databaseActionMap == null ) {
					databaseActionMap = new HashMap<>();
					contributors = new HashSet<>();
					databaseActionMap.put( grouping.databaseAction, contributors );
				}
				else {
					contributors = databaseActionMap.computeIfAbsent(
							grouping.databaseAction,
							action -> new HashSet<>()
					);
				}
				contributors.add( grouping.contributor );
			}

			// for script action
			if ( grouping.scriptAction != Action.NONE ) {
				final Set<String> contributors;
				if ( scriptActionMap == null ) {
					scriptActionMap = new HashMap<>();
					contributors = new HashSet<>();
					scriptActionMap.put( grouping.scriptAction, contributors );
				}
				else {
					contributors = scriptActionMap.computeIfAbsent(
							grouping.scriptAction,
							action -> new HashSet<>()
					);
				}
				contributors.add( grouping.contributor );
			}
		}


		final SchemaManagementTool tool = serviceRegistry.getService( SchemaManagementTool.class );
		final ConfigurationService configService = serviceRegistry.getService( ConfigurationService.class );

		final boolean haltOnError = configService.getSetting(
				AvailableSettings.HBM2DDL_HALT_ON_ERROR,
				StandardConverters.BOOLEAN,
				false
		);
		final ExceptionHandler exceptionHandler = haltOnError ? ExceptionHandlerHaltImpl.INSTANCE : ExceptionHandlerLoggedImpl.INSTANCE;

		final ExecutionOptions executionOptions = buildExecutionOptions(
				configurationValues,
				exceptionHandler
		);

		if ( scriptActionMap != null ) {
			scriptActionMap.forEach(
					(action, contributors) -> {
						performScriptAction( action, metadata, tool, serviceRegistry, executionOptions );
					}
			);
		}

		if ( databaseActionMap != null ) {
			databaseActionMap.forEach(
					(action, contributors) -> {

						performDatabaseAction(
								action,
								metadata,
								tool,
								serviceRegistry,
								executionOptions,
								(exportable) -> contributors.contains( exportable.getContributor() )
						);

						if ( action == Action.CREATE_DROP ) {
							delayedDropRegistry.registerOnCloseAction(
									tool.getSchemaDropper( configurationValues ).buildDelayedAction(
											metadata,
											executionOptions,
											(exportable) -> contributors.contains( exportable.getContributor() ),
											buildDatabaseTargetDescriptor(
													configurationValues,
													DropSettingSelector.INSTANCE,
													serviceRegistry
											)
									)
							);
						}
					}
			);
		}
	}

	public static ExecutionOptions buildExecutionOptions(
			final Map<?,?> configurationValues,
			final ExceptionHandler exceptionHandler) {
		return buildExecutionOptions(
				configurationValues,
				DefaultSchemaFilter.INSTANCE,
				exceptionHandler
		);
	}

	public static ExecutionOptions buildExecutionOptions(
			final Map<?,?> configurationValues,
			final SchemaFilter schemaFilter,
			final ExceptionHandler exceptionHandler) {
		return new ExecutionOptions() {
			@Override
			public boolean shouldManageNamespaces() {
				return Helper.interpretNamespaceHandling( configurationValues );
			}

			@Override
			public Map<?,?> getConfigurationValues() {
				return configurationValues;
			}

			@Override
			public ExceptionHandler getExceptionHandler() {
				return exceptionHandler;
			}

			@Override
			public SchemaFilter getSchemaFilter() {
				return schemaFilter;
			}
		};
	}

	private static void performDatabaseAction(
			final Action action,
			Metadata metadata,
			SchemaManagementTool tool,
			ServiceRegistry serviceRegistry,
			final ExecutionOptions executionOptions,
			ContributableMatcher contributableInclusionFilter) {

		// IMPL NOTE : JPA binds source and target info..

		switch ( action ) {
			case CREATE_ONLY: {
				//
				final JpaTargetAndSourceDescriptor createDescriptor = buildDatabaseTargetDescriptor(
						executionOptions.getConfigurationValues(),
						CreateSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaCreator( executionOptions.getConfigurationValues() ).doCreation(
						metadata,
						executionOptions,
						contributableInclusionFilter,
						createDescriptor,
						createDescriptor
				);
				break;
			}
			case CREATE:
			case CREATE_DROP: {
				final JpaTargetAndSourceDescriptor dropDescriptor = buildDatabaseTargetDescriptor(
						executionOptions.getConfigurationValues(),
						DropSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaDropper( executionOptions.getConfigurationValues() ).doDrop(
						metadata,
						executionOptions,
						contributableInclusionFilter,
						dropDescriptor,
						dropDescriptor
				);
				final JpaTargetAndSourceDescriptor createDescriptor = buildDatabaseTargetDescriptor(
						executionOptions.getConfigurationValues(),
						CreateSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaCreator( executionOptions.getConfigurationValues() ).doCreation(
						metadata,
						executionOptions,
						contributableInclusionFilter,
						createDescriptor,
						createDescriptor
				);
				break;
			}
			case DROP: {
				final JpaTargetAndSourceDescriptor dropDescriptor = buildDatabaseTargetDescriptor(
						executionOptions.getConfigurationValues(),
						DropSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaDropper( executionOptions.getConfigurationValues() ).doDrop(
						metadata,
						executionOptions,
						contributableInclusionFilter,
						dropDescriptor,
						dropDescriptor
				);
				break;
			}
			case UPDATE: {
				final JpaTargetAndSourceDescriptor migrateDescriptor = buildDatabaseTargetDescriptor(
						executionOptions.getConfigurationValues(),
						MigrateSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaMigrator( executionOptions.getConfigurationValues() ).doMigration(
						metadata,
						executionOptions,
						contributableInclusionFilter,
						migrateDescriptor
				);
				break;
			}
			case VALIDATE: {
				tool.getSchemaValidator( executionOptions.getConfigurationValues() ).doValidation(
						metadata,
						executionOptions,
						contributableInclusionFilter
				);
				break;
			}
		}
	}

	private static JpaTargetAndSourceDescriptor buildDatabaseTargetDescriptor(
			Map<?,?> configurationValues,
			SettingSelector settingSelector,
			ServiceRegistry serviceRegistry) {
		final Object scriptSourceSetting = settingSelector.getScriptSourceSetting( configurationValues );
		final SourceType sourceType = SourceType.interpret(
				settingSelector.getSourceTypeSetting( configurationValues ),
				scriptSourceSetting != null ? SourceType.SCRIPT : SourceType.METADATA
		);

		final boolean includesScripts = sourceType != SourceType.METADATA;
		if ( includesScripts && scriptSourceSetting == null ) {
			throw new SchemaManagementException(
					"Schema generation configuration indicated to include CREATE scripts, but no script was specified"
			);
		}

		final ScriptSourceInput scriptSourceInput = includesScripts
				? Helper.interpretScriptSourceSetting(
						scriptSourceSetting,
						serviceRegistry.getService( ClassLoaderService.class ),
						(String) configurationValues.get( AvailableSettings.HBM2DDL_CHARSET_NAME )
				)
				: null;

		return new JpaTargetAndSourceDescriptor() {
			@Override
			public EnumSet<TargetType> getTargetTypes() {
				return EnumSet.of( TargetType.DATABASE );
			}

			@Override
			public ScriptTargetOutput getScriptTargetOutput() {
				return null;
			}

			@Override
			public SourceType getSourceType() {
				return sourceType;
			}

			@Override
			public ScriptSourceInput getScriptSourceInput() {
				return scriptSourceInput;
			}
		};
	}

	private static void performScriptAction(
			Action scriptAction,
			Metadata metadata,
			SchemaManagementTool tool,
			ServiceRegistry serviceRegistry,
			ExecutionOptions executionOptions) {
		switch ( scriptAction ) {
			case CREATE_ONLY: {
				final JpaTargetAndSourceDescriptor createDescriptor = buildScriptTargetDescriptor(
						executionOptions.getConfigurationValues(),
						CreateSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaCreator( executionOptions.getConfigurationValues() ).doCreation(
						metadata,
						executionOptions,
						(contributed) -> true,
						createDescriptor,
						createDescriptor
				);
				break;
			}
			case CREATE:
			case CREATE_DROP: {
				final JpaTargetAndSourceDescriptor dropDescriptor = buildScriptTargetDescriptor(
						executionOptions.getConfigurationValues(),
						DropSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaDropper( executionOptions.getConfigurationValues() ).doDrop(
						metadata,
						executionOptions,
						(contributed) -> true,
						dropDescriptor,
						dropDescriptor
				);
				final JpaTargetAndSourceDescriptor createDescriptor = buildScriptTargetDescriptor(
						executionOptions.getConfigurationValues(),
						CreateSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaCreator( executionOptions.getConfigurationValues() ).doCreation(
						metadata,
						executionOptions,
						(contributed) -> true,
						createDescriptor,
						createDescriptor
				);
				break;
			}
			case DROP: {
				final JpaTargetAndSourceDescriptor dropDescriptor = buildScriptTargetDescriptor(
						executionOptions.getConfigurationValues(),
						DropSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaDropper( executionOptions.getConfigurationValues() ).doDrop(
						metadata,
						executionOptions,
						(contributed) -> true,
						dropDescriptor,
						dropDescriptor
				);
				break;
			}
			case UPDATE: {
				final JpaTargetAndSourceDescriptor migrateDescriptor = buildScriptTargetDescriptor(
						executionOptions.getConfigurationValues(),
						MigrateSettingSelector.INSTANCE,
						serviceRegistry
				);
				tool.getSchemaMigrator( executionOptions.getConfigurationValues() ).doMigration(
						metadata,
						executionOptions,
						(contributed) -> true,
						migrateDescriptor
				);
				break;
			}
			case VALIDATE: {
				throw new SchemaManagementException( "VALIDATE is not valid SchemaManagementTool action for script output" );
			}
		}
	}

	private static JpaTargetAndSourceDescriptor buildScriptTargetDescriptor(
			Map<?,?> configurationValues,
			SettingSelector settingSelector,
			ServiceRegistry serviceRegistry) {
		final Object scriptSourceSetting = settingSelector.getScriptSourceSetting( configurationValues );
		final SourceType sourceType = SourceType.interpret(
				settingSelector.getSourceTypeSetting( configurationValues ),
				scriptSourceSetting != null ? SourceType.SCRIPT : SourceType.METADATA
		);

		final boolean includesScripts = sourceType != SourceType.METADATA;
		if ( includesScripts && scriptSourceSetting == null ) {
			throw new SchemaManagementException(
					"Schema generation configuration indicated to include CREATE scripts, but no script was specified"
			);
		}

		String charsetName = (String) configurationValues.get( AvailableSettings.HBM2DDL_CHARSET_NAME );

		final ScriptSourceInput scriptSourceInput = includesScripts
				? Helper.interpretScriptSourceSetting( scriptSourceSetting, serviceRegistry.getService( ClassLoaderService.class ), charsetName )
				: null;

		final ScriptTargetOutput scriptTargetOutput = Helper.interpretScriptTargetSetting(
				settingSelector.getScriptTargetSetting( configurationValues ),
				serviceRegistry.getService( ClassLoaderService.class ),
				charsetName
		);

		return new JpaTargetAndSourceDescriptor() {
			@Override
			public EnumSet<TargetType> getTargetTypes() {
				return EnumSet.of( TargetType.SCRIPT );
			}

			@Override
			public ScriptTargetOutput getScriptTargetOutput() {
				return scriptTargetOutput;
			}

			@Override
			public SourceType getSourceType() {
				return sourceType;
			}

			@Override
			public ScriptSourceInput getScriptSourceInput() {
				return scriptSourceInput;
			}
		};
	}


	private interface SettingSelector {
		Object getSourceTypeSetting(Map<?,?> configurationValues);
		Object getScriptSourceSetting(Map<?,?> configurationValues);
		Object getScriptTargetSetting(Map<?,?> configurationValues);
	}

	private static class CreateSettingSelector implements SettingSelector {
		/**
		 * Singleton access
		 */
		public static final CreateSettingSelector INSTANCE = new CreateSettingSelector();

		@Override
		public Object getSourceTypeSetting(Map<?,?> configurationValues) {
			return configurationValues.get( HBM2DDL_CREATE_SOURCE );
		}

		@Override
		public Object getScriptSourceSetting(Map<?,?> configurationValues) {
			return configurationValues.get( HBM2DDL_CREATE_SCRIPT_SOURCE );
		}

		@Override
		public Object getScriptTargetSetting(Map<?,?> configurationValues) {
			return configurationValues.get( HBM2DDL_SCRIPTS_CREATE_TARGET );
		}
	}

	private static class DropSettingSelector implements SettingSelector {
		/**
		 * Singleton access
		 */
		public static final DropSettingSelector INSTANCE = new DropSettingSelector();

		@Override
		public Object getSourceTypeSetting(Map<?,?> configurationValues) {
			return configurationValues.get( HBM2DDL_DROP_SOURCE );
		}

		@Override
		public Object getScriptSourceSetting(Map<?,?> configurationValues) {
			return configurationValues.get( HBM2DDL_DROP_SCRIPT_SOURCE );
		}

		@Override
		public Object getScriptTargetSetting(Map<?,?> configurationValues) {
			return configurationValues.get( HBM2DDL_SCRIPTS_DROP_TARGET );
		}
	}

	private static class MigrateSettingSelector implements SettingSelector {
		/**
		 * Singleton access
		 */
		public static final MigrateSettingSelector INSTANCE = new MigrateSettingSelector();

		// todo : should this define new migrator-specific settings?
		// for now we reuse the CREATE settings where applicable

		@Override
		public Object getSourceTypeSetting(Map<?,?> configurationValues) {
			// for now, don't allow script source
			return SourceType.METADATA;
		}

		@Override
		public Object getScriptSourceSetting(Map<?,?> configurationValues) {
			// for now, don't allow script source
			return null;
		}

		@Override
		public Object getScriptTargetSetting(Map<?,?> configurationValues) {
			// for now, reuse the CREATE script target setting
			return configurationValues.get( HBM2DDL_SCRIPTS_CREATE_TARGET );
		}
	}

	/**
	 * For JPA-style schema-gen, database and script target handing are configured
	 * individually - this tuple allows interpreting the the action for both targets
	 * simultaneously
	 */
	public static class ActionGrouping {
		private final String contributor;
		private final Action databaseAction;
		private final Action scriptAction;

		public ActionGrouping(String contributor, Action databaseAction, Action scriptAction) {
			this.contributor = contributor;
			this.databaseAction = databaseAction;
			this.scriptAction = scriptAction;
		}

		public String getContributor() {
			return contributor;
		}

		public Action getDatabaseAction() {
			return databaseAction;
		}

		public Action getScriptAction() {
			return scriptAction;
		}

		/**
		 * For test use
		 */
		@Internal
		public static ActionGrouping interpret(Map configurationValues) {
			// interpret the JPA settings first
			Action databaseAction = Action.interpretJpaSetting( configurationValues.get( HBM2DDL_DATABASE_ACTION ) );
			Action scriptAction = Action.interpretJpaSetting( configurationValues.get( HBM2DDL_SCRIPTS_ACTION ) );

			// if no JPA settings were specified, look at the legacy HBM2DDL_AUTO setting...
			if ( databaseAction == Action.NONE && scriptAction == Action.NONE ) {
				final Action hbm2ddlAutoAction = Action.interpretHbm2ddlSetting( configurationValues.get( HBM2DDL_AUTO ) );
				if ( hbm2ddlAutoAction != Action.NONE ) {
					databaseAction = hbm2ddlAutoAction;
				}
			}

			return new ActionGrouping( "orm", databaseAction, scriptAction );
		}

		public static Set<ActionGrouping> interpret(Metadata metadata, Map<?,?> configurationValues) {
			// these represent the base (non-contributor-specific) values
			final Action rootDatabaseAction = Action.interpretJpaSetting( configurationValues.get( HBM2DDL_DATABASE_ACTION ) );
			final Action rootScriptAction = Action.interpretJpaSetting( configurationValues.get( HBM2DDL_SCRIPTS_ACTION ) );
			final Action rootExportAction = Action.interpretHbm2ddlSetting( configurationValues.get( HBM2DDL_AUTO ) );

			final Set<String> contributors = metadata.getContributors();
			final Set<ActionGrouping> groupings = new HashSet<>( contributors.size() );

			// for each contributor, look for specific tooling config values
			for ( String contributor : contributors ) {
				final Object contributorDatabaseActionSetting = configurationValues.get( HBM2DDL_DATABASE_ACTION + "." + contributor );
				final Object contributorScriptActionSetting = configurationValues.get( HBM2DDL_SCRIPTS_ACTION + "." + contributor );
				final Object contributorExportActionSetting = configurationValues.get( HBM2DDL_AUTO + "." + contributor );

				final Action contributorDatabaseAction = contributorDatabaseActionSetting == null
						? rootDatabaseAction
						: Action.interpretJpaSetting( contributorDatabaseActionSetting );
				final Action contributorScriptAction = contributorScriptActionSetting == null
						? rootScriptAction
						: Action.interpretJpaSetting( contributorScriptActionSetting );
				final Action contributorExportAction = contributorExportActionSetting == null
						? rootExportAction
						: Action.interpretJpaSetting( contributorExportActionSetting );

				Action databaseAction = contributorDatabaseAction;
				if ( databaseAction == Action.NONE && contributorScriptAction == Action.NONE ) {
					if ( contributorExportAction != Action.NONE ) {
						databaseAction = contributorExportAction;
					}

					if ( databaseAction == Action.NONE ) {
						log.debugf( "No schema actions specified for contributor `%s`; doing nothing", contributor );
						continue;
					}
				}

				groupings.add( new ActionGrouping( contributor, databaseAction, contributorScriptAction ) );
			}

			return groupings;
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}
			ActionGrouping that = (ActionGrouping) o;
			return contributor.equals( that.contributor ) &&
					databaseAction == that.databaseAction &&
					scriptAction == that.scriptAction;
		}

		@Override
		public int hashCode() {
			return Objects.hash( contributor );
		}
	}
}
