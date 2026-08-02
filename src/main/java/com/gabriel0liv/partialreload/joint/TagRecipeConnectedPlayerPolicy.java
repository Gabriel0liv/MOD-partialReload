package com.gabriel0liv.partialreload.joint;

public enum TagRecipeConnectedPlayerPolicy {
    REJECT,
    DEFER_CLIENT_REFRESH_UNTIL_RELOGIN;

    public void validateConnectedPlayerCount(int connectedPlayerCount) {
        if (connectedPlayerCount < 0) {
            throw new IllegalArgumentException("connectedPlayerCount must be non-negative");
        }
        if (this == REJECT && connectedPlayerCount > 0) {
            throw new IllegalStateException("TAG_RECIPE_COMMIT_PLAYERS_CONNECTED");
        }
    }
}
