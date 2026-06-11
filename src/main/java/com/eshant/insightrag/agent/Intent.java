package com.eshant.insightrag.agent;

/**
 * What kind of question the user asked, and therefore which tool answers it. This is the decision the
 * agent makes on every query — the "agentic" routing the project demonstrates.
 */
public enum Intent {

    /** A conceptual / how-to question best answered from the documentation via RAG. */
    DOC_QA,

    /** A request for precise structured facts (exact versions, release dates) best answered by SQL. */
    STRUCTURED_DATA
}
