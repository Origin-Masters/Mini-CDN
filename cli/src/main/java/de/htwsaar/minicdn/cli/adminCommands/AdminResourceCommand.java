package de.htwsaar.minicdn.cli.adminCommands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "resource",
        description = "Manage CDN resources",
        subcommands = {
                AdminResourceCommand.AdminResourceAddCommand.class,
                AdminResourceCommand.AdminResourceUpdateCommand.class,
                AdminResourceCommand.AdminResourceDeleteCommand.class,
                AdminResourceCommand.AdminResourceListCommand.class,
                AdminResourceCommand.AdminResourceShowCommand.class
        })
public class AdminResourceCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "add", description = "Add a new resource")
    public static class AdminResourceAddCommand implements Runnable {

        @Option(names = "--path", required = true, description = "Resource path, e.g. /img/logo.png")
        String path;

        @Option(names = "--origin", required = true, description = "Origin server URL")
        String origin;

        @Option(names = "--cache-ttl", required = true, description = "Cache time-to-live in seconds")
        int cacheTtl;

        @Override
        public void run() {
            // TODO: ResourceService.create(...)
            System.out.printf("[ADMIN] Add resource: path=%s, origin=%s, ttl=%d%n", path, origin, cacheTtl);
        }
    }

    @Command(name = "update", description = "Update resource configuration")
    public static class AdminResourceUpdateCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Option(names = "--path", description = "New path (optional)")
        String path;

        @Option(names = "--origin", description = "New origin URL (optional)")
        String origin;

        @Option(names = "--cache-ttl", description = "New cache TTL in seconds (optional)")
        Integer cacheTtl;

        @Override
        public void run() {
            // TODO: ResourceService.update(...)
            System.out.printf("[ADMIN] Update resource %d (path=%s, origin=%s, ttl=%s)%n", id, path, origin, cacheTtl);
        }
    }

    @Command(name = "delete", description = "Delete a resource")
    public static class AdminResourceDeleteCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Override
        public void run() {
            // TODO: ResourceService.delete(id)
            System.out.printf("[ADMIN] Delete resource %d%n", id);
        }
    }

    @Command(name = "list", description = "List resources")
    public static class AdminResourceListCommand implements Runnable {

        @Option(names = "--page", description = "Page number", defaultValue = "1")
        int page;

        @Option(names = "--size", description = "Page size", defaultValue = "20")
        int size;

        @Override
        public void run() {
            // TODO: ResourceService.list(page, size)
            System.out.printf("[ADMIN] List resources page=%d size=%d%n", page, size);
        }
    }

    @Command(name = "show", description = "Show resource details")
    public static class AdminResourceShowCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Override
        public void run() {
            // TODO: ResourceService.show(id)
            System.out.printf("[ADMIN] Show resource %d%n", id);
        }
    }
}
