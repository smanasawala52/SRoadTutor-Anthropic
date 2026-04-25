package com.sroadtutor.auth.service;

import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.User;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FacebookOAuthServiceTest {

    private AppProperties props;

    @BeforeEach
    void props() {
        props = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 15, 30, "iss"),
                new AppProperties.OAuth(
                        new AppProperties.OAuth.Google("cid", "csec"),
                        new AppProperties.OAuth.Facebook("fbid", "fbsecret")),
                new AppProperties.Cors(List.of("*"), "GET", "Authorization", true, 3600L));
    }

    @Test
    void verify_returnsIdentityWhenGraphReturnsUser() {
        FacebookClient fb = mock(FacebookClient.class);
        User me = mock(User.class);
        when(me.getId()).thenReturn("fb-id-1");
        when(me.getEmail()).thenReturn("fb@example.com");
        when(me.getName()).thenReturn("Fran");
        when(fb.fetchObject(eq("me"), eq(User.class), any(Parameter.class))).thenReturn(me);

        FacebookOAuthService svc = new FacebookOAuthService(props, token -> fb);
        OAuthVerifier out = svc.verify("access-token");

        assertThat(out.providerUserId()).isEqualTo("fb-id-1");
        assertThat(out.email()).isEqualTo("fb@example.com");
        assertThat(out.fullName()).isEqualTo("Fran");
    }

    @Test
    void verify_fallsBackToPlaceholderEmailWhenGraphOmitsIt() {
        FacebookClient fb = mock(FacebookClient.class);
        User me = mock(User.class);
        when(me.getId()).thenReturn("fb-id-2");
        when(me.getEmail()).thenReturn(null);
        when(me.getName()).thenReturn("No Email");
        when(fb.fetchObject(eq("me"), eq(User.class), any(Parameter.class))).thenReturn(me);

        FacebookOAuthService svc = new FacebookOAuthService(props, token -> fb);
        OAuthVerifier out = svc.verify("access-token");
        assertThat(out.email()).isEqualTo("facebook_fb-id-2@users.noreply.sroadtutor.app");
    }

    @Test
    void verify_rejectsWhenGraphReturnsEmptyId() {
        FacebookClient fb = mock(FacebookClient.class);
        User me = mock(User.class);
        when(me.getId()).thenReturn("");
        when(fb.fetchObject(eq("me"), eq(User.class), any(Parameter.class))).thenReturn(me);

        FacebookOAuthService svc = new FacebookOAuthService(props, token -> fb);
        assertThatThrownBy(() -> svc.verify("tok"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
