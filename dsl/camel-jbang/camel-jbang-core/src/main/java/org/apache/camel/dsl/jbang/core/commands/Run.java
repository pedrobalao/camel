/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.console.DevConsole;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.impl.lw.LightweightCamelContext;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.apache.camel.dsl.jbang.core.common.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.fetchGithubUrls;

@Command(name = "run", description = "Run as local Camel integration")
class Run extends CamelCommand {

    public static final String WORK_DIR = ".camel-jbang";
    public static final String RUN_SETTINGS_FILE = "camel-jbang-run.properties";

    private static final String[] ACCEPTED_FILE_EXT
            = new String[] { "java", "groovy", "js", "jsh", "kts", "xml", "yaml" };

    private static final String OPENAPI_GENERATED_FILE = ".camel-jbang/generated-openapi.yaml";
    private static final String CLIPBOARD_GENERATED_FILE = ".camel-jbang/generated-clipboard";

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][\\.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*public class\\s+([a-zA-Z0-9]*)[\\s+|;].*$", Pattern.MULTILINE);

    private CamelContext context;
    private File lockFile;
    private File statusFile;
    private ScheduledExecutorService executor;
    private boolean silentRun;
    private boolean pipeRun;

    //CHECKSTYLE:OFF
    @Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
                arity = "0..9")
    String[] files;

    @CommandLine.Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT, defaultValue = "application",
            description = "Profile to use, which refers to loading properties file with the given profile name. By default application.properties is loaded.")
    String profile;

    @Option(names = {
            "--dep", "--deps" }, description = "Add additional dependencies (Use commas to separate multiple dependencies).")
    String dependencies;

    @Option(names = {"--repos"}, description = "Additional maven repositories for download on-demand (Use commas to separate multiple repositories).")
    String repos;

    @Option(names = { "--fresh" }, description = "Make sure we use fresh (i.e. non-cached) resources")
    boolean fresh;

    @Option(names = {"--download"}, defaultValue = "true", description = "Whether to allow automatic downloaded JAR dependencies, over the internet, that Camel requires.")
    boolean download = true;

    @Option(names = { "--name" }, defaultValue = "CamelJBang", description = "The name of the Camel application")
    String name;

    @Option(names = { "--logging" }, defaultValue = "true", description = "Can be used to turn off logging")
    boolean logging = true;

    @Option(names = { "--logging-level" }, defaultValue = "info", description = "Logging level")
    String loggingLevel;

    @Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @Option(names = { "--logging-json" }, description = "Use JSON logging (ECS Layout)")
    boolean loggingJson;

    @Option(names = { "--stop" }, description = "Stop all running instances of Camel JBang")
    boolean stopRequested;

    @Option(names = { "--max-messages" }, defaultValue = "0", description = "Max number of messages to process before stopping")
    int maxMessages;

    @Option(names = { "--max-seconds" }, defaultValue = "0", description = "Max seconds to run before stopping")
    int maxSeconds;

    @Option(names = { "--max-idle-seconds" }, defaultValue = "0",
            description = "For how long time in seconds Camel can be idle before stopping")
    int maxIdleSeconds;

    @Option(names = { "--reload", "--dev" },
            description = "Enables dev mode (live reload when source files are updated and saved)")
    boolean dev;

    @Option(names = { "--trace" }, description = "Enables trace logging of the routed messages")
    boolean trace;

    @Option(names = { "--properties" },
            description = "Load properties file for route placeholders (ex. /path/to/file.properties")
    String propertiesFiles;

    @Option(names = { "-p", "--prop", "--property" }, description = "Additional properties (override existing)", arity = "0")
    String[] property;

    @Option(names = { "--file-lock" },
            description = "Whether to create a temporary file lock, which upon deleting triggers this process to terminate", defaultValue = "true")
    boolean fileLock = true;

    @Option(names = { "--jfr" },
            description = "Enables Java Flight Recorder saving recording to disk on exit")
    boolean jfr;

    @Option(names = { "--jfr-profile" },
            description = "Java Flight Recorder profile to use (such as default or profile)")
    String jfrProfile;

    @Option(names = { "--local-kamelet-dir" },
            description = "Local directory for loading Kamelets (takes precedence)")
    String localKameletDir;

    @Option(names = { "--port" }, description = "Embeds a local HTTP server on this port")
    int port;

    @Option(names = { "--console" }, description = "Developer console at /q/dev on local HTTP server (port 8080 by default)")
    boolean console;

    @Option(names = { "--health" }, description = "Health check at /q/health on local HTTP server (port 8080 by default)")
    boolean health;

    @Option(names = { "--modeline" }, defaultValue = "true", description = "Enables Camel-K style modeline")
    boolean modeline = true;

    @Option(names = { "--open-api" }, description = "Add an OpenAPI spec from the given file")
    String openapi;

    public Run(CamelJBangMain main) {
        super(main);
    }

    //CHECKSTYLE:ON

    public String getProfile() {
        return profile;
    }

    @Override
    public Integer call() throws Exception {
        if (stopRequested) {
            stop();
            return 0;
        } else {
            return run();
        }
    }

    protected Integer runSilent() throws Exception {
        // just boot silently and exit
        silentRun = true;
        return run();
    }

    protected Integer runPipe(String file) throws Exception {
        this.files = new String[] { file };
        pipeRun = true;
        return run();
    }

    private void writeSetting(KameletMain main, Properties existing, String key, String value) {
        String val = existing != null ? existing.getProperty(key, value) : value;
        if (val != null) {
            main.addInitialProperty(key, val);
            writeSettings(key, val);
        }
    }

    private void writeSetting(KameletMain main, Properties existing, String key, Supplier<String> value) {
        String val = existing != null ? existing.getProperty(key, value.get()) : value.get();
        if (val != null) {
            main.addInitialProperty(key, val);
            writeSettings(key, val);
        }
    }

    private int stop() {
        if (lockFile != null) {
            FileUtil.deleteFile(lockFile);
        }
        if (statusFile != null) {
            FileUtil.deleteFile(statusFile);
        }
        return 0;
    }

    private Properties loadProfileProperties(File source) throws Exception {
        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, source);

        // special for routes include pattern that we need to "fix" after reading from properties
        // to make this work in run command
        String value = prop.getProperty("camel.main.routesIncludePattern");
        if (value != null) {
            // if not scheme then must use file: as this is what run command expects
            StringJoiner sj = new StringJoiner(",");
            for (String part : value.split(",")) {
                if (!part.contains(":")) {
                    part = "file:" + part;
                }
                sj.add(part);
            }
            value = sj.toString();
            prop.setProperty("camel.main.routesIncludePattern", value);
        }

        return prop;
    }

    private int run() throws Exception {
        File work = new File(WORK_DIR);
        removeDir(work);
        work.mkdirs();

        Properties profileProperties = null;
        File profilePropertiesFile = new File(getProfile() + ".properties");
        if (profilePropertiesFile.exists()) {
            profileProperties = loadProfileProperties(profilePropertiesFile);
            // logging level/color may be configured in the properties file
            loggingLevel = profileProperties.getProperty("loggingLevel", loggingLevel);
            loggingColor
                    = "true".equals(profileProperties.getProperty("loggingColor", loggingColor ? "true" : "false"));
            loggingJson
                    = "true".equals(profileProperties.getProperty("loggingJson", loggingJson ? "true" : "false"));
            if (propertiesFiles == null) {
                propertiesFiles = "file:" + profilePropertiesFile.getName();
            } else {
                propertiesFiles = propertiesFiles + ",file:" + profilePropertiesFile.getName();
            }
            repos = profileProperties.getProperty("camel.jbang.repos", repos);
            openapi = profileProperties.getProperty("camel.jbang.openApi", openapi);
            download = "true".equals(profileProperties.getProperty("camel.jbang.download", download ? "true" : "false"));
        }

        // generate open-api early
        if (openapi != null) {
            generateOpenApi();
        }

        // if no specific file to run then try to auto-detect
        if (files == null || files.length == 0) {
            String routes = profileProperties != null ? profileProperties.getProperty("camel.main.routesIncludePattern") : null;
            if (routes == null) {
                if (!silentRun) {
                    System.out.println("Cannot run because " + getProfile() + ".properties file does not exist");
                    return 1;
                } else {
                    // silent-run then auto-detect all files (except properties as they are loaded explicit or via profile)
                    files = new File(".").list((dir, name) -> !name.endsWith(".properties"));
                }
            }
        }
        // filter out duplicate files
        if (files != null && files.length > 0) {
            files = Arrays.stream(files).distinct().toArray(String[]::new);
        }

        // configure logging first
        configureLogging();

        final KameletMain main = createMainInstance();

        final Set<String> downloaded = new HashSet<>();
        main.setRepos(repos);
        main.setDownload(download);
        main.setFresh(fresh);
        main.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadDependency(String groupId, String artifactId, String version) {
                String line = "mvn:" + groupId + ":" + artifactId;
                if (version != null) {
                    line += ":" + version;
                }
                if (!downloaded.contains(line)) {
                    writeSettings("dependency", line);
                    downloaded.add(line);
                }
            }

            @Override
            public void onAlreadyDownloadedDependency(String groupId, String artifactId, String version) {
                // we want to register everything
                onDownloadDependency(groupId, artifactId, version);
            }
        });
        main.setAppName("Apache Camel (JBang)");

        writeSetting(main, profileProperties, "camel.main.name", name);
        writeSetting(main, profileProperties, "camel.main.sourceLocationEnabled", "true");
        if (dev) {
            writeSetting(main, profileProperties, "camel.main.routesReloadEnabled", "true");
            // allow quick shutdown during development
            writeSetting(main, profileProperties, "camel.main.shutdownTimeout", "5");
        }
        if (trace) {
            writeSetting(main, profileProperties, "camel.main.tracing", "true");
        }
        if (modeline) {
            writeSetting(main, profileProperties, "camel.main.modeline", "true");
        }
        // allow java-dsl to compile to .class which we need in uber-jar mode
        writeSetting(main, profileProperties, "camel.main.routesCompileDirectory", WORK_DIR);
        writeSetting(main, profileProperties, "camel.jbang.dependencies", dependencies);
        writeSetting(main, profileProperties, "camel.jbang.openApi", openapi);
        writeSetting(main, profileProperties, "camel.jbang.repos", repos);
        writeSetting(main, profileProperties, "camel.jbang.health", health ? "true" : "false");
        writeSetting(main, profileProperties, "camel.jbang.console", console ? "true" : "false");

        // command line arguments
        if (property != null) {
            for (String p : property) {
                String k = StringHelper.before(p, "=");
                String v = StringHelper.after(p, "=");
                if (k != null && v != null) {
                    main.addArgumentProperty(k, v);
                    writeSettings(k, v);
                }
            }
        }

        if (silentRun) {
            // enable stub in silent mode so we do not use real components
            main.setStub(true);
            // do not run for very long in silent run
            main.addInitialProperty("camel.main.autoStartup", "false");
            main.addInitialProperty("camel.main.durationMaxSeconds", "1");
        } else if (pipeRun) {
            // auto terminate if being idle
            main.addInitialProperty("camel.main.durationMaxIdleSeconds", "1");
        }
        writeSetting(main, profileProperties, "camel.main.durationMaxMessages",
                () -> maxMessages > 0 ? String.valueOf(maxMessages) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxSeconds",
                () -> maxSeconds > 0 ? String.valueOf(maxSeconds) : null);
        writeSetting(main, profileProperties, "camel.main.durationMaxIdleSeconds",
                () -> maxIdleSeconds > 0 ? String.valueOf(maxIdleSeconds) : null);
        writeSetting(main, profileProperties, "camel.jbang.platform-http.port",
                () -> port > 0 ? String.valueOf(port) : null);
        writeSetting(main, profileProperties, "camel.jbang.jfr", jfr || jfrProfile != null ? "jfr" : null);
        writeSetting(main, profileProperties, "camel.jbang.jfr-profile", jfrProfile != null ? jfrProfile : null);

        if (fileLock) {
            initLockFile(main);
        }

        StringJoiner js = new StringJoiner(",");
        StringJoiner sjReload = new StringJoiner(",");
        StringJoiner sjClasspathFiles = new StringJoiner(",");
        StringJoiner sjKamelets = new StringJoiner(",");

        // include generated openapi to files to run
        if (openapi != null) {
            List<String> list = new ArrayList<>();
            if (files != null) {
                list.addAll(Arrays.asList(files));
            }
            list.add(OPENAPI_GENERATED_FILE);
            files = list.toArray(new String[0]);
        }

        if (files != null) {
            for (String file : files) {

                if (file.startsWith("clipboard") && !(new File(file).exists())) {
                    file = loadFromClipboard(file);
                } else if (skipFile(file)) {
                    continue;
                } else if (!knownFile(file) && !file.endsWith(".properties")) {
                    // non known files to be added on classpath
                    sjClasspathFiles.add(file);
                    continue;
                }

                // process known files as its likely DSLs or configuration files

                // check for properties files
                if (file.endsWith(".properties")) {
                    if (!ResourceHelper.hasScheme(file) && !file.startsWith("github:")) {
                        file = "file:" + file;
                    }
                    if (ObjectHelper.isEmpty(propertiesFiles)) {
                        propertiesFiles = file;
                    } else {
                        propertiesFiles = propertiesFiles + "," + file;
                    }
                    if (dev && file.startsWith("file:")) {
                        // we can only reload if file based
                        sjReload.add(file.substring(5));
                    }
                    continue;
                }

                // Camel DSL files
                if (!ResourceHelper.hasScheme(file) && !file.startsWith("github:")) {
                    file = "file:" + file;
                }
                if (file.startsWith("file:")) {
                    // check if file exist
                    File inputFile = new File(file.substring(5));
                    if (!inputFile.exists() && !inputFile.isFile()) {
                        System.err.println("File does not exist: " + file);
                        return 1;
                    }
                }

                if (file.startsWith("file:") && file.endsWith(".kamelet.yaml")) {
                    sjKamelets.add(file);
                }

                // automatic map github https urls to github resolver
                if (file.startsWith("https://github.com/")) {
                    file = evalGithubSource(main, file);
                    if (file == null) {
                        continue; // all mapped continue to next
                    }
                } else if (file.startsWith("https://gist.github.com/")) {
                    file = evalGistSource(main, file);
                    if (file == null) {
                        continue; // all mapped continue to next
                    }
                }

                js.add(file);
                if (dev && file.startsWith("file:")) {
                    // we can only reload if file based
                    sjReload.add(file.substring(5));
                }
            }
        }

        if (js.length() > 0) {
            main.addInitialProperty("camel.main.routesIncludePattern", js.toString());
            writeSettings("camel.main.routesIncludePattern", js.toString());
        } else {
            writeSetting(main, profileProperties, "camel.main.routesIncludePattern", () -> null);
        }
        if (sjClasspathFiles.length() > 0) {
            main.addInitialProperty("camel.jbang.classpathFiles", sjClasspathFiles.toString());
            writeSettings("camel.jbang.classpathFiles", sjClasspathFiles.toString());
        } else {
            writeSetting(main, profileProperties, "camel.jbang.classpathFiles", () -> null);
        }

        if (sjKamelets.length() > 0) {
            String loc = main.getInitialProperties().getProperty("camel.component.kamelet.location");
            if (loc != null) {
                loc = loc + "," + sjKamelets;
            } else {
                loc = sjKamelets.toString();
            }
            main.addInitialProperty("camel.component.kamelet.location", loc);
            writeSettings("camel.component.kamelet.location", loc);
        } else {
            writeSetting(main, profileProperties, "camel.component.kamelet.location", () -> null);
        }

        // we can only reload if file based
        if (dev && sjReload.length() > 0) {
            String reload = sjReload.toString();
            main.addInitialProperty("camel.main.routesReloadEnabled", "true");
            main.addInitialProperty("camel.main.routesReloadDirectory", ".");
            main.addInitialProperty("camel.main.routesReloadPattern", reload);
            main.addInitialProperty("camel.main.routesReloadDirectoryRecursive", isReloadRecursive(reload) ? "true" : "false");
            // do not shutdown the JVM but stop routes when max duration is triggered
            main.addInitialProperty("camel.main.durationMaxAction", "stop");
        }

        if (propertiesFiles != null) {
            String[] filesLocation = propertiesFiles.split(",");
            StringBuilder locations = new StringBuilder();
            for (String file : filesLocation) {
                if (!file.startsWith("file:")) {
                    if (!file.startsWith("/")) {
                        file = FileSystems.getDefault().getPath("").toAbsolutePath() + File.separator + file;
                    }
                    file = "file://" + file;
                }
                if (locations.length() > 0) {
                    locations.append(",");
                }
                locations.append(file);
            }
            // there may be existing properties
            String loc = main.getInitialProperties().getProperty("camel.component.properties.location");
            if (loc != null) {
                loc = loc + "," + locations;
            } else {
                loc = locations.toString();
            }
            // TODO: remove duplicates in loc
            main.addInitialProperty("camel.component.properties.location", loc);
            writeSettings("camel.component.properties.location", loc);
        }

        main.start();

        context = main.getCamelContext();

        main.run();

        return main.getExitCode();
    }

    private void initLockFile(KameletMain main) {
        lockFile = createLockFile(getPid());
        if (lockFile != null) {
            statusFile = createLockFile(lockFile.getName() + "-status.json");
        }
        // to trigger shutdown on file lock deletion
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> {
            // if the lock file is deleted then stop
            if (!lockFile.exists()) {
                context.stop();
                return;
            }
            // update status file with details from the context console
            try {
                DevConsole dc = main.getCamelContext().adapt(ExtendedCamelContext.class)
                        .getDevConsoleResolver().resolveDevConsole("context");
                DevConsole dc2 = main.getCamelContext().adapt(ExtendedCamelContext.class)
                        .getDevConsoleResolver().resolveDevConsole("route");
                int ready = 0;
                int total = 0;
                // and health-check readiness
                Collection<HealthCheck.Result> res = HealthCheckHelper.invokeReadiness(main.getCamelContext());
                for (var r : res) {
                    if (r.getState().equals(HealthCheck.State.UP)) {
                        ready++;
                    }
                    total++;
                }
                JsonObject hc = new JsonObject();
                hc.put("ready", ready);
                hc.put("total", total);
                if (dc != null && dc2 != null) {
                    JsonObject json = (JsonObject) dc.call(DevConsole.MediaType.JSON);
                    JsonObject json2 = (JsonObject) dc2.call(DevConsole.MediaType.JSON);
                    if (json != null && json2 != null) {
                        JsonObject root = new JsonObject();
                        root.put("context", json);
                        root.put("routes", json2.get("routes"));
                        root.put("healthChecks", hc);
                        IOHelper.writeText(root.toJson(), statusFile);
                    }
                }
            } catch (Throwable e) {
                // ignore
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    private String evalGistSource(KameletMain main, String file) throws Exception {
        StringJoiner routes = new StringJoiner(",");
        StringJoiner kamelets = new StringJoiner(",");
        StringJoiner properties = new StringJoiner(",");
        fetchGistUrls(file, routes, kamelets, properties);

        if (properties.length() > 0) {
            main.addInitialProperty("camel.component.properties.location", properties.toString());
        }
        if (kamelets.length() > 0) {
            String loc = main.getInitialProperties().getProperty("camel.component.kamelet.location");
            if (loc != null) {
                // local kamelets first
                loc = kamelets + "," + loc;
            } else {
                loc = kamelets.toString();
            }
            main.addInitialProperty("camel.component.kamelet.location", loc);
        }
        if (routes.length() > 0) {
            return routes.toString();
        }
        return null;
    }

    private String evalGithubSource(KameletMain main, String file) throws Exception {
        String ext = FileUtil.onlyExt(file);
        boolean wildcard = FileUtil.onlyName(file, false).contains("*");
        if (ext != null && !wildcard) {
            // it is a single file so map to
            return asGithubSingleUrl(file);
        } else {
            StringJoiner routes = new StringJoiner(",");
            StringJoiner kamelets = new StringJoiner(",");
            StringJoiner properties = new StringJoiner(",");
            fetchGithubUrls(file, routes, kamelets, properties);

            if (properties.length() > 0) {
                main.addInitialProperty("camel.component.properties.location", properties.toString());
            }
            if (kamelets.length() > 0) {
                String loc = main.getInitialProperties().getProperty("camel.component.kamelet.location");
                if (loc != null) {
                    // local kamelets first
                    loc = kamelets + "," + loc;
                } else {
                    loc = kamelets.toString();
                }
                main.addInitialProperty("camel.component.kamelet.location", loc);
            }
            if (routes.length() > 0) {
                return routes.toString();
            }
            return null;
        }
    }

    private String loadFromClipboard(String file) throws UnsupportedFlavorException, IOException {
        // run from clipboard (not real file exists)
        String ext = FileUtil.onlyExt(file, true);
        if (ext == null || ext.isEmpty()) {
            throw new IllegalArgumentException(
                    "When running from clipboard, an extension is required to let Camel know what kind of file to use");
        }
        Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
        Object t = c.getData(DataFlavor.stringFlavor);
        if (t != null) {
            String fn = CLIPBOARD_GENERATED_FILE + "." + ext;
            if ("java".equals(ext)) {
                String fqn = determineClassName(t.toString());
                if (fqn == null) {
                    throw new IllegalArgumentException(
                            "Cannot determine the Java class name from the source in the clipboard");
                }
                fn = fqn + ".java";
            }
            Files.write(Paths.get(fn), t.toString().getBytes(StandardCharsets.UTF_8));
            file = "file:" + fn;
        }
        return file;
    }

    private KameletMain createMainInstance() {
        KameletMain main;
        if (localKameletDir == null) {
            main = new KameletMain();
        } else {
            main = new KameletMain("file:" + localKameletDir);
            writeSettings("camel.jbang.localKameletDir", localKameletDir);
        }
        return main;
    }

    private void configureLogging() throws Exception {
        if (silentRun) {
            // do not configure logging
        } else if (logging) {
            RuntimeUtil.configureLog(loggingLevel, loggingColor, loggingJson, pipeRun, false);
            writeSettings("loggingLevel", loggingLevel);
            writeSettings("loggingColor", loggingColor ? "true" : "false");
            writeSettings("loggingJson", loggingJson ? "true" : "false");
        } else {
            RuntimeUtil.configureLog("off", false, false, false, false);
            writeSettings("loggingLevel", "off");
        }
    }

    private void generateOpenApi() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode node = mapper.readTree(Paths.get(openapi).toFile());
        OasDocument document = (OasDocument) Library.readDocument(node);
        Configurator.setRootLevel(Level.OFF);
        try (CamelContext context = new LightweightCamelContext()) {
            String out = RestDslGenerator.toYaml(document).generate(context, false);
            Files.write(Paths.get(OPENAPI_GENERATED_FILE), out.getBytes());
        }
    }

    public File createLockFile(String name) {
        File answer = null;
        if (name != null) {
            File dir = new File(System.getProperty("user.home"), ".camel");
            try {
                dir.mkdirs();
                answer = new File(dir, name);
                if (!answer.exists()) {
                    answer.createNewFile();
                }
                answer.deleteOnExit();
            } catch (Exception e) {
                answer = null;
            }
        }
        return answer;
    }

    private static String getPid() {
        try {
            return "" + ProcessHandle.current().pid();
        } catch (Throwable e) {
            return null;
        }
    }

    private boolean knownFile(String file) throws Exception {
        // always include kamelets
        String ext = FileUtil.onlyExt(file, false);
        if ("kamelet.yaml".equals(ext)) {
            return true;
        }

        String ext2 = FileUtil.onlyExt(file, true);
        if (ext2 != null) {
            boolean github = file.startsWith("github:") || file.startsWith("https://github.com/")
                    || file.startsWith("https://gist.github.com/");
            // special for yaml or xml, as we need to check if they have camel or not
            if (!github && ("xml".equals(ext2) || "yaml".equals(ext2))) {
                // load content into memory
                try (FileInputStream fis = new FileInputStream(file)) {
                    String data = IOHelper.loadText(fis);
                    if ("xml".equals(ext2)) {
                        return data.contains("<routes") || data.contains("<routeConfiguration") || data.contains("<rests");
                    } else {
                        // also support kamelet bindings
                        return data.contains("- from:") || data.contains("- route:") || data.contains("- route-configuration:")
                                || data.contains("- rest:") || data.contains("KameletBinding");
                    }
                }
            }
            // if the ext is an accepted file then we include it as a potential route
            // (java files need to be included as route to support pojos/processors with routes)
            return Arrays.stream(ACCEPTED_FILE_EXT).anyMatch(e -> e.equalsIgnoreCase(ext2));
        } else {
            // assume match as it can be wildcard or dir
            return true;
        }
    }

    private boolean skipFile(String name) {
        if (OPENAPI_GENERATED_FILE.equals(name)) {
            return false;
        }
        if (name.startsWith(".")) {
            return true;
        }
        if ("pom.xml".equalsIgnoreCase(name)) {
            return true;
        }
        if ("build.gradle".equalsIgnoreCase(name)) {
            return true;
        }
        if ("camel-runner.jar".equals(name)) {
            return true;
        }
        if ("docker-compose.yml".equals(name) || "docker-compose.yaml".equals(name) || "compose.yml".equals(name)
                || "compose.yaml".equals(name)) {
            return true;
        }

        // skip dirs
        File f = new File(name);
        if (f.exists() && f.isDirectory()) {
            return true;
        }

        String on = FileUtil.onlyName(name, true);
        on = on.toLowerCase(Locale.ROOT);
        if (on.startsWith("readme")) {
            return true;
        }

        return false;
    }

    private void writeSettings(String key, String value) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(WORK_DIR + "/" + RUN_SETTINGS_FILE, true);
            String line = key + "=" + value;
            fos.write(line.getBytes(StandardCharsets.UTF_8));
            fos.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // ignore
        } finally {
            IOHelper.close(fos);
        }
    }

    private static void removeDir(File d) {
        String[] list = d.list();
        if (list == null) {
            list = new String[0];
        }
        for (String s : list) {
            File f = new File(d, s);
            if (f.isDirectory()) {
                removeDir(f);
            } else {
                delete(f);
            }
        }
        delete(d);
    }

    private static void delete(File f) {
        if (!f.delete()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                // Ignore Exception
            }
            if (!f.delete()) {
                f.deleteOnExit();
            }
        }
    }

    private static String determineClassName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        String pn = matcher.find() ? matcher.group(1) : null;

        matcher = CLASS_PATTERN.matcher(content);
        String cn = matcher.find() ? matcher.group(1) : null;

        String fqn;
        if (pn != null) {
            fqn = pn + "." + cn;
        } else {
            fqn = cn;
        }
        return fqn;
    }

    private static boolean isReloadRecursive(String reload) {
        for (String part : reload.split(",")) {
            String dir = FileUtil.onlyPath(part);
            if (dir != null) {
                return true;
            }
        }
        return false;
    }

}
