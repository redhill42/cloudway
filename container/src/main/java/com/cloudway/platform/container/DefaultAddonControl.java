/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.platform.container;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.cloudway.platform.common.fp.data.Maybe;
import com.cloudway.platform.common.os.Etc;
import com.cloudway.platform.common.os.Exec;
import com.cloudway.platform.common.util.MoreFiles;
import com.cloudway.platform.common.fp.io.IO;
import com.cloudway.platform.common.fp.io.IOAction;
import com.cloudway.platform.common.util.SimpleFileVisitor;
import com.cloudway.platform.common.fp.data.Seq;
import com.cloudway.platform.container.proxy.HttpProxy;
import com.cloudway.platform.container.proxy.ProxyMapping;
import static com.cloudway.platform.container.AddonType.FRAMEWORK;

import static java.lang.String.format;
import static java.util.stream.Collectors.*;
import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.Files.*;
import static com.cloudway.platform.common.util.MoreFiles.*;
import static com.cloudway.platform.common.fp.control.Predicates.*;
import static com.cloudway.platform.common.fp.control.StringPredicates.*;

public class DefaultAddonControl implements AddonControl
{
    private final ApplicationContainer container;
    private Map<String, Addon> _addons;

    public DefaultAddonControl(ApplicationContainer container) {
        this.container = container;
    }

    @Override
    public Map<String, Addon> addons() {
        if (_addons == null) {
            Map<String, String> env = container.environ();
            try (Stream<Path> paths = list(container.getHomeDir())) {
                _addons = paths
                    .filter(Addon::isAddonDirectory)
                    .map(d -> Addon.load(d, env))
                    .collect(toMap(Addon::getName, Function.identity()));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return _addons;
    }

    @Override
    public Stream<Addon> validAddons() {
        return addons().values().stream().filter(Addon::isValid);
    }

    @Override
    public Maybe<Addon> addon(String name) {
        return Maybe.ofNullable(addons().get(name));
    }

    @Override
    public Maybe<Addon> getFrameworkAddon() {
        return Maybe.fromOptional(
            validAddons().filter(having(Addon::getType, is(FRAMEWORK)))
                         .findFirst());
    }

    @Override
    public void install(Path source, String repo)
        throws IOException
    {
        Addon addon = IO.with(source).<Addon>get()
          .when(Files::isDirectory,   () -> installFromDirectory(source.toRealPath(), true))
          .when(Files::isRegularFile, () -> installFromArchive(source))
          .orElseThrow(() -> new IOException("Invalid addon file: " + source));

        String name = addon.getName();
        Path target = addon.getPath();

        try {
            // add environment variables
            container.setenv("CLOUDWAY_" + name.toUpperCase() + "_DIR", target.toString());
            container.setenv("CLOUDWAY_" + name.toUpperCase() + "_VERSION", addon.getVersion());
            if (addon.getType() == FRAMEWORK) {
                container.setenv("CLOUDWAY_FRAMEWORK", name);
                container.setenv("CLOUDWAY_FRAMEWORK_DIR", target.toString());
            }

            // allocates and assigns private IP/port
            createPrivateEndpoints(addon);

            // run addon actions with files unlocked
            do_validate(target, () -> with_unlocked(target, true, () -> {
                processTemplates(target, null);
                do_action(target, null, "setup");
            }));

            // securing addon directory
            container.setFileTreeReadOnly(target.resolve("metadata"));
            container.setFileTreeReadOnly(target.resolve("bin"));

            // initialize repository for framework addon
            if (addon.getType() == FRAMEWORK) {
                populateRepository(target, repo);
            }

            // create proxy mappings
            // TODO: make sure don't override application framework's proxy mappings
            addProxyMappings(addon);

            // put addon into cache
            addons().put(name, addon);
        } catch (IOException|RuntimeException|Error ex) {
            deleteFileTree(target);
            throw ex;
        }
    }

    @Override
    public void remove(String name) throws IOException {
        Path path = container.getHomeDir().resolve(name);
        if (!exists(path)) {
            return;
        }

        Map<String,String> env = container.environ();

        Addon addon = null;
        if (this._addons != null)
            addon = this._addons.remove(name); // remove addon from cache
        if (addon == null)
            addon = Addon.load(path, env);     // load addon metadata
        addon.validate();

        try {
            // remove allocated private IP/port
            deletePrivateEndpoints(addon);

            // gracefully stop the addon
            do_action(path, env, "control", "stop");
            with_unlocked(path, false, () -> do_action(path, env, "teardown"));
        } finally {
            removeProxyMappings(addon);
            deleteFileTree(path);
        }
    }

    @Override
    public void destroy() {
        try {
            Map<String,String> env = container.environ();
            validAddons().map(Addon::getPath).forEach(path -> {
                try {
                    do_action(path, env, "control", "stop");
                    with_unlocked(path, false, () -> do_action(path, env, "teardown"));
                } catch (IOException ex) {
                    // log and ignore
                }
            });
        } finally {
            try {
                HttpProxy.purge(container);
            } catch (IOException ex) {
                // log and ignore
            }
        }
    }

    @Override
    public void start() throws IOException {
        control("start", true, true);
    }

    @Override
    public void stop() throws IOException {
        control("stop", true, false);
    }

    @Override
    public void restart() throws IOException {
        control("restart", true, true);
    }

    @Override
    public void tidy() throws IOException {
        control("tidy", false, false);
    }

    private Addon installFromDirectory(Path source, boolean copy)
        throws IOException
    {
        // load addon metadata from source directory
        Addon meta = Addon.load(source, container.environ());
        meta.validate();

        String name = meta.getName();
        Path target = container.getHomeDir().resolve(name);

        if (addons().containsKey(name) || exists(target))
            throw new IllegalStateException("Addon already installed: " + name);
        if (meta.getType() == FRAMEWORK && getFrameworkAddon().isPresent())
            throw new IllegalStateException("A framework addon already installed");

        // copy or move files from source to target
        if (copy) {
            copydir(source, target);
        } else {
            move(source, target);
        }

        container.setFileTreeReadWrite(target);
        return meta.copyOf(target);
    }

    private Addon installFromArchive(Path source)
        throws IOException
    {
        Path tmpdir = createTempDirectory(container.getHomeDir().resolve(".tmp"), "tmp");
        chmod(tmpdir, 0750);

        try {
            // extract archive into temporary directory and load addon from it
            extract(source, tmpdir);
            return installFromDirectory(tmpdir, false);
        } finally {
            deleteFileTree(tmpdir);
        }
    }

    private void copydir(Path source, Path target)
        throws IOException
    {
        String[] sharedFiles = getSharedFiles(source);
        if (sharedFiles == null || sharedFiles.length == 0) {
            copyFileTree(source, target);
            return;
        }

        FileSystem fs = source.getFileSystem();
        Seq<PathMatcher> matchers = Seq.of(sharedFiles)
            .map(s -> fs.getPathMatcher("glob:" + s));

        walkFileTree(source, new SimpleFileVisitor()
        {
            @Override
            protected FileVisitResult visit(Path path) throws IOException {
                Path relativePath = source.relativize(path);
                Path targetPath = target.resolve(relativePath);

                if (matchers.anyMatch(m -> m.matches(relativePath))) {
                    createSymbolicLink(targetPath, path);
                    if (isDirectory(path)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                } else {
                    copy(path, targetPath, REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void extract(Path source, Path target)
        throws IOException
    {
        String name = source.getFileName().toString();
        container.exec(
            IO.with(name).<Exec>get()
              .when(endsWith(".zip"), () ->
                    Exec.args("unzip", source, "-d", target))
              .when(endsWith(".tar.gz").or(endsWith(".tgz")), () ->
                    Exec.args("tar", "-C", target, "-xzpf", source))
              .when(endsWith(".tar"), () ->
                    Exec.args("tar", "-C", target, "-xpf", source))
              .orElseThrow(() -> new IOException("Unsupported addon archive file: " + source)))
          .silentIO()
          .checkError()
          .run();
    }

    private void processTemplates(Path path, Map<String, String> env)
        throws IOException
    {
        container.processTemplates(path, getAddonEnviron(path, env), Etc.getuid() == 0);
    }

    private String[] getSharedFiles(Path source)
        throws IOException
    {
        Path shared_files = MoreFiles.join(source, "metadata", "shared_files");
        if (exists(shared_files)) {
            try (Stream<String> lines = lines(shared_files)) {
                return lines.map(String::trim)
                            .filter(not(String::isEmpty))
                            .toArray(String[]::new);
            }
        } else if (exists(source.resolve("share"))) {
            return new String[]{"share"};
        } else {
            return null;
        }
    }

    private static final Pattern GLOB_CHARS = Pattern.compile("[*?\\[\\]{}]");
    private static final Pattern PROTECTED_FILES = Pattern.compile("^\\.(ssh|tmp|env)");
    private static final String[] DEFAULT_LOCKED_FILES = {"env/", "env/*"};

    private Collection<String> getLockedFiles(Path target) throws IOException {
        return getLockedFiles(container.getHomeDir(), target);
    }

    // package private for unit test
    static Collection<String> getLockedFiles(Path home, Path target)
        throws IOException
    {
        Path locked_files = MoreFiles.join(target, "metadata", "locked_files");

        SortedSet<String> entries = new TreeSet<>();
        SortedSet<String> patterns = new TreeSet<>();

        try (Stream<String> lines = exists(locked_files) ? lines(locked_files) : Stream.empty()) {
            Stream.concat(lines, Stream.of(DEFAULT_LOCKED_FILES))
                .map(s -> makeRelative(home, target, s))
                .filter(Objects::nonNull)
                .forEach(entry -> {
                    if (GLOB_CHARS.matcher(entry).find()) {
                        patterns.add(entry);
                    } else {
                        entries.add(entry);
                    }
                });
        }

        if (!patterns.isEmpty()) {
            Seq<String> patternSeq = Seq.wrap(patterns);
            Seq<PathMatcher> matcherSeq = patternSeq
                .map(pattern -> home.getFileSystem().getPathMatcher("glob:" + pattern));

            walkFileTree(home, new SimpleFileVisitor()
            {
                @Override
                protected FileVisitResult visit(Path path) {
                    Path rel = home.relativize(path);
                    if (matcherSeq.anyMatch(m -> m.matches(rel))) {
                        entries.add(rel.toString());
                    }
                    if (isDirectory(path) && patternSeq.noneMatch(p -> home.resolve(p).startsWith(path))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return entries;
    }

    static String makeRelative(Path home, Path target, String entry) {
        if ((entry = entry.trim()).isEmpty() || entry.startsWith("#")) {
            return null;
        }

        Path path = entry.startsWith("~/")
            ? home.resolve(entry.substring(2))
            : target.resolve(entry);
        path = path.normalize();

        // Ensure the files are in the home directory
        if (!path.startsWith(home)) {
            // TODO: log warning
            System.err.println("invalid lock file entry: " + entry);
            return null;
        }

        // Get the relative path rooted at home directory
        String rel = home.relativize(path).toString();
        if (entry.endsWith("/") && !rel.endsWith("/")) {
            rel = rel.concat("/");
        }

        if (rel.startsWith(".")) {
            // Allow files in dot files/dirs except for protected files
            if (PROTECTED_FILES.matcher(rel).find()) {
                System.err.println("invalid lock file entry: " + entry);
                return null;
            }
        } else {
            // Only allow files in app or addon directory
            if (!path.startsWith(target) && !path.startsWith(home.resolve("app"))) {
                System.err.println("invalid lock file entry: " + entry);
                return null;
            }
        }

        return rel;
    }

    private void with_unlocked(Path target, boolean relock, IOAction action)
        throws IOException
    {
        do_unlock(target, getLockedFiles(target));
        try {
            action.perform();
        } finally {
            if (relock) {
                do_lock(target, getLockedFiles(target));
            }
        }
    }

    private void do_lock(Path target, Collection<String> entries)
        throws IOException
    {
        Path home = container.getHomeDir();

        IO.forEach(entries, entry -> {
            Path path = home.resolve(entry);
            if (exists(path)) {
                container.setFileReadOnly(path);

                // Remove template file if the generated file is locked,
                // so the template will never process again.
                if (isRegularFile(path)) {
                    String filename = path.getFileName().toString();
                    String ext = DefaultApplicationContainer.TEMPLATE_EXT;
                    deleteIfExists(path.resolveSibling(filename + ext));
                    deleteIfExists(path.resolveSibling("." + filename + ext));
                }
            }
        });

        container.setFileReadOnly(home);
        container.setFileReadOnly(target);
    }

    private void do_unlock(Path target, Collection<String> entries)
        throws IOException
    {
        Path home = container.getHomeDir();

        IO.forEach(entries, entry -> {
            Path path = home.resolve(entry);
            if (!exists(path)) {
                if (entry.endsWith("/")) {
                    mkdir(path, 0755);
                } else {
                    touch(path, 0644);
                }
            }
            container.setFileReadWrite(path);
        });

        container.setFileReadWrite(home);
        container.setFileReadWrite(target);
    }

    private void do_validate(Path path, IOAction action)
        throws IOException
    {
        Set<String> before_run = listHomeDir();
        action.perform();
        Set<String> after_run = listHomeDir();

        after_run.removeAll(before_run);
        if (!after_run.isEmpty()) {
            throw new IllegalStateException(
                "Add-on created the following files or directories in the home directory: " +
                String.join(", ", after_run));
        }

        Set<String> env_keys = Environ.list(container.getEnvDir());
        env_keys.retainAll(Environ.list(path.resolve("env")));
        if (!env_keys.isEmpty()) {
            throw new IllegalStateException(
                "Add-on attempted to override the following environment variables: " +
                String.join(", ", env_keys));
        }
    }

    private Set<String> listHomeDir() throws IOException {
        try (Stream<Path> files = list(container.getHomeDir())) {
            return files.map(f -> f.getFileName().toString())
                        .filter(not(startsWith(".")))
                        .collect(toSet());
        }
    }

    private void createPrivateEndpoints(Addon addon) {
        if (addon.getEndpoints().isEmpty()) {
            return;
        }

        Map<String, String> env = container.environ();

        // Collect all existing endpoint IP allocations
        Set<String> allocated_ips = validAddons()
            .flatMap(a -> a.getEndpoints().stream())
            .map(a -> env.get(a.getPrivateHostName()))
            .filter(Objects::nonNull)
            .collect(toSet());

        addon.getEndpoints().stream().forEach(endpoint -> {
            String host_name = endpoint.getPrivateHostName();
            String port_name = endpoint.getPrivatePortName();

            // Reuse previously allocated IPs of the same name.
            String ip = env.get(host_name);
            if (ip == null) {
                // Find the next IP address available for the current guest user.
                // The IP is assumed to be available only if IP is not already
                // associated with an existing endpoint defined by any addon
                // within the application
                ip = IntStream.rangeClosed(1, 127)
                    .mapToObj(container::getIpAddress)
                    .filter(not(in(allocated_ips)))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException(
                        format("No IP was available for endpoint %s(%s)", host_name, port_name)));

                container.setenv(host_name, ip);
                env.put(host_name, ip);
                allocated_ips.add(ip);
            }
            endpoint.setPrivateHost(ip);

            String port = String.valueOf(endpoint.getPrivatePort());
            if (!env.containsKey(port_name)) {
                container.setenv(port_name, port);
                env.put(port_name, port);
            }
        });

        // Validate all the allocations to ensure they are not already bound.
        String failure = addon.getEndpoints().stream()
            .filter(ep -> container.isAddressInUse(ep.getPrivateHost(), ep.getPrivatePort()))
            .map(ep -> format("%s(%s)=%s(%d)", ep.getPrivateHostName(), ep.getPrivatePortName(),
                                               ep.getPrivateHost(),     ep.getPrivatePort()))
            .collect(joining(", "));

        if (!failure.isEmpty()) {
            throw new IllegalStateException("Failed to create the following endpoints: " + failure);
        }
    }

    private void deletePrivateEndpoints(Addon addon) {
        addon.getEndpoints().forEach(endpoint -> {
            container.unsetenv(endpoint.getPrivateHostName());
            container.unsetenv(endpoint.getPrivatePortName());
        });
    }

    public void addProxyMappings(Addon addon) throws IOException {
        List<ProxyMapping> mappings = addon.getProxyMappings();
        if (!mappings.isEmpty()) {
            HttpProxy.addMappings(container, mappings);
        }
    }

    public void removeProxyMappings(Addon addon) {
        List<ProxyMapping> mappings = addon.getProxyMappings();
        if (!mappings.isEmpty()) {
            try {
                HttpProxy.removeMappings(container, mappings);
            } catch (IOException ex) {
                ex.printStackTrace(); // FIXME
            }
        }
    }

    private void populateRepository(Path path, String url)
        throws IOException
    {
        ApplicationRepository repo = ApplicationRepository.of(container);

        IO.with(url)
          .when(nullOrEmpty(), () -> repo.populateFromTemplate(path))
          .when(is("empty"),   () -> repo.populateEmpty())
          .otherwise(          () -> repo.populateFromURL(url));

        if (repo.exists()) {
            repo.checkout(container.getRepoDir());
        }
    }

    @Override
    public void control(String action, boolean enable_action_hooks, boolean process_templates)
        throws IOException
    {
        Map<String, String> env = container.environ();

        if (enable_action_hooks) {
            do_action_hook("pre_" + action, env);
        }

        IO.forEach(validAddons().map(Addon::getPath), path -> {
            if (process_templates)
                processTemplates(path, env);
            do_action(path, env, "control", action);
        });

        if (enable_action_hooks) {
            do_action_hook("post_" + action, env);
        }
    }

    private void do_action(Path path, Map<String,String> env, String action, String... args)
        throws IOException
    {
        Path executable = MoreFiles.join(path, "bin", action);
        if (isExecutable(executable)) { // FIXME: log warning for unexecutable script
            Exec exec = Exec.args(executable);
            exec.command().addAll(Arrays.asList(args));
            container.join(exec)
                     .directory(path)
                     .environment(getAddonEnviron(path, env))
                     .run();
        }
    }

    private void do_action_hook(String action, Map<String,String> env)
        throws IOException
    {
        Path hooks_dir = MoreFiles.join(container.getRepoDir(), ".cloudway", "hooks");
        Path action_hook = hooks_dir.resolve(action);
        if (isExecutable(action_hook)) {
            container.join(Exec.args(action_hook))
                     .environment(env)
                     .run();
        }
    }

    private Map<String, String> getAddonEnviron(Path path, Map<String, String> app_env) {
        // make sure the addon's environments overrides that of other addons
        Map<String, String> env =
            app_env == null ? container.environ()
                            : new HashMap<>(app_env);
        env.putAll(Environ.load(path.resolve("env")));
        return env;
    }
}
