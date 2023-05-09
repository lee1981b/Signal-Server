/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.protobuf.InvalidProtocolBufferException;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.whispersystems.websocket.WebSocketResourceProvider;
import org.whispersystems.websocket.auth.WebsocketAuthValueFactoryProvider;
import org.whispersystems.websocket.logging.WebsocketRequestLog;
import org.whispersystems.websocket.messages.protobuf.ProtobufWebSocketMessageFactory;
import org.whispersystems.websocket.messages.protobuf.SubProtocol;
import org.whispersystems.websocket.session.WebSocketSessionContextValueFactoryProvider;

class MetricsRequestEventListenerTest {

  private MeterRegistry meterRegistry;
  private Counter counter;
  private MetricsRequestEventListener listener;

  private static final TrafficSource TRAFFIC_SOURCE = TrafficSource.HTTP;

  @BeforeEach
  void setup() {
    meterRegistry = mock(MeterRegistry.class);
    counter = mock(Counter.class);
    listener = new MetricsRequestEventListener(TRAFFIC_SOURCE, meterRegistry);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testOnEvent() {
    final String path = "/test";
    final String method = "GET";
    final int statusCode = 200;

    final ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(uriInfo.getMatchedTemplates()).thenReturn(Collections.singletonList(new UriTemplate(path)));

    final ContainerRequest request = mock(ContainerRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getRequestHeader(HttpHeaders.USER_AGENT)).thenReturn(
        Collections.singletonList("Signal-Android/4.53.7 (Android 8.1)"));

    final ContainerResponse response = mock(ContainerResponse.class);
    when(response.getStatus()).thenReturn(statusCode);

    final RequestEvent event = mock(RequestEvent.class);
    when(event.getType()).thenReturn(RequestEvent.Type.FINISHED);
    when(event.getUriInfo()).thenReturn(uriInfo);
    when(event.getContainerRequest()).thenReturn(request);
    when(event.getContainerResponse()).thenReturn(response);

    final ArgumentCaptor<Iterable<Tag>> tagCaptor = ArgumentCaptor.forClass(Iterable.class);
    when(meterRegistry.counter(eq(MetricsRequestEventListener.REQUEST_COUNTER_NAME), any(Iterable.class)))
        .thenReturn(counter);

    listener.onEvent(event);

    verify(meterRegistry).counter(eq(MetricsRequestEventListener.REQUEST_COUNTER_NAME), tagCaptor.capture());

    final Iterable<Tag> tagIterable = tagCaptor.getValue();
    final Set<Tag> tags = new HashSet<>();

    for (final Tag tag : tagIterable) {
      tags.add(tag);
    }

    assertEquals(5, tags.size());
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.PATH_TAG, path)));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.METHOD_TAG, method)));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.STATUS_CODE_TAG, String.valueOf(statusCode))));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.TRAFFIC_SOURCE_TAG, TRAFFIC_SOURCE.name().toLowerCase())));
    assertTrue(tags.contains(Tag.of(UserAgentTagUtil.PLATFORM_TAG, "android")));
  }

  @Test
  void testActualRouteMessageSuccess() throws InvalidProtocolBufferException {
    final MetricsApplicationEventListener applicationEventListener = mock(MetricsApplicationEventListener.class);
    when(applicationEventListener.onRequest(any())).thenReturn(listener);

    final ResourceConfig resourceConfig = new DropwizardResourceConfig();
    resourceConfig.register(applicationEventListener);
    resourceConfig.register(new TestResource());
    resourceConfig.register(new WebSocketSessionContextValueFactoryProvider.Binder());
    resourceConfig.register(new WebsocketAuthValueFactoryProvider.Binder<>(TestPrincipal.class));
    resourceConfig.register(new JacksonMessageBodyProvider(new ObjectMapper()));

    final ApplicationHandler applicationHandler = new ApplicationHandler(resourceConfig);
    final WebsocketRequestLog requestLog = mock(WebsocketRequestLog.class);
    final WebSocketResourceProvider<TestPrincipal> provider = new WebSocketResourceProvider<>("127.0.0.1",
        applicationHandler,
        requestLog,
        new TestPrincipal("foo"),
        new ProtobufWebSocketMessageFactory(),
        Optional.empty(),
        30000);

    final Session session = mock(Session.class);
    final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
    final UpgradeRequest request = mock(UpgradeRequest.class);

    when(session.getUpgradeRequest()).thenReturn(request);
    when(session.getRemote()).thenReturn(remoteEndpoint);
    when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("Signal-Android/4.53.7 (Android 8.1)");
    when(request.getHeaders()).thenReturn(Map.of(HttpHeaders.USER_AGENT, List.of("Signal-Android/4.53.7 (Android 8.1)")));

    final ArgumentCaptor<Iterable<Tag>> tagCaptor = ArgumentCaptor.forClass(Iterable.class);
    when(meterRegistry.counter(eq(MetricsRequestEventListener.REQUEST_COUNTER_NAME), any(Iterable.class)))
        .thenReturn(counter);

    provider.onWebSocketConnect(session);

    byte[] message = new ProtobufWebSocketMessageFactory().createRequest(Optional.of(111L), "GET", "/v1/test/hello",
        new LinkedList<>(), Optional.empty()).toByteArray();

    provider.onWebSocketBinary(message, 0, message.length);

    final ArgumentCaptor<ByteBuffer> responseBytesCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
    verify(remoteEndpoint).sendBytesByFuture(responseBytesCaptor.capture());

    SubProtocol.WebSocketResponseMessage response = getResponse(responseBytesCaptor);

    assertThat(response.getStatus()).isEqualTo(200);

    verify(meterRegistry).counter(eq(MetricsRequestEventListener.REQUEST_COUNTER_NAME), tagCaptor.capture());

    final Iterable<Tag> tagIterable = tagCaptor.getValue();
    final Set<Tag> tags = new HashSet<>();

    for (final Tag tag : tagIterable) {
      tags.add(tag);
    }

    assertEquals(5, tags.size());
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.PATH_TAG, "/v1/test/hello")));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.METHOD_TAG, "GET")));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.STATUS_CODE_TAG, String.valueOf(200))));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.TRAFFIC_SOURCE_TAG, TRAFFIC_SOURCE.name().toLowerCase())));
    assertTrue(tags.contains(Tag.of(UserAgentTagUtil.PLATFORM_TAG, "android")));
  }

  @Test
  void testActualRouteMessageSuccessNoUserAgent() throws InvalidProtocolBufferException {
    final MetricsApplicationEventListener applicationEventListener = mock(MetricsApplicationEventListener.class);
    when(applicationEventListener.onRequest(any())).thenReturn(listener);

    final ResourceConfig resourceConfig = new DropwizardResourceConfig();
    resourceConfig.register(applicationEventListener);
    resourceConfig.register(new TestResource());
    resourceConfig.register(new WebSocketSessionContextValueFactoryProvider.Binder());
    resourceConfig.register(new WebsocketAuthValueFactoryProvider.Binder<>(TestPrincipal.class));
    resourceConfig.register(new JacksonMessageBodyProvider(new ObjectMapper()));

    final ApplicationHandler applicationHandler = new ApplicationHandler(resourceConfig);
    final WebsocketRequestLog requestLog = mock(WebsocketRequestLog.class);
    final WebSocketResourceProvider<TestPrincipal> provider = new WebSocketResourceProvider<>("127.0.0.1", applicationHandler,
        requestLog, new TestPrincipal("foo"), new ProtobufWebSocketMessageFactory(), Optional.empty(), 30000);

    final Session session = mock(Session.class);
    final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
    final UpgradeRequest request = mock(UpgradeRequest.class);

    when(session.getUpgradeRequest()).thenReturn(request);
    when(session.getRemote()).thenReturn(remoteEndpoint);

    final ArgumentCaptor<Iterable<Tag>> tagCaptor = ArgumentCaptor.forClass(Iterable.class);
    when(meterRegistry.counter(eq(MetricsRequestEventListener.REQUEST_COUNTER_NAME), any(Iterable.class))).thenReturn(
        counter);

    provider.onWebSocketConnect(session);

    final byte[] message = new ProtobufWebSocketMessageFactory().createRequest(Optional.of(111L), "GET", "/v1/test/hello",
        new LinkedList<>(), Optional.empty()).toByteArray();

    provider.onWebSocketBinary(message, 0, message.length);

    final ArgumentCaptor<ByteBuffer> responseBytesCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
    verify(remoteEndpoint).sendBytesByFuture(responseBytesCaptor.capture());

    SubProtocol.WebSocketResponseMessage response = getResponse(responseBytesCaptor);

    assertThat(response.getStatus()).isEqualTo(200);

    verify(meterRegistry).counter(eq(MetricsRequestEventListener.REQUEST_COUNTER_NAME), tagCaptor.capture());

    final Iterable<Tag> tagIterable = tagCaptor.getValue();
    final Set<Tag> tags = new HashSet<>();

    for (final Tag tag : tagIterable) {
      tags.add(tag);
    }

    assertEquals(5, tags.size());
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.PATH_TAG, "/v1/test/hello")));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.METHOD_TAG, "GET")));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.STATUS_CODE_TAG, String.valueOf(200))));
    assertTrue(tags.contains(Tag.of(MetricsRequestEventListener.TRAFFIC_SOURCE_TAG, TRAFFIC_SOURCE.name().toLowerCase())));
    assertTrue(tags.contains(Tag.of(UserAgentTagUtil.PLATFORM_TAG, "unrecognized")));
  }

  private static SubProtocol.WebSocketResponseMessage getResponse(ArgumentCaptor<ByteBuffer> responseCaptor)
      throws InvalidProtocolBufferException {

    return SubProtocol.WebSocketMessage.parseFrom(responseCaptor.getValue().array()).getResponse();
  }

  public static class TestPrincipal implements Principal {

    private final String name;

    private TestPrincipal(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  @Path("/v1/test")
  public static class TestResource {

    @GET
    @Path("/hello")
    public String testGetHello() {
      return "Hello!";
    }
  }
}
