package com.microsoft.aad.adal;

import android.content.Context;
import android.content.Intent;
import android.net.http.SslError;
import android.support.annotation.UiThread;
import android.test.AndroidTestCase;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;

import org.mockito.Mockito;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.microsoft.aad.adal.AuthenticationConstants.Browser.RESPONSE_ERROR_CODE;
import static com.microsoft.aad.adal.AuthenticationConstants.Browser.RESPONSE_ERROR_MESSAGE;

public class BasicWebViewClientTests extends AndroidTestCase {

    private static final String NONCE = "123123-123213-123";
    private static final String CONTEXT = "ABcdeded";
    private static final String TEST_PKEY_AUTH_URL = AuthenticationConstants.Broker.PKEYAUTH_REDIRECT
            + "?Nonce="
            + NONCE
            + "&CertAuthorities=ABC"
            + "&Version=1.0"
            + "&SubmitUrl=http://fs.contoso.com/adfs/services/trust&Context="
            + CONTEXT;
    private static final String TEST_CANCELLATION_URL =
            "https://cancel.com?error=cancel&error_description=bye";
    private static final String TEST_EXTERNAL_SITE_URL =
            AuthenticationConstants.Broker.BROWSER_EXT_PREFIX + "https://graph.microsoft.io";
    private static final String TEST_INSTALL_REQUEST_URL =
            AuthenticationConstants.Broker.BROWSER_EXT_INSTALL_PREFIX + "foo";

    private WebView mMockWebView;

    @UiThread
    @Override
    protected void setUp() throws Exception {
        mMockWebView = Mockito.mock(WebView.class);
        // Since BasicWebViewClient is abstract,
        // we'll subclass the entire class under test
        MockDeviceCertProxy.reset();
    }

    private BasicWebViewClient setUpWebViewClient(
            final Context context,
            final String redirect,
            final AuthenticationRequest request,
            final UIEvent uiEvent) {
        return new BasicWebViewClient(context, redirect, request, uiEvent) {
            @Override
            public void showSpinner(boolean status) {
                // Do nothing. Test Object.
            }

            @Override
            public void sendResponse(int returnCode, Intent responseIntent) {
                // Do nothing. Test Object.
            }

            @Override
            public void cancelWebViewRequest() {
                // Do nothing. Test Object.
            }

            @Override
            public void prepareForBrokerResumeRequest() {
                // Do nothing. Test Object.
            }

            @Override
            public void setPKeyAuthStatus(boolean status) {
                // Do nothing. Test Object.
            }

            @Override
            public void postRunnable(Runnable item) {
                // Do nothing. Test Object.
            }

            @Override
            public void processRedirectUrl(WebView view, String url) {
                // Do nothing. Test Object.
            }

            @Override
            public boolean processInvalidUrl(WebView view, String url) {
                return false;
            }
        };
    }

    @UiThreadTest
    @SmallTest
    public void testUrlOverrideHandlesPKeyAuthRedirect() {
        final BasicWebViewClient basicWebViewClient =
                setUpWebViewClient(
                        getContext(),
                        "",
                        new AuthenticationRequest(
                                "NA", // authority
                                "NA", // resource
                                "NA", // client
                                "NA", // redirect
                                "user", // loginhint,
                                false
                        ),
                        new UIEvent("")
                );
        assertTrue(basicWebViewClient.shouldOverrideUrlLoading(mMockWebView, TEST_PKEY_AUTH_URL));
    }

    @UiThreadTest
    @SmallTest
    public void testUrlOverrideHandlesCancellation() {
        final BasicWebViewClient basicWebViewClient =
                setUpWebViewClient(
                        getContext(),
                        TEST_CANCELLATION_URL,
                        new AuthenticationRequest(
                                "NA", // authority
                                "NA", // resource
                                "NA", // client
                                TEST_CANCELLATION_URL, // redirect
                                "user", // loginhint,
                                false
                        ),
                        new UIEvent("")
                );
        assertTrue(basicWebViewClient.shouldOverrideUrlLoading(mMockWebView, TEST_CANCELLATION_URL));
        Mockito.verify(mMockWebView, Mockito.times(1)).stopLoading();
    }

    @SmallTest
    public void testUrlOverrideHandlesExternalSiteRequests() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final BasicWebViewClient dummyClient = new BasicWebViewClient(
                getContext(),
                "www.redirect.com",
                new AuthenticationRequest(
                        "NA",
                        "NA",
                        "NA",
                        "NA",
                        "user",
                        false
                ),
                new UIEvent("")) {
            @Override
            public void showSpinner(boolean status) {
                // Not under test
            }

            @Override
            public void sendResponse(int returnCode, Intent responseIntent) {
                // Not under test
            }

            @Override
            public void cancelWebViewRequest() {
                countDownLatch.countDown();
            }

            @Override
            public void prepareForBrokerResumeRequest() {
                // Not under test
            }

            @Override
            public void setPKeyAuthStatus(boolean status) {
                // Not under test
            }

            @Override
            public void postRunnable(Runnable item) {
                // Not under test
            }

            @Override
            public void processRedirectUrl(WebView view, String url) {
                // Not under test
            }

            @Override
            public boolean processInvalidUrl(WebView view, String url) {
                return false;
            }

            @Override
            protected void openLinkInBrowser(String url) {
                assertEquals(url, TEST_EXTERNAL_SITE_URL);
                countDownLatch.countDown();
            }
        };

        // Load the external url
        dummyClient.shouldOverrideUrlLoading(mMockWebView, TEST_EXTERNAL_SITE_URL);

        // Since we can neither spy() nor mock() this class (pkg private)
        // we're going to use a CountDownLatch that gets decremented in the
        // overridden methods we would normally verify()
        if (!countDownLatch.await(1, TimeUnit.SECONDS)) {
            fail();
        }

        Mockito.verify(mMockWebView).stopLoading();
    }

    @SmallTest
    public void testUrlOverrideHandlesInstallRequests() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final BasicWebViewClient dummyClient = new BasicWebViewClient(
                getContext(),
                TEST_INSTALL_REQUEST_URL,
                new AuthenticationRequest(
                        "NA",
                        "NA",
                        "NA",
                        "NA",
                        "user",
                        false
                ),
                new UIEvent("")) {
            @Override
            public void showSpinner(boolean status) {
                // Not under test
            }

            @Override
            public void sendResponse(int returnCode, Intent responseIntent) {
                // Not under test
            }

            @Override
            public void cancelWebViewRequest() {
                // Not under test
            }

            @Override
            public void prepareForBrokerResumeRequest() {
                countDownLatch.countDown();
            }

            @Override
            public void setPKeyAuthStatus(boolean status) {
                // Not under test
            }

            @Override
            public void postRunnable(Runnable item) {
                // Not under test
            }

            @Override
            public void processRedirectUrl(WebView view, String url) {
                // Not under test
            }

            @Override
            public boolean processInvalidUrl(WebView view, String url) {
                return false;
            }

            @Override
            protected void openLinkInBrowser(String url) {
                assertEquals(url, "myapplink.com");
                countDownLatch.countDown();
            }
        };

        dummyClient.shouldOverrideUrlLoading(
                mMockWebView,
                AuthenticationConstants.Broker.BROWSER_EXT_INSTALL_PREFIX
                        + "https://testdomain.com?app_link=myapplink.com"
        );

        if (!countDownLatch.await(1, TimeUnit.SECONDS)) {
            fail();
        }
    }

    @SmallTest
    public void testOnReceivedErrorSendsIntentWithErrorData() throws InterruptedException {
        final int errCode = 400;
        final String errMsg = "Bad Request";
        final CountDownLatch latch = new CountDownLatch(1);
        final BasicWebViewClient dummyClient = new BasicWebViewClient(
                getContext(),
                TEST_INSTALL_REQUEST_URL,
                new AuthenticationRequest(
                        "NA",
                        "NA",
                        "NA",
                        "NA",
                        "user",
                        false
                ),
                new UIEvent("")) {
            @Override
            public void showSpinner(boolean status) {
                // Not under test
            }

            @Override
            public void sendResponse(int returnCode, Intent responseIntent) {
                assertEquals(returnCode, AuthenticationConstants.UIResponse.BROWSER_CODE_ERROR);
                final String errString = responseIntent.getStringExtra(RESPONSE_ERROR_CODE);
                final String intentErrMsg = responseIntent.getStringExtra(RESPONSE_ERROR_MESSAGE);
                assertTrue(errString.contains(String.valueOf(errCode)));
                assertEquals(errMsg, intentErrMsg);
                latch.countDown();
            }

            @Override
            public void cancelWebViewRequest() {
                // Not under test
            }

            @Override
            public void prepareForBrokerResumeRequest() {
                // Not under test
            }

            @Override
            public void setPKeyAuthStatus(boolean status) {
                // Not under test
            }

            @Override
            public void postRunnable(Runnable item) {
                // Not under test
            }

            @Override
            public void processRedirectUrl(WebView view, String url) {
                // Not under test
            }

            @Override
            public boolean processInvalidUrl(WebView view, String url) {
                return false;
            }

            @Override
            protected void openLinkInBrowser(String url) {
                // Not under test
            }
        };

        dummyClient.onReceivedError(
                mMockWebView,
                errCode,
                errMsg,
                TEST_EXTERNAL_SITE_URL
        );

        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail();
        }
    }

    @SmallTest
    public void testSslErrorsSendsIntentWithErrorData() throws InterruptedException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore keystore = JwsBuilderTests.loadTestCertificate(getContext());
        Certificate cert = keystore.getCertificate(JwsBuilderTests.TEST_CERT_ALIAS);
        final SslError sslError = new SslError(SslError.SSL_DATE_INVALID, (X509Certificate) cert);
        final CountDownLatch latch = new CountDownLatch(1);
        final BasicWebViewClient dummyClient = new BasicWebViewClient(
                getContext(),
                TEST_INSTALL_REQUEST_URL,
                new AuthenticationRequest(
                        "NA",
                        "NA",
                        "NA",
                        "NA",
                        "user",
                        false
                ),
                new UIEvent("")) {
            @Override
            public void showSpinner(boolean status) {
                // Not under test
            }

            @Override
            public void sendResponse(int returnCode, Intent responseIntent) {
                assertEquals(returnCode, AuthenticationConstants.UIResponse.BROWSER_CODE_ERROR);
                final String errString = responseIntent.getStringExtra(RESPONSE_ERROR_CODE);
                final String intentErrMsg = responseIntent.getStringExtra(RESPONSE_ERROR_MESSAGE);
                assertTrue(errString.contains(String.valueOf(ERROR_FAILED_SSL_HANDSHAKE)));
                assertEquals(sslError.toString(), intentErrMsg);
                latch.countDown();
            }

            @Override
            public void cancelWebViewRequest() {
                // Not under test
            }

            @Override
            public void prepareForBrokerResumeRequest() {
                // Not under test
            }

            @Override
            public void setPKeyAuthStatus(boolean status) {
                // Not under test
            }

            @Override
            public void postRunnable(Runnable item) {
                // Not under test
            }

            @Override
            public void processRedirectUrl(WebView view, String url) {
                // Not under test
            }

            @Override
            public boolean processInvalidUrl(WebView view, String url) {
                return false;
            }

            @Override
            protected void openLinkInBrowser(String url) {
                // Not under test
            }
        };

        dummyClient.onReceivedSslError(
                mMockWebView,
                Mockito.mock(SslErrorHandler.class),
                sslError
        );

        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail();
        }
    }
}
