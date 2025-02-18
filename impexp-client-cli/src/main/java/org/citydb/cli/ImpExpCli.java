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

package org.citydb.cli;

import org.citydb.cli.operation.deleter.DeleteCommand;
import org.citydb.cli.operation.exporter.ExportCommand;
import org.citydb.cli.operation.importer.ImportCommand;
import org.citydb.cli.operation.validator.ValidateCommand;
import org.citydb.cli.operation.visExporter.ExportVisCommand;
import org.citydb.cli.option.CliOption;
import org.citydb.cli.option.StartupProgressListener;
import org.citydb.cli.util.CliConstants;
import org.citydb.cli.util.PidFile;
import org.citydb.config.Config;
import org.citydb.config.ConfigUtil;
import org.citydb.config.ProjectConfig;
import org.citydb.config.i18n.Language;
import org.citydb.config.project.global.LanguageType;
import org.citydb.config.project.global.LogFileMode;
import org.citydb.config.project.global.LogLevel;
import org.citydb.config.project.global.Logging;
import org.citydb.core.ade.ADEExtension;
import org.citydb.core.ade.ADEExtensionManager;
import org.citydb.core.database.schema.mapping.SchemaMapping;
import org.citydb.core.database.schema.mapping.SchemaMappingException;
import org.citydb.core.database.schema.mapping.SchemaMappingValidationException;
import org.citydb.core.database.schema.util.SchemaMappingUtil;
import org.citydb.core.plugin.CliCommand;
import org.citydb.core.plugin.Plugin;
import org.citydb.core.plugin.PluginException;
import org.citydb.core.plugin.PluginManager;
import org.citydb.core.plugin.extension.config.ConfigExtension;
import org.citydb.core.registry.ObjectRegistry;
import org.citydb.core.util.CoreConstants;
import org.citydb.core.util.InternalProxySelector;
import org.citydb.core.util.Util;
import org.citydb.util.log.Logger;
import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.ade.ADEException;
import org.citygml4j.model.citygml.ade.binding.ADEContext;
import picocli.CommandLine;

import javax.xml.bind.JAXBException;
import java.io.Console;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ProxySelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(
        name = CliConstants.CLI_NAME,
        description = "Command-line interface for the 3D City Database.",
        synopsisSubcommandLabel = "COMMAND",
        versionProvider = ImpExpCli.class,
        subcommands = {
                CommandLine.HelpCommand.class,
                ImportCommand.class,
                ExportCommand.class,
                ExportVisCommand.class,
                DeleteCommand.class,
                ValidateCommand.class
        }
)
public class ImpExpCli extends CliCommand implements CommandLine.IVersionProvider {
    @CommandLine.Option(names = {"-c", "--config"}, scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Use configuration from this file.")
    private Path configFile;

    @CommandLine.Option(names = "--log-level", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<level>",
            description = "Log level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private LogLevel logLevel = LogLevel.INFO;

    @CommandLine.Option(names = "--log-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Write log messages to this file.")
    private Path logFile;

    @CommandLine.Option(names = "--pid-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Create a file containing the current process ID.")
    private Path pidFile;

    @CommandLine.Option(names = "--plugins", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<folder>",
            description = "Load plugins from this folder.")
    private Path pluginsFolder;

    @CommandLine.Option(names = "--use-plugin", scope = CommandLine.ScopeType.INHERIT, split = ",",
            paramLabel = "<plugin[=true|false]>", mapFallbackValue = "true",
            description = "Enable or disable plugins with a matching fully qualified class name " +
                    "(default: ${MAP-FALLBACK-VALUE}).")
    private Map<String, Boolean> enabledPlugins;

    @CommandLine.Option(names = "--ade-extensions", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<folder>",
            description = "Load ADE extensions from this folder.")
    private Path adeExtensionsFolder;

    private final Logger log = Logger.getInstance();
    private final PluginManager pluginManager = PluginManager.getInstance();
    private final ADEExtensionManager adeManager = ADEExtensionManager.getInstance();
    private final Util.URLClassLoader classLoader = new Util.URLClassLoader(Thread.currentThread().getContextClassLoader());

    private StartupProgressListener progressListener;
    private String commandLineString;
    private String subCommandName;
    private int processStep;

    private String defaultCommand;
    private boolean useDefaultConfiguration;
    private boolean useDefaultLogLevel = true;
    private boolean failOnADEExceptions = true;
    private boolean failOnPluginExceptions = true;

    public static void main(String[] args) {
        int exitCode = new ImpExpCli().start(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public int start(String[] args) {
        try {
            return process(args);
        } catch (Exception e) {
            logError(e);
            return 1;
        }
    }

    public ImpExpCli withCliCommand(CliCommand command) {
        if (command != null) {
            pluginManager.registerCliCommand(command);
        }

        return this;
    }

    public ImpExpCli withPlugin(Plugin plugin) {
        if (plugin != null) {
            pluginManager.registerExternalPlugin(plugin);
        }

        return this;
    }

    public ImpExpCli withADEExtension(ADEExtension extension) {
        if (extension != null) {
            if (extension.getBasePath() == null) {
                extension.setBasePath(Paths.get(""));
            }

            adeManager.loadExtension(extension);
        }

        return this;
    }

    public ImpExpCli withStartupProgressListener(StartupProgressListener progressListener) {
        this.progressListener = progressListener;
        return this;
    }

    public ImpExpCli withDefaultCommand(String defaultCommand) {
        this.defaultCommand = defaultCommand;
        return this;
    }

    public ImpExpCli useDefaultConfiguration(boolean useDefaultConfiguration) {
        this.useDefaultConfiguration = useDefaultConfiguration;
        return this;
    }

    public ImpExpCli failOnADEExceptions(boolean failOnADEExceptions) {
        this.failOnADEExceptions = failOnADEExceptions;
        return this;
    }

    public ImpExpCli failOnPluginExceptions(boolean failOnPluginExceptions) {
        this.failOnPluginExceptions = failOnPluginExceptions;
        return this;
    }

    public Path getConfigFile() {
        return configFile;
    }

    private int process(String[] args) throws Exception {
        CommandLine cmd = new CommandLine(this);

        try {
            // load CLI commands from plugins
            loadClasses(getPluginsFolder(args), classLoader);
            pluginManager.loadCliCommands(classLoader);
            for (CliCommand command : pluginManager.getCliCommands()) {
                cmd.addSubcommand(command);
            }
        } catch (IOException e) {
            throw new ImpExpException("Failed to initialize CLI commands from plugins.", e);
        }

        cmd.setCaseInsensitiveEnumValuesAllowed(true)
                .setAbbreviatedOptionsAllowed(true)
                .setExecutionStrategy(new CommandLine.RunAll());

        if (defaultCommand != null) {
            args = addDefaultCommand(cmd, args);
        }

        try {
            CommandLine.ParseResult parseResult = cmd.parseArgs(args);
            List<CommandLine> commandLines = parseResult.asCommandLineList();

            // check for help options
            for (CommandLine commandLine : commandLines) {
                if (commandLine.isUsageHelpRequested() || commandLine.isVersionHelpRequested()) {
                    return CommandLine.executeHelpRequest(parseResult);
                }
            }

            // check for subcommand
            if (!parseResult.hasSubcommand()) {
                throw new CommandLine.ParameterException(cmd, "Missing required subcommand.");
            }

            for (CommandLine commandLine : commandLines) {
                CommandLine.ParseResult subParseResult = commandLine.getParseResult();

                // check for user-defined log level
                if (subParseResult.hasMatchedOption("--log-level")) {
                    useDefaultLogLevel = false;
                }

                // read password from keyboard
                CommandLine.Model.OptionSpec passwordOption = subParseResult.matchedOption("-p");
                if (passwordOption != null && passwordOption.getValue().equals("")) {
                    passwordOption.setValue(readPassword(subParseResult));
                }

                // preprocess options
                Object command = commandLine.getCommand();
                for (Field field : command.getClass().getDeclaredFields()) {
                    if (CliOption.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        CliOption option = (CliOption) field.get(command);
                        if (option != null) {
                            option.preprocess(commandLine);
                        }
                    }
                }

                // preprocess command
                if (command instanceof CliCommand) {
                    ((CliCommand) command).preprocess(commandLine);
                }
            }

            // set default configuration file if required
            if (useDefaultConfiguration && configFile == null) {
                configFile = CoreConstants.IMPEXP_DATA_DIR
                        .resolve(CliConstants.CONFIG_DIR)
                        .resolve(CliConstants.PROJECT_SETTINGS_FILE);
            } else {
                useDefaultConfiguration = false;
            }

            log.setConsoleLogLevel(logLevel);
            subCommandName = commandLines.get(1).getCommandName();
            commandLineString = cmd.getCommandName() + " " + String.join(" ", args);

            // execute command
            return cmd.getExecutionStrategy().execute(parseResult);
        } catch (CommandLine.ParameterException e) {
            cmd.getParameterExceptionHandler().handleParseException(e, args);
            return 2;
        } catch (CommandLine.ExecutionException e) {
            logError(e.getCause());
            return 1;
        }
    }

    @Override
    public Integer call() throws Exception {
        log.info("Starting " + getClass().getPackage().getImplementationTitle() +
                ", version " + this.getClass().getPackage().getImplementationVersion());

        Config config = ObjectRegistry.getInstance().getConfig();
        boolean loadConfig = configFile != null;
        if (progressListener != null) {
            progressListener.setProcessSteps(loadConfig ? 6 : 5);
        }

        // load plugins
        logProgress("Loading plugins");
        loadPlugins();

        // load database schema mapping
        logProgress("Loading database schema mapping");
        loadSchemaMapping();

        // load ADE extensions
        logProgress("Loading ADE extensions");
        loadADEExtensions();

        // load CityGML and ADE contexts
        logProgress("Loading CityGML and ADE contexts");
        createCityGMLBuilder();

        // load configuration
        if (loadConfig) {
            logProgress("Loading project settings");
            loadConfig(config);
        }

        // initialize application environment
        logProgress("Initializing application environment");
        initializeEnvironment(config);
        initializeLogging(config);
        createPidFile();

        log.info("Executing '" + subCommandName + "' command");
        return 0;
    }

    private void loadPlugins() throws ImpExpException {
        pluginManager.loadPlugins(classLoader);
        for (Plugin plugin : pluginManager.getExternalPlugins()) {
            log.info("Initializing plugin " + plugin.getClass().getName());
        }

        // load config classes from plugins
        for (ConfigExtension<?> plugin : pluginManager.getExternalPlugins(ConfigExtension.class)) {
            try {
                ConfigUtil.getInstance().withConfigClass(pluginManager.getConfigClass(plugin));
            } catch (PluginException e) {
                throw new ImpExpException("Failed to initialize config context for plugin " + plugin.getClass().getName() + ".", e);
            }
        }

        if (pluginManager.hasExceptions() && failOnPluginExceptions) {
            pluginManager.logExceptions();
            throw new ImpExpException("Failed to load plugins.");
        }
    }

    private void loadSchemaMapping() throws ImpExpException {
        try {
            SchemaMapping schemaMapping = SchemaMappingUtil.getInstance().unmarshal(CoreConstants.CITYDB_SCHEMA_MAPPING_FILE);
            ObjectRegistry.getInstance().setSchemaMapping(schemaMapping);
        } catch (JAXBException | SchemaMappingException | SchemaMappingValidationException e) {
            throw new ImpExpException("Failed to process 3DCityDB schema mapping file.", e);
        }
    }

    private void loadADEExtensions() throws ImpExpException {
        try {
            adeExtensionsFolder = adeExtensionsFolder != null ?
                    CoreConstants.WORKING_DIR.resolve(adeExtensionsFolder) :
                    CoreConstants.IMPEXP_HOME.resolve(CliConstants.ADE_EXTENSIONS_DIR);

            loadClasses(adeExtensionsFolder, classLoader);
        } catch (IOException e) {
            throw new ImpExpException("Failed to initialize ADE extension support.", e);
        }

        adeManager.loadExtensions(classLoader);
        adeManager.loadSchemaMappings(ObjectRegistry.getInstance().getSchemaMapping());
        for (ADEExtension extension : adeManager.getExtensions()) {
            log.info("Initializing ADE extension " + extension.getClass().getName());
        }

        if (adeManager.hasExceptions() && failOnADEExceptions) {
            adeManager.logExceptions();
            throw new ImpExpException("Failed to load ADE extensions.");
        }
    }

    private void createCityGMLBuilder() throws ImpExpException {
        try {
            CityGMLContext context = CityGMLContext.getInstance();
            for (ADEContext adeContext : adeManager.getADEContexts()) {
                context.registerADEContext(adeContext);
            }

            ObjectRegistry.getInstance().setCityGMLBuilder(context.createCityGMLBuilder(classLoader));
        } catch (CityGMLBuilderException | ADEException e) {
            throw new ImpExpException("Failed to initialize CityGML and ADE contexts.", e);
        }
    }

    private void loadConfig(Config config) throws ImpExpException {
        if (!configFile.isAbsolute()) {
            configFile = CoreConstants.WORKING_DIR.resolve(configFile);
        }

        try {
            Object object = ConfigUtil.getInstance().unmarshal(configFile.toFile());
            if (!(object instanceof ProjectConfig)) {
                throw new JAXBException("Failed to parse project settings.");
            }

            config.setProjectConfig((ProjectConfig) object);
        } catch (JAXBException | IOException e) {
            if (useDefaultConfiguration) {
                log.error("Failed to load configuration from file " + configFile + ".", e);
                log.info("Using default configuration settings instead.");
            } else {
                throw new ImpExpException("Failed to load configuration from file " + configFile + ".", e);
            }
        }

        // propagate configuration to plugins
        for (ConfigExtension<?> plugin : pluginManager.getExternalPlugins(ConfigExtension.class)) {
            try {
                pluginManager.propagatePluginConfig(plugin, config);
            } catch (PluginException e) {
                throw new ImpExpException("Failed to load configuration for plugin " + plugin.getClass().getName() + ".", e);
            }
        }
    }

    private void initializeEnvironment(Config config)  {
        // set internal proxy selector as default selector
        ProxySelector.setDefault(InternalProxySelector.getInstance());

        // set internationalization
        LanguageType language = config.getGlobalConfig().getLanguage();
        if (language != LanguageType.EN) {
            Locale locale = new Locale(language.value());
            if (Language.existsLanguagePack(locale)) {
                Language.I18N = ResourceBundle.getBundle("org.citydb.config.i18n.language", locale);
            } else {
                config.getGlobalConfig().setLanguage(LanguageType.EN);
            }
        }

        // set enabled status of external plugins
        if (!pluginManager.getExternalPlugins().isEmpty()) {
            Map<Plugin, Boolean> plugins = new IdentityHashMap<>();
            for (Plugin plugin : pluginManager.getExternalPlugins()) {
                boolean enable = enabledPlugins != null ?
                        enabledPlugins.getOrDefault(plugin.getClass().getName(), false) :
                        config.isPluginEnabled(plugin.getClass().getName());

                plugins.put(plugin, enable);
            }

            pluginManager.setPluginsEnabled(plugins);
        }
    }

    private void initializeLogging(Config config) {
        Logging logging = config.getGlobalConfig().getLogging();

        // set console log level
        if (!useDefaultLogLevel) {
            logging.getConsole().setLogLevel(logLevel);
        } else {
            log.setConsoleLogLevel(logging.getConsole().getLogLevel());
        }

        // overwrite log file settings in configuration
        if (logFile != null) {
            logging.getFile().setActive(true);
            logging.getFile().setUseAlternativeLogFile(true);
            logging.getFile().setAlternativeLogFile(logFile.normalize().toAbsolutePath().toString());
            logging.getFile().setLogFileMode(LogFileMode.TRUNCATE);

            if (!useDefaultLogLevel) {
                logging.getFile().setLogLevel(logLevel);
            }
        }

        // enable writing to log file
        if (logging.getFile().isActive()) {
            Path file = logging.getFile().isUseAlternativeLogFile() ?
                    Paths.get(logging.getFile().getAlternativeLogFile()) :
                    CoreConstants.IMPEXP_DATA_DIR.resolve(CliConstants.LOG_DIR).resolve(log.getDefaultLogFileName());

            log.setFileLogLevel(logging.getFile().getLogLevel());
            if (log.appendLogFile(file, logging.getFile().getLogFileMode())) {
                log.printToFile("*** Command line: " + commandLineString);
            } else {
                logging.getFile().setActive(false);
                logging.getFile().setUseAlternativeLogFile(false);
                log.detachLogFile();
            }
        }
    }

    private void createPidFile() throws ImpExpException {
        if (pidFile != null) {
            try {
                log.debug("Creating PID file '" + pidFile.normalize().toAbsolutePath() + "'.");
                PidFile.create(pidFile, true);
            } catch (IOException e) {
                throw new ImpExpException("Failed to create PID file.", e);
            }
        }
    }

    private void loadClasses(Path path, Util.URLClassLoader classLoader) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> stream = Files.walk(path)
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase().endsWith(".jar"))) {
                stream.forEach(classLoader::addPath);
            }
        }
    }

    public void logProgress(String message) {
        log.info(message);
        if (progressListener != null) {
            progressListener.nextStep(message, ++processStep);
        }
    }

    private void logError(Throwable t) {
        log.error("Aborting due to a fatal " + t.getClass().getName() + " exception.");
        log.logStackTrace(t);
    }

    private Path getPluginsFolder(String[] args) {
        String pluginsFolder = null;
        int candidate = 2;

        for (int i = 0; i < args.length; i++) {
            int length = args[i].length();
            if (length > candidate) {
                int delimiter = args[i].indexOf('=');
                String optionName = delimiter != -1 ?
                        args[i].substring(0, delimiter) :
                        args[i];

                if ("--plugins".startsWith(optionName)) {
                    if (delimiter != -1) {
                        pluginsFolder = delimiter + 1 < args[i].length() ?
                                args[i].substring(delimiter + 1) :
                                null;
                    } else if (i + 1 < args.length) {
                        pluginsFolder = args[(i++) + 1];
                    }

                    candidate = length;
                }
            }
        }

        return pluginsFolder != null ?
                CoreConstants.WORKING_DIR.resolve(pluginsFolder) :
                CoreConstants.IMPEXP_HOME.resolve(CliConstants.PLUGINS_DIR);
    }

    private String readPassword(CommandLine.ParseResult parseResult) {
        String prompt = "Enter password for " + parseResult.matchedOptionValue("-u", "") + ": ";
        Console console = System.console();
        if (console != null) {
            char[] input = console.readPassword(prompt);
            return input != null ? new String(input) : null;
        } else {
            System.out.print(prompt);
            try (Scanner scanner = new Scanner(System.in)) {
                if (scanner.hasNext()) {
                    return scanner.nextLine();
                } else {
                    System.out.println("Failed to connect to console");
                    return null;
                }
            }
        }
    }

    private String[] addDefaultCommand(CommandLine cmd, String[] args) {
        Set<String> subCommands = cmd.getSubcommands().keySet().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        CommandLine.Model.CommandSpec commandSpec = cmd.getCommandSpec().mixins().get("mixinStandardHelpOptions");
        for (String arg : args) {
            if (subCommands.contains(arg.toLowerCase())
                    || (commandSpec != null
                    && commandSpec.findOption(arg) != null)) {
                return args;
            }
        }

        return Stream.concat(Stream.of(defaultCommand), Arrays.stream(args)).toArray(String[]::new);
    }

    @Override
    public String[] getVersion() {
        return new String[]{
                getClass().getPackage().getImplementationTitle() +
                        ", version " + getClass().getPackage().getImplementationVersion(),
                "(C) 2013-" + LocalDate.now().getYear() + " " + getClass().getPackage().getImplementationVendor()
        };
    }
}
