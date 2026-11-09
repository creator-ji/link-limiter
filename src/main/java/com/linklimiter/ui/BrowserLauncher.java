package com.linklimiter.ui;

import java.net.URI;

@FunctionalInterface
public interface BrowserLauncher {
  boolean open(URI uri);
}
