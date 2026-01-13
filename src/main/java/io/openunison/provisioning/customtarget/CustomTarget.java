package io.openunison.provisioning.customtarget;

public class CustomTarget {

    public String provision(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be empty");
        }

        return "provisioned:" + username;
    }
}