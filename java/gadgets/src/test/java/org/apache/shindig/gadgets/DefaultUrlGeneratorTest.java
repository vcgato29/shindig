/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.gadgets;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;

import org.apache.shindig.common.EasyMockTestCase;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.config.AbstractContainerConfig;
import org.apache.shindig.gadgets.features.FeatureRegistry;
import org.apache.shindig.gadgets.features.FeatureResource;
import org.apache.shindig.gadgets.spec.GadgetSpec;

import com.google.caja.util.Join;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringEscapeUtils;

import junitx.framework.StringAssert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tests for DefaultUrlGenerator.
 */
public class DefaultUrlGeneratorTest extends EasyMockTestCase {
  private static final String IFR_BASE = "/gadgets/eye-frame";
  private static final String JS_BASE = "http://%host%/get-together/livescript/%js%";
  private static final String SPEC_URL = "http://example.org/gadget.xml";
  private static final String TYPE_URL_HREF_HOST = "opensocial.org";
  private static final String TYPE_URL_HREF_PATH = "/app/foo";
  private static final String TYPE_URL_HREF_QUERY_FOO = "foo=bar";
  private static final String TYPE_URL_HREF_QUERY_BAR = "bar=baz";
  private static final String TYPE_URL_HREF = "http://" + TYPE_URL_HREF_HOST
      + TYPE_URL_HREF_PATH + '?' + TYPE_URL_HREF_QUERY_FOO + '&'
      + TYPE_URL_HREF_QUERY_BAR;
  private static final String UP_NAME = "user-pref-name";
  private static final String UP_VALUE = "user-pref-value";
  private static final String CONTAINER = "shindig";
  private static final String VIEW = "canvas";
  private static final int MODULE_ID = 3435;

  private final GadgetContext context = mock(GadgetContext.class);
  private final LockedDomainService lockedDomainService = mock(LockedDomainService.class);
  private final FeatureRegistry registry = mock(FeatureRegistry.class);
  private final FakeContainerConfig config = new FakeContainerConfig();
  private DefaultUrlGenerator urlGenerator;

  @Before
  public void setUp() throws Exception {
    expect(context.getContainer()).andReturn(CONTAINER).anyTimes();
    expect(context.getUrl()).andReturn(Uri.parse(SPEC_URL)).anyTimes();
    Map<String, String> prefMap = Maps.newHashMap();
    prefMap.put(UP_NAME, UP_VALUE);
    UserPrefs prefs = new UserPrefs(prefMap);
    expect(context.getUserPrefs()).andReturn(prefs).anyTimes();
    expect(context.getLocale()).andReturn(Locale.getDefault()).anyTimes();
    expect(context.getModuleId()).andReturn(MODULE_ID).anyTimes();
    expect(context.getView()).andReturn(VIEW).anyTimes();

    List<FeatureResource> features = Lists.newArrayList();

    expect(registry.getAllFeatures()).andReturn(features);

    config.properties.put(DefaultUrlGenerator.IFRAME_URI_PARAM, IFR_BASE);
    config.properties.put(DefaultUrlGenerator.JS_URI_PARAM, JS_BASE);

    // Yikes!
    replay(registry);
    urlGenerator = new DefaultUrlGenerator(config, lockedDomainService, registry);
    reset(registry);
  }

  @Test
  public void testGetBundledJsParamWithGoodFeatureName() throws Exception {
    List<String> features = ImmutableList.of(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        "abcdefghijklmnopqrstuvwxyz",
        "0123456789",
        "_.-");

    expect(context.getDebug()).andReturn(true);
    replay();

    String jsParam = urlGenerator.getBundledJsParam(features, context);

    assertTrue(jsParam.matches(Join.join(":", features) + "\\.js\\?v=[0-9a-zA-Z]*&container=" +
        CONTAINER + "&debug=1"));
  }

  @Test
  public void testGetBundledJsParamWithBadFeatureName() throws Exception {
    List<String> features = Lists.newArrayList();
    features.add("foo!");
    features.add("bar");
    expect(context.getDebug()).andReturn(true);
    replay();

    String jsParam = urlGenerator.getBundledJsParam(features, context);

    assertTrue(jsParam.matches("bar\\.js\\?v=[0-9a-zA-Z]*&container=" + CONTAINER + "&debug=1"));
  }

  @Test
  public void testGetBundledJsParamWithNoFeatures() throws Exception {
    List<String> features = Lists.newArrayList();
    expect(context.getDebug()).andReturn(false);
    replay();

    String jsParam = urlGenerator.getBundledJsParam(features, context);

    assertTrue(jsParam.matches("core\\.js\\?v=[0-9a-zA-Z]*&container=" + CONTAINER + "&debug=0"));
  }

  @Test
  public void testGetBundledJsUrl() throws Exception {
    List<String> features = Arrays.asList("foo", "bar");
    expect(context.getDebug()).andReturn(false);
    expect(context.getHost()).andReturn("example.org");
    replay();

    String jsParam = urlGenerator.getBundledJsUrl(features, context);

    Uri uri = Uri.parse(jsParam);

    assertEquals("example.org", uri.getAuthority());
    assertEquals("/get-together/livescript/foo:bar.js", uri.getPath());
    assertTrue("Missing checksum.", uri.getQueryParameter("v").matches("[0-9a-zA-Z]*"));
    assertEquals(CONTAINER, uri.getQueryParameter("container"));
    assertEquals("0", uri.getQueryParameter("debug"));
  }

  @Test
  public void testGetIframeUrlTypeHtml() throws Exception {
    String xml
        = "<Module>" +
          " <ModulePrefs title='test'/>" +
          " <Content type='html'/>" +
          " <UserPref name='" + UP_NAME + "' datatype='string'/>" +
          "</Module>";
    GadgetSpec spec = new GadgetSpec(Uri.parse(SPEC_URL), xml);
    replay();

    Gadget gadget = new Gadget()
        .setContext(context)
        .setSpec(spec)
        .setCurrentView(spec.getView("default"));

    Uri iframeUrl = Uri.parse(urlGenerator.getIframeUrl(gadget));

    assertEquals(IFR_BASE, iframeUrl.getPath());
    assertEquals(CONTAINER, iframeUrl.getQueryParameter("container"));
    assertEquals(UP_VALUE, iframeUrl.getQueryParameter("up_" + UP_NAME));
    assertEquals(Integer.toString(MODULE_ID), iframeUrl.getQueryParameter("mid"));
    assertEquals(VIEW, iframeUrl.getQueryParameter("view"));
  }

  @Test
  public void testGetIframeUrlTypeHtmlWithLockedDomain() throws Exception {
    String xml
        = "<Module>" +
          " <ModulePrefs title='test'/>" +
          " <Content type='html'/>" +
          " <UserPref name='" + UP_NAME + "' datatype='string'/>" +
          "</Module>";
    GadgetSpec spec = new GadgetSpec(Uri.parse(SPEC_URL), xml);

    expect(lockedDomainService.getLockedDomainForGadget(isA(Gadget.class), eq(CONTAINER)))
        .andReturn("locked.example.org");
    replay();

    Gadget gadget = new Gadget()
        .setContext(context)
        .setSpec(spec)
        .setCurrentView(spec.getView("default"));

    Uri iframeUrl = Uri.parse(urlGenerator.getIframeUrl(gadget));

    assertEquals("locked.example.org", iframeUrl.getAuthority());
    assertEquals(IFR_BASE, iframeUrl.getPath());
    assertEquals(CONTAINER, iframeUrl.getQueryParameter("container"));
    assertEquals(UP_VALUE, iframeUrl.getQueryParameter("up_" + UP_NAME));
    assertEquals(Integer.toString(MODULE_ID), iframeUrl.getQueryParameter("mid"));
    assertEquals(VIEW, iframeUrl.getQueryParameter("view"));
  }

  @Test
  public void testGetIframeUrlTypeUrl() throws Exception {
    String xml
        = "<Module>" +
          " <ModulePrefs title='test'/>" +
          " <Content type='url' href='" + StringEscapeUtils.escapeHtml(TYPE_URL_HREF) + "'/>" +
          " <UserPref name='" + UP_NAME + "' datatype='string'/>" +
          "</Module>";
    GadgetSpec spec = new GadgetSpec(Uri.parse(SPEC_URL), xml);
    replay();

    Gadget gadget = new Gadget()
        .setContext(context)
        .setSpec(spec)
        .setCurrentView(spec.getView("default"));

    URI iframeUrl = URI.create(urlGenerator.getIframeUrl(gadget));

    assertEquals(TYPE_URL_HREF_HOST, iframeUrl.getAuthority());
    assertEquals(TYPE_URL_HREF_PATH, iframeUrl.getPath());
    String query = iframeUrl.getQuery();
    StringAssert.assertContains(TYPE_URL_HREF_QUERY_FOO, query);
    StringAssert.assertContains(TYPE_URL_HREF_QUERY_BAR, query);
    StringAssert.assertContains("container=" + CONTAINER, iframeUrl.getQuery());
    StringAssert.assertContains("up_" + UP_NAME + '=' + UP_VALUE, iframeUrl.getQuery());
    StringAssert.assertContains("mid=" + MODULE_ID, iframeUrl.getQuery());
  }

  private static class FakeContainerConfig extends AbstractContainerConfig {
    protected final Map<String, String> properties = Maps.newHashMap();

    @Override
    public Object getProperty(String container, String property) {
      return properties.get(property);
    }

    @Override
    public Collection<String> getContainers() {
      return Arrays.asList(CONTAINER);
    }
  }
}
