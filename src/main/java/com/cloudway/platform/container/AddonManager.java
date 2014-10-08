/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.platform.container;

import java.io.IOException;
import java.util.Optional;

public class AddonManager
{
    private final ApplicationContainer container;

    public AddonManager(ApplicationContainer container) {
        this.container = container;
    }

    public Optional<Addon> getAddon(String name) {
        return Optional.empty(); // TODO
    }

    public Addon getPrimaryAddon() {
        throw new UnsupportedOperationException();
    }

    public void start() throws IOException {
        // TODO
    }

    public void stop() throws IOException {
        // TODO
    }

    public void tidy() throws IOException {
        // TODO
    }

    public void destroy() throws IOException {
        // TODO
    }
}