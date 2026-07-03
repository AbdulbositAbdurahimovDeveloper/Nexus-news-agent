package org.platform.nexus.llm;

/**
 * LLM provayderining transport qatlami. Qaysi implementatsiya bean bo'lishi
 * {@code llm.provider} (LLM_PROVIDER env) qiymatiga bog'liq.
 */
public interface LlmClient {

    /** Berilgan prompt uchun modeldan matnli javob oladi. */
    String complete(String prompt);

    /** Log va diagnostika uchun provayder nomi. */
    String providerName();
}
