/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.web;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.coyote.AbstractProtocol;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.boot.bind.RelaxedDataBinder;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.undertow.UndertowEmbeddedServletContainerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DefaultServletContainerCustomizer}.
 *
 * @author Brian Clozel
 */
public class DefaultServletContainerCustomizerTests {

	private final ServerProperties properties = new ServerProperties();

	private DefaultServletContainerCustomizer customizer;

	@Before
	public void setup() throws Exception {
		MockitoAnnotations.initMocks(this);
		this.customizer = new DefaultServletContainerCustomizer(this.properties);
	}

	@Test
	public void tomcatAccessLogIsDisabledByDefault() {
		TomcatEmbeddedServletContainerFactory tomcatContainer = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(tomcatContainer);
		assertThat(tomcatContainer.getEngineValves()).isEmpty();
	}

	@Test
	public void tomcatAccessLogCanBeEnabled() {
		TomcatEmbeddedServletContainerFactory tomcatContainer = new TomcatEmbeddedServletContainerFactory();
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.accesslog.enabled", "true");
		bindProperties(map);
		this.customizer.customize(tomcatContainer);
		assertThat(tomcatContainer.getEngineValves()).hasSize(1);
		assertThat(tomcatContainer.getEngineValves()).first()
				.isInstanceOf(AccessLogValve.class);
	}

	@Test
	public void tomcatAccessLogIsBufferedByDefault() {
		TomcatEmbeddedServletContainerFactory tomcatContainer = new TomcatEmbeddedServletContainerFactory();
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.accesslog.enabled", "true");
		bindProperties(map);
		this.customizer.customize(tomcatContainer);
		assertThat(((AccessLogValve) tomcatContainer.getEngineValves().iterator().next())
				.isBuffered()).isTrue();
	}

	@Test
	public void tomcatAccessLogBufferingCanBeDisabled() {
		TomcatEmbeddedServletContainerFactory tomcatContainer = new TomcatEmbeddedServletContainerFactory();
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.accesslog.enabled", "true");
		map.put("server.tomcat.accesslog.buffered", "false");
		bindProperties(map);
		this.customizer.customize(tomcatContainer);
		assertThat(((AccessLogValve) tomcatContainer.getEngineValves().iterator().next())
				.isBuffered()).isFalse();
	}

	@Test
	public void redirectContextRootCanBeConfigured() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.redirect-context-root", "false");
		bindProperties(map);
		ServerProperties.Tomcat tomcat = this.properties.getTomcat();
		assertThat(tomcat.getRedirectContextRoot()).isEqualTo(false);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		Context context = mock(Context.class);
		for (TomcatContextCustomizer customizer : container
				.getTomcatContextCustomizers()) {
			customizer.customize(context);
		}
		verify(context).setMapperContextRootRedirectEnabled(false);
	}

	@Test
	public void testCustomizeTomcat() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.customizer.customize(factory);
		verify(factory, never()).setContextPath("");
	}

	@Test
	public void testDefaultDisplayName() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.customizer.customize(factory);
		verify(factory).setDisplayName("application");
	}

	@Test
	public void testCustomizeDisplayName() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.properties.setDisplayName("TestName");
		this.customizer.customize(factory);
		verify(factory).setDisplayName("TestName");
	}

	@Test
	public void customizeSessionProperties() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.session.timeout", "123");
		map.put("server.session.tracking-modes", "cookie,url");
		map.put("server.session.cookie.name", "testname");
		map.put("server.session.cookie.domain", "testdomain");
		map.put("server.session.cookie.path", "/testpath");
		map.put("server.session.cookie.comment", "testcomment");
		map.put("server.session.cookie.http-only", "true");
		map.put("server.session.cookie.secure", "true");
		map.put("server.session.cookie.max-age", "60");
		bindProperties(map);
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		ServletContext servletContext = mock(ServletContext.class);
		SessionCookieConfig sessionCookieConfig = mock(SessionCookieConfig.class);
		given(servletContext.getSessionCookieConfig()).willReturn(sessionCookieConfig);
		this.customizer.customize(factory);
		triggerInitializers(factory, servletContext);
		verify(factory).setSessionTimeout(123);
		verify(servletContext).setSessionTrackingModes(
				EnumSet.of(SessionTrackingMode.COOKIE, SessionTrackingMode.URL));
		verify(sessionCookieConfig).setName("testname");
		verify(sessionCookieConfig).setDomain("testdomain");
		verify(sessionCookieConfig).setPath("/testpath");
		verify(sessionCookieConfig).setComment("testcomment");
		verify(sessionCookieConfig).setHttpOnly(true);
		verify(sessionCookieConfig).setSecure(true);
		verify(sessionCookieConfig).setMaxAge(60);
	}

	@Test
	public void testCustomizeTomcatPort() throws Exception {
		ConfigurableEmbeddedServletContainer factory = mock(
				ConfigurableEmbeddedServletContainer.class);
		this.properties.setPort(8080);
		this.customizer.customize(factory);
		verify(factory).setPort(8080);
	}

	@Test
	public void customizeTomcatDisplayName() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.display-name", "MyBootApp");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		assertThat(container.getDisplayName()).isEqualTo("MyBootApp");
	}

	@Test
	public void disableTomcatRemoteIpValve() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.remote_ip_header", "");
		map.put("server.tomcat.protocol_header", "");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		assertThat(container.getEngineValves()).isEmpty();
	}

	@Test
	public void defaultTomcatBackgroundProcessorDelay() throws Exception {
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		assertThat(
				((TomcatEmbeddedServletContainer) container.getEmbeddedServletContainer())
						.getTomcat().getEngine().getBackgroundProcessorDelay())
				.isEqualTo(30);
	}

	@Test
	public void customTomcatBackgroundProcessorDelay() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.background-processor-delay", "5");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		assertThat(
				((TomcatEmbeddedServletContainer) container.getEmbeddedServletContainer())
						.getTomcat().getEngine().getBackgroundProcessorDelay())
				.isEqualTo(5);
	}

	@Test
	public void defaultTomcatRemoteIpValve() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		// Since 1.1.7 you need to specify at least the protocol
		map.put("server.tomcat.protocol_header", "X-Forwarded-Proto");
		map.put("server.tomcat.remote_ip_header", "X-Forwarded-For");
		bindProperties(map);
		testRemoteIpValveConfigured();
	}

	@Test
	public void setUseForwardHeadersTomcat() throws Exception {
		// Since 1.3.0 no need to explicitly set header names if use-forward-header=true
		this.properties.setUseForwardHeaders(true);
		testRemoteIpValveConfigured();
	}

	@Test
	public void deduceUseForwardHeadersTomcat() throws Exception {
		this.customizer.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		testRemoteIpValveConfigured();
	}

	private void testRemoteIpValveConfigured() {
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		assertThat(container.getEngineValves()).hasSize(1);
		Valve valve = container.getEngineValves().iterator().next();
		assertThat(valve).isInstanceOf(RemoteIpValve.class);
		RemoteIpValve remoteIpValve = (RemoteIpValve) valve;
		assertThat(remoteIpValve.getProtocolHeader()).isEqualTo("X-Forwarded-Proto");
		assertThat(remoteIpValve.getProtocolHeaderHttpsValue()).isEqualTo("https");
		assertThat(remoteIpValve.getRemoteIpHeader()).isEqualTo("X-Forwarded-For");
		String expectedInternalProxies = "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" // 10/8
				+ "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" // 192.168/16
				+ "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" // 169.254/16
				+ "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" // 127/8
				+ "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" // 172.16/12
				+ "172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|"
				+ "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}";
		assertThat(remoteIpValve.getInternalProxies()).isEqualTo(expectedInternalProxies);
	}

	@Test
	public void customTomcatRemoteIpValve() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.remote_ip_header", "x-my-remote-ip-header");
		map.put("server.tomcat.protocol_header", "x-my-protocol-header");
		map.put("server.tomcat.internal_proxies", "192.168.0.1");
		map.put("server.tomcat.port-header", "x-my-forward-port");
		map.put("server.tomcat.protocol-header-https-value", "On");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		assertThat(container.getEngineValves()).hasSize(1);
		Valve valve = container.getEngineValves().iterator().next();
		assertThat(valve).isInstanceOf(RemoteIpValve.class);
		RemoteIpValve remoteIpValve = (RemoteIpValve) valve;
		assertThat(remoteIpValve.getProtocolHeader()).isEqualTo("x-my-protocol-header");
		assertThat(remoteIpValve.getProtocolHeaderHttpsValue()).isEqualTo("On");
		assertThat(remoteIpValve.getRemoteIpHeader()).isEqualTo("x-my-remote-ip-header");
		assertThat(remoteIpValve.getPortHeader()).isEqualTo("x-my-forward-port");
		assertThat(remoteIpValve.getInternalProxies()).isEqualTo("192.168.0.1");
	}

	@Test
	public void customTomcatAcceptCount() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.accept-count", "10");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory(
				0);
		this.customizer.customize(container);
		TomcatEmbeddedServletContainer embeddedContainer = (TomcatEmbeddedServletContainer) container
				.getEmbeddedServletContainer();
		embeddedContainer.start();
		try {
			assertThat(((AbstractProtocol<?>) embeddedContainer.getTomcat().getConnector()
					.getProtocolHandler()).getBacklog()).isEqualTo(10);
		}
		finally {
			embeddedContainer.stop();
		}
	}

	@Test
	public void customTomcatMaxConnections() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.max-connections", "5");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory(
				0);
		this.customizer.customize(container);
		TomcatEmbeddedServletContainer embeddedContainer = (TomcatEmbeddedServletContainer) container
				.getEmbeddedServletContainer();
		embeddedContainer.start();
		try {
			assertThat(((AbstractProtocol<?>) embeddedContainer.getTomcat().getConnector()
					.getProtocolHandler()).getMaxConnections()).isEqualTo(5);
		}
		finally {
			embeddedContainer.stop();
		}
	}

	@Test
	public void customTomcatMaxHttpPostSize() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.max-http-post-size", "10000");
		bindProperties(map);
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory(
				0);
		this.customizer.customize(container);
		TomcatEmbeddedServletContainer embeddedContainer = (TomcatEmbeddedServletContainer) container
				.getEmbeddedServletContainer();
		embeddedContainer.start();
		try {
			assertThat(embeddedContainer.getTomcat().getConnector().getMaxPostSize())
					.isEqualTo(10000);
		}
		finally {
			embeddedContainer.stop();
		}
	}

	@Test
	public void customizeUndertowAccessLog() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.undertow.accesslog.enabled", "true");
		map.put("server.undertow.accesslog.pattern", "foo");
		map.put("server.undertow.accesslog.prefix", "test_log");
		map.put("server.undertow.accesslog.suffix", "txt");
		map.put("server.undertow.accesslog.dir", "test-logs");
		map.put("server.undertow.accesslog.rotate", "false");
		bindProperties(map);
		UndertowEmbeddedServletContainerFactory container = spy(
				new UndertowEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setAccessLogEnabled(true);
		verify(container).setAccessLogPattern("foo");
		verify(container).setAccessLogPrefix("test_log");
		verify(container).setAccessLogSuffix("txt");
		verify(container).setAccessLogDirectory(new File("test-logs"));
		verify(container).setAccessLogRotate(false);
	}

	@Test
	public void testCustomizeTomcatMinSpareThreads() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.min-spare-threads", "10");
		bindProperties(map);
		assertThat(this.properties.getTomcat().getMinSpareThreads()).isEqualTo(10);
	}

	@Test
	public void customTomcatTldSkip() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.additional-tld-skip-patterns", "foo.jar,bar.jar");
		bindProperties(map);
		testCustomTomcatTldSkip("foo.jar", "bar.jar");
	}

	@Test
	public void customTomcatTldSkipAsList() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.tomcat.additional-tld-skip-patterns[0]", "biz.jar");
		map.put("server.tomcat.additional-tld-skip-patterns[1]", "bah.jar");
		bindProperties(map);
		testCustomTomcatTldSkip("biz.jar", "bah.jar");
	}

	private void testCustomTomcatTldSkip(String... expectedJars) {
		TomcatEmbeddedServletContainerFactory container = new TomcatEmbeddedServletContainerFactory();
		this.customizer.customize(container);
		assertThat(container.getTldSkipPatterns()).contains(expectedJars);
		assertThat(container.getTldSkipPatterns()).contains("junit-*.jar",
				"spring-boot-*.jar");
	}

	@Test
	public void defaultUseForwardHeadersUndertow() throws Exception {
		UndertowEmbeddedServletContainerFactory container = spy(
				new UndertowEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setUseForwardHeaders(false);
	}

	@Test
	public void setUseForwardHeadersUndertow() throws Exception {
		this.properties.setUseForwardHeaders(true);
		UndertowEmbeddedServletContainerFactory container = spy(
				new UndertowEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void deduceUseForwardHeadersUndertow() throws Exception {
		this.customizer.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		UndertowEmbeddedServletContainerFactory container = spy(
				new UndertowEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void defaultUseForwardHeadersJetty() throws Exception {
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setUseForwardHeaders(false);
	}

	@Test
	public void setUseForwardHeadersJetty() throws Exception {
		this.properties.setUseForwardHeaders(true);
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void deduceUseForwardHeadersJetty() throws Exception {
		this.customizer.setEnvironment(new MockEnvironment().withProperty("DYNO", "-"));
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setUseForwardHeaders(true);
	}

	@Test
	public void sessionStoreDir() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		map.put("server.session.store-dir", "myfolder");
		bindProperties(map);
		JettyEmbeddedServletContainerFactory container = spy(
				new JettyEmbeddedServletContainerFactory());
		this.customizer.customize(container);
		verify(container).setSessionStoreDir(new File("myfolder"));
	}

	@Test
	public void skipNullElementsForUndertow() throws Exception {
		UndertowEmbeddedServletContainerFactory container = mock(
				UndertowEmbeddedServletContainerFactory.class);
		this.customizer.customize(container);
		verify(container, never()).setAccessLogEnabled(anyBoolean());
	}

	private void triggerInitializers(ConfigurableEmbeddedServletContainer container,
			ServletContext servletContext) throws ServletException {
		verify(container, atLeastOnce())
				.addInitializers(this.initializersCaptor.capture());
		for (Object initializers : this.initializersCaptor.getAllValues()) {
			if (initializers instanceof ServletContextInitializer) {
				((ServletContextInitializer) initializers).onStartup(servletContext);
			}
			else {
				for (ServletContextInitializer initializer : (ServletContextInitializer[]) initializers) {
					initializer.onStartup(servletContext);
				}
			}
		}
	}

	@Captor
	private ArgumentCaptor<ServletContextInitializer[]> initializersCaptor;

	private void bindProperties(Map<String, String> map) {
		new RelaxedDataBinder(this.properties, "server")
				.bind(new MutablePropertyValues(map));
	}
}
