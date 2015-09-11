/*
 * Copyright (c) 2014 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 *     http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.asynchttpclient.ntlm;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.asynchttpclient.AbstractBasicTest;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.Realm;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.Realm.AuthScheme;
import org.asynchttpclient.Realm.RealmBuilder;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.Assert;
import org.testng.annotations.Test;

public abstract class NtlmTest extends AbstractBasicTest {

    public static class NTLMHandler extends AbstractHandler {

        @Override
        public void handle(String pathInContext, org.eclipse.jetty.server.Request request, HttpServletRequest httpRequest,
                HttpServletResponse httpResponse) throws IOException, ServletException {

            String authorization = httpRequest.getHeader("Authorization");
            if (authorization == null) {
                httpResponse.setStatus(401);
                httpResponse.setHeader("WWW-Authenticate", "NTLM");

            } else if (authorization.equals("NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==")) {
                httpResponse.setStatus(401);
                httpResponse.setHeader("WWW-Authenticate", "NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==");

            } else if (authorization
                    .equals("NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAASABIAmAAAAAAAAACqAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABMAGkAZwBoAHQAQwBpAHQAeQA=")) {
                httpResponse.setStatus(200);
            } else {
                httpResponse.setStatus(401);
            }

            httpResponse.setContentLength(0);
            httpResponse.getOutputStream().flush();
            httpResponse.getOutputStream().close();
        }
    }

    @Override
    public AbstractHandler configureHandler() throws Exception {
        return new NTLMHandler();
    }

    private RealmBuilder realmBuilderBase() {
        return new Realm.RealmBuilder()//
                .setScheme(AuthScheme.NTLM)//
                .setNtlmDomain("Ursa-Minor")//
                .setNtlmHost("LightCity")//
                .setPrincipal("Zaphod")//
                .setPassword("Beeblebrox");
    }

    private void ntlmAuthTest(RealmBuilder realmBuilder) throws IOException, InterruptedException, ExecutionException {

        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder().setRealm(realmBuilder.build()).build();

        try (AsyncHttpClient client = getAsyncHttpClient(config)) {
            Request request = new RequestBuilder("GET").setUrl(getTargetUrl()).build();
            Future<Response> responseFuture = client.executeRequest(request);
            int status = responseFuture.get().getStatusCode();
            Assert.assertEquals(status, 200);
        }
    }

    @Test
    public void lazyNTLMAuthTest() throws IOException, InterruptedException, ExecutionException {
        ntlmAuthTest(realmBuilderBase());
    }

    @Test
    public void preemptiveNTLMAuthTest() throws IOException, InterruptedException, ExecutionException {
        ntlmAuthTest(realmBuilderBase().setUsePreemptiveAuth(true));
    }
}

