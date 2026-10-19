package com.brainconsulting.adt.mcp.server;

/** Erro com mensagem pronta para mostrar ao usuário. */
public class McpException extends Exception {

    private static final long serialVersionUID = 1L;

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}
