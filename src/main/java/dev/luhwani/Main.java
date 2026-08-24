package dev.luhwani;

import dev.luhwani.application.TweetAuditApp;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        TweetAuditApp.run();
        // TODO: fix Google GenAI worker threads that keep running after application closes
        System.exit(1);
    }

}
