/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package cloudway;

import java.io.IOException;
import java.util.List;

import com.cloudway.platform.container.Addon;
import com.cloudway.platform.container.ApplicationContainer;

@SuppressWarnings("unused")
public class UserControl extends Control
{
    private ApplicationContainer getContainer() {
        try {
            String id = System.getenv("CLOUDWAY_APP_ID");
            return ApplicationContainer.fromId(id);
        } catch (Exception ex) {
            System.err.println("This program must run in a cloudway container.");
            System.exit(2);
            return null;
        }
    }

    @Command("Show application information")
    public void info(String[] args) {
        ApplicationContainer container = getContainer();
        System.out.println("ID:      " + container.getId());
        System.out.println("Name:    " + container.getName());
        System.out.println("DNS:     " + container.getDomainName());
        System.out.println("Size:    " + container.getCapacity());
        System.out.println("State:   " + container.getState());
    }

    @Command("Start the application")
    public void start(String[] args) throws IOException {
        getContainer().start();
    }

    @Command("Stop the application")
    public void stop(String[] args) throws IOException {
        getContainer().stop();
    }

    @Command("Restart the application")
    public void restart(String[] args) throws IOException {
        getContainer().restart();
    }

    @Command("Cleanup application data")
    public void tidy(String[] args) throws IOException {
        getContainer().tidy();
    }

    @Command("Show current application status")
    public void status(String[] args) throws IOException {
        getContainer().control("status", false);
    }

    @Command("Install add-on into application")
    public void install(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("usage: cwctl install SOURCE [REPO]");
            System.exit(1);
            return;
        }

        String source = args[1];
        String repo   = args.length > 1 ? args[1] : null;
        install(getContainer(), source, repo);
    }

    @Command("Uninstall add-on from application")
    public void uninstall(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: cwctl uninstall NAME");
            System.exit(1);
            return;
        }

        getContainer().remove(args[0]);
    }

    public void endpoints(String[] args) {
        getContainer().getEndpoints().forEach(ep ->
            System.out.printf("%-16s %-8s %s%n", ep.getPrivateIP(), ep.getPrivatePort(), ep.getInfo())
        );
    }

    public void pre_receive(String[] args) throws IOException {
        getContainer().pre_receive();
    }

    public void post_receive(String[] args) throws IOException {
        getContainer().post_receive();
    }
}
