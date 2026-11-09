package com.linklimiter;

import com.linklimiter.config.AppConfig;
import com.linklimiter.repository.InMemoryNotificationRepository;
import com.linklimiter.repository.InMemoryShortLinkRepository;
import com.linklimiter.repository.InMemoryUserRepository;
import com.linklimiter.service.LinkService;
import com.linklimiter.service.ShortCodeGenerator;
import com.linklimiter.ui.BrowserLauncher;
import com.linklimiter.ui.ConsoleApplication;

import java.time.Clock;
import java.util.Scanner;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.defaultConfig();
        try (LinkService linkService = new LinkService(
                new InMemoryUserRepository(),
                new InMemoryShortLinkRepository(),
                new InMemoryNotificationRepository(),
                new ShortCodeGenerator(),
                config,
                Clock.systemUTC()
        )) {
            ConsoleApplication application = new ConsoleApplication(
                    linkService,
                    new BrowserLauncher(),
                    config,
                    new Scanner(System.in)
            );
            application.run();
        }
    }
}
