package com.gabriel0liv.partialreload.recipe;

public enum RecipeSerializerTagSafety {
    TAG_INDEPENDENT_DURING_PARSE,
    STORES_TAG_KEY_ONLY,
    READS_ACTIVE_TAG_MEMBERS,
    STORES_ACTIVE_HOLDER_SET,
    UNKNOWN_TAG_BEHAVIOR
}
