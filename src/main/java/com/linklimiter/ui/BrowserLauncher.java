package com.linklimiter.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public final class BrowserLauncher {
    public boolean open(URI uri) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return false;
        }

        try {
            desktop.browse(uri);
            return true;
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }
}
