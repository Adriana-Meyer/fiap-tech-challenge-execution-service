package com.fiap.workshop.execution.infrastructure.security;

/**
 * Identity extracted from a verified JWT. Tokens are issued by the OS service;
 * this service never looks the user up, it trusts the signed claims.
 */
public record AuthenticatedUser(String username, String role) {}
