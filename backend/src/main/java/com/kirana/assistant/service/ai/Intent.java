package com.kirana.assistant.service.ai;

/** Intents the LLM / rule-based parser can return. */
public enum Intent {
    ADD_ITEM,
    REMOVE_ITEM,
    UPDATE_QUANTITY,
    CREATE_ORDER,
    CONFIRM_ORDER,
    CANCEL_ORDER,
    ASK_QUESTION,
    UNKNOWN
}
