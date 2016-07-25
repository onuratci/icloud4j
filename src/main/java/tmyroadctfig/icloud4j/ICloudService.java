/*
 *    Copyright 2016 Luke Quinane
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package tmyroadctfig.icloud4j;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.apache.http.Consts;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import tmyroadctfig.icloud4j.util.JsonToMapResponseHandler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * The iCloud service.
 *
 * @author Luke Quinane
 */
public class ICloudService implements java.io.Closeable
{
    /**
     * A flag indicating whether to disable SSL checks.
     */
    private static final boolean DISABLE_SSL_CHECKS = Boolean.parseBoolean(System.getProperty("tmyroadctfig.icloud4j.disableSslChecks", "false"));

    /**
     * The proxy host to use.
     */
    private static final String PROXY_HOST = System.getProperty("http.proxyHost");

    /**
     * The proxy port to use.
     */
    private static final Integer PROXY_PORT = Integer.getInteger("http.proxyPort");

    /**
     * The end point.
     */
    public static final String endPoint = "https://www.icloud.com";

    /**
     * The setup end point.
     */
    public static final String setupEndPoint = "https://setup.icloud.com/setup/ws/1";

    /**
     * The client ID.
     */
    private final String clientId;

    /**
     * The HTTP client.
     */
    private final CloseableHttpClient httpClient;

    /**
     * The cookie store.
     */
    private final CookieStore cookieStore;

    /**
     * The login info.
     */
    private Map<String, Object> loginInfo;

    /**
     * The sesion ID.
     */
    private String dsid;

    /**
     * Creates a new iCloud service instance
     *
     * @param clientId the client ID.
     */
    public ICloudService(@Nonnull String clientId)
    {
        this.clientId = clientId;

        cookieStore = new BasicCookieStore();
        try
        {
            HttpClientBuilder clientBuilder = HttpClientBuilder
                .create()
                .setDefaultCookieStore(cookieStore);

            if (!Strings.isNullOrEmpty(PROXY_HOST))
            {
                clientBuilder.setProxy(new HttpHost(PROXY_HOST, PROXY_PORT));
            }

            if (DISABLE_SSL_CHECKS)
            {
                clientBuilder
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setSslcontext(new SSLContextBuilder()
                        .loadTrustMaterial(null, (x509CertChain, authType) -> true)
                        .build());
            }

            httpClient = clientBuilder.build();
        }
        catch (Exception e)
        {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Attempts to log in to iCloud.
     *
     * @param username the username.
     * @param password the password.
     */
    public Map<String, Object> authenticate(@Nonnull String username, @Nonnull char[] password)
    {
        try
        {
            URIBuilder uriBuilder = new URIBuilder(setupEndPoint + "/login");
            populateUriParameters(uriBuilder);
            URI uri = uriBuilder.build();

            Map<String, Object> params = ImmutableMap.of(
                "apple_id", username,
                "password", new String(password),
                "extended_login", false);

            HttpPost post = new HttpPost(uri);
            post.setEntity(new StringEntity(new Gson().toJson(params), Consts.UTF_8));
            populateRequestHeadersParameters(post);

            HttpResponse response = httpClient.execute(post);
            Map<String, Object> result = new JsonToMapResponseHandler().handleResponse(response);
            if (Boolean.FALSE.equals(result.get("success")))
            {
                throw new RuntimeException("Failed to log into iCloud: " + result.get("error"));
            }

            loginInfo = result;

            // Grab the session ID
            Map<String, Object> dsInfoMap = (Map<String, Object>) result.get("dsInfo");
            dsid = (String) dsInfoMap.get("dsid");

            return loginInfo;
        }
        catch (Exception e)
        {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Gets the login info.
     *
     * @return the login info.
     */
    public Map<String, Object> getLoginInfo()
    {
        return loginInfo;
    }

    /**
     * Gets the web services map.
     *
     * @return the web services map.
     */
    public Map<String, Object> getWebServicesMap()
    {
        //noinspection unchecked
        return (Map<String, Object>) loginInfo.get("webservices");
    }

    /**
     * Gets the HTTP client.
     *
     * @return the client.
     */
    public CloseableHttpClient getHttpClient()
    {
        return httpClient;
    }

    /**
     * Gets the client ID.
     *
     * @return the client ID.
     */
    public String getClientId()
    {
        return clientId;
    }

    /**
     * Populates the URI parameters for a request.
     *
     * @param uriBuilder the URI builder.
     */
    public void populateUriParameters(URIBuilder uriBuilder)
    {
        uriBuilder
            .addParameter("clientId", clientId)
            .addParameter("clientBuildNumber", "14E45");

        if (!Strings.isNullOrEmpty(dsid))
        {
            uriBuilder.addParameter("dsid", dsid);
        }
    }

    /**
     * Gets the session ID.
     *
     * @return the session ID.
     */
    public String getSessionId()
    {
        return dsid;
    }

    /**
     * Populates the HTTP request headers.
     *
     * @param request the request to populate.
     */
    public void populateRequestHeadersParameters(HttpRequestBase request)
    {
        request.setHeader("Origin", endPoint);
        request.setHeader("Referer", endPoint + "/");
        request.setHeader("User-Agent", "Opera/9.52 (X11; Linux i686; U; en)");
    }

    @Override
    public void close() throws IOException
    {
        httpClient.close();
    }
}