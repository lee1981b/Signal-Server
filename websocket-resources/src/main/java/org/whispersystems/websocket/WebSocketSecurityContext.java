/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.websocket;

import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.whispersystems.websocket.session.ContextPrincipal;
import org.whispersystems.websocket.session.WebSocketSessionContext;

public class WebSocketSecurityContext implements SecurityContext {

  private final ContextPrincipal principal;

  public WebSocketSecurityContext(ContextPrincipal principal) {
    this.principal = principal;
  }

  @Override
  public Principal getUserPrincipal() {
    return (Principal)principal.getContext().getAuthenticated();
  }

  @Override
  public boolean isUserInRole(String role) {
    return false;
  }

  @Override
  public boolean isSecure() {
    return principal != null;
  }

  @Override
  public String getAuthenticationScheme() {
    return null;
  }

  public WebSocketSessionContext getSessionContext() {
    return principal.getContext();
  }
}
