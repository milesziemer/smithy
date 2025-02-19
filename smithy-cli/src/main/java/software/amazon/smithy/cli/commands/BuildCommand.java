/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.cli.commands;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.ProjectionResult;
import software.amazon.smithy.build.SmithyBuild;
import software.amazon.smithy.build.model.SmithyBuildConfig;
import software.amazon.smithy.cli.ArgumentReceiver;
import software.amazon.smithy.cli.Arguments;
import software.amazon.smithy.cli.CliError;
import software.amazon.smithy.cli.CliPrinter;
import software.amazon.smithy.cli.ColorFormatter;
import software.amazon.smithy.cli.HelpPrinter;
import software.amazon.smithy.cli.StandardOptions;
import software.amazon.smithy.cli.Style;
import software.amazon.smithy.cli.dependencies.DependencyResolver;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.validation.Severity;

final class BuildCommand extends ClasspathCommand {

    BuildCommand(String parentCommandName, DependencyResolver.Factory dependencyResolverFactory) {
        super(parentCommandName, dependencyResolverFactory);
    }

    @Override
    public String getName() {
        return "build";
    }

    @Override
    public String getSummary() {
        return "Builds Smithy models and creates plugin artifacts for each projection found in smithy-build.json.";
    }

    private static final class Options implements ArgumentReceiver {
        private String projection;
        private String plugin;

        @Override
        public boolean testOption(String name) {
            return false;
        }

        @Override
        public Consumer<String> testParameter(String name) {
            switch (name) {
                case "--projection":
                    return value -> projection = value;
                case "--plugin":
                    return value -> plugin = value;
                default:
                    return null;
            }
        }

        @Override
        public void registerHelp(HelpPrinter printer) {
            printer.param("--projection", null, "PROJECTION_NAME", "Only generate artifacts for this projection.");
            printer.param("--plugin", null, "PLUGIN_NAME", "Only generate artifacts for this plugin.");
        }
    }

    @Override
    protected void addAdditionalArgumentReceivers(List<ArgumentReceiver> receivers) {
        receivers.add(new Options());
    }

    @Override
    int runWithClassLoader(SmithyBuildConfig config, Arguments arguments, Env env, List<String> models) {
        Options options = arguments.getReceiver(Options.class);
        BuildOptions buildOptions = arguments.getReceiver(BuildOptions.class);
        StandardOptions standardOptions = arguments.getReceiver(StandardOptions.class);
        ClassLoader classLoader = env.classLoader();

        // Build the model and fail if there are errors. Prints errors to stdout.
        // Configure whether the build is quiet or not based on the --quiet option.
        Model model = CommandUtils.buildModel(arguments, models, env, env.stderr(), standardOptions.quiet(), config);

        SmithyBuild smithyBuild = SmithyBuild.create(classLoader)
                .config(config)
                .model(model);

        if (buildOptions.output() != null) {
            smithyBuild.outputDirectory(buildOptions.output());
        }

        if (options.plugin != null) {
            smithyBuild.pluginFilter(name -> name.equals(options.plugin));
        }

        if (options.projection != null) {
            smithyBuild.projectionFilter(name -> name.equals(options.projection));
        }

        // Register sources with the builder.
        models.forEach(path -> smithyBuild.registerSources(Paths.get(path)));

        ResultConsumer resultConsumer = new ResultConsumer(env.colors(), env.stderr(), standardOptions.quiet());
        smithyBuild.build(resultConsumer, resultConsumer);

        if (!standardOptions.quiet()) {
            Style ansiColor = resultConsumer.failedProjections.isEmpty()
                              ? Style.BRIGHT_GREEN
                              : Style.BRIGHT_YELLOW;
            env.colors().println(env.stderr(),
                                 String.format("Smithy built %s projection(s), %s plugin(s), and %s artifacts",
                                               resultConsumer.projectionCount,
                                               resultConsumer.pluginCount,
                                               resultConsumer.artifactCount),
                                 Style.BOLD, ansiColor);
        }

        // Throw an exception if any errors occurred.
        if (!resultConsumer.failedProjections.isEmpty()) {
            resultConsumer.failedProjections.sort(String::compareTo);
            throw new CliError(String.format(
                    "The following %d Smithy build projection(s) failed: %s",
                    resultConsumer.failedProjections.size(),
                    resultConsumer.failedProjections));
        }

        return 0;
    }

    private static final class ResultConsumer implements Consumer<ProjectionResult>, BiConsumer<String, Throwable> {
        private final List<String> failedProjections = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger artifactCount = new AtomicInteger();
        private final AtomicInteger pluginCount = new AtomicInteger();
        private final AtomicInteger projectionCount = new AtomicInteger();
        private final boolean quiet;
        private final ColorFormatter colors;
        private final CliPrinter printer;

        ResultConsumer(ColorFormatter colors, CliPrinter stderr, boolean quiet) {
            this.colors = colors;
            this.printer = stderr;
            this.quiet = quiet;
        }

        @Override
        public void accept(String name, Throwable exception) {
            failedProjections.add(name);
            StringWriter writer = new StringWriter();
            writer.write(String.format("%nProjection %s failed: %s%n", name, exception.toString()));
            exception.printStackTrace(new PrintWriter(writer));
            colors.println(printer, writer.toString(), Style.RED);
        }

        @Override
        public void accept(ProjectionResult result) {
            try (ColorFormatter.PrinterBuffer buffer = colors.printerBuffer(printer)) {
                printProjectionResult(buffer, result);
            }
        }

        private void printProjectionResult(ColorFormatter.PrinterBuffer buffer, ProjectionResult result) {
            if (result.isBroken()) {
                // Write out validation errors as they occur.
                failedProjections.add(result.getProjectionName());
                buffer
                        .println()
                        .print(result.getProjectionName(), Style.RED)
                        .println(" has a model that failed validation");
                result.getEvents().forEach(event -> {
                    if (event.getSeverity() == Severity.DANGER || event.getSeverity() == Severity.ERROR) {
                        buffer.println(event.toString(), Style.RED);
                    }
                });
            } else {
                // Only increment the projection count if it succeeded.
                projectionCount.incrementAndGet();
            }

            pluginCount.addAndGet(result.getPluginManifests().size());

            // Get the base directory of the projection.
            Iterator<FileManifest> manifestIterator = result.getPluginManifests().values().iterator();
            Path root = manifestIterator.hasNext() ? manifestIterator.next().getBaseDir().getParent() : null;

            if (!quiet) {
                String message = String.format("Completed projection %s (%d shapes): %s",
                                               result.getProjectionName(), result.getModel().toSet().size(), root);
                buffer.println(message, Style.GREEN);
            }

            // Increment the total number of artifacts written.
            for (FileManifest manifest : result.getPluginManifests().values()) {
                artifactCount.addAndGet(manifest.getFiles().size());
            }
        }
    }
}
