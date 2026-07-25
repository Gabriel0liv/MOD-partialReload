package com.gabriel0liv.partialreload.kubejs;

public enum KubeJsScriptClassification {
    RECIPE_EVENT_ONLY,
    RECIPE_AND_OTHER_SERVER_EVENTS,
    NON_RECIPE_SERVER_SCRIPT,
    STARTUP_SCRIPT,
    CLIENT_SCRIPT,
    ADDON_DEPENDENT,
    DYNAMIC_OR_UNCLASSIFIABLE
}
